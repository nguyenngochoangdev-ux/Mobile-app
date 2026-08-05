-- V2 — Hoan thien rang buoc nonce cho CA NAM mien neo.
--
-- BOI CANH. `docs/canonicalization.md` §7 ghi viec con lai la "them cot nonce BINARY(16)".
-- Kiem tra lai V1__init thi ba bang da CO SAN nonce NOT NULL: attendances, credentials,
-- scores. Nhung co NAM mien neo, khong phai ba:
--
--     ATTEND -> attendances    OK tu V1
--     CRED   -> credentials    OK tu V1
--     SCORE  -> scores         OK tu V1
--     AUDIT  -> audit_logs     THIEU
--     RULESET-> rulesets       THIEU
--
-- `LeafHasher` (Java) va `leaf.mjs` (JS) deu TU CHOI hash payload khong co nonce hop le,
-- nen hai mien AUDIT va RULESET hien khong sinh duoc leaf nao ca. Migration nay sua dung
-- cho do, va them mot rang buoc chan loi im lang o ca nam bang.
--
-- AN TOAN CUA VIEC BACKFILL. Doi nonce cua mot ban ghi DA NEO se lam moi Merkle proof cua
-- no fail vinh vien, va fail im lang. Backfill duoi day chi an toan vi tinh den 2026-08-05
-- CHUA CO giao dich anchor() nao (`anchor_batches` rong). NEU BANG DA CO DU LIEU DA NEO,
-- DUNG CHAY FILE NAY -- viet migration khac chi dung cho ban ghi chua neo.

-- ===== 1. rulesets — mien RULESET ============================================
--
-- Luu y ve y nghia cua nonce o day: bo quy tac cham diem la TAI LIEU CONG KHAI (sinh vien
-- phai doc duoc de tu tinh lai diem), nen nonce o bang nay KHONG phai bien phap rieng tu
-- nhu o ba bang kia. No ton tai de `LeafHasher` co DUNG MOT duong di cho ca nam mien.
-- Mo ngoai le "mien nay khong can nonce" la mo mot nhanh thu hai trong ham nhay cam nhat
-- cua he thong, va ngoai le kieu do luon lan ra. Nonce cua ruleset duoc cong bo kem ruleset.

ALTER TABLE rulesets
    ADD COLUMN nonce BINARY(16) NULL AFTER ruleset_hash,
    ADD COLUMN leaf_hash BINARY(32) NULL AFTER nonce;

UPDATE rulesets SET nonce = RANDOM_BYTES(16) WHERE nonce IS NULL;

ALTER TABLE rulesets
    MODIFY COLUMN nonce BINARY(16) NOT NULL
        COMMENT 'Ngau nhien 16 byte. O bang nay la de dong nhat duong di LeafHasher, khong phai de che giau — ruleset von cong khai. Xem PROJECT.md 2.3',
    MODIFY COLUMN leaf_hash BINARY(32) NULL
        COMMENT 'Don vi dua vao cay Merkle mien RULESET. KHAC ruleset_hash: leaf_hash co tien to bytes8(domain) va co nonce.',
    ADD INDEX idx_ruleset_leaf (leaf_hash);

-- ===== 2. audit_logs — mien AUDIT ============================================
--
-- HAI HASH TRONG CUNG MOT BANG, DUNG NHAM LA HONG:
--   `hash`      = keccak(prev_hash || record) — MAT XICH cua chuoi bam. Noi ban ghi nay
--                 voi ban ghi truoc. Dut xich = phat hien duoc viec chen/sua qua khu.
--   `leaf_hash` = keccak(bytes8('AUDIT') || ':' || JCS(payload co nonce)) — LA trong cay
--                 Merkle cua lo neo. Cho phep chung minh MOT ban ghi cu the da ton tai.
-- Hai thu phuc vu hai muc dich khac nhau va khong thay the nhau duoc.
--
-- Vi sao van can nonce du da co prev_hash: prev_hash cho entropy o moi ban ghi TRU BAN GHI
-- DAU TIEN (prev_hash NULL), va dua vao no la dua vao mot tinh chat phu. Ngoai ra
-- before_json/after_json chua du lieu ca nhan that, nen day la nhu cau rieng tu that su
-- chu khong chi la dong nhat hoa nhu o rulesets.

ALTER TABLE audit_logs
    ADD COLUMN nonce BINARY(16) NULL AFTER hash,
    ADD COLUMN leaf_hash BINARY(32) NULL AFTER nonce;

UPDATE audit_logs SET nonce = RANDOM_BYTES(16) WHERE nonce IS NULL;

ALTER TABLE audit_logs
    MODIFY COLUMN nonce BINARY(16) NOT NULL
        COMMENT 'Ngau nhien 16 byte. before_json/after_json chua du lieu ca nhan — xem PROJECT.md 2.3',
    MODIFY COLUMN leaf_hash BINARY(32) NULL
        COMMENT 'La trong cay Merkle mien AUDIT. KHAC cot hash: hash la mat xich cua chuoi bam.',
    ADD INDEX idx_audit_leaf (leaf_hash);

-- ===== 3. Chan nonce toan 0x00 o ca nam bang =================================
--
-- Dung `NOT NULL` mot minh la khong du. Cach hong pho bien nhat cua cot BINARY NOT NULL la
-- duoc them vao bang da co du lieu roi nhan gia tri mac dinh ngam — voi BINARY thi mac dinh
-- do la TOAN BYTE 0x00. Mot nonce toan 0x00 van thoa `NOT NULL`, van thoa regex
-- `^0x[0-9a-f]{32}$` cua LeafHasher, nen no di lot qua MOI tang kiem tra hien co va vo hieu
-- hoa dung cai bien phap ma PROJECT.md 2.3 dat ra. Rang buoc duoi day la cho chan cuoi cung.
--
-- Backfill truoc khi them CHECK: neu con ban ghi vi pham thi lenh ADD CONSTRAINT se that bai
-- va ca migration bi cuon lai.

UPDATE attendances SET nonce = RANDOM_BYTES(16) WHERE nonce = 0x00000000000000000000000000000000;
UPDATE credentials SET nonce = RANDOM_BYTES(16) WHERE nonce = 0x00000000000000000000000000000000;
UPDATE scores      SET nonce = RANDOM_BYTES(16) WHERE nonce = 0x00000000000000000000000000000000;

ALTER TABLE attendances
    ADD CONSTRAINT ck_att_nonce_khac_khong CHECK (nonce <> 0x00000000000000000000000000000000);
ALTER TABLE credentials
    ADD CONSTRAINT ck_cred_nonce_khac_khong CHECK (nonce <> 0x00000000000000000000000000000000);
ALTER TABLE scores
    ADD CONSTRAINT ck_score_nonce_khac_khong CHECK (nonce <> 0x00000000000000000000000000000000);
ALTER TABLE rulesets
    ADD CONSTRAINT ck_ruleset_nonce_khac_khong CHECK (nonce <> 0x00000000000000000000000000000000);
ALTER TABLE audit_logs
    ADD CONSTRAINT ck_audit_nonce_khac_khong CHECK (nonce <> 0x00000000000000000000000000000000);
