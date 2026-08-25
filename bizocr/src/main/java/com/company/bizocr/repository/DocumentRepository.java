package com.company.bizocr.repository;

import com.company.bizocr.model.entity.DocumentRecord;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DocumentRepository extends ReactiveCrudRepository<DocumentRecord, Long> {

    @Query("SELECT * FROM documents WHERE " +
            "(:status IS NULL OR status = :status) AND " +
            "(:documentType IS NULL OR document_type = :documentType) " +
            "ORDER BY id DESC LIMIT :limit OFFSET :offset")
    Flux<DocumentRecord> findByFilter(String status, String documentType, int limit, long offset);

    @Query("SELECT COUNT(*) FROM documents WHERE " +
            "(:status IS NULL OR status = :status) AND " +
            "(:documentType IS NULL OR document_type = :documentType)")
    Mono<Long> countByFilter(String status, String documentType);
}