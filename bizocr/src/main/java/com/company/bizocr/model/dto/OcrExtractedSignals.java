package com.company.bizocr.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OcrExtractedSignals {
    private String taxCode;         // Có thể null (Namecard không có MST)
    private String companyName;     // Tên/Brand name trên card
    private String domain;          // Trích từ Email (user@vng.com.vn -> vng.com.vn) hoặc Website
    private String phoneNumber;     // Số cố định hoặc Hotline
    private String contactName;
    private String contactTitle;
}