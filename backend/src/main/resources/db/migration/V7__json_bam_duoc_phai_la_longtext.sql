-- V7 — Moi cot JSON co byte DI VAO MOT PHEP BAM phai la LONGTEXT.
--
-- =============================================================================
-- LAN THU HAI CUNG MOT BAY. LAN NAY CHAN CA HO, KHONG CHI CHO DANG DUNG.
-- =============================================================================
--
-- V6 da doi `credentials.payload_json` tu JSON sang LONGTEXT vi MySQL KHONG luu
-- chuoi JSON nguyen van: no phan tich ra, luu o dinh dang nhi phan rieng, roi
-- tuan tu hoa lai khi doc -- sap xep khoa theo DO DAI khoa va chen dau cach sau
-- `:` va `,`. Chuoi JCS da bam khong song sot qua vong luu-doc.
--
-- Gio den luot chuoi bam cua `audit_logs`. Mat xich cua no la
--
--     hash_i = keccak256( prev_hash_i || UTF-8(JCS(record_i)) )
--
-- va `record_i` chua keccak cua CHINH BYTE cua before_json/after_json. De kieu
-- JSON thi byte doc ra khac byte da bam, va TOAN BO chuoi bam dut ngay o ban ghi
-- dau tien -- dung thu no sinh ra de phat hien.
--
-- =============================================================================
-- QUY TAC CHUNG, ghi lai de lan thu ba khong xay ra
-- =============================================================================
--
--   Cot JSON co byte di vao mot phep bam  ->  BAT BUOC LONGTEXT
--   Cot JSON chi de doc/truy van          ->  kieu JSON van tot hon
--
-- Da ra soat toan bo cot JSON trong luoc do:
--
--   credentials.payload_json   -> da doi o V6
--   audit_logs.before_json     -> doi o day
--   audit_logs.after_json      -> doi o day
--   rulesets.json_body         -> doi o day, xem ly do rieng ben duoi
--   anchor_leaves.proof_json   -> GIU NGUYEN kieu JSON, xem ly do ben duoi
--
-- `rulesets.json_body` chua duoc dung (mien RULESET la viec tuan 5), nhung bo
-- quy tac cham diem CHAC CHAN se duoc neo -- do la ca diem cua mien RULESET, de
-- sinh vien tu tinh lai duoc diem cua minh. Doi bay gio khi bang con RONG re hon
-- nhieu so voi doi sau khi da neo, luc do phai neo lai tu dau.
--
-- `anchor_leaves.proof_json` GIU NGUYEN va day KHONG phai ngoai le tuy tien:
-- noi dung cua no la mot MANG, ma MySQL giu nguyen thu tu phan tu cua mang (chi
-- sap xep lai khoa cua OBJECT). Ngoai ra byte cua no khong di vao phep bam nao --
-- proof duoc DOC ra roi dung de tinh lai root, chu ban than chuoi JSON khong bi
-- bam. `AnchorProofService.parseProofJson` cung da trim khoang trang. Bang nay
-- dang co 5 dong THAT thuoc mot lo DA NEO tren Amoy, nen khong dung vao.

ALTER TABLE audit_logs
    MODIFY COLUMN before_json LONGTEXT NULL
        COMMENT 'Trang thai truoc, nguyen van. LONGTEXT chu KHONG phai JSON -- keccak cua chinh byte nay di vao chuoi bam, xem V7.',
    MODIFY COLUMN after_json LONGTEXT NULL
        COMMENT 'Trang thai sau, nguyen van. LONGTEXT chu KHONG phai JSON -- xem V7.';

ALTER TABLE rulesets
    MODIFY COLUMN json_body LONGTEXT NOT NULL
        COMMENT 'Bo quy tac cham diem, nguyen van. LONGTEXT vi no se duoc neo o mien RULESET (tuan 5) -- xem V7.';

-- Chuoi bam can lay ban ghi CUOI CUNG that nhanh o moi lan ghi.
-- `id` la khoa chinh AUTO_INCREMENT nen ORDER BY id DESC LIMIT 1 da dung index,
-- khong can index them.

-- Chan mot cach hong im lang: hai ban ghi khong duoc tro cung mot prev_hash.
--
-- Chuoi bam chi co nghia neu no la mot DUONG THANG. Neu hai ban ghi cung tro ve
-- mot ban ghi cha thi chuoi thanh cai CAY, va ke tan cong xoa mot nhanh ma khong
-- lam dut xich o nhanh con lai -- tuc la mat dung thu no bao ve.
--
-- Truong hop nay xay ra that khi hai luong cung doc "ban ghi cuoi" roi cung ghi.
-- He chay mot instance nen xac suat nho, nhung "nho" khong phai "khong co", va
-- CSDL la cho duy nhat chan duoc chac chan.
--
-- prev_hash NULL chi o ban ghi DAU TIEN; UNIQUE trong MySQL cho phep nhieu NULL,
-- nen rang buoc nay khong chan duoc hai ban ghi dau tien. Do la khiem khuyet co
-- that va da co test rieng chan o tang ung dung.
ALTER TABLE audit_logs
    ADD CONSTRAINT uk_audit_prev_hash UNIQUE (prev_hash);
