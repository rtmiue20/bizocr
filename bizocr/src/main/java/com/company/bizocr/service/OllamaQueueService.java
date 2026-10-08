package com.company.bizocr.service;

import com.company.bizocr.extract.DocumentFieldSchema;
import com.company.bizocr.extract.NamecardSanitizer;
import com.company.bizocr.model.dto.OllamaTaskPayload;
import com.company.bizocr.repository.DocumentRepository;
import com.company.bizocr.validator.ExtractionValidatorService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class OllamaQueueService implements SmartLifecycle {

    private static final String QUEUE_NAME = "ocr:ollama:tasks";
    private static final String PROCESSING_QUEUE = "ocr:ollama:processing";
    private static final String FAILED_QUEUE = "ocr:ollama:failed";

    private final Scheduler ollamaScheduler = Schedulers.newBoundedElastic(2, 100, "ollama-worker");
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private Disposable workerDisposable;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;
    private final ExtractionValidatorService extractionValidatorService;
    private final DocumentRepository documentRepository;

    @Value("${spring.ai.ollama.chat.options.model:ollama-local}")
    private String ollamaModel;

    private record AiResult(Long docId, String json, String contactName, String contactTitle) {}

    public OllamaQueueService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ChatClient.Builder chatClientBuilder,
            ExtractionValidatorService extractionValidatorService,
            DocumentRepository documentRepository
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.chatClient = chatClientBuilder.build();
        this.extractionValidatorService = extractionValidatorService;
        this.documentRepository = documentRepository;
    }

    public boolean enqueueTask(String documentId, String documentType, String rawText) {
        if (!isRunning.get()) {
            log.warn(">> [Ollama Queue] Queue service đang trong trạng thái dừng hoặc chưa khởi động, không nhận task (DocID: {})", documentId);
            return false;
        }
        try {
            OllamaTaskPayload payload = new OllamaTaskPayload(documentId, documentType, rawText);
            String jsonPayload = objectMapper.writeValueAsString(payload);
            redisTemplate.opsForList().leftPush(QUEUE_NAME, jsonPayload);
            log.info(">> [Ollama Queue] Đã đẩy task vào Redis (DocID: {})", documentId);
            return true;
        } catch (Exception e) {
            log.error("Lỗi khi push task vào Redis Queue", e);
            return false;
        }
    }

    // ================== SMART LIFECYCLE MANAGEMENT ==================

    @Override
    public synchronized void start() {
        startWorker();
    }

    @Override
    public synchronized void stop() {
        stopWorker();
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return isRunning.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 1000;
    }

    public synchronized void startWorker() {
        if (!isRunning.compareAndSet(false, true)) {
            log.warn(">> [Ollama Worker] Worker đã đang chạy.");
            return;
        }

        // Khôi phục task bị kẹt khi khởi động
        String stuck;
        while ((stuck = redisTemplate.opsForList().rightPopAndLeftPush(PROCESSING_QUEUE, QUEUE_NAME)) != null) {
            log.warn(">> [Ollama Worker] Khôi phục task kẹt trong PROCESSING_QUEUE");
        }

        this.workerDisposable = Flux.interval(Duration.ofSeconds(1))
                .takeWhile(tick -> isRunning.get())
                .publishOn(ollamaScheduler)
                .flatMap(tick -> {
                    if (!isRunning.get()) {
                        return Mono.empty();
                    }
                    return processNextTask().onErrorResume(err -> {
                        log.error(">> [Ollama Worker] Lỗi khi thực thi processNextTask trong flatMap:", err);
                        return Mono.empty();
                    });
                }, 2)
                .subscribe(
                        success -> {
                        },
                        error -> log.error(">> [Ollama Worker] Worker đã chết vĩnh viễn vì lỗi:", error)
                );
        log.info(">> [Ollama Worker] Đã khởi động worker với Max Concurrency = 2 (Phase: {}).", getPhase());
    }

    @PreDestroy
    public synchronized void stopWorker() {
        if (!isRunning.compareAndSet(true, false)) {
            return;
        }
        log.info(">> [Ollama Worker] Đang dừng worker trước khi shutdown Redis kết nối...");
        if (workerDisposable != null && !workerDisposable.isDisposed()) {
            workerDisposable.dispose();
        }
        if (!ollamaScheduler.isDisposed()) {
            ollamaScheduler.dispose();
        }
        log.info(">> [Ollama Worker] Đã dừng worker và giải phóng scheduler thành công.");
    }

    private Mono<Void> processNextTask() {
        if (!isRunning.get()) return Mono.empty();
        return Mono.fromCallable(() ->
                        redisTemplate.opsForList().rightPopAndLeftPush(QUEUE_NAME, PROCESSING_QUEUE)) // nguyên tử
                .filter(payload -> payload != null && isRunning.get())
                .flatMap(payload -> handleTask(payload)
                        .onErrorResume(err -> {
                            log.error(">> [Ollama Worker] Task lỗi, chuyển sang FAILED_QUEUE:", err);
                            moveToFailed(payload);
                            return Mono.empty();
                        }));
    }

    private Mono<Void> handleTask(String payload) {
        return Mono.fromCallable(() -> {
                    OllamaTaskPayload task = objectMapper.readValue(payload, OllamaTaskPayload.class);
                    String raw = callOllamaSync(task.getDocumentType(), task.getRawText()); // ném lỗi nếu Ollama fail
                    String validated = extractionValidatorService.validateAndSanitize(task.getDocumentType(), raw);
                    String json = DocumentFieldSchema.normalize(task.getDocumentType(), validated, objectMapper);
                    JsonNode root = objectMapper.readTree(json);
                    if (root.isObject() && ("NAMECARD".equalsIgnoreCase(task.getDocumentType())
                            || "NAME_CARD".equalsIgnoreCase(task.getDocumentType())
                            || "DANH_THIEP".equalsIgnoreCase(task.getDocumentType()))) {
                        root = NamecardSanitizer.sanitize((ObjectNode) root, task.getRawText(), objectMapper);
                        json = objectMapper.writeValueAsString(root);
                    }
                    return new AiResult(Long.valueOf(task.getDocumentId()), json,
                            firstText(root, "contact_name", "name"),
                            firstText(root, "contact_title", "title"));
                })
                .flatMap(r -> documentRepository.updateAiExtractionIfNotReviewed(
                                r.docId(), r.json(), "ollama:" + ollamaModel, r.contactName(), r.contactTitle())
                        .doOnNext(rows -> {
                            if (rows == 0) log.info(">> [Ollama Worker] Bỏ kết quả AI cho DocID={} (đã có người đụng vào)", r.docId());
                            else log.info(">> [Ollama Worker] Đã ghi kết quả AI cho DocID={}", r.docId());
                        }))
                .doOnSuccess(x -> redisTemplate.opsForList().remove(PROCESSING_QUEUE, 1, payload))
                .then();
    }

    private void moveToFailed(String payload) {
        redisTemplate.opsForList().remove(PROCESSING_QUEUE, 1, payload);
        redisTemplate.opsForList().leftPush(FAILED_QUEUE, payload);
        try {
            OllamaTaskPayload task = objectMapper.readValue(payload, OllamaTaskPayload.class);
            if (task.getDocumentId() != null) {
                Long docId = Long.valueOf(task.getDocumentId());
                String failedJson = objectMapper.writeValueAsString(java.util.Map.of(
                        "review_status", "AI_FAILED",
                        "document_id", docId,
                        "message", "AI bóc tách thất bại hoặc quá tải"
                ));
                documentRepository.updateAiExtractionIfNotReviewed(
                        docId, failedJson, "ollama:failed", null, null
                ).subscribe();
            }
        } catch (Exception e) {
            log.error("Lỗi khi cập nhật trạng thái thất bại cho task", e);
        }
    }

    /**
     * Lấy giá trị chữ không rỗng đầu tiên trong các key được liệt kê; không có thì trả null.
     */
    private String firstText(JsonNode node, String... keys) {
        if (node == null) return null;
        for (String key : keys) {
            JsonNode v = node.get(key);
            if (v != null && !v.isNull()) {
                String s = v.asText().trim();
                if (!s.isEmpty()) return s;
            }
        }
        return null;
    }

    private String callOllamaSync(String documentType, String rawText) {
        String jsonTemplate = getJsonTemplateForDocument(documentType);

        String specificInstructions = "";
        if ("NAMECARD".equalsIgnoreCase(documentType) || "NAME_CARD".equalsIgnoreCase(documentType) || "DANH_THIEP".equalsIgnoreCase(documentType)) {
            specificInstructions = """
                
                [QUY TẮC BẮT BUỘC RIÊNG CHO DANH THIẾP / NAMECARD]
                1. SĐT và Email: Trích xuất các số điện thoại và email tìm thấy từ thẻ (kèm nhãn nếu có). KHÔNG gộp nhiều số điện thoại vào một trường.
                2. Tên công ty (company_name): Lấy tên thương hiệu / tên công ty viết hoa (vd: "FORLYBRAND", "KHAI HOAN LAND"). TUYỆT ĐỐI KHÔNG điền tên miền website vào trường tên công ty.
                3. Tên người (contact_name) & Chức danh (contact_title): Bóc tách đúng thông tin cá nhân của người sở hữu danh thiếp.
                4. Nếu trường nào không có dữ liệu, hãy để null. Bộ định tuyến hệ thống sẽ tự động gán vào khối cá nhân hoặc công ty theo quy tắc chuẩn.
                """;
        }

        String promptText = """
                [TASK]
                Trích xuất thông tin cốt lõi từ văn bản OCR thô và điền chính xác vào khung JSON mẫu.
                
                [CONTEXT]
                Bạn là chuyên gia AI bóc tách chứng từ, hóa đơn và danh thiếp Việt Nam. Văn bản OCR đầu vào thường chứa nhiều nhiễu, câu chữ rác (quảng cáo, slogan), lỗi xuống dòng và bị khuyết dữ liệu.
                
                [REFERENCE]
                Khung JSON mẫu bắt buộc dùng làm mốc đối chiếu:
                %s
                
                [EVALUATION / EXPECTED OUTPUT]
                1. Trả về DUY NHẤT một chuỗi JSON hợp lệ.
                2. KHÔNG bọc trong Markdown codeblock (```json).
                3. KHÔNG chứa lời chào, giải thích hay bất kỳ văn bản nào khác ngoài JSON.
                4. Bắt buộc sử dụng đúng 100%% tên key từ khung mẫu tại [REFERENCE].
                
                [ITERATE / REFINEMENT INSTRUCTIONS]
                - Bước 1 (Lọc rác): Quét văn bản OCR và chủ động loại bỏ các từ rác/slogan (vd: "Cloud", "Server", "Free Shipping", "Mẫu số...").
                - Bước 2 (Khớp dữ liệu): Đối chiếu từng thông tin tìm thấy với các key tương ứng trong khung [REFERENCE].
                - Bước 3 (Xử lý khuyết): Nếu một key không có dữ liệu trong văn bản, BẮT BUỘC gán giá trị key đó là null (không tự bịa hay suy diễn).
                - Bước 4 (Tự kiểm định): Rà soát lại JSON lần cuối, nếu phát hiện key rác hoặc key bị đổi tên ngoài khung [REFERENCE], hãy loại bỏ và đưa về đúng chuẩn trước khi xuất kết quả.%s
                
                [INPUT]
                - Loại chứng từ: %s
                - Văn bản OCR (RawText):
                \"\"\"
                %s
                \"\"\"
                """.formatted(jsonTemplate, specificInstructions, documentType, rawText);

        String response = chatClient.prompt().user(promptText).call().content();

        if (response == null || response.isBlank()) {
            throw new IllegalStateException("Ollama trả về rỗng");
        }

        // Làm sạch chuỗi trả về
        return response.replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*", "")
                .trim();
    }

    /**
     * Trả về khung JSON mẫu theo loại chứng từ.
     * Khung được SINH từ DocumentFieldSchema nên key luôn đúng bộ key chuẩn (và luôn là JSON hợp lệ).
     * Chỉ loại tùy biến chưa có schema mới dùng Khung Mẫu Tổng Quát bên dưới.
     */
    private String getJsonTemplateForDocument(String documentType) {
        String template = DocumentFieldSchema.jsonTemplate(documentType);
        if (template != null) {
            return template;
        }

        return """
                {
                  "documentTitle": "Tên loại chứng từ / Tiêu đề giấy tờ",
                  "documentNumber": "Số hiệu / Mã số chứng từ",
                  "issueDate": "Ngày tháng năm",
                  "issuerName": "Tên đơn vị / Cá nhân phát hành",
                  "taxCode": "Mã số thuế (nếu có)",
                  "personInCharge": "Họ tên người liên quan / Đại diện / Chức vụ",
                  "title": "Chức danh / Vị trí công tác (nếu có)",
                  "phoneNumber": "Số điện thoại liên hệ",
                  "email": "Email liên hệ",
                  "address": "Địa chỉ ghi trên chứng từ",
                  "totalAmount": "Số tiền / Giá trị (nếu có)",
                  "note": "Ghi chú / Nội dung chính"
                }
                """;
    }
}