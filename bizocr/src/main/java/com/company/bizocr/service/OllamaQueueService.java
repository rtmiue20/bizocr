package com.company.bizocr.service;

import com.company.bizocr.extract.DocumentFieldSchema;
import com.company.bizocr.model.dto.OllamaTaskPayload;
import com.company.bizocr.model.entity.Document;
import com.company.bizocr.repository.DocumentRepository;
import com.company.bizocr.validator.ExtractionValidatorService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
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
    private static final String AI_MODEL_VERSION = "ollama-local";
    private final Scheduler ollamaScheduler = Schedulers.newBoundedElastic(2, 100, "ollama-worker");
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private Disposable workerDisposable;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;
    private final ExtractionValidatorService extractionValidatorService;
    private final DocumentRepository documentRepository;

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

    /**
     * Phase cao (ví dụ: Integer.MAX_VALUE - 1000) đảm bảo:
     * - Khi STARTUP: LettuceConnectionFactory (Phase 0) khởi động trước, OllamaQueueService khởi động sau.
     * - Khi SHUTDOWN: Spring dừng SmartLifecycle theo thứ tự Phase giảm dần -> OllamaQueueService (Phase cao) dừng trước,
     * LettuceConnectionFactory (Phase 0) dừng sau, triệt tiêu 100% race condition "LettuceConnectionFactory has been STOPPED".
     */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 1000;
    }

    public synchronized void startWorker() {
        if (!isRunning.compareAndSet(false, true)) {
            log.warn(">> [Ollama Worker] Worker đã đang chạy.");
            return;
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
        if (!isRunning.get()) {
            return Mono.empty();
        }

        // 1. Pop task từ Redis
        return Mono.fromCallable(() -> redisTemplate.opsForList().rightPop(QUEUE_NAME))
                // 2. Nếu có task và service đang chạy thì đi tiếp
                .filter(jsonPayload -> jsonPayload != null && isRunning.get())
                // 3. Xử lý logic
                .flatMap(jsonPayload -> {
                    try {
                        OllamaTaskPayload task = objectMapper.readValue(jsonPayload, OllamaTaskPayload.class);
                        log.info(">> [Ollama Worker] Bắt đầu xử lý AI cho DocID: {}", task.getDocumentId());

                        String rawJsonResult = callOllamaSync(task.getDocumentType(), task.getRawText());
                        String validatedJson = extractionValidatorService.validateAndSanitize(task.getDocumentType(), rawJsonResult);

                        // Cố định bộ key theo loại chứng từ (NAMECARD -> contact_name, company_name, tax_code, ...).
                        // Loại chưa khai báo schema thì giữ nguyên.
                        String finalValidatedJson = DocumentFieldSchema.normalize(task.getDocumentType(), validatedJson, objectMapper);

                        log.info(">> [Ollama Worker] Hoàn tất bóc tách AI cho DocID: {}. Result: {}", task.getDocumentId(), finalValidatedJson);

                        Long docId = Long.valueOf(task.getDocumentId());

                        // 4. Luồng Reactive DB: Gọi findById -> Cập nhật entity -> Gọi save
                        return documentRepository.findById(docId)
                                .flatMap(doc -> {
                                    doc.setExtractedFields(finalValidatedJson);

                                    try {
                                        JsonNode rootNode = objectMapper.readTree(finalValidatedJson);
                                        // Ưu tiên key chuẩn (contact_name/contact_title), fallback key cũ (name/title)
                                        String contactName = firstText(rootNode, "contact_name", "name");
                                        String contactTitle = firstText(rootNode, "contact_title", "title");
                                        if (contactName != null) {
                                            doc.setContactName(contactName);
                                        }
                                        if (contactTitle != null) {
                                            doc.setContactTitle(contactTitle);
                                        }
                                    } catch (Exception ex) {
                                        log.warn(">> [Ollama Worker] Lỗi parse JSON map field cho DocID: {}", docId);
                                    }

                                    // Return lệnh lưu DB vào luồng chính
                                    return documentRepository.save(doc);
                                })
                                .doOnSuccess(saved -> log.info(">> [Ollama Worker] Đã cập nhật kết quả vào DB cho DocID: {}", docId))
                                // Chuyển kết quả cuối cùng về Mono<Void> để khớp với processNextTask()
                                .then();

                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                })
                // Hứng lỗi riêng lẻ không làm sập Worker
                .onErrorResume(error -> {
                    log.error(">> [Ollama Worker] Lỗi khi xử lý 1 task cụ thể, nuốt lỗi để worker tiếp tục chạy:", error);
                    return Mono.empty();
                });
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
        try {
            String jsonTemplate = getJsonTemplateForDocument(documentType);

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
                    - Bước 4 (Tự kiểm định): Rà soát lại JSON lần cuối, nếu phát hiện key rác hoặc key bị đổi tên ngoài khung [REFERENCE], hãy loại bỏ và đưa về đúng chuẩn trước khi xuất kết quả.
                    
                    [INPUT]
                    - Loại chứng từ: %s
                    - Văn bản OCR (RawText):
                    \"\"\"
                    %s
                    \"\"\"
                    """.formatted(jsonTemplate, documentType, rawText);

            String response = chatClient.prompt().user(promptText).call().content();

            // Làm sạch chuỗi trả về
            return response.replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();
        } catch (Exception e) {
            log.error(">> [Ollama Engine] Lỗi khi gọi Ollama", e);
            return "{\"error\":\"ollama_processing_failed\"}";
        }
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