-- V4 — Credential: chup anh (snapshot) moi truong di vao payload neo, + cot thu hoi.
--
-- =============================================================================
-- VI SAO PHAI CHUP ANH, KHONG DOC QUA KHOA NGOAI
-- =============================================================================
--
-- Payload mien CRED chua `studentCode`, `studentName`, `issuerAddress`. Neu dung
-- nao doc chung qua khoa ngoai (students.mssv, students.full_name,
-- organizations.issuer_address) thi:
--
--   can bo sua ten sinh vien  ->  payload doi  ->  leaf hash doi
--   ->  MOI Merkle proof da neo cua sinh vien do FAIL VINH VIEN
--
-- Va no fail IM LANG: khong co gi bao loi, chi la proof khong verify duoc nua.
-- AnchorRegistry khong cho neo lai (domain, batchId) nen khong co duong sua.
--
-- Cach chan duy nhat la chup anh gia tri tai thoi diem cap credential. Doi ten
-- sinh vien sau do khong dong toi credential da cap -- dung nhu mong muon: to
-- chuc cap phat da ky vao mot phat bieu cu the tai mot thoi diem cu the.
--
-- >> CANH BAO KE THUA: `attendances` KHONG chup anh. AttendancePayload.of() doc
-- >> a.getStudent().getMssv() qua khoa ngoai. Doi MSSV cua mot sinh vien se lam
-- >> hong moi proof diem danh da neo cua ho. MSSV gan nhu khong bao gio doi nen
-- >> chua sua, nhung day la NO KY THUAT co that -- ghi vao phan han che cua bao
-- >> cao, dung de hoi dong tu tim ra. Xem docs/canonicalization.md §11.
-- =============================================================================
--
-- Bang `credentials` dang RONG (chua cap credential nao), da kiem truoc khi viet
-- migration nay -- nen them cot NOT NULL truc tiep la an toan, khong can backfill.
-- Neu ban ghi da ton tai thi cac lenh duoi se loi, va do la hanh vi dung: xem
-- docs/canonicalization.md §9.5 ve viec dung backfill bang du lieu da neo.

ALTER TABLE credentials
    ADD COLUMN student_code   VARCHAR(32)  NOT NULL AFTER student_id,
    ADD COLUMN student_name   VARCHAR(255) NOT NULL AFTER student_code,
    ADD COLUMN issuer_address CHAR(42)     NOT NULL AFTER issuer_org_id,
    ADD COLUMN semester       VARCHAR(16)  NOT NULL AFTER type,
    ADD COLUMN activity_count INT          NOT NULL AFTER semester,
    ADD COLUMN total_points   INT          NOT NULL AFTER activity_count;

ALTER TABLE credentials
    MODIFY COLUMN student_code VARCHAR(32) NOT NULL
        COMMENT 'CHUP ANH tu students.mssv luc cap. KHONG doc qua khoa ngoai -- xem dau file V4.',
    MODIFY COLUMN student_name VARCHAR(255) NOT NULL
        COMMENT 'CHUP ANH tu students.full_name luc cap.',
    MODIFY COLUMN issuer_address CHAR(42) NOT NULL
        COMMENT 'CHUP ANH dia chi vi da ky. Chu thuong -- xem ck_cred_issuer_address.',
    MODIFY COLUMN semester VARCHAR(16) NOT NULL
        COMMENT 'Hoc ky ma credential nay tong ket, vi du 2026-1.',
    MODIFY COLUMN activity_count INT NOT NULL
        COMMENT 'So hoat dong da diem danh trong hoc ky, chot luc cap.',
    MODIFY COLUMN total_points INT NOT NULL
        COMMENT 'Tong diem hoat dong, chot luc cap.';

-- Dia chi chu thuong la RANG BUOC CAU TRUC, khong phai quy uoc.
--
-- EIP-55 tron hoa/thuong theo hash cua chinh dia chi. Java va JS deu sinh duoc
-- dang checksum, nhung neu mot phia luu checksum con phia kia luu chu thuong thi
-- JCS ra hai chuoi khac nhau -> hai leaf khac nhau -> proof fail im lang. Chot
-- chu thuong o tang CSDL de khong con cho lech.
ALTER TABLE credentials
    ADD CONSTRAINT ck_cred_issuer_address CHECK (issuer_address REGEXP '^0x[0-9a-f]{40}$');

-- Cot thu hoi. Bit tren StatusList moi la nguon su that ma verifier doc; hai cot
-- nay la ban sao off-chain de backend biet minh da gui giao dich nao.
--
-- Thu tu ghi giong het job neo (AnchorJob javadoc): gui giao dich TRUOC, ghi
-- revoked_at SAU. Nguoc lai thi CSDL bao da thu hoi trong khi tren chuoi chua co
-- gi, va verifier -- thu duy nhat nha tuyen dung tin -- van thay credential con
-- hieu luc.
ALTER TABLE credentials
    ADD COLUMN revoked_at     DATETIME(3) NULL AFTER expires_at,
    ADD COLUMN revoke_tx_hash CHAR(66)    NULL AFTER revoked_at;

ALTER TABLE credentials
    MODIFY COLUMN revoked_at DATETIME(3) NULL
        COMMENT 'Chi dat SAU khi giao dich setRevoked() da len chuoi.',
    MODIFY COLUMN revoke_tx_hash CHAR(66) NULL
        COMMENT 'Giao dich StatusList.setRevoked(). NULL khi chua thu hoi.';

-- Sua chu thich cot `signature` cho dung thuat toan THAT SU dung.
--
-- V1 ghi 'ES256K'. Chu thich do KHONG dung va de gay hieu nham khi doc lai:
-- ES256K trong JOSE la ECDSA tren secp256k1 voi ham bam SHA-256 va chu ky 64 byte
-- (r||s, khong co recovery id). Thu dang dung o day la:
--
--   sig = ECDSA_secp256k1( leaf_hash )   -- leaf_hash da la keccak256, khong bam lai
--   dinh dang 65 byte: r(32) || s(32) || v(1), v thuoc {27, 28}
--
-- Chon co chu dich, khong phai cho tien: recovery id cho phep PHUC HOI DIA CHI vi
-- tu chu ky. Nho do verifier doi chieu thang dia chi phuc hoi duoc voi
-- IssuerRegistry tren chuoi -- khong can biet truoc khoa cong khai, khong can goi
-- backend de tra khoa. Chu ky ES256K 64 byte thuan thi khong lam duoc viec do.
-- Doi lai: lech chuan JOSE, phai ghi vao phan han che cua bao cao.
--
-- (Lenh MODIFY cho cot nay nam o cuoi file, chung voi hai cot bat buoc con lai.)

-- payload_json luu dung chuoi JCS da bam, khong phai mot ban dung lai.
--
-- Day la ban sao doi chung: luc neo, job dung lai payload tu cac cot roi canonical
-- hoa va so voi chuoi nay. Lech mot byte la nem loi ngay thay vi neo mot leaf khac
-- voi leaf da ky.
ALTER TABLE credentials
    MODIFY COLUMN payload_json JSON NOT NULL
        COMMENT 'Dung chuoi JCS(payload) da dung de tinh leaf_hash va de ky. Doi chung chong troi.';

-- Ba cot V1 de NULL nhung thuc te la BAT BUOC -- siet lai.
--
-- V1 viet lieu de vi luc do chua biet luong cap credential trong nhu the nao. Gio da biet:
-- KHONG cap duoc credential neu thieu bat ky cai nao trong ba.
--
--   leaf_hash          tinh NGAY luc cap, khong doi den luc neo -- vi `signature` ky chinh no.
--                      (Khac `attendances.leaf_hash`, cai do do job neo dien sau.)
--   signature          khong ky thi khong chung minh duoc AI cap -> mat luan diem 3.
--   status_list_index  khong cap thi khong bao gio thu hoi duoc credential do.
--
-- De NULL nghia la mot loi o tang service lang le sinh ra credential khong xac minh duoc,
-- va chi phat hien khi nha tuyen dung thu verify -- luc do da qua muon.
ALTER TABLE credentials
    MODIFY COLUMN leaf_hash BINARY(32) NOT NULL
        COMMENT 'keccak256 theo cong thuc leaf. Tinh LUC CAP vi chu ky ky chinh no.',
    MODIFY COLUMN signature VARBINARY(128) NOT NULL
        COMMENT 'ECDSA secp256k1 tren leaf_hash, 65 byte r||s||v. KHONG phai ES256K cua JOSE -- xem V4.',
    MODIFY COLUMN status_list_index BIGINT NOT NULL
        COMMENT 'Cap NGAU NHIEN tu pool con trong, KHONG tuan tu. Xem PROJECT.md 2.3.';

-- Tra credential chua neo. `anchor_leaves` moi la noi biet mot ban ghi da vao lo nao, vi
-- credentials.leaf_hash co gia tri ngay tu luc cap nen KHONG dung lam co "da neo chua".
CREATE INDEX idx_cred_semester ON credentials (semester);
