package com.company.bizocr.model.dto.request;

import lombok.Data;

@Data
public class DocumentUpdateRequest {
    private String status; // APPROVED hoặc REJECTED
    private String extractedFields; // Chuỗi JSON đã qua chỉnh sửa
    private String reviewer; // Tên người duyệt (mặc định: admin)
}