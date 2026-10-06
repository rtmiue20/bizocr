package com.company.bizocr.repository;

import com.company.bizocr.model.entity.TrainingBatch;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TrainingBatchRepository extends ReactiveCrudRepository<TrainingBatch, Long> {

    /**
     * Tìm lô đang ở trạng thái chỉ định mới nhất (thường là OPEN).
     */
    @Query("SELECT * FROM training_batches WHERE status = :status ORDER BY id DESC LIMIT 1")
    Mono<TrainingBatch> findLatestByStatus(String status);

    Flux<TrainingBatch> findByStatus(String status);

    @Modifying
    @Query("UPDATE training_batches SET size = size + 1 WHERE id = :id")
    Mono<Integer> incrementBatchSize(Long id);

    @Modifying
    @Query("UPDATE training_batches SET status = :status WHERE id = :id")
    Mono<Integer> updateStatus(Long id, String status);

    @Modifying
    @Query("""
            UPDATE training_batches
               SET status = :status,
                   model_version_after = :versionAfter,
                   metrics_json = :metricsJson
             WHERE id = :id
            """)
    Mono<Integer> updateBatchCompletion(Long id, String status, String versionAfter, String metricsJson);
}
