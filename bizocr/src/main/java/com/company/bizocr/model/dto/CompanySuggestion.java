package com.company.bizocr.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompanySuggestion {
    private String taxCode;      // "" nếu không tìm thấy trong OCR
    private String companyName;  // "" nếu không tìm thấy trong OCR
    private String contactName;
    private String contactTitle;
}