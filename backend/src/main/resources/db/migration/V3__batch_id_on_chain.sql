-- V3 — Luu batchId ON-CHAIN vao anchor_batches.
--
-- THIEU SOT CUA V1. Bang anchor_batches co cot `id` AUTO_INCREMENT cua CSDL, nhung KHONG co
-- cho nao luu `batchId` ma contract dung lam khoa. Hai so nay khac nhau va khong thay the
-- nhau duoc:
--
--   id        khoa noi bo cua CSDL, khong xuat hien tren chuoi
--   batch_id  tham so `uint64 batchId` cua AnchorRegistry.anchor(), theo quy uoc
--             YYYYMMDDnn (xem AnchorBatchId.java)
--
-- Khong co cot nay thi bundle cua sinh vien khong mang duoc batchId, va verifier khong goi
-- duoc getRoot(domain, batchId) -- tuc la mat luon luan diem 2 (xac minh doc lap). Dung `id`
-- lam batchId cung duoc ve mat ky thuat, nhung khi do batchId phu thuoc thu tu chen cua CSDL
-- va khong doc duoc ngay neo tu chinh con so.
--
-- Them luon `error_message` de lan neo hong con lai dau vet. Lo co tx_hash IS NULL la lo da
-- dung xong cay Merkle nhung chua len chuoi -- job se neo lai chinh lo do voi cung root,
-- thay vi gom lo moi.

ALTER TABLE anchor_batches
    ADD COLUMN batch_id BIGINT NULL AFTER domain,
    ADD COLUMN error_message VARCHAR(500) NULL AFTER anchored_at;

-- Bang dang rong (chua co giao dich neo nao) nen khong can backfill. Neu ban ghi nao do
-- ton tai, dat batch_id = id de khong vi pham NOT NULL; chung se khong khop chuoi, nhung
-- cung khong co gi tren chuoi de khop ca.
UPDATE anchor_batches SET batch_id = id WHERE batch_id IS NULL;

ALTER TABLE anchor_batches
    MODIFY COLUMN batch_id BIGINT NOT NULL
        COMMENT 'batchId tren chuoi, quy uoc YYYYMMDDnn. KHAC cot id (khoa noi bo CSDL).',
    MODIFY COLUMN error_message VARCHAR(500) NULL
        COMMENT 'Ly do lan neo gan nhat that bai. NULL khi thanh cong.';

-- Mot mien khong duoc co hai lo cung batchId -- vi contract cung khong cho.
-- Rang buoc nay bat loi ngay o tang CSDL thay vi de den luc giao dich bi revert.
ALTER TABLE anchor_batches
    ADD UNIQUE KEY uk_batch_domain_batchid (domain, batch_id);

-- Tra lo chua len chuoi de neo lai. Dung cho ca viec do dac o tuan 7.
CREATE INDEX idx_batch_pending ON anchor_batches (domain, tx_hash);
