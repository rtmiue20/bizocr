package com.company.bizocr.model.entity;

/** Trạng thái xác thực pháp lý của Company */
public enum CompanyVerificationStatus {
    VERIFIED,    // Đã đối chiếu chéo qua VietQR (Tổng cục Thuế)
    UNVERIFIED   // Chỉ có tín hiệu OCR/Namecard, chưa tra được MST
}