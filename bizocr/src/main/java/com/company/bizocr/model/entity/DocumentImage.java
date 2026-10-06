package com.company.bizocr.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Lưu trữ ảnh mặt trước / mặt sau của namecard.
 * <p>
 * Tách ra khỏi bảng documents để:
 * - Không phá vỡ pipeline OCR hiện tại (documents.file_path vẫn giữ nguyên).
 * - Cho phép 1 document có tối đa 2 ảnh (FRONT + BACK) dưới dạng UNIQUE(document_id, side).
 * - Dễ dàng chuyển sang MinIO/S3 sau này (chỉ đổi storage_key scheme).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("document_images")
public class DocumentImage {

    @Id
    private Long id;

    @Column("document_id")
    private Long documentId;

    /**
     * FRONT — mặt trước (bắt buộc).
     * BACK  — mặt sau (tùy chọn).
     */
    private String side;

    /**
     * Đường dẫn tương đối trong storage, VD: "2026-10-06/abc123_card.jpg".
     * KHÔNG lưu đường dẫn tuyệt đối — để dễ di chuyển sang MinIO/S3.
     */
    @Column("storage_key")
    private String storageKey;

    /**
     * Key thumbnail do Python/Pillow tạo. Null-safe:
     * nếu null, UI dùng ảnh gốc thu nhỏ bằng CSS (không block pipeline).
     */
    @Column("thumb_key")
    private String thumbKey;

    /** SHA-256 của file gốc để phát hiện upload ảnh trùng lặp. */
    private String sha256;

    /**
     * PENDING  — chưa OCR.
     * DONE     — đã OCR xong.
     * SKIPPED  — ảnh gần trắng (quality gate).
     * FAILED   — OCR lỗi kỹ thuật.
     */
    @Column("ocr_status")
    private String ocrStatus;

    /**
     * OCR text riêng cho mặt sau. Chỉ dùng để FILL-IN-BLANK (không ghi đè mặt trước).
     * Null khi side = FRONT.
     */
    @Column("raw_text_back")
    private String rawTextBack;

    @Column("uploaded_at")
    private LocalDateTime uploadedAt;
}
