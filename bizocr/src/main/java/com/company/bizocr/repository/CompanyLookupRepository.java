package com.company.bizocr.repository;

import com.company.bizocr.model.entity.Company;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface CompanyLookupRepository extends ReactiveCrudRepository<Company, Long> {

    /**
     * Đa tín hiệu: Domain/Phone khớp tuyệt đối = 1.0.
     * Tên công ty (normalized_name / alias) dùng pg_trgm similarity() để tính % tương đồng,
     * chỉ nhận kết quả > 0.8 (ngưỡng 80% theo chiến lược Multi-Signal Identification).
     */
    @Query("""
        SELECT c.*
        FROM companies c
        WHERE (:domain IS NOT NULL AND c.website ILIKE CONCAT('%', :domain, '%'))
           OR (:phone IS NOT NULL AND c.phone_number = :phone)
           OR (:name IS NOT NULL AND similarity(c.normalized_name, :name) > 0.8)
           OR (:name IS NOT NULL AND EXISTS (
                SELECT 1 FROM company_aliases ca
                WHERE ca.company_id = c.id AND similarity(ca.normalized_alias, :name) > 0.8
           ))
        ORDER BY GREATEST(
            CASE WHEN :domain IS NOT NULL AND c.website ILIKE CONCAT('%', :domain, '%') THEN 1.0 ELSE 0.0 END,
            CASE WHEN :phone IS NOT NULL AND c.phone_number = :phone THEN 1.0 ELSE 0.0 END,
            COALESCE(similarity(c.normalized_name, :name), 0.0),
            COALESCE((SELECT MAX(similarity(ca.normalized_alias, :name))
                      FROM company_aliases ca WHERE ca.company_id = c.id), 0.0)
        ) DESC
        LIMIT 1
    """)
    Mono<Company> findBestMatch(@Param("domain") String domain,
                                @Param("phone") String phone,
                                @Param("name") String name);

    Mono<Company> findByTaxCode(String taxCode);
}