package com.company.bizocr.repository;

import com.company.bizocr.model.entity.Document;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DocumentRepository extends ReactiveCrudRepository<Document, Long> {

    // =========================================================================
    // FSM — Conditional UPDATE (trả về số row bị ảnh hưởng)
    // =========================================================================

    /**
     * Chuyển trạng thái document theo kiểu optimistic-lock:
     * chỉ UPDATE khi status hiện tại trong DB đúng bằng {@code fromStatus}.
     *
     * <p>Trả về:
     * <ul>
     *   <li>{@code 1} — chuyển trạng thái thành công</li>
     *   <li>{@code 0} — document không ở trạng thái {@code fromStatus} (idempotent hoặc mismatch)</li>
     * </ul>
     *
     * @param id         ID document
     * @param fromStatus trạng thái mong đợi (điều kiện WHERE)
     * @param toStatus   trạng thái đích
     */
    @Modifying
    @Query("UPDATE documents SET status = :toStatus WHERE id = :id AND status = :fromStatus")
    Mono<Integer> updateStatusIfMatch(Long id, String fromStatus, String toStatus);

    /**
     * Buộc cập nhật trạng thái bất kể trạng thái hiện tại (dùng cho emergency FAILED).
     * Không đi qua bảng FSM — chỉ dùng trong {@code onErrorResume}.
     *
     * @param id     ID document
     * @param status trạng thái mới (thường là "FAILED")
     */
    @Modifying
    @Query("UPDATE documents SET status = :status WHERE id = :id")
    Mono<Integer> updateStatusForced(Long id, String status);

    /**
     * Cập nhật kết quả AI trích xuất (Ollama / LLM fallback) có điều kiện:
     * Chỉ ghi khi document CHƯA bị human review (reviewed_by IS NULL)
     * và trạng thái đang ở các bước phù hợp ('EXTRACTED', 'NEED_REVIEW', 'VALIDATED').
     */
    @Modifying
    @Query("""
            UPDATE documents
               SET extracted_fields      = :fields,
                   predicted_fields_json = :fields,
                   model_version         = :modelVersion,
                   contact_name          = COALESCE(:contactName, contact_name),
                   contact_title         = COALESCE(:contactTitle, contact_title)
             WHERE id = :id
               AND reviewed_by IS NULL
               AND status IN ('EXTRACTED', 'NEED_REVIEW', 'VALIDATED')
            """)
    Mono<Integer> updateAiExtractionIfNotReviewed(Long id, String fields, String modelVersion,
                                                  String contactName, String contactTitle);

    /**
     * Gắn document vào lô huấn luyện — chỉ khi training_eligible = 1 và chưa có lô.
     */
    @Modifying
    @Query("""
            UPDATE documents
               SET training_batch_id = :batchId
             WHERE id               = :id
               AND training_eligible = TRUE
               AND training_batch_id IS NULL
            """)
    Mono<Integer> assignToBatchIfEligible(Long id, Long batchId);

    /**
     * Cập nhật cờ kiểm định dữ liệu và điều kiện huấn luyện.
     */
    @Modifying
    @Query("UPDATE documents SET human_verified = :verified, training_eligible = :eligible WHERE id = :id")
    Mono<Integer> markTrainingOutcome(Long id, boolean verified, boolean eligible);

    /**
     * Cập nhật kết quả pipeline vào DB có điều kiện:
     * Chỉ ghi khi document CHƯA bị human review (reviewed_by IS NULL)
     * và trạng thái đang ở các bước pipeline tự động.
     */
    @Modifying
    @Query("""
            UPDATE documents
               SET document_type         = :documentType,
                   confidence_score      = :confidenceScore,
                   matched_by            = :matchedBy,
                   used_ai_fallback      = :usedAiFallback,
                   model_version         = :modelVersion,
                   raw_ocr_text          = :rawOcrText,
                   ocr_engine            = :ocrEngine,
                   company_id            = :companyId,
                   extracted_fields      = :extractedFields,
                   predicted_fields_json = :extractedFields
             WHERE id = :id
               AND reviewed_by IS NULL
               AND status IN ('PROCESSING','OCR_DONE','CLASSIFIED','EXTRACTED','NEED_REVIEW','VALIDATED')
            """)
    Mono<Integer> updatePipelineResultsIfNotReviewed(
            Long id,
            String documentType,
            Double confidenceScore,
            String matchedBy,
            Boolean usedAiFallback,
            String modelVersion,
            String rawOcrText,
            String ocrEngine,
            Long companyId,
            String extractedFields
    );

    @Query("SELECT * FROM documents WHERE company_id = :companyId AND " +
            "(:status IS NULL OR status = :status) AND " +
            "(:documentType IS NULL OR document_type = :documentType) " +
            "ORDER BY id DESC LIMIT :limit OFFSET :offset")
    Flux<Document> findByFilterAndCompanyId(String status, String documentType, Long companyId, int limit, long offset);

    @Query("SELECT COUNT(*) FROM documents WHERE company_id = :companyId AND " +
            "(:status IS NULL OR status = :status) AND " +
            "(:documentType IS NULL OR document_type = :documentType)")
    Mono<Long> countByFilterAndCompanyId(String status, String documentType, Long companyId);

    // FIX: 4 method mới bên dưới lọc theo owner_company_id (tenant sở hữu, từ JWT) -
    // dùng cho MỌI kiểm tra quyền sở hữu (IDOR) ở DocumentController. Các method phía
    // trên (company_id) giữ nguyên không đổi, vì company_id mang nghĩa "công ty được OCR
    // nhận diện trong nội dung chứng từ" - đúng chủ đích cho các use-case khác (không phải
    // IDOR), ví dụ findHistoricalAmountsAndCompanyId bên dưới dùng để so lịch sử giao dịch
    // theo ĐÚNG công ty đối tác trên hóa đơn, không phải theo tenant.
    @Query("SELECT * FROM documents WHERE owner_company_id = :ownerCompanyId AND " +
            "(:status IS NULL OR status = :status) AND " +
            "(:documentType IS NULL OR document_type = :documentType) " +
            "ORDER BY id DESC LIMIT :limit OFFSET :offset")
    Flux<Document> findByFilterAndOwnerCompanyId(String status, String documentType, Long ownerCompanyId, int limit, long offset);

    @Query("SELECT COUNT(*) FROM documents WHERE owner_company_id = :ownerCompanyId AND " +
            "(:status IS NULL OR status = :status) AND " +
            "(:documentType IS NULL OR document_type = :documentType)")
    Mono<Long> countByFilterAndOwnerCompanyId(String status, String documentType, Long ownerCompanyId);

    @Query("SELECT CAST(JSON_UNQUOTE(JSON_EXTRACT(extracted_fields, '$.total_amount')) AS DOUBLE) " +
            "FROM documents " +
            "WHERE company_id = :companyId " +
            "  AND document_type = :docType " +
            "  AND JSON_UNQUOTE(JSON_EXTRACT(extracted_fields, '$.tax_code')) = :taxCode " +
            "  AND status = 'APPROVED' " +
            "ORDER BY id DESC LIMIT 20")
    Flux<Double> findHistoricalAmountsAndCompanyId(String docType, String taxCode, Long companyId);

    // Giữ nguyên - method cũ dùng company_id, có thể còn nơi khác đang gọi (chưa rà hết toàn bộ codebase)
    Mono<Document> findByIdAndCompanyId(Long id, Long companyId);

    Flux<Document> findByStatusAndCompanyId(String status, Long companyId);

    // FIX: method mới dùng owner_company_id (tenant) - DocumentController đã đổi sang dùng các method này
    Mono<Document> findByIdAndOwnerCompanyId(Long id, Long ownerCompanyId);

    Flux<Document> findByStatusAndOwnerCompanyId(String status, Long ownerCompanyId);
}