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
public class DocumentRecord {
    @Id
    private Long id;

    @Column("file_name")
    private String fileName;

    @Column("file_path")
    private String filePath;

    @Column("document_type")
    private String documentType;

    @Column("confidence_score")
    private Double confidenceScore;

    private String status; // PENDING, COMPLETED, APPROVED, REJECTED

    @Column("extracted_fields")
    private String extractedFields;

    @Column("received_at")
    private String receivedAt;

    @Column("reviewed_by")
    private String reviewedBy;

    @Column("reviewed_at")
    private String reviewedAt;
}