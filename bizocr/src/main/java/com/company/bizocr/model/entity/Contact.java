package com.company.bizocr.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Người liên hệ được số hóa từ namecard vật lý.
 * <p>
 * Đây là đơn vị trung tâm của module "thay sổ namecard":
 * - Một Contact thuộc một Company (company_id).
 * - Liên kết với Document namecard nguồn qua document_id.
 * - Được tạo/cập nhật tự động khi DocumentApprovalService duyệt NAMECARD.
 * - dedup_status = POSSIBLE_DUPLICATE khi tên+company khớp gần giống,
 *   chờ người dùng xác nhận gộp tại ContactDetailPage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("contacts")
public class Contact {

    @Id
    private Long id;

    /** Tenant sở hữu — lấy từ JWT, KHÔNG nhận từ request (phòng IDOR). */
    @Column("owner_company_id")
    private Long ownerCompanyId;

    /** User đã tạo bản ghi này (để sau này làm danh bạ riêng tư nếu cần). */
    @Column("created_by")
    private Long createdBy;

    /** Document namecard nguồn gốc. Null-safe (ON DELETE SET NULL). */
    @Column("document_id")
    private Long documentId;

    /** Công ty của người này (FK tới companies). */
    @Column("company_id")
    private Long companyId;

    // ── Thông tin cá nhân ─────────────────────────────────────────────────

    @Column("full_name")
    private String fullName;

    /**
     * Tên đã bỏ dấu, uppercase — dùng cho FULLTEXT search và dedup.
     * Không dùng để hiển thị.
     */
    @Column("normalized_name")
    private String normalizedName;

    private String title;

    private String department;

    /** Số điện thoại cố định, đã chuẩn hóa về dạng 0xxxxxxxxx. */
    private String phone;

    /** Di động, đã chuẩn hóa. */
    private String mobile;

    /** Email viết thường. */
    private String email;

    private String website;
    private String fax;

    @Column("company_phone")
    private String companyPhone;

    @Column("company_email")
    private String companyEmail;

    // ── CRM nhẹ ───────────────────────────────────────────────────────────

    /** Gặp ở đâu, VD: "Hội thảo Tech Summit 2026", "Giới thiệu qua anh Minh". */
    @Column("met_at")
    private String metAt;

    @Column("met_date")
    private LocalDate metDate;

    private String notes;

    /** Tags phân cách dấu phẩy: "VIP,khách hàng,tech". */
    private String tags;

    @Column("last_contacted")
    private LocalDateTime lastContacted;

    // ── Dedup ─────────────────────────────────────────────────────────────

    /**
     * OK            — bản ghi sạch.
     * POSSIBLE_DUPLICATE — tên+company khớp gần giống với một Contact khác,
     *                       chờ người dùng xác nhận tại ContactDetailPage.
     */
    @Column("dedup_status")
    private String dedupStatus;

    /**
     * Chỉ có giá trị khi dedupStatus = POSSIBLE_DUPLICATE.
     * Trỏ tới Contact được nghi là trùng.
     */
    @Column("possible_duplicate_id")
    private Long possibleDuplicateId;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
