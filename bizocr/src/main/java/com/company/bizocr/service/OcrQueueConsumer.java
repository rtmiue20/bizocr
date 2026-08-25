package com.company.bizocr.service;

import com.company.bizocr.model.entity.DocumentRecord;
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

    @Scheduled(fixedDelay = 2000)
    public void processQueue() {
        log.info("OCR CONSUMER TICK chạy mỗi 2s");

        redisTemplate.opsForList()
                .rightPop(QUEUE_NAME)
                .doOnNext(docId ->
                        log.info("REDIS POP thành công, documentId={}", docId))
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("REDIS QUEUE RỖNG: {}", QUEUE_NAME);
                    return Mono.empty();
                }))
                .flatMap(docIdStr -> {
                    Long docId = Long.valueOf(docIdStr);

                    return documentRepository.findById(docId)
                            .doOnNext(record ->
                                    log.info("Đã tìm thấy document id={}, path={}",
                                            record.getId(),
                                            record.getFilePath()))
                            .switchIfEmpty(Mono.error(
                                    new IllegalStateException(
                                            "Không tìm thấy document id=" + docId)))
                            .flatMap(record ->
                                    loadFileAsByteArrayResource(record.getFilePath())
                                            .flatMap(resource ->
                                                    pipelineOrchestrator
                                                            .processDocument(resource, record)
                                            )
                            );
                })
                .doOnError(error ->
                        log.error("Lỗi toàn bộ OCR consumer", error))
                .subscribe(
                        result -> log.info("OCR job hoàn tất"),
                        error -> log.error("Subscription OCR consumer thất bại", error)
                );
    }

    private Mono<DocumentRecord> processJob(String docIdStr) {
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