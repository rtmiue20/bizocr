package com.company.bizocr.service;

import com.company.bizocr.model.dto.OllamaTaskPayload;
import com.company.bizocr.model.entity.Document;
import com.company.bizocr.repository.DocumentRepository;
import com.company.bizocr.validator.ExtractionValidatorService;
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
     *   LettuceConnectionFactory (Phase 0) dừng sau, triệt tiêu 100% race condition "LettuceConnectionFactory has been STOPPED".
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
                        success -> {},
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
        return Mono.<Void>fromCallable(() -> {
                    if (!isRunning.get()) return null;
                    String jsonPayload = redisTemplate.opsForList().rightPop(QUEUE_NAME);
                    if (jsonPayload == null) return null;

                    OllamaTaskPayload task = objectMapper.readValue(jsonPayload, OllamaTaskPayload.class);
                    log.info(">> [Ollama Worker] Bắt đầu xử lý AI cho DocID: {}", task.getDocumentId());

                    String rawJsonResult = callOllamaSync(task.getDocumentType(), task.getRawText());
                    String finalValidatedJson = extractionValidatorService.validateAndSanitize(task.getDocumentType(), rawJsonResult);

                    log.info(">> [Ollama Worker] Hoàn tất bóc tách AI cho DocID: {}. Result: {}", task.getDocumentId(), finalValidatedJson);
                    return null;
                })
                // Hứng lỗi của từng task độc lập
                .onErrorResume(error -> {
                    log.error(">> [Ollama Worker] Lỗi khi xử lý 1 task cụ thể, nuốt lỗi để worker tiếp tục chạy:", error);
                    return Mono.empty();
                });
    }

    private String callOllamaSync(String documentType, String rawText) {
        try {
            String promptText = """
            Bạn là chuyên gia trích xuất dữ liệu chứng từ kế toán Việt Nam.
            Loại chứng từ: %s
            Văn bản OCR RawText:
            \"\"\"
            %s
            \"\"\"
            Trả về DUY NHẤT một chuỗi JSON hợp lệ, không kèm Markdown codeblock, không giải thích.
            """.formatted(documentType, rawText);

            String response = chatClient.prompt().user(promptText).call().content();
            return response.replaceAll("```json", "").replaceAll("```", "").trim();
        } catch (Exception e) {
            log.error(">> [Ollama Engine] Lỗi khi gọi Ollama", e);
            return "{\"error\":\"ollama_processing_failed\"}";
        }
    }
}