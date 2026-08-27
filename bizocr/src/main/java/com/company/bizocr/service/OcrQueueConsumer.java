package com.company.bizocr.service;

import com.company.bizocr.model.entity.Document;
import com.company.bizocr.pipeline.PipelineOrchestrator;
import com.company.bizocr.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.core.publisher.Flux;
import java.util.concurrent.atomic.AtomicBoolean;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrQueueConsumer {

    private static final String QUEUE_NAME = "ocr_task_queue";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final DocumentRepository documentRepository;
    private final PipelineOrchestrator pipelineOrchestrator;

    // Đường dẫn tuyệt đối tới thư mục gốc "uploads"
    private final Path rootLocation = Paths.get("uploads").toAbsolutePath().normalize();
    private final AtomicBoolean batchRunning = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${ocr.queue.poll-delay-ms:2000}")
    public void processQueue() {
        if (!batchRunning.compareAndSet(false, true)) {
            log.debug("Batch OCR trước vẫn đang chạy");
            return;
        }
        log.info("OCR CONSUMER TICK");
        Flux.range(0, 10)
                .flatMap(i ->
                                redisTemplate.opsForList()
                                        .rightPop(QUEUE_NAME),
                        10
                )
                .doOnNext(docId ->
                        log.info(
                                "REDIS POP thành công, documentId={}",
                                docId
                        ))
                .flatMap(
                        docId -> processJob(docId)
                                .doOnError(error ->
                                        log.error(
                                                "Job {} thất bại",
                                                docId,
                                                error
                                        ))
                                .onErrorResume(error -> Mono.empty()),
                        5
                )
                .doFinally(signal ->
                        batchRunning.set(false))
                .subscribe(
                        result -> log.info(
                                "OCR job hoàn tất, documentId={}",
                                result.getId()
                        ),
                        error -> log.error(
                                "OCR batch thất bại",
                                error
                        )
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
            // Ghép thư mục gốc "uploads" với chuỗi đường dẫn trong DB
            Path path = rootLocation
                    .resolve(filePathStr)
                    .normalize()
                    .toAbsolutePath();

            // Chống lỗi bảo mật Path Traversal (ví dụ: ../../etc/passwd)
            if (!path.startsWith(rootLocation)) {
                throw new SecurityException("Đường dẫn file không hợp lệ: " + filePathStr);
            }

            // Kiểm tra file tồn tại và có quyền đọc không
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