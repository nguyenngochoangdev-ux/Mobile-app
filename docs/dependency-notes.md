# Ghi chú phụ thuộc — các quyết định cần giải trình

Những lựa chọn dưới đây trông "sai" nếu chỉ nhìn version mới nhất. Ghi lại lý do để
không bị sửa nhầm, và để trả lời được nếu hội đồng hỏi.

---

## 1. Spring Boot 3.5.16 dù đã hết hỗ trợ OSS

**Trạng thái:** 3.5.x hết hỗ trợ OSS từ 30/06/2026. Initializr không còn sinh 3.x.
Chỉ 4.0 và 4.1 còn được hỗ trợ.

**Vẫn chọn 3.5.16 vì:**
- Boot 4.x dùng **Jackson 3** (`tools.jackson`), trong khi **web3j 5.0.3 kéo Jackson 2**
  cho JSON-RPC. Trộn hai Jackson trên cùng classpath ngay tại tầng canonicalization —
  cạm bẫy số 1 của đề tài — rủi ro hơn nhiều so với dùng nhánh EOL.
- Boot 4 đổi tên starter (`spring-boot-starter-webmvc` thay `-web`), nên tutorial và
  câu trả lời StackOverflow hiện có không dùng lại nguyên văn được.
- Đây là prototype testnet, không chạy production. EOL là dòng ghi chú trong phần hạn
  chế, không phải rủi ro bảo mật thật.

**Nếu đổi sang Boot 4:** springdoc lên 3.1.0, đổi tên starter, dự phòng ~3 ngày gỡ Jackson.

---

## 2. react-router-dom 7.18.2 dù `npm audit` báo 1 lỗi high

**Advisory:** GHSA-qwww-vcr4-c8h2 — CSRF bypass ở **RSC mode**, ảnh hưởng 7.12.0–8.2.0.

**Không áp dụng cho đề tài:** app là SPA thuần client, dùng `BrowserRouter`, không có
React Server Components, không có server action. Đường tấn công không tồn tại ở đây.

**Đã thử hạ version và nó TỆ HƠN.** `npm audit fix --force` khuyên về 7.11.0. Nhưng
7.11.0 nằm trong dải 6.0.0–7.17.0 với **4 advisory high**:

| Advisory | Nội dung |
|---|---|
| GHSA-2w69-qvjg-hvjx | XSS qua open redirect |
| GHSA-8v8x-cx79-35w7 | SSR XSS trong `ScrollRestoration` |
| GHSA-49rj-9fvp-4h2h | RCE qua turbo-stream v2 đóng gói kèm |
| GHSA-2j2x-hqr9-3h42 | Open redirect qua URL protocol-relative |

Bản mới nhất của nhánh 7.x hiện là 7.18.2 — chính là bản đang dùng — nên chưa có
bản vá nào để lên.

**Kết luận: giữ 7.18.2. KHÔNG chạy `npm audit fix --force`** — nó sẽ đổi 1 advisory
không áp dụng được lấy 4 advisory áp dụng được.

Đây là một ví dụ tốt để đưa vào báo cáo: công cụ tự động không thay được việc đọc
phạm vi ảnh hưởng của lỗ hổng.

---

## 3. React 19 thay vì React 18 như `PROJECT.md` ghi

Template Vite hiện tại mặc định React 19 / Vite 8 / TypeScript 6. Khác với vụ Spring
Boot, ở đây **không có xung đột cụ thể nào**: `html5-qrcode`, `qrcode`, `zustand`,
`react-router`, `@tanstack/react-query` đều chạy với React 19. Quay về 18 là chống lại
toolchain mà không được gì.

---

## 4. `erasableSyntaxOnly: false` trong `tsconfig.app.json`

`openapi-typescript-codegen` sinh `namespace` + `enum` cho các trường enum — cú pháp
không xoá được, vi phạm cờ này. Code trong `src/` do mình viết không dùng
namespace/enum. Tắt cờ chỉ để chấp nhận code sinh tự động.

---

## 5. Endpoint phân trang không dùng client sinh tự động

springdoc mô tả `Pageable` của Spring thành MỘT object query param (`pageable`), nên
client sinh ra gửi `?pageable=[object Object]`. Spring lại mong đợi ba tham số phẳng
`page`, `size`, `sort`.

Giải pháp: helper `src/lib/paged.ts` dùng `fetch` cho các endpoint phân trang. Client
sinh tự động vẫn dùng cho mọi endpoint khác, nên quyết định số 3 giữ nguyên giá trị.

---

## 6. Tên method controller phải duy nhất toàn API

`openapi-typescript-codegen` lấy `operationId` làm tên hàm client. Hai method cùng tên
`register` ở hai controller khác nhau sinh ra `register` và `register1` — và **số thứ
tự đó đổi theo thứ tự endpoint**, làm vỡ ngầm khi sinh lại client.

Đã đổi thành `registerDevice` và `registerForEvent`, kèm `@Operation(operationId=...)`.
Quy tắc: mọi method controller đặt tên duy nhất trên toàn bộ API.
