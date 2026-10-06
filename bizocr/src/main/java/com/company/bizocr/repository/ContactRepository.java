package com.company.bizocr.repository;

import com.company.bizocr.model.entity.Contact;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ContactRepository extends R2dbcRepository<Contact, Long> {

    /** Dedup: tìm theo email (chuẩn hóa lowercase) trong cùng tenant. */
    Mono<Contact> findByEmailAndOwnerCompanyId(String email, Long ownerCompanyId);

    /** Dedup: tìm theo phone (chuẩn hóa 0xxxxxxxxx) trong cùng tenant. */
    Mono<Contact> findByPhoneAndOwnerCompanyId(String phone, Long ownerCompanyId);

    /** Danh sách phân trang cho tenant. */
    @Query("SELECT * FROM contacts WHERE owner_company_id = :ownerId ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    Flux<Contact> findByOwnerCompanyIdPaged(Long ownerId, long limit, long offset);

    Mono<Long> countByOwnerCompanyId(Long ownerCompanyId);

    /**
     * FULLTEXT search trên normalized_name.
     * Dùng BOOLEAN MODE để hỗ trợ tìm từng từ: VD "nguyen van" → "+nguyen* +van*".
     */
    @Query("""
            SELECT * FROM contacts
            WHERE owner_company_id = :ownerId
              AND MATCH(normalized_name) AGAINST(:q IN BOOLEAN MODE)
            LIMIT :limit OFFSET :offset
            """)
    Flux<Contact> fullTextSearchByName(String q, Long ownerId, long limit, long offset);

    @Query("""
            SELECT COUNT(*) FROM contacts
            WHERE owner_company_id = :ownerId
              AND MATCH(normalized_name) AGAINST(:q IN BOOLEAN MODE)
            """)
    Mono<Long> countFullTextSearchByName(String q, Long ownerId);

    /**
     * Tìm theo ngành (join qua company → company_industries).
     */
    @Query("""
            SELECT c.* FROM contacts c
            JOIN company_industries ci ON ci.company_id = c.company_id
            WHERE c.owner_company_id = :ownerId
              AND ci.industry_code = :industryCode
            ORDER BY c.created_at DESC
            LIMIT :limit OFFSET :offset
            """)
    Flux<Contact> findByOwnerCompanyIdAndIndustryCode(Long ownerId, String industryCode, long limit, long offset);

    @Query("""
            SELECT COUNT(*) FROM contacts c
            JOIN company_industries ci ON ci.company_id = c.company_id
            WHERE c.owner_company_id = :ownerId
              AND ci.industry_code = :industryCode
            """)
    Mono<Long> countByOwnerCompanyIdAndIndustryCode(Long ownerId, String industryCode);

    /** Tìm các contact bị cờ POSSIBLE_DUPLICATE để hiển thị banner cảnh báo. */
    @Query("SELECT * FROM contacts WHERE owner_company_id = :ownerId AND dedup_status = 'POSSIBLE_DUPLICATE' ORDER BY created_at DESC")
    Flux<Contact> findPossibleDuplicates(Long ownerId);

    /** Tìm contact theo document_id nguồn. */
    Mono<Contact> findByDocumentIdAndOwnerCompanyId(Long documentId, Long ownerCompanyId);
}
