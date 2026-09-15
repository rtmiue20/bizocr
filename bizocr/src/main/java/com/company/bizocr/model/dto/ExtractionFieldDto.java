package com.company.bizocr.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionFieldDto {
    private String key;
    private String label;
    private String dataType; // STRING, NUMBER, DATE, TEXT
    private boolean required;
    private String description;
}