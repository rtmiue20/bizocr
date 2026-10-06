package com.company.bizocr.model.entity;

public enum TrainingBatchStatus {
    OPEN,       // Lô đang mở, đang nhận thêm tài liệu hợp lệ
    READY,      // Đã đủ batch_size, sẵn sàng gửi sang Python
    RUNNING,    // Python đang trong tiến trình huấn luyện
    PROMOTED,   // Mô hình mới đã vượt qua đánh giá và được kích hoạt phục vụ
    REJECTED    // Mô hình mới không đạt chỉ số yêu cầu hoặc gặp lỗi
}
