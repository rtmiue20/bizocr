CREATE TABLE IF NOT EXISTS documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, -- id
    file_name VARCHAR(255) NOT NULL, -- tên
    document_type VARCHAR(100), -- loại
    confidence_score DOUBLE, -- độ tin cậy
    status VARCHAR(50) DEFAULT 'PENDING',  -- Statuses: 'PENDING', 'APPROVED', 'REJECTED', 'FAILED'
    extracted_fields JSON,                -- Dynamic JSON schema for ~200 document types
    received_at VARCHAR(50), -- ngày nhận
    file_path VARCHAR(255) -- ảnh lưu ở
);