# ERD — 14 bảng

Sau khi cắt `courses`, `enrollments`, `appeals`, `rewards` (xem `scope.md`).

> **Đính chính số bảng.** Tài liệu gốc ghi "16 bảng" nhưng liệt kê 17. `PROJECT.md` §6
> ghi "12". Con số đúng là **14**:
>
> 17 (tài liệu gốc) − 4 (đã cắt) = 13, **+1 bảng `student_devices` được thêm mới**.
>
> **Lý do thêm `student_devices`:** tài liệu §7.4 yêu cầu "mỗi tài khoản sinh viên gắn với
> một device fingerprint duy nhất, đổi thiết bị phải qua duyệt của cán bộ, có ghi nhật ký"
> — nhưng danh sách bảng của tài liệu chỉ có `attendances.device_fp`, không đủ để lưu
> trạng thái đăng ký/duyệt/thu hồi thiết bị. Đây là **sửa thiếu sót của tài liệu**, không
> phải phình phạm vi: device binding nằm trong khối lõi "điểm danh nhiều lớp kiểm tra"
> (`scope.md`), và thiếu nó thì toàn bộ cơ chế QR động vô nghĩa.
>
> Đã áp dụng thật: Flyway `V1__init` chạy thành công, 14 bảng + `flyway_schema_history`.

## Nhóm 1 — Định danh và tổ chức

```
users(id, username, password_hash, role, student_id?, staff_org_id?,
      enabled, created_at, updated_at)
  role ∈ {STUDENT, STAFF, ADMIN}
  CHECK: STUDENT phải có student_id, STAFF phải có staff_org_id

student_devices(id, student_id, device_fp, label, status, approved_by,
                approved_at, created_at)
  UNIQUE(student_id, device_fp)
  status ∈ {PENDING, ACTIVE, REVOKED}
  Bảng thêm mới so với tài liệu gốc — xem đính chính đầu file.
  Thiếu bảng này thì cơ chế QR động vô nghĩa: sinh viên chỉ cần đưa tài khoản
  cho bạn quét hộ. Đổi thiết bị phải qua duyệt của cán bộ.

students(id, mssv UNIQUE, full_name, class_code, faculty_id, cohort,
         did, created_at, updated_at)
  did: DID dạng did:key sinh từ khóa issuer của tổ chức (không phải khóa riêng SV)

organizations(id, name, type, parent_id → organizations.id,
              issuer_address, on_chain_registered_at, created_at, updated_at)
  type ∈ {TRUONG, KHOA, DOAN, HOI_SV, CLB, TRUNG_TAM, DOANH_NGHIEP}
  issuer_address: địa chỉ ví đã đăng ký trong IssuerRegistry
```

## Nhóm 2 — Sự kiện và điểm danh

```
events(id, org_id → organizations.id, title, type, start_at, end_at,
       location, lat, lng, radius_m, capacity, secret_key, criteria_code,
       points, status, created_at, updated_at)
  secret_key: seed HMAC RIÊNG cho từng sự kiện — không dùng chung toàn hệ thống.
              Lộ một secret chỉ ảnh hưởng một sự kiện.
  criteria_code: mã tiêu chí rèn luyện (C1..C5) mà sự kiện này đóng góp
  status ∈ {DRAFT, OPEN, RUNNING, CLOSED}

registrations(id, event_id, student_id, registered_at, status)
  UNIQUE(event_id, student_id)
  status ∈ {REGISTERED, CANCELLED}

attendances(id, event_id, student_id, checkin_at, checkout_at, method,
            device_fp, lat, lng, qr_slot, verified, geofence_ok,
            nonce, leaf_hash, created_at)
  UNIQUE(event_id, student_id)
  method ∈ {QR_SCAN, QR_SHOW, MANUAL, OFFLINE_SYNC}
  geofence_ok: cảnh báo mềm, KHÔNG chặn check-in (GPS trong nhà rất kém)
  nonce: BINARY(16) ngẫu nhiên — xem PROJECT.md §2.3
  leaf_hash: BINARY(32), đơn vị đưa vào cây Merkle
```

## Nhóm 3 — Chứng chỉ số

```
credentials(id, student_id, student_code, student_name,
            issuer_org_id, issuer_address,
            type, semester, activity_count, total_points,
            payload_json, issued_at, expires_at,
            revoked_at, revoke_tx_hash,
            status_list_index, signature, nonce, leaf_hash, created_at)

  -- CHỤP ẢNH (V4) — KHÔNG đọc qua khóa ngoại.
  student_code, student_name, issuer_address:
      bản sao chốt lúc cấp. Đọc qua khóa ngoại thì đổi tên một sinh viên sẽ
      làm MỌI Merkle proof đã neo của họ fail vĩnh viễn, và fail im lặng —
      AnchorRegistry không cho neo lại nên không có đường sửa.
      Xem docs/canonicalization.md §11.3.
  issuer_address: '0x' + 40 hex CHỮ THƯỜNG (ck_cred_issuer_address).
      Dạng checksum EIP-55 bị cấm — nó làm JCS ra hai chuỗi khác nhau
      giữa hai phía.

  -- NỘI DUNG PHÁT BIỂU, chốt lúc cấp.
  semester, activity_count, total_points:
      thành `claims` trong payload neo. Tham gia thêm hoạt động sau đó thì
      cấp credential MỚI, không sửa bản cũ.

  -- BẰNG CHỨNG.
  payload_json: đúng chuỗi JCS(payload) đã bam và đã ký. Bản đối chứng
      chống trôi lược đồ — job neo dựng lại rồi so từng byte.
  leaf_hash: tính NGAY lúc cấp (khác attendances.leaf_hash do job neo điền
      sau), vì `signature` ký chính nó.
  signature: ECDSA secp256k1 trên leaf_hash, 65 byte r||s||v, v ∈ {27,28}.
      KHÔNG phải ES256K của JOSE — recovery id là thứ cho verifier phục hồi
      địa chỉ ví mà không cần biết trước khóa công khai, tức là không cần
      gọi backend. Xem docs/canonicalization.md §12.2.

  -- THU HỒI.
  status_list_index: cấp NGẪU NHIÊN từ pool còn trống, KHÔNG tuần tự.
      Cấp tuần tự làm sự kiện StatusChanged(index) trên chuỗi lộ thứ tự
      cấp phát và tương quan với danh sách sinh viên (PROJECT.md §2.3).
      NẰM TRONG payload được ký — để ngoài thì người cầm credential đã bị
      thu hồi chỉ cần trỏ verifier sang một bit chưa bật.
  revoked_at, revoke_tx_hash: chỉ đặt SAU khi giao dịch setRevoked() lên
      chuỗi. Nguồn sự thật là bit trên StatusList, không phải hai cột này.

  -- ĐÃ BỎ.
  payload_hash: bỏ ở V5. Nó bam cùng nội dung với leaf_hash (chỉ thiếu tiền
      tố miền) nên vừa thừa vừa có thể trôi khỏi leaf_hash. Cột NOT NULL
      không được entity ánh xạ còn làm mọi INSERT credential thất bại.
```

## Nhóm 4 — Chấm điểm rèn luyện

```
rulesets(id, version, semester, json_body, ruleset_hash, nonce, leaf_hash,
         effective_from, created_at)
  ruleset_hash được neo với domain "RULESET" — chứng minh bộ quy tắc nào đã
  dùng để chấm, chống sửa quy chế sau khi công bố điểm
  nonce, leaf_hash: thêm ở Flyway V2. leaf_hash KHÁC ruleset_hash — nó có
  tiền tố bytes8(domain) và có nonce. Nonce ở bảng này KHÔNG phải biện pháp
  riêng tư (ruleset vốn công khai) mà để LeafHasher có một đường đi duy nhất
  cho cả năm miền. Xem docs/canonicalization.md §9.2

score_runs(id, semester, ruleset_id → rulesets.id, run_at, status)
  status ∈ {RUNNING, DONE, FAILED}

scores(id, run_id → score_runs.id, student_id, c1, c2, c3, c4, c5, total,
       classification, evidence_hash, nonce, leaf_hash, created_at)
  UNIQUE(run_id, student_id)
  evidence_hash = keccak(danh sách ĐÃ SẮP XẾP các leaf_hash đầu vào)
                  → sinh viên tự tính lại điểm từ dữ liệu công khai và đối chiếu
  classification: xuất sắc 90-100 | tốt 80-<90 | khá 65-<80 |
                  trung bình 50-<65 | yếu 35-<50 | kém <35
```

## Nhóm 5 — Neo dữ liệu và kiểm toán

```
anchor_batches(id, domain, merkle_root, leaf_count, tx_hash, block_number,
               anchored_at, created_at)
  domain ∈ {ATTEND, CRED, SCORE, AUDIT, RULESET}
  merkle_root: BINARY(32)

anchor_leaves(id, batch_id → anchor_batches.id, leaf_hash, proof_json,
              source_table, source_id)
  INDEX(source_table, source_id) — tra proof theo bản ghi gốc
  INDEX(leaf_hash)

audit_logs(id, actor_id, action, entity, entity_id, before_json, after_json,
           prev_hash, hash, nonce, leaf_hash, created_at)
  hash = keccak(prev_hash || record)  ← HASH CHAIN, mắt xích nối các bản ghi
  Đây là cơ chế hiện thực hóa luận điểm 2.2a — chống sửa hồi tố bởi
  chính người quản trị.

  HAI HASH, DÙNG NHẦM LÀ HỎNG (thêm ở Flyway V2):
    hash      = mắt xích của chuỗi băm. Đứt xích ⇒ phát hiện chèn/sửa quá khứ.
    leaf_hash = lá trong cây Merkle domain "AUDIT". Chứng minh MỘT bản ghi
                cụ thể đã tồn tại.
  Vẫn cần nonce dù đã có prev_hash: prev_hash NULL ở bản ghi đầu tiên, và
  before_json/after_json chứa dữ liệu cá nhân thật.
  Xem docs/canonicalization.md §9.3
```

---

## Ba chi tiết dễ bị bỏ sót

1. **`leaf_hash` xuất hiện ở mọi bảng cần neo** (`attendances`, `credentials`, `scores`).
   Không có cột này thì không sinh được proof cho từng bản ghi.

2. **`audit_logs` là hash chain**, không phải log thường. `prev_hash` trỏ tới bản ghi
   trước. Chèn hoặc sửa một bản ghi quá khứ làm đứt chuỗi → phát hiện được.

3. **`events.secret_key` riêng từng sự kiện.** Đây là điểm khác biệt an ninh quan trọng
   so với dùng một secret toàn hệ thống.

## Chi tiết thứ tư — bổ sung so với tài liệu gốc

4. **`nonce` ở mọi bảng có `leaf_hash`.** Thiếu nó, payload nằm trong không gian đoán
   được (MSSV × eventId × thời gian ≈ 10⁸ tổ hợp) và ai cầm một leaf hash vét cạn được
   nội dung trong vài giây. Vỡ ngay khi sinh viên xuất bundle, vì proof chứa sibling
   hash — tức hash bản ghi **của sinh viên khác**. Xem `PROJECT.md` §2.3.

   **Cả năm miền neo, không phải ba.** `V1__init` chỉ có `nonce` ở `attendances`,
   `credentials`, `scores`; `rulesets` và `audit_logs` thiếu, nên hai miền `RULESET` và
   `AUDIT` **không sinh được leaf nào** (`LeafHasher` từ chối payload không có nonce).
   Flyway `V2` sửa chỗ đó.

   **`NOT NULL` một mình không đủ.** Cột `BINARY NOT NULL` thêm vào bảng đã có dữ liệu sẽ
   nhận mặc định ngầm là **toàn byte `0x00`** — vẫn thỏa `NOT NULL`, vẫn khớp regex của
   `LeafHasher`, và vô hiệu hóa đúng biện pháp §2.3. `V2` thêm CHECK constraint
   `nonce <> 0x00…00` ở cả năm bảng.
