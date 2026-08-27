-- 1. Bảng lưu trữ thông tin Công ty (Company)
CREATE TABLE IF NOT EXISTS companies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_code VARCHAR(100) UNIQUE,     -- Mã công ty nội bộ (VD: ABC001)
    tax_code VARCHAR(50) UNIQUE,          -- Mã số thuế (VD: 0312345678)
    legal_name VARCHAR(255) NOT NULL,     -- Tên pháp lý gốc
    normalized_name VARCHAR(255),         -- Tên đã chuẩn hóa (bỏ dấu, viết hoa)
    active BOOLEAN DEFAULT TRUE           -- Trạng thái hoạt động
);

-- 2. Bảng lưu trữ tên gọi khác của Công ty (Company Alias)
CREATE TABLE IF NOT EXISTS company_aliases (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,           -- Khóa ngoại liên kết tới companies.id
    normalized_alias VARCHAR(255) NOT NULL, -- Tên gọi khác đã chuẩn hóa
    CONSTRAINT fk_company_alias FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);

-- 3. Bảng lưu trữ Chứng từ (Documents)
CREATE TABLE IF NOT EXISTS documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,      -- Tên file
    document_type VARCHAR(100),           -- Loại chứng từ (VAT_INVOICE, CONTRACT...)
    confidence_score DOUBLE,              -- Độ tin cậy (0.0 - 1.0)
    status VARCHAR(50) DEFAULT 'PENDING', -- PENDING, PROCESSING, COMPLETED, NEEDS_REVIEW, FAILED
    extracted_fields JSON,                -- JSON lưu trữ dữ liệu bóc tách
    received_at VARCHAR(50),              -- Thời gian nhận file
    file_path VARCHAR(255),               -- Đường dẫn lưu file vật lý

-- Các cột bổ sung phục vụ AI & Pipeline
    raw_ocr_text LONGTEXT,                -- Văn bản thô từ OCR
    matched_by VARCHAR(100),              -- Nhận diện bởi (TEMPLATE_MATCHING, AI_FALLBACK, v.v.)
    used_ai_fallback BOOLEAN,             -- Cờ đánh dấu có dùng AI Ollama không
    model_version VARCHAR(50),            -- Phiên bản model (VD: template-v1)

-- Các cột phục vụ kiểm duyệt (Human-in-the-loop)
    reviewed_at VARCHAR(50),              -- Thời gian kế toán duyệt
    reviewed_by VARCHAR(100)              -- Tên/ID người duyệt
);