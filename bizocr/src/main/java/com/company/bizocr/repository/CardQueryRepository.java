package com.company.bizocr.repository;

import com.company.bizocr.model.dto.response.IndustryCountResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
@RequiredArgsConstructor
public class CardQueryRepository {

    private final DatabaseClient db;

    /**
     * Thống kê số danh thiếp đã duyệt theo ngành nghề cho một tenant.
     * Trả về trực tiếp {@link IndustryCountResponse} — tránh MappingException
     * vì Spring Data R2DBC @Query không hỗ trợ Map<String, Object>.
     */
    public Flux<IndustryCountResponse> countCardsByIndustry(Long ownerId) {
        return db.sql("""
                        SELECT COALESCE(NULLIF(TRIM(c.industry), ''), 'Khác') AS industry_name,
                               COUNT(*) AS card_count
                        FROM contacts c
                        JOIN documents d ON d.id = c.document_id
                        WHERE c.owner_company_id = :ownerId
                          AND d.status = 'APPROVED'
                        GROUP BY industry_name
                        ORDER BY card_count DESC
                        """)
                .bind("ownerId", ownerId)
                .map((row, meta) -> IndustryCountResponse.builder()
                        .industry(row.get("industry_name", String.class))
                        .count(row.get("card_count", Long.class))
                        .build())
                .all();
    }
}
