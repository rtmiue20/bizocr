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
                        result -> {
                            // Lớp phòng thủ: chỉ log "thành công" đúng nghĩa khi status thật sự
                            // không phải FAILED — tránh log sai lệch nếu pipeline sau này lại
                            // vô tình "nuốt" lỗi mà không propagate ra ngoài.
                            if ("FAILED".equals(result.getStatus())) {
                                log.warn("OCR job kết thúc với trạng thái FAILED, documentId={}", result.getId());
                            } else {
                                log.info("OCR job hoàn tất thành công, documentId={}, status={}", result.getId(), result.getStatus());
                            }
                        },
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

    // ─────────────────────────────────────────────────────────────────────────
    // REQUEUE: Chạy lại document từ DLQ
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Chạy lại 1 document cụ thể từ DLQ về queue chính.
     * Dùng để retry document 2127 (hoặc bất kỳ documentId nào) sau khi đã fix lỗi.
     *
     * <pre>
     * Gọi thủ công qua REST endpoint hoặc actuator:
     *   POST /api/v1/admin/ocr/requeue/{documentId}
     * </pre>
     *
     * @param documentId ID của document cần chạy lại
     * @return Mono<Boolean> true nếu đã đẩy vào queue thành công
     */
    public Mono<Boolean> requeueDocumentFromDlq(Long documentId) {
        String docIdStr = String.valueOf(documentId);
        log.info("[Requeue] Chuẩn bị chạy lại documentId={} từ DLQ", documentId);

        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(
                        new IllegalArgumentException("Không tìm thấy document: " + documentId)))
                .flatMap(doc -> {
                    // Reset status về PENDING để pipeline xử lý lại từ đầu
                    doc.setStatus("PENDING");
                    return documentRepository.save(doc);
                })
                .flatMap(doc ->
                        // Xóa khỏi DLQ (nếu còn đó) để tránh xử lý 2 lần
                        redisTemplate.opsForList()
                                .remove(DLQ_NAME, 0, docIdStr)
                                .then(
                                        // Đẩy vào đầu queue chính (leftPush = ưu tiên xử lý sớm)
                                        redisTemplate.opsForList().leftPush(QUEUE_NAME, docIdStr)
                                )
                )
                .map(pushed -> {
                    log.info("[Requeue] documentId={} đã được đẩy vào queue chính thành công", documentId);
                    return true;
                })
                .doOnError(e -> log.error("[Requeue] Lỗi khi requeue documentId={}: {}", documentId, e.getMessage(), e));
    }

    /**
     * Chạy lại toàn bộ document trong DLQ.
     * Dùng sau khi đã fix lỗi hệ thống (VD: sau khi fix CompanyFinderService).
     *
     * @return Mono<Long> số lượng document đã được đẩy lại vào queue
     */
    public Mono<Long> requeueAllFromDlq() {
        log.info("[Requeue] Bắt đầu chạy lại toàn bộ DLQ...");

        return redisTemplate.opsForList()
                .size(DLQ_NAME)
                .flatMap(size -> {
                    if (size == null || size == 0) {
                        log.info("[Requeue] DLQ rỗng, không có gì để chạy lại");
                        return Mono.just(0L);
                    }
                    log.info("[Requeue] DLQ có {} document, bắt đầu chuyển về queue chính", size);

                    // Pop từng phần tử từ DLQ và push vào queue chính
                    return Flux.range(0, size.intValue())
                            .flatMap(i -> redisTemplate.opsForList().rightPop(DLQ_NAME))
                            .filter(docId -> docId != null && !docId.isBlank())
                            .flatMap(docId ->
                                    documentRepository.findById(Long.valueOf(docId))
                                            .flatMap(doc -> {
                                                doc.setStatus("PENDING");
                                                return documentRepository.save(doc);
                                            })
                                            .flatMap(doc ->
                                                    redisTemplate.opsForList().leftPush(QUEUE_NAME, docId)
                                            )
                                            .doOnNext(r -> log.info("[Requeue] documentId={} đã đẩy lại queue", docId))
                                            .onErrorResume(e -> {
                                                log.error("[Requeue] Bỏ qua documentId={} do lỗi: {}", docId, e.getMessage());
                                                return Mono.just(0L);
                                            })
                            )
                            .count();
                })
                .doOnSuccess(n -> log.info("[Requeue] Đã chạy lại {} document từ DLQ", n));
    }
}