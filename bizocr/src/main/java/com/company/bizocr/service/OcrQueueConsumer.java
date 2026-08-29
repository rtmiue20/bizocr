package com.company.bizocr.service;

import com.company.bizocr.model.entity.Document;
import com.company.bizocr.pipeline.PipelineOrchestrator;
import com.company.bizocr.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrQueueConsumer {

    private static final String QUEUE_NAME = "ocr_task_queue";
    private static final String PROCESSING_ZSET_NAME = "ocr_task_queue:processing";
    private static final String DLQ_NAME = "ocr_task_queue:dlq";

    // Thời hạn tối đa cho 1 job xử lý trước khi coi là mồ côi (VD: 5 phút)
    private static final long JOB_TIMEOUT_MS = 5 * 60 * 1000L;

    private final ReactiveStringRedisTemplate redisTemplate;
    private final DocumentRepository documentRepository;
    private final PipelineOrchestrator pipelineOrchestrator;

    private final Path rootLocation = Paths.get("uploads").toAbsolutePath().normalize();
    private final AtomicBoolean batchRunning = new AtomicBoolean(false);

    @Scheduled(fixedDelay = 1000)
    public void processQueue() {
        if (!batchRunning.compareAndSet(false, true)) {
            log.debug("Batch OCR trước vẫn đang chạy...");
            return;
        }

        Flux.range(0, 10)
                .flatMap(i -> redisTemplate.opsForList().rightPop(QUEUE_NAME), 10)
                .filter(docId -> docId != null && !docId.isBlank())
                .flatMap(docId -> {
                    long currentTime = System.currentTimeMillis();
                    // 1. Ghi nhận job vào Processing ZSET với score = timestamp hiện tại
                    return redisTemplate.opsForZSet()
                            .add(PROCESSING_ZSET_NAME, docId, currentTime)
                            .thenReturn(docId);
                })
                .doOnNext(docId -> log.info("REDIS POP thành công, đã ghi nhận vào Processing ZSET, documentId={}", docId))
                .flatMap(docId ->
                                processJob(docId)
                                        .flatMap(doc ->
                                                // 2. Xử lý thành công -> Xóa khỏi Processing ZSET
                                                redisTemplate.opsForZSet()
                                                        .remove(PROCESSING_ZSET_NAME, docId)
                                                        .thenReturn(doc)
                                        )
                                        .doOnError(error -> log.error("Job {} xử lý thất bại, chuyển sang DLQ", docId, error))
                                        .onErrorResume(error ->
                                                // 3. Xử lý thất bại -> Xóa khỏi ZSET và chuyển sang Dead Letter Queue
                                                redisTemplate.opsForZSet()
                                                        .remove(PROCESSING_ZSET_NAME, docId)
                                                        .then(redisTemplate.opsForList().leftPush(DLQ_NAME, docId))
                                                        .then(Mono.empty())
                                        ),
                        5
                )
                .doFinally(signal -> batchRunning.set(false))
                .subscribe(
                        result -> log.info("OCR job hoàn tất thành công, documentId={}", result.getId()),
                        error -> log.error("Lỗi hệ thống trong OCR batch loop", error)
                );
    }

    /**
     * Sweeper Job: Quét định kỳ 1 phút/lần để thu hồi các job mồ côi (treo > 5 phút)
     * Redis Distributed Lock (Khóa phân tán cho Scheduled Task)
     */
    @Scheduled(fixedRate = 60000)
    public void recoverOrphanJobs() {
        String lockKey = "lock:ocr_sweeper";

        // 1. Thử xin lock trong 55 giây (ngắn hơn fixedRate 60s để tự nhả lock)
        redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "LOCKED", java.time.Duration.ofSeconds(55))
                .flatMap(acquired -> {
                    if (Boolean.FALSE.equals(acquired)) {
                        // Instance khác đã nhận task này rồi, bỏ qua
                        return Mono.empty();
                    }

                    log.info("[Sweeper] Giữ Lock thành công. Bắt đầu kiểm tra job mồ côi...");
                    long cutoffTime = System.currentTimeMillis() - JOB_TIMEOUT_MS;

                    return redisTemplate.opsForZSet()
                            .rangeByScore(PROCESSING_ZSET_NAME, Range.closed(0.0, (double) cutoffTime))
                            .flatMap(docId ->
                                    redisTemplate.opsForZSet()
                                            .remove(PROCESSING_ZSET_NAME, docId)
                                            .filter(count -> count > 0)
                                            .flatMap(count -> {
                                                log.warn("[Sweeper] Đẩy lại job mồ côi #{} vào queue chính", docId);
                                                return redisTemplate.opsForList().leftPush(QUEUE_NAME, docId);
                                            })
                            )
                            .then();
                })
                .subscribe(
                        null,
                        error -> log.error("[Sweeper] Lỗi trong quá trình quét Sweeper Lock", error)
                );
    }

    private Mono<Document> processJob(String docIdStr) {
        return Mono.fromCallable(() -> Long.valueOf(docIdStr))
                .flatMap(documentRepository::findById)
                .switchIfEmpty(Mono.error(
                        new IllegalStateException("Không tìm thấy document trong DB: " + docIdStr)))
                .flatMap(record ->
                        loadFileAsByteArrayResource(record.getFilePath())
                                .flatMap(resource ->
                                        pipelineOrchestrator.processDocument(resource, record)
                                )
                );
    }

    private Mono<Resource> loadFileAsByteArrayResource(String filePathStr) {
        return Mono.fromCallable(() -> {
            if (filePathStr == null || filePathStr.isBlank()) {
                throw new IllegalArgumentException("Đường dẫn file trong DB bị rỗng");
            }

            Path path = rootLocation
                    .resolve(filePathStr)
                    .normalize()
                    .toAbsolutePath();

            if (!path.startsWith(rootLocation)) {
                throw new SecurityException("Đường dẫn file không hợp lệ: " + filePathStr);
            }

            if (!Files.exists(path) || !Files.isReadable(path)) {
                throw new FileNotFoundException(
                        "Không tìm thấy hoặc không thể đọc file OCR: " + path);
            }

            byte[] bytes = Files.readAllBytes(path);

            return (Resource) new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return path.getFileName().toString();
                }
            };
        }).subscribeOn(Schedulers.boundedElastic());
    }
}