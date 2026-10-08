package com.company.bizocr.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
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

    /**
     * FIX: Tenant/công ty SỞ HỮU document (từ JWT lúc upload) - dùng cho MỌI kiểm tra
     * quyền sở hữu (IDOR) ở DocumentController (findByIdAndOwnerCompanyId,
     * findByFilterAndOwnerCompanyId, ...). KHÔNG được PipelineOrchestrator đụng vào,
     * chỉ gán 1 lần duy nhất lúc tạo document.
     * Trước đây dùng chung "companyId" bên dưới cho cả 2 mục đích -> PipelineOrchestrator
     * ghi đè companyId bằng công ty OCR nhận diện, làm mất quyền sở hữu tenant, khiến mọi
     * document sau khi xử lý xong bị 404 sai với chính người đã upload nó.
     */
    @Column("owner_company_id")
    private Long ownerCompanyId;

    /**
     * Công ty được OCR/CompanyResolverService NHẬN DIỆN trong nội dung chứng từ
     * (đối tác/khách hàng xuất hiện trên hóa đơn, hợp đồng...) - KHÔNG phải tenant sở hữu.
     * Dùng cho Multi-Signal Identification, verification, và so sánh lịch sử giao dịch
     * (findHistoricalAmountsAndCompanyId). Trường này được PipelineOrchestrator ghi đè
     * bình thường mỗi lần xử lý - đúng chủ đích.
     */
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
     * NEED_REVIEW  - mẫu lạ hoặc confidence thấp
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

    private String ocrEngine;

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

    @Column("contact_name")
    private String contactName;

    @Column("contact_title")
    private String contactTitle;

    @Column("industry")
    private String industry;

    @Column("approved_at")
    private java.time.LocalDateTime approvedAt;

    // ── ML / Continuous Learning fields ──────────────────────────────────────

    /**
     * Snapshot JSON dự đoán của model tại thời điểm bóc tách — KHÔNG bị human ghi đè.
     * Dùng để so sánh với extractedFields sau khi kế toán sửa, phát hiện sai số của model.
     */
    @Column("predicted_fields_json")
    @ReadOnlyProperty
    private String predictedFieldsJson;

    /**
     * true khi kế toán đánh dấu đây là dữ liệu tin cậy (human_verified = 1).
     * Truyền từ DocumentApproveRequest.humanVerified, không được pipeline tự gán.
     */
    @Column("human_verified")
    @ReadOnlyProperty
    private Boolean humanVerified;

    /**
     * true khi document đủ điều kiện đưa vào lô huấn luyện:
     * trustedLabeler && humanVerified && qua validator && TransitionResult != IDEMPOTENT.
     * Do DocumentApprovalService gán sau khi duyệt.
     */
    @Column("training_eligible")
    @ReadOnlyProperty
    private Boolean trainingEligible;

    /**
     * FK tới training_batches.id — được gán khi document đã được gom vào lô.
     * null khi chưa gom.
     */
    @Column("training_batch_id")
    @ReadOnlyProperty
    private Long trainingBatchId;

    @Column("tax_code")
    @ReadOnlyProperty
    private String taxCode;

    @Column("issue_date")
    @ReadOnlyProperty
    private String issueDate;

    @Column("total_amount")
    @ReadOnlyProperty
    private String totalAmount;

}