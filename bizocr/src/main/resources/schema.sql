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

    -- Generated columns từ extracted_fields
    tax_code             VARCHAR(20)   AS (JSON_UNQUOTE(JSON_EXTRACT(extracted_fields, '$.tax_code'))) STORED,
    issue_date           VARCHAR(20)   AS (JSON_UNQUOTE(JSON_EXTRACT(extracted_fields, '$.issue_date'))) STORED,
    total_amount         VARCHAR(30)   AS (JSON_UNQUOTE(JSON_EXTRACT(extracted_fields, '$.total_amount'))) STORED,

    received_at          VARCHAR(50),
    file_path            VARCHAR(255),

    raw_ocr_text         LONGTEXT,
    ocr_engine           VARCHAR(50),                           -- Engine OCR đã xử lý
    matched_by           VARCHAR(50),                           -- TEMPLATE_MATCHING, AI_FALLBACK, ...
    used_ai_fallback     TINYINT(1)    NOT NULL DEFAULT 0,
    model_version        VARCHAR(100),

    reviewed_at          VARCHAR(50),
    reviewed_by          VARCHAR(100),

    owner_company_id     BIGINT        NOT NULL DEFAULT 0,        -- Tenant sở hữu document

-- Liên kết Multi-Signal Identification
    company_id           BIGINT,
    company_match_score  DOUBLE,
    company_matched_by   VARCHAR(50),

    contact_name          VARCHAR(255),
    contact_title         VARCHAR(255),
    industry              VARCHAR(255),
    approved_at           DATETIME,

    -- ML / Continuous Learning fields (Từ Migration V2)
    predicted_fields_json JSON         COMMENT 'Snapshot dự đoán của model tại thời điểm bóc tách (chưa bị human ghi đè)',
    human_verified        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1 khi kế toán tin tưởng xác nhận đây là dữ liệu đúng',
    training_eligible     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1 khi đủ điều kiện đưa vào lô huấn luyện',
    training_batch_id     BIGINT                          COMMENT 'FK tới training_batches.id khi document đã được gom vào lô',

    PRIMARY KEY (id),
    KEY idx_documents_owner_company_id (owner_company_id),
    KEY idx_documents_company_id (company_id),
    KEY idx_documents_tax_code (tax_code),
    KEY idx_documents_issue_date (issue_date),
    KEY idx_documents_total_amount (total_amount),
    KEY idx_documents_cards (owner_company_id, status, approved_at),
    -- Chỉ mục cho vòng lặp huấn luyện (Từ Migration V3)
    KEY idx_documents_training (training_eligible, training_batch_id),
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

-- =========================================================
-- 9. USERS — thông tin tài khoản người dùng
-- =========================================================
CREATE TABLE IF NOT EXISTS users (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    username         VARCHAR(100)  NOT NULL,
    password_hash    VARCHAR(255)  NOT NULL,
    email            VARCHAR(255)  NOT NULL,
    phone_number     VARCHAR(50),
    role             VARCHAR(50)   NOT NULL DEFAULT 'REVIEWER',
    company_id       BIGINT        NOT NULL,
    active           TINYINT(1)    NOT NULL DEFAULT 1,
    trusted_labeler  TINYINT(1)    NOT NULL DEFAULT 0 COMMENT 'Chỉ bật tay cho tài khoản kế toán đáng tin cậy; ảnh hưởng trực tiếp đến training_eligible',
    created_at       VARCHAR(100),

    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_company_id (company_id),
    CONSTRAINT fk_users_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- =========================================================
-- 10. TRAINING_BATCHES — Lô huấn luyện vòng học liên tục
-- =========================================================
CREATE TABLE IF NOT EXISTS training_batches (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    status               VARCHAR(20)  NOT NULL DEFAULT 'OPEN'  COMMENT 'OPEN | READY | RUNNING | PROMOTED | REJECTED',
    size                 INT          NOT NULL DEFAULT 0        COMMENT 'Số document đã gom vào lô',
    model_version_before VARCHAR(100)                          COMMENT 'Phiên bản model trước khi huấn luyện',
    model_version_after  VARCHAR(100)                          COMMENT 'Phiên bản model sau khi huấn luyện thành công',
    metrics_json         JSON                                  COMMENT 'Kết quả đánh giá từ Python (accuracy, f1, ...)',
    created_at           TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    KEY idx_training_batches_status (status)
) ENGINE=InnoDB COMMENT='Quản lý lô huấn luyện vòng học liên tục';

-- =========================================================
-- 11. CONTACTS — danh bạ người liên hệ từ namecard
-- =========================================================
CREATE TABLE IF NOT EXISTS contacts (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    owner_company_id      BIGINT        NOT NULL,           -- Tenant sở hữu (từ JWT)
    created_by            BIGINT        NOT NULL DEFAULT 0, -- user_id tạo bản ghi
    document_id           BIGINT,                           -- Document namecard nguồn gốc
    company_id            BIGINT,                           -- Công ty của người này

    -- Thông tin cá nhân
    full_name             VARCHAR(255)  NOT NULL,
    normalized_name       VARCHAR(255)  NOT NULL,           -- bỏ dấu, uppercase, dùng FULLTEXT search
    title                 VARCHAR(255),                     -- chức vụ
    department            VARCHAR(255),
    phone                 VARCHAR(20),                      -- chuẩn hóa 0xxxxxxxxx
    mobile                VARCHAR(20),
    email                 VARCHAR(255),
    website               VARCHAR(255),
    fax                   VARCHAR(50),
    company_phone         VARCHAR(50),
    company_email         VARCHAR(255),

    -- CRM nhẹ
    met_at                VARCHAR(500),                     -- gặp ở đâu (VD: "Hội thảo Tech 2026")
    met_date              DATE,
    notes                 TEXT,
    tags                  VARCHAR(500),                     -- "khách hàng,VIP,tech" phân cách dấu phẩy
    last_contacted        DATETIME,
    industry              VARCHAR(255),
    approved_at           DATETIME,

    -- Dedup: OK | POSSIBLE_DUPLICATE
    dedup_status          VARCHAR(20)   NOT NULL DEFAULT 'OK',
    possible_duplicate_id BIGINT        NULL COMMENT 'FK tới contacts.id khi bị cờ nghi trùng',

    created_at            DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    KEY idx_contacts_owner          (owner_company_id),
    KEY idx_contacts_email          (email),
    KEY idx_contacts_phone          (phone),
    KEY idx_contacts_company        (company_id),
    KEY idx_contacts_dedup          (dedup_status),
    KEY idx_contacts_cards          (owner_company_id, approved_at),
    FULLTEXT KEY ft_contacts_name   (normalized_name),
    CONSTRAINT fk_contacts_document      FOREIGN KEY (document_id)    REFERENCES documents(id)  ON DELETE SET NULL,
    CONSTRAINT fk_contacts_company       FOREIGN KEY (company_id)     REFERENCES companies(id)  ON DELETE SET NULL,
    CONSTRAINT fk_contacts_owner_company FOREIGN KEY (owner_company_id) REFERENCES companies(id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='Danh bạ người liên hệ số hóa từ namecard vật lý';

-- =========================================================
-- 12. DOCUMENT_IMAGES — ảnh mặt trước/sau của namecard
--     Tách ra khỏi documents để không làm thay đổi pipeline
-- =========================================================
CREATE TABLE IF NOT EXISTS document_images (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    document_id   BIGINT        NOT NULL,
    side          VARCHAR(10)   NOT NULL COMMENT 'FRONT | BACK',
    storage_key   VARCHAR(500)  NOT NULL COMMENT 'Đường dẫn tương đối, không tuyệt đối',
    thumb_key     VARCHAR(500)           COMMENT 'Thumbnail key (null nếu Python lỗi → UI dùng CSS fallback)',
    sha256        VARCHAR(64)            COMMENT 'Hash ảnh để dedup upload trùng',
    ocr_status    VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | DONE | SKIPPED | FAILED',
    raw_text_back LONGTEXT               COMMENT 'OCR text riêng cho mặt sau (side=BACK), fill-in-blank',
    uploaded_at   DATETIME      DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_side    (document_id, side),
    KEY idx_doc_images_doc    (document_id),
    CONSTRAINT fk_doc_images_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='Ảnh mặt trước/sau namecard, hỗ trợ thumbnail và OCR mặt sau nhẹ';

-- Migration an toàn cho các DB đã chạy trước đó:
-- Thêm cột nếu chưa tồn tại
ALTER TABLE documents ADD COLUMN IF NOT EXISTS industry VARCHAR(255);
ALTER TABLE documents ADD COLUMN IF NOT EXISTS approved_at DATETIME;
ALTER TABLE contacts ADD COLUMN IF NOT EXISTS industry VARCHAR(255);
ALTER TABLE contacts ADD COLUMN IF NOT EXISTS approved_at DATETIME;


