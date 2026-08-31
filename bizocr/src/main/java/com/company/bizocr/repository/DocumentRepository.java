package com.company.bizocr.repository;

import com.company.bizocr.model.entity.Document;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DocumentRepository extends ReactiveCrudRepository<Document, Long> {

    @Query("SELECT * FROM documents WHERE company_id = :companyId AND " +
            "(:status IS NULL OR status = :status) AND " +
            "(:documentType IS NULL OR document_type = :documentType) " +
            "ORDER BY id DESC LIMIT :limit OFFSET :offset")
    Flux<Document> findByFilterAndCompanyId(String status, String documentType, Long companyId, int limit, long offset);

    @Query("SELECT COUNT(*) FROM documents WHERE company_id = :companyId AND " +
            "(:status IS NULL OR status = :status) AND " +
            "(:documentType IS NULL OR document_type = :documentType)")
    Mono<Long> countByFilterAndCompanyId(String status, String documentType, Long companyId);

    @Query("SELECT CAST(JSON_UNQUOTE(JSON_EXTRACT(extracted_fields, '$.total_amount')) AS DOUBLE) " +
            "FROM documents " +
            "WHERE company_id = :companyId " +
            "  AND document_type = :docType " +
            "  AND JSON_UNQUOTE(JSON_EXTRACT(extracted_fields, '$.tax_code')) = :taxCode " +
            "  AND status = 'APPROVED' " +
            "ORDER BY id DESC LIMIT 20")
    Flux<Double> findHistoricalAmountsAndCompanyId(String docType, String taxCode, Long companyId);

    Mono<Document> findByIdAndCompanyId(Long id, Long companyId);

    Flux<Document> findByStatusAndCompanyId(String status, Long companyId);
}