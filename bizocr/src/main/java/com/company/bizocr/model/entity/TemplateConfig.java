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
 * Cấu hình 1 loại chứng từ động cho thuật toán Template Matching.[cite: 14]
 * Được TemplateConfigLoaderService nạp lên và đăng ký vào TemplateMatcher lúc khởi động,[cite: 14]
 * cho phép mở rộng danh mục chứng từ mà không cần sửa/deploy lại code Java.[cite: 14]
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("template_config")
public class TemplateConfig {

    @Id
    private Long id;

    /**
     * Mã loại chứng từ, VD: invoice_supermarket, invoice_electricity, NAMECARD...[cite: 14]
     * Dùng trực tiếp làm documentType khi đăng ký vào TemplateMatcher.[cite: 14]
     */
    @Column("doc_type_code")
    private String docTypeCode;

    @Column("display_name")
    private String displayName;

    /**
     * Từ khóa định danh (Anchors), cách nhau bởi dấu phẩy — VD: "CO.OPMART, HÓA ĐƠN, Ngày"[cite: 14]
     */
    private String anchors;

    @Builder.Default
    private String status = "ACTIVE";

    private String note;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}