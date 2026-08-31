package com.company.bizocr.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("field_correction_audits")
public class FieldCorrectionAudit {
    @Id
    private Long id;
    private Long documentId;
    private Long companyId;
    private String fieldName;
    private String originalValue;
    private String correctedValue;
    private String reviewedBy;
    private String reviewedAt;
}