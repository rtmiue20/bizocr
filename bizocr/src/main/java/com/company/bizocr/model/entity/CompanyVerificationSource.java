package com.company.bizocr.model.entity;

/** Nguồn gốc dữ liệu dẫn tới trạng thái verification hiện tại */
public enum CompanyVerificationSource {
    VIETQR_DIRECT,       // OCR đọc trực tiếp MST -> VietQR khớp
    DB_MATCH,            // Khớp company sẵn có trong DB nội bộ (giữ nguyên status cũ của bản ghi đó)
    WEB_SEARCH_VIETQR,   // Search API tìm ra MST -> VietQR khớp
    OCR_ONLY,             // Không tra được MST ở bất kỳ bước nào
    MANUAL_REGISTRATION // Company tạo lúc user tự đăng ký, chưa qua bước xác thực nào
}