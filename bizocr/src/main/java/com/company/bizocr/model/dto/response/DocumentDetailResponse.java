package com.company.bizocr.model.dto.response;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDetailResponse {
    private Long documentId;
    private String fileName;
    private String documentType;
    private String status;
    private String matchedBy;
    private Double confidenceScore;
    private String receivedAt;
    private String reviewedBy;
    private String reviewedAt;
    private String ocrEngine;

    @JsonRawValue
    private String extractedFields;

    // Completeness score breakdown
    private Double completenessScore;
    private String completenessGrade;
    private String completenessLabel;
    private Double mandatoryFieldsRatio;
    private Double contourAreaRatio;
    private Double avgOcrConfidence;
    private Integer mandatoryFilled;
    private Integer mandatoryTotal;
    private String confirmPrompt;
}
