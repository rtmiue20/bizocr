package com.company.bizocr.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OllamaTaskPayload {
    private String documentId; // ID của chứng từ trong DB để Worker update lại sau khi xong
    private String documentType;
    private String rawText;
}