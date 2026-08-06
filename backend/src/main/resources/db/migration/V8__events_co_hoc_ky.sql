-- V8 — `events.semester`: cham diem THEO KY can biet su kien thuoc ky nao.
--
-- =============================================================================
-- TRA MOT KHOAN NO DA GHI HAI LAN
-- =============================================================================
--
-- docs/measurements.md 11.7 han che so 3 va javadoc CredentialNowRunner deu ghi:
-- `events` khong co cot hoc ky nen luong cap credential dem TOAN BO ban ghi diem
-- danh cua sinh vien, khong loc duoc theo ky. Voi du lieu demo mot dot thi dung;
-- nhieu ky thi gop.
--
-- Tuan 5 cham diem theo ky nen khong hoan duoc nua.
--
-- =============================================================================
-- QUY UOC HOC KY, va vi sao no la QUY UOC chu khong phai su that
-- =============================================================================
--
-- Nam hoc Viet Nam thong thuong:
--     HK1  thang 8 nam Y   -> thang 1 nam Y+1     ma "Y-1"
--     HK2  thang 2         -> thang 7 nam Y+1     ma "Y-2"
--
-- Vi du: su kien 2026-08-04 thuoc hoc ky "2026-1"; su kien 2027-03-10 thuoc
-- "2026-2".
--
-- DAY LA MOT PHONG DOAN, khong phai lich hoc that cua truong. Truong co the bat
-- dau hoc ky som/muon hon, co hoc ky he, co su kien dien ra giua hai ky. Vi vay:
--
--   - cot de NULLABLE: su kien khong xac dinh duoc ky thi de trong, va viec cham
--     diem BO QUA no thay vi doan bua;
--   - backfill chi la GIA TRI KHOI DAU cho du lieu demo, can bo sua duoc;
--   - KHONG viet quy uoc nay thanh ham trong ma nguon. Neu no nam trong code thi
--     doi lich hoc phai sua code va deploy lai; nam trong mot cot thi chi la mot
--     lenh UPDATE.
--
-- Phai ghi vao phan han che cua bao cao: viec gan su kien vao hoc ky hien dua
-- tren khoang ngay theo quy uoc, chua noi voi lich hoc chinh thuc cua truong.

ALTER TABLE events
    ADD COLUMN semester VARCHAR(16) NULL AFTER end_at;

ALTER TABLE events
    MODIFY COLUMN semester VARCHAR(16) NULL
        COMMENT 'Hoc ky dang YYYY-1 / YYYY-2. NULL = chua xac dinh, viec cham diem BO QUA. Xem V8.';

-- Backfill theo quy uoc tren, dua vao `start_at`.
UPDATE events
   SET semester = CASE
       WHEN MONTH(start_at) >= 8 THEN CONCAT(YEAR(start_at), '-1')
       WHEN MONTH(start_at) <= 1 THEN CONCAT(YEAR(start_at) - 1, '-1')
       ELSE CONCAT(YEAR(start_at) - 1, '-2')
   END
 WHERE semester IS NULL;

CREATE INDEX idx_event_semester ON events (semester);

-- `scores.classification` da co CHECK tu V1 (XUAT_SAC/TOT/KHA/TRUNG_BINH/YEU/KEM)
-- va `ck_score_range` chot thang 20/25/20/25/10. Khong dung vao — chung dung theo
-- Thong tu 16/2015/TT-BGDDT va bo quy tac phai nam trong khung do.
