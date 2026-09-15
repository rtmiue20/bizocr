package com.company.bizocr.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionSchemaDto {
    private String documentType;
    private List<ExtractionFieldDto> fields;
}