package com.company.bizocr.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassifyResult {
    private String documentType;     // Ví dụ: "VAT_INVOICE", "CONTRACT", "NAME_CARD"
    private Double confidenceScore;  // Độ tin cậy (từ 0.0 đến 1.0)
    private String matchedBy;        // "TEMPLATE_MATCHING" hoặc "AI_FALLBACK"
    private boolean success;         // true nếu nhận diện thành công
}