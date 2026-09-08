package com.company.bizocr.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Cấu hình 1 field cần bóc tách cho 1 loại chứng từ (doc_type_code) — thay cho các hàm
 * regex hardcode extractXxxFields() trong FieldExtractorService khi loại chứng từ đó
 * đã được định nghĩa qua bảng này (nạp từ Excel).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("template_field_config")
public class TemplateFieldConfig {

    @Id
    private Long id;

    @Column("doc_type_code")
    private String docTypeCode;

    @Column("field_key")
    private String fieldKey;

    @Column("ui_label")
    private String uiLabel;

    /**
     * TEXT / NUMBER / DATE
     */
    @Column("data_type")
    private String dataType;

    @Builder.Default
    private Boolean required = false;

    @Column("regex_pattern")
    private String regexPattern;

    private String note;

    @Column("display_order")
    private Integer displayOrder;
}