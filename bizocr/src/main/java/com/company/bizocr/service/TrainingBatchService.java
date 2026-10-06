package com.company.bizocr.service;

import com.company.bizocr.client.PythonMlClient;
import com.company.bizocr.model.entity.Document;
import com.company.bizocr.model.entity.TrainingBatch;
import com.company.bizocr.model.entity.TrainingBatchStatus;
import com.company.bizocr.repository.DocumentRepository;
import com.company.bizocr.repository.TrainingBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingBatchService {

    private final TrainingBatchRepository batchRepository;
    private final DocumentRepository documentRepository;
    private final PythonMlClient pythonMlClient;

    @Value("${bizocr.learning.batch-size:25}")
    private int batchSize = 25;

    /**
     * Xếp một document đủ điều kiện vào lô huấn luyện hiện tại.
     * Nếu lô hiện tại đạt ngưỡng batch-size thì đánh dấu READY và kích hoạt Python ML huấn luyện.
     */
    public Mono<TrainingBatch> enrollDocument(Document doc) {
        if (doc == null || doc.getId() == null) {
            return Mono.empty();
        }
        if (!Boolean.TRUE.equals(doc.getTrainingEligible())) {
            log.debug("[TrainingBatch] Doc id={} không đủ điều kiện học (training_eligible != true), bỏ qua.", doc.getId());
            return Mono.empty();
        }
        if (doc.getTrainingBatchId() != null) {
            log.debug("[TrainingBatch] Doc id={} đã thuộc lô batchId={}, bỏ qua.", doc.getId(), doc.getTrainingBatchId());
            return Mono.empty();
        }

        return getOrCreateOpenBatch(doc.getModelVersion())
                .flatMap(batch -> documentRepository.assignToBatchIfEligible(doc.getId(), batch.getId())
                        .flatMap(rows -> {
                            if (rows == 0) {
                                log.warn("[TrainingBatch] Không thể gắn doc id={} vào batch id={} (có thể race-condition hoặc không đủ điều kiện)",
                                        doc.getId(), batch.getId());
                                return Mono.just(batch);
                            }

                            doc.setTrainingBatchId(batch.getId());
                            return batchRepository.incrementBatchSize(batch.getId())
                                    .flatMap(incRows -> batchRepository.findById(batch.getId()))
                                    .flatMap(updatedBatch -> {
                                        log.info("[TrainingBatch] Đã thêm doc id={} vào batch id={} (kích thước hiện tại: {}/{})",
                                                doc.getId(), updatedBatch.getId(), updatedBatch.getSize(), batchSize);

                                        if (updatedBatch.getSize() >= batchSize) {
                                            return triggerBatchExecution(updatedBatch);
                                        }
                                        return Mono.just(updatedBatch);
                                    });
                        }));
    }

    /**
     * Tìm lô OPEN hiện tại, nếu chưa có thì tạo mới.
     */
    public Mono<TrainingBatch> getOrCreateOpenBatch(String currentModelVersion) {
        return batchRepository.findLatestByStatus(TrainingBatchStatus.OPEN.name())
                .switchIfEmpty(Mono.defer(() -> {
                    TrainingBatch newBatch = TrainingBatch.builder()
                            .status(TrainingBatchStatus.OPEN.name())
                            .size(0)
                            .modelVersionBefore(currentModelVersion != null ? currentModelVersion : "unknown")
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    log.info("[TrainingBatch] Tạo lô huấn luyện mới với modelVersionBefore={}", currentModelVersion);
                    return batchRepository.save(newBatch);
                }));
    }

    /**
     * Chuyển batch sang READY và kích hoạt Python ML triggerLearnBatch.
     * Khi Python xác nhận nhận task thì chuyển sang RUNNING.
     */
    private Mono<TrainingBatch> triggerBatchExecution(TrainingBatch batch) {
        log.info("[TrainingBatch] Batch id={} đã gom đủ {} tài liệu. Đánh dấu READY và kích hoạt Python ML...",
                batch.getId(), batch.getSize());

        return batchRepository.updateStatus(batch.getId(), TrainingBatchStatus.READY.name())
                .then(pythonMlClient.triggerLearnBatch(batch.getId()))
                .then(batchRepository.updateStatus(batch.getId(), TrainingBatchStatus.RUNNING.name()))
                .then(batchRepository.findById(batch.getId()))
                .doOnSuccess(b -> log.info("[TrainingBatch] Batch id={} đã kích hoạt thành công, trạng thái chuyển sang RUNNING", batch.getId()))
                .doOnError(err -> log.error("[TrainingBatch] Lỗi khi kích hoạt Python ML cho batch id={}: {}",
                        batch.getId(), err.getMessage()));
    }
}
