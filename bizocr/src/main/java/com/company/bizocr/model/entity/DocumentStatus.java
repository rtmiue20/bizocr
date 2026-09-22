package com.company.bizocr.model.entity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Máy trạng thái (FSM) cho Document.
 *
 * <pre>
 * PENDING
 *   └─► PROCESSING
 *         ├─► OCR_DONE
 *         │     └─► CLASSIFIED
 *         │           └─► EXTRACTED
 *         │                 ├─► VALIDATED ──► APPROVED
 *         │                 └─► NEEDS_REVIEW ──► APPROVED
 *         │                                 └─► REJECTED_QUALITY
 *         └─► FAILED          (lỗi kỹ thuật bất kỳ bước nào)
 * NEEDS_REVIEW ──► APPROVED   (reviewer duyệt)
 * NEEDS_REVIEW ──► REJECTED_QUALITY (reviewer từ chối)
 * </pre>
 *
 * <b>Quy tắc race-condition:</b>
 * Mọi lần chuyển trạng thái phải đi qua {@link DocumentStateMachine#transition},
 * dùng câu UPDATE có điều kiện {@code WHERE status = :from}.
 * Nếu {@code rowsAffected == 0}:
 * <ul>
 *   <li>Document vẫn đang ở đúng trạng thái {@code to} → <b>idempotent</b> (trùng lặp vô hại)</li>
 *   <li>Document đã ở trạng thái khác → <b>lệch luồng</b> (state mismatch)</li>
 * </ul>
 */
public enum DocumentStatus {

    // ── Luồng tự động (pipeline) ──────────────────────────────────────────────
    /** Vừa upload, đang chờ Redis pop */
    PENDING,
    /** Consumer đã nhận job, đang OCR/classify/extract */
    PROCESSING,
    /** OCR engine trả về text thô thành công */
    OCR_DONE,
    /** Classifier đã gán document_type */
    CLASSIFIED,
    /** FieldExtractor đã bóc tách xong các trường */
    EXTRACTED,
    /** Validator kiểm tra hợp lệ (tax_code, total_amount, issue_date) */
    VALIDATED,

    // ── Trạng thái chờ reviewer ──────────────────────────────────────────────
    /**
     * AI confidence thấp, field thiếu, hoặc validation fail →
     * cần reviewer kiểm tra thủ công trước khi approve/reject.
     */
    NEEDS_REVIEW,

    // ── Trạng thái cuối (terminal) ────────────────────────────────────────────
    /** Reviewer đã xác nhận và duyệt */
    APPROVED,
    /** Reviewer từ chối / chứng từ không hợp lệ */
    REJECTED_QUALITY,
    /** Lỗi kỹ thuật không thể tiếp tục xử lý */
    FAILED;

    // =========================================================================
    // Bảng transition hợp lệ (allowed transitions)
    // =========================================================================
    private static final Map<DocumentStatus, Set<DocumentStatus>> ALLOWED_TRANSITIONS = Map.ofEntries(

            // Pipeline tự động
            Map.entry(PENDING,           EnumSet.of(PROCESSING, FAILED)),
            Map.entry(PROCESSING,        EnumSet.of(OCR_DONE, NEEDS_REVIEW, FAILED)),
            Map.entry(OCR_DONE,          EnumSet.of(CLASSIFIED, NEEDS_REVIEW, FAILED)),
            Map.entry(CLASSIFIED,        EnumSet.of(EXTRACTED, NEEDS_REVIEW, FAILED)),
            Map.entry(EXTRACTED,         EnumSet.of(VALIDATED, NEEDS_REVIEW, FAILED)),
            Map.entry(VALIDATED,         EnumSet.of(APPROVED, NEEDS_REVIEW, FAILED)),

            // Reviewer action
            Map.entry(NEEDS_REVIEW,      EnumSet.of(APPROVED, REJECTED_QUALITY)),

            // Terminal — không chuyển tiếp nữa
            Map.entry(APPROVED,          EnumSet.noneOf(DocumentStatus.class)),
            Map.entry(REJECTED_QUALITY,  EnumSet.noneOf(DocumentStatus.class)),
            Map.entry(FAILED,            EnumSet.noneOf(DocumentStatus.class))
    );

    /**
     * Kiểm tra transition {@code from → to} có hợp lệ theo bảng FSM không.
     *
     * @param from trạng thái hiện tại
     * @param to   trạng thái muốn chuyển sang
     * @return {@code true} nếu transition được phép
     */
    public static boolean isAllowed(DocumentStatus from, DocumentStatus to) {
        Set<DocumentStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * Parse String → Enum an toàn (không ném exception với giá trị lạ từ DB).
     *
     * @param value giá trị chuỗi (VD: "PENDING")
     * @return Enum tương ứng, hoặc {@code null} nếu không nhận ra
     */
    public static DocumentStatus fromString(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return DocumentStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}