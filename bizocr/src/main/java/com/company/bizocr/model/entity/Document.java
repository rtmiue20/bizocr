package com.company.bizocr.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("documents")
public class Document {

    @Id
    private Long id;

    @Column("file_name")
    private String fileName;

    @Column("file_path")
    private String filePath;

    @Column("document_type")
    private String documentType;

    @Column("company_id")
    private Long companyId;

    @Column("company_match_score")
    private Double companyMatchScore;

    @Column("company_matched_by")
    private String companyMatchedBy;

    @Column("confidence_score")
    private Double confidenceScore;

    /**
     * Các trạng thái:
     *
     * PENDING       - mới upload, đang chờ Redis
     * PROCESSING    - đang OCR/ML/Ollama
     * COMPLETED     - xử lý tự động thành công
     * NEEDS_REVIEW  - mẫu lạ hoặc confidence thấp
     * APPROVED      - người dùng đã xác nhận
     * REJECTED      - người dùng từ chối
     * FAILED        - lỗi kỹ thuật
     */
    private String status;

    @Column("extracted_fields")
    private String extractedFields;

    /**
     * Nội dung OCR gốc, dùng để tạo training sample.
     */
    @Column("raw_ocr_text")
    private String rawOcrText;

    /**
     * Nguồn tạo kết quả:
     * TEMPLATE_MATCHING
     * ML_MODEL
     * OLLAMA_LOCAL
     * AI_FALLBACK
     */
    @Column("matched_by")
    private String matchedBy;

    /**
     * Có sử dụng Ollama/AI fallback hay không.
     */
    @Column("used_ai_fallback")
    private Boolean usedAiFallback;

    /**
     * Phiên bản model nội bộ đã dùng để dự đoán.
     */
    @Column("model_version")
    private String modelVersion;

    @Column("received_at")
    private String receivedAt;

    @Column("reviewed_by")
    private String reviewedBy;

    @Column("reviewed_at")
    private String reviewedAt;

}