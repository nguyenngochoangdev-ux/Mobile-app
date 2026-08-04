-- =============================================================================
-- V1__init.sql — 13 bảng
-- Xem docs/erd.md. Đã cắt courses, enrollments, appeals, rewards (docs/scope.md).
--
-- Quy ước:
--   - Mọi thời gian lưu UTC. DATETIME(3) — độ chính xác mili giây, CHỐT CỨNG.
--     Canonicalization serialize ISO-8601 UTC mili giây. Đổi độ chính xác ở đây
--     làm lệch leaf hash giữa Java và JS → mọi Merkle proof fail.
--   - Hash: BINARY(32) (keccak256). Nonce: BINARY(16).
--   - utf8mb4 — tên sinh viên có dấu tiếng Việt.
-- =============================================================================

-- ===== Nhóm 1: Định danh và tổ chức ==========================================

CREATE TABLE organizations (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    name                    VARCHAR(255) NOT NULL,
    type                    VARCHAR(32)  NOT NULL,
    parent_id               BIGINT       NULL,
    issuer_address          CHAR(42)     NULL COMMENT 'Dia chi vi da dang ky trong IssuerRegistry',
    on_chain_registered_at  DATETIME(3)  NULL,
    created_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_org_parent FOREIGN KEY (parent_id) REFERENCES organizations (id),
    CONSTRAINT ck_org_type CHECK (type IN
        ('TRUONG','KHOA','DOAN','HOI_SV','CLB','TRUNG_TAM','DOANH_NGHIEP')),
    INDEX idx_org_parent (parent_id),
    UNIQUE KEY uk_org_issuer_address (issuer_address)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE students (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    mssv        VARCHAR(32)  NOT NULL,
    full_name   VARCHAR(255) NOT NULL,
    class_code  VARCHAR(32)  NULL,
    faculty_id  BIGINT       NULL,
    cohort      VARCHAR(16)  NULL COMMENT 'Khoa hoc, vi du K21',
    did         VARCHAR(255) NULL COMMENT 'did:key sinh tu khoa issuer cua to chuc, KHONG phai khoa rieng SV',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_mssv (mssv),
    CONSTRAINT fk_student_faculty FOREIGN KEY (faculty_id) REFERENCES organizations (id),
    INDEX idx_student_faculty (faculty_id),
    INDEX idx_student_class (class_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE users (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    username       VARCHAR(64)  NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    role           VARCHAR(16)  NOT NULL,
    student_id     BIGINT       NULL,
    staff_org_id   BIGINT       NULL,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    UNIQUE KEY uk_user_student (student_id),
    CONSTRAINT fk_user_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_user_org     FOREIGN KEY (staff_org_id) REFERENCES organizations (id),
    CONSTRAINT ck_user_role CHECK (role IN ('STUDENT','STAFF','ADMIN')),
    -- STUDENT phai co student_id; STAFF phai co staff_org_id
    CONSTRAINT ck_user_link CHECK (
        (role = 'STUDENT' AND student_id IS NOT NULL) OR
        (role = 'STAFF'   AND staff_org_id IS NOT NULL) OR
        (role = 'ADMIN')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Thiet bi da dang ky cua sinh vien.
-- Thieu bang nay thi toan bo co che QR dong vo nghia: sinh vien chi can dua
-- tai khoan cho ban quet ho. Doi thiet bi phai qua duyet cua can bo.
CREATE TABLE student_devices (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    student_id    BIGINT       NOT NULL,
    device_fp     VARCHAR(128) NOT NULL COMMENT 'Device fingerprint',
    label         VARCHAR(128) NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    approved_by   BIGINT       NULL,
    approved_at   DATETIME(3)  NULL,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_student_fp (student_id, device_fp),
    CONSTRAINT fk_device_student  FOREIGN KEY (student_id)  REFERENCES students (id),
    CONSTRAINT fk_device_approver FOREIGN KEY (approved_by) REFERENCES users (id),
    CONSTRAINT ck_device_status CHECK (status IN ('PENDING','ACTIVE','REVOKED')),
    INDEX idx_device_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===== Nhóm 2: Sự kiện và điểm danh ==========================================

CREATE TABLE events (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    org_id         BIGINT       NOT NULL,
    title          VARCHAR(255) NOT NULL,
    type           VARCHAR(32)  NOT NULL,
    start_at       DATETIME(3)  NOT NULL,
    end_at         DATETIME(3)  NOT NULL,
    location       VARCHAR(255) NULL,
    lat            DECIMAL(10,7) NULL,
    lng            DECIMAL(10,7) NULL,
    radius_m       INT          NULL DEFAULT 100,
    capacity       INT          NULL,
    secret_key     VARBINARY(32) NOT NULL
        COMMENT 'Seed HMAC RIENG tung su kien. Lo mot secret chi anh huong mot su kien.',
    criteria_code  VARCHAR(8)   NULL COMMENT 'Tieu chi ren luyen C1..C5 ma su kien dong gop',
    points         INT          NULL DEFAULT 0,
    status         VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_event_org FOREIGN KEY (org_id) REFERENCES organizations (id),
    CONSTRAINT ck_event_status CHECK (status IN ('DRAFT','OPEN','RUNNING','CLOSED')),
    CONSTRAINT ck_event_time CHECK (end_at >= start_at),
    INDEX idx_event_org (org_id),
    INDEX idx_event_start (start_at),
    INDEX idx_event_criteria (criteria_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE registrations (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    event_id       BIGINT      NOT NULL,
    student_id     BIGINT      NOT NULL,
    registered_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    status         VARCHAR(16) NOT NULL DEFAULT 'REGISTERED',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reg_event_student (event_id, student_id),
    CONSTRAINT fk_reg_event   FOREIGN KEY (event_id)   REFERENCES events (id),
    CONSTRAINT fk_reg_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT ck_reg_status CHECK (status IN ('REGISTERED','CANCELLED')),
    INDEX idx_reg_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE attendances (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    event_id     BIGINT        NOT NULL,
    student_id   BIGINT        NOT NULL,
    checkin_at   DATETIME(3)   NOT NULL,
    checkout_at  DATETIME(3)   NULL,
    method       VARCHAR(16)   NOT NULL,
    device_fp    VARCHAR(128)  NULL,
    lat          DECIMAL(10,7) NULL,
    lng          DECIMAL(10,7) NULL,
    qr_slot      BIGINT        NULL COMMENT 'epochSecond / 10 tai thoi diem quet',
    verified     BOOLEAN       NOT NULL DEFAULT FALSE,
    geofence_ok  BOOLEAN       NULL
        COMMENT 'CANH BAO MEM. GPS trong nha rat kem — khong dung de tu choi check-in.',
    nonce        BINARY(16)    NOT NULL
        COMMENT 'Ngau nhien. Thieu nonce thi leaf_hash vet can duoc — xem PROJECT.md 2.3',
    leaf_hash    BINARY(32)    NULL COMMENT 'Don vi dua vao cay Merkle',
    created_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_att_event_student (event_id, student_id),
    CONSTRAINT fk_att_event   FOREIGN KEY (event_id)   REFERENCES events (id),
    CONSTRAINT fk_att_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT ck_att_method CHECK (method IN ('QR_SCAN','QR_SHOW','MANUAL','OFFLINE_SYNC')),
    INDEX idx_att_student (student_id),
    INDEX idx_att_checkin (checkin_at),
    INDEX idx_att_leaf (leaf_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===== Nhóm 3: Chứng chỉ số ==================================================

CREATE TABLE credentials (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    student_id         BIGINT       NOT NULL,
    issuer_org_id      BIGINT       NOT NULL,
    type               VARCHAR(64)  NOT NULL,
    payload_json       JSON         NOT NULL,
    payload_hash       BINARY(32)   NOT NULL,
    issued_at          DATETIME(3)  NOT NULL,
    expires_at         DATETIME(3)  NULL,
    -- Cap NGAU NHIEN tu pool con trong, KHONG tuan tu. Cap tuan tu lam su kien
    -- StatusChanged(index) on-chain lo thu tu cap phat va tuong quan voi danh
    -- sach sinh vien. Xem PROJECT.md 2.3.
    status_list_index  BIGINT       NULL,
    signature          VARBINARY(128) NULL COMMENT 'ES256K bang khoa cua issuer_org',
    nonce              BINARY(16)   NOT NULL,
    leaf_hash          BINARY(32)   NULL,
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cred_status_index (status_list_index),
    CONSTRAINT fk_cred_student FOREIGN KEY (student_id)    REFERENCES students (id),
    CONSTRAINT fk_cred_issuer  FOREIGN KEY (issuer_org_id) REFERENCES organizations (id),
    INDEX idx_cred_student (student_id),
    INDEX idx_cred_type (type),
    INDEX idx_cred_leaf (leaf_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===== Nhóm 4: Chấm điểm rèn luyện ===========================================

CREATE TABLE rulesets (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    version         VARCHAR(32) NOT NULL,
    semester        VARCHAR(16) NOT NULL,
    json_body       JSON        NOT NULL,
    -- Neo voi domain RULESET — chung minh bo quy tac nao da dung de cham,
    -- chong sua quy che sau khi da cong bo diem.
    ruleset_hash    BINARY(32)  NOT NULL,
    effective_from  DATETIME(3) NOT NULL,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ruleset_version_sem (version, semester),
    INDEX idx_ruleset_semester (semester)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE score_runs (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    semester    VARCHAR(16) NOT NULL,
    ruleset_id  BIGINT      NOT NULL,
    run_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    status      VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    PRIMARY KEY (id),
    CONSTRAINT fk_run_ruleset FOREIGN KEY (ruleset_id) REFERENCES rulesets (id),
    CONSTRAINT ck_run_status CHECK (status IN ('RUNNING','DONE','FAILED')),
    INDEX idx_run_semester (semester)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE scores (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    run_id          BIGINT      NOT NULL,
    student_id      BIGINT      NOT NULL,
    c1              INT         NOT NULL DEFAULT 0,
    c2              INT         NOT NULL DEFAULT 0,
    c3              INT         NOT NULL DEFAULT 0,
    c4              INT         NOT NULL DEFAULT 0,
    c5              INT         NOT NULL DEFAULT 0,
    total           INT         NOT NULL DEFAULT 0,
    classification  VARCHAR(16) NULL,
    -- keccak(danh sach DA SAP XEP cac leaf_hash dau vao). Cho phep sinh vien tu
    -- tinh lai diem tu du lieu cong khai va doi chieu. Day la dong gop hoc thuat
    -- ro nhat cua de tai — verifiable computation ban nhe.
    evidence_hash   BINARY(32)  NULL,
    nonce           BINARY(16)  NOT NULL,
    leaf_hash       BINARY(32)  NULL,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_score_run_student (run_id, student_id),
    CONSTRAINT fk_score_run     FOREIGN KEY (run_id)     REFERENCES score_runs (id),
    CONSTRAINT fk_score_student FOREIGN KEY (student_id) REFERENCES students (id),
    -- Thang 100 theo Thong tu 16/2015/TT-BGDDT: 20/25/20/25/10.
    -- KIEM TRA LAI voi quy che truong dang ap dung truoc khi viet ruleset.
    CONSTRAINT ck_score_range CHECK (
        c1 BETWEEN 0 AND 20 AND c2 BETWEEN 0 AND 25 AND c3 BETWEEN 0 AND 20 AND
        c4 BETWEEN 0 AND 25 AND c5 BETWEEN 0 AND 10 AND total BETWEEN 0 AND 100
    ),
    CONSTRAINT ck_score_class CHECK (classification IS NULL OR classification IN
        ('XUAT_SAC','TOT','KHA','TRUNG_BINH','YEU','KEM')),
    INDEX idx_score_student (student_id),
    INDEX idx_score_leaf (leaf_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===== Nhóm 5: Neo dữ liệu và kiểm toán ======================================

CREATE TABLE anchor_batches (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    domain       VARCHAR(8)  NOT NULL,
    merkle_root  BINARY(32)  NOT NULL,
    leaf_count   INT         NOT NULL,
    tx_hash      CHAR(66)    NULL,
    block_number BIGINT      NULL,
    anchored_at  DATETIME(3) NULL,
    created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT ck_batch_domain CHECK (domain IN
        ('ATTEND','CRED','SCORE','AUDIT','RULESET')),
    UNIQUE KEY uk_batch_root (merkle_root),
    INDEX idx_batch_domain (domain),
    INDEX idx_batch_anchored (anchored_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE anchor_leaves (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    batch_id      BIGINT      NOT NULL,
    leaf_hash     BINARY(32)  NOT NULL,
    proof_json    JSON        NOT NULL COMMENT 'Mang sibling hash tu la len goc',
    source_table  VARCHAR(32) NOT NULL,
    source_id     BIGINT      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_leaf_batch FOREIGN KEY (batch_id) REFERENCES anchor_batches (id),
    UNIQUE KEY uk_leaf_source (source_table, source_id, batch_id),
    INDEX idx_leaf_hash (leaf_hash),
    INDEX idx_leaf_source (source_table, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- HASH CHAIN, khong phai log thuong.
-- hash = keccak(prev_hash || record). Chen hoac sua mot ban ghi qua khu lam dut
-- chuoi -> phat hien duoc. Chinh chuoi nay cung duoc neo (domain AUDIT).
-- Day la co che hien thuc hoa luan diem 2.2a.
CREATE TABLE audit_logs (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    actor_id    BIGINT       NULL,
    action      VARCHAR(64)  NOT NULL,
    entity      VARCHAR(64)  NOT NULL,
    entity_id   BIGINT       NULL,
    before_json JSON         NULL,
    after_json  JSON         NULL,
    prev_hash   BINARY(32)   NULL COMMENT 'NULL chi o ban ghi dau tien cua chuoi',
    hash        BINARY(32)   NOT NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES users (id),
    UNIQUE KEY uk_audit_hash (hash),
    INDEX idx_audit_entity (entity, entity_id),
    INDEX idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
