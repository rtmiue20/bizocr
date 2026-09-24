-- =========================================================
-- 1. COMPANIES — thông tin công ty + trạng thái xác thực MST
-- =========================================================
CREATE TABLE IF NOT EXISTS companies (
                                         id                    BIGINT        NOT NULL AUTO_INCREMENT,
                                         company_code          VARCHAR(100),                          -- Mã công ty nội bộ (VD: ABC001)
    tax_code              VARCHAR(30),                           -- Mã số thuế (VD: 0312345678)
    legal_name            VARCHAR(255)  NOT NULL,                 -- Tên pháp lý gốc
    short_name            VARCHAR(255),
    normalized_name       VARCHAR(255)  NOT NULL,                 -- Tên đã chuẩn hóa (bỏ dấu, viết hoa)
    address               VARCHAR(500),
    phone                 VARCHAR(50),
    email                 VARCHAR(255),
    website               VARCHAR(255),
    industry              VARCHAR(255),
    active                TINYINT(1)    NOT NULL DEFAULT 1,
    created_at            VARCHAR(100),
    updated_at            VARCHAR(100),
    legal_representative  VARCHAR(255),

    -- Multi-Signal Identification: tách bạch dữ liệu đã đối chiếu Thuế vs. chỉ có tín hiệu OCR
    verification_status   VARCHAR(20)   NOT NULL DEFAULT 'UNVERIFIED',   -- VERIFIED | UNVERIFIED
    verification_source   VARCHAR(30),                                  -- VIETQR_DIRECT | DB_MATCH | WEB_SEARCH_VIETQR | OCR_ONLY
    match_confidence      DECIMAL(4,3),                                 -- 0.000–1.000, null nếu không áp dụng
    verified_at           TIMESTAMP     NULL DEFAULT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_companies_company_code (company_code),
    UNIQUE KEY uk_companies_tax_code (tax_code),
    KEY idx_companies_normalized_name (normalized_name),
    -- Dùng thay pg_trgm (không có trên MariaDB): fuzzy match qua MATCH...AGAINST
    FULLTEXT KEY ft_companies_normalized_name (normalized_name)
    ) ENGINE=InnoDB;

-- =========================================================
-- 2. COMPANY_ALIASES — tên gọi khác (self-learning)
-- =========================================================
CREATE TABLE IF NOT EXISTS company_aliases (
                                               id                BIGINT        NOT NULL AUTO_INCREMENT,
                                               company_id        BIGINT        NOT NULL,
                                               alias_name        VARCHAR(255)  NOT NULL,
    normalized_alias  VARCHAR(255)  NOT NULL,
    source            VARCHAR(50)   NOT NULL,
    confidence_score  DOUBLE        NOT NULL DEFAULT 1,
    created_at        VARCHAR(100),

    PRIMARY KEY (id),
    KEY idx_company_aliases_company_id (company_id),
    KEY idx_company_aliases_normalized_alias (normalized_alias),
    CONSTRAINT fk_company_alias FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
    ) ENGINE=InnoDB;

-- =========================================================
-- 3. INDUSTRIES — danh mục ngành nghề
-- =========================================================
CREATE TABLE IF NOT EXISTS industries (
                                          code        VARCHAR(10)   NOT NULL,       -- Mã ngành (VD: 6201)
    name        VARCHAR(255)  NOT NULL,
    description TEXT,
    is_active   TINYINT(1)    DEFAULT 1,
    created_at  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (code)
    ) ENGINE=InnoDB;

-- =========================================================
-- 4. COMPANY_INDUSTRIES — quan hệ N-N công ty <-> ngành nghề
-- =========================================================
CREATE TABLE IF NOT EXISTS company_industries (
                                                  company_id    BIGINT       NOT NULL,
                                                  industry_code VARCHAR(10)  NOT NULL,
    is_main       TINYINT(1)   DEFAULT 0,
    assigned_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (company_id, industry_code),
    CONSTRAINT fk_company_industries_company  FOREIGN KEY (company_id)    REFERENCES companies(id)  ON DELETE CASCADE,
    CONSTRAINT fk_company_industries_industry FOREIGN KEY (industry_code) REFERENCES industries(code) ON DELETE CASCADE
    ) ENGINE=InnoDB;

-- =========================================================
-- 5. DOCUMENTS — chứng từ
-- =========================================================
CREATE TABLE IF NOT EXISTS documents (
                                         id                   BIGINT        NOT NULL AUTO_INCREMENT,
                                         file_name            VARCHAR(255)  NOT NULL,
    document_type        VARCHAR(100),                          -- VAT_INVOICE, CONTRACT, ...
    confidence_score     DOUBLE,                                -- 0.0 - 1.0
    status               VARCHAR(50)   DEFAULT 'PENDING',       -- PENDING, PROCESSING, COMPLETED, NEED_REVIEW, FAILED
    extracted_fields     JSON,

    -- Generated columns từ extracted_fields (Phase 1 - virtual columns cho query nhanh không parse JSON)
    -- LƯU Ý: '$.issue_date' cần xác nhận đúng tên key thật trong JSON trước khi chạy trên production
    tax_code             VARCHAR(20)   AS (JSON_UNQUOTE(JSON_EXTRACT(extracted_fields, '$.tax_code'))) STORED,
    issue_date           VARCHAR(20)   AS (JSON_UNQUOTE(JSON_EXTRACT(extracted_fields, '$.issue_date'))) STORED,
    total_amount         VARCHAR(30)   AS (JSON_UNQUOTE(JSON_EXTRACT(extracted_fields, '$.total_amount'))) STORED,

    received_at          VARCHAR(50),
    file_path            VARCHAR(255),

    raw_ocr_text         LONGTEXT,
    ocr_engine           VARCHAR(50),                           -- Engine OCR đã xử lý (vd: 'paddleocr'), phục vụ audit/debug khi đổi engine
    matched_by           VARCHAR(50),                           -- TEMPLATE_MATCHING, AI_FALLBACK, ...
    used_ai_fallback     TINYINT(1)    NOT NULL DEFAULT 0,
    model_version        VARCHAR(100),

    reviewed_at          VARCHAR(50),
    reviewed_by          VARCHAR(100),

    -- FIX: tách riêng "công ty SỞ HỮU document" (tenant, dùng để check quyền IDOR ở
    -- DocumentController) khỏi "company_id" bên dưới (công ty được OCR NHẬN DIỆN trong
    -- nội dung chứng từ, dùng cho CompanyResolverService/Multi-Signal Identification).
    -- Trước đây dùng chung 1 cột company_id cho cả 2 mục đích -> PipelineOrchestrator ghi
    -- đè company_id bằng công ty OCR nhận diện, làm mất giá trị tenant đã gán lúc upload,
    -- khiến findByIdAndCompanyId(id, jwtCompanyId) luôn 404 sau khi document được xử lý xong.
    -- owner_company_id được gán 1 LẦN DUY NHẤT lúc upload (DocumentController.uploadDocument)
    -- và KHÔNG được PipelineOrchestrator đụng vào.
    owner_company_id     BIGINT        NOT NULL DEFAULT 0,        -- Tenant sở hữu document - dùng cho mọi check IDOR

-- Liên kết Multi-Signal Identification tới companies (công ty được OCR NHẬN DIỆN trong nội dung)
    company_id           BIGINT,
    company_match_score  DOUBLE,                                -- Điểm tương đồng khi match (0.0–1.0)
    company_matched_by   VARCHAR(50),                            -- VIETQR_DIRECT | DB_MATCH | WEB_SEARCH_VIETQR | OCR_ONLY

    contact_name          VARCHAR(255),
    contact_title         VARCHAR(255),

    PRIMARY KEY (id),
    KEY idx_documents_owner_company_id (owner_company_id),
    KEY idx_documents_company_id (company_id),
    KEY idx_documents_tax_code (tax_code),
    KEY idx_documents_issue_date (issue_date),
    KEY idx_documents_total_amount (total_amount),
    CONSTRAINT fk_documents_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE SET NULL
    ) ENGINE=InnoDB;

-- =========================================================
-- 6. FIELD_CORRECTION_AUDITS — audit log khi kế toán sửa field
-- =========================================================
CREATE TABLE IF NOT EXISTS field_correction_audits (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    document_id       BIGINT        NOT NULL,
    company_id        BIGINT        NOT NULL,
    field_name        VARCHAR(100)  NOT NULL,
    original_value    TEXT,
    corrected_value   TEXT,
    reviewed_by       VARCHAR(100)  NOT NULL,
    reviewed_by_role  VARCHAR(50),
    reviewed_at       VARCHAR(50)   NOT NULL,

    PRIMARY KEY (id),
    KEY idx_field_correction_audits_company_id (company_id),
    CONSTRAINT fk_audit_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    CONSTRAINT fk_audit_company  FOREIGN KEY (company_id)  REFERENCES companies(id) ON DELETE CASCADE
    ) ENGINE=InnoDB;

-- =========================================================
-- 7. TEMPLATE_CONFIG — cấu hình loại chứng từ (động, không hardcode)
-- =========================================================
CREATE TABLE IF NOT EXISTS template_config (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    doc_type_code   VARCHAR(100) NOT NULL,
    display_name    VARCHAR(255) NOT NULL,
    anchors         TEXT         NOT NULL,   -- từ khóa định danh, cách nhau bởi dấu phẩy
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    note            VARCHAR(500),
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_template_config_doc_type_code (doc_type_code)
    ) ENGINE=InnoDB;

-- =========================================================
-- 8. TEMPLATE_FIELD_CONFIG — cấu hình field theo từng loại chứng từ
-- =========================================================
CREATE TABLE IF NOT EXISTS template_field_config (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    doc_type_code   VARCHAR(100)  NOT NULL,
    field_key       VARCHAR(100)  NOT NULL,
    ui_label        VARCHAR(255)  NOT NULL,
    data_type       VARCHAR(20)   NOT NULL,   -- TEXT / NUMBER / DATE
    required        TINYINT(1)    NOT NULL DEFAULT 0,
    regex_pattern   VARCHAR(1000) NOT NULL,
    note            VARCHAR(500),
    display_order   INT           NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_field_config_doc_type_code (doc_type_code),
    CONSTRAINT fk_field_config_doc_type
    FOREIGN KEY (doc_type_code) REFERENCES template_config (doc_type_code)
    ON UPDATE CASCADE ON DELETE CASCADE
    ) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS users (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    username       VARCHAR(100)  NOT NULL,
    password_hash  VARCHAR(255)  NOT NULL,
    email          VARCHAR(255)  NOT NULL,
    phone_number   VARCHAR(50),
    role           VARCHAR(50)   NOT NULL DEFAULT 'REVIEWER',
    company_id     BIGINT        NOT NULL,
    active         TINYINT(1)    NOT NULL DEFAULT 1,
    created_at     VARCHAR(100),

    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_company_id (company_id),
    CONSTRAINT fk_users_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
    ) ENGINE=InnoDB;