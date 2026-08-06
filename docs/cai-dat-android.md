# Cài app lên Android bằng PWA

**Không cần APK, không cần keystore, không cần CH Play.** Chrome trên Android cài PWA thành
một ứng dụng thật (WebAPK): có biểu tượng trên màn hình chính, mở toàn màn hình, **không có
thanh URL**.

> **Vì sao không đóng gói APK.** Đã xét bằng `/scope-guard` hai lần (2026-08-05 và
> 2026-08-07), kết luận không đổi: TWA **chính là Chrome đang render trang đó**, nên nó không
> cho thêm gì so với PWA đã cài — kể cả hành vi camera. Đổi lại nó đòi Android SDK, keystore,
> và một `assetlinks.json` khớp; sai một chỗ là Chrome tụt về Custom Tab **hiện thanh URL**,
> mất đúng thứ duy nhất APK mang lại. Xem `PROJECT.md` §2.4.

---

## Điều kiện bắt buộc: HTTPS

Chrome **chỉ cho cài** PWA khi trang chạy trên HTTPS (ngoại lệ duy nhất là `localhost`).
Đây là rào cản thật, không phải thủ tục.

Kèm theo là một cái bẫy ít người lường trước:

> ⚠️ **Nếu app chạy HTTPS mà API chạy `http://192.168.x.x:8080`, trình duyệt CHẶN THẲNG mọi
> lời gọi API** — lỗi *mixed content*. App cài được, mở lên đẹp, và **đăng nhập không nổi**.

Dự án xử lý bằng cách cho **backend phục vụ luôn giao diện**: một origin duy nhất cho cả app
lẫn API, nên không có mixed content và cũng không cần CORS.
Xem `backend/.../common/config/WebAppConfig.java`.

---

## Bước 1 — Đưa giao diện vào backend

```powershell
.\scripts\build-pwa.ps1
```

Script build PWA rồi chép sang `backend/src/main/resources/static`. Chạy backend là có luôn
giao diện:

```powershell
.\scripts\run-backend.ps1
# mở http://localhost:8080
```

Kiểm nhanh trên máy tính trước khi đụng tới điện thoại:

| Đường | Phải trả về |
|---|---|
| `/` | trang HTML |
| `/sv/diem` | **cùng** HTML đó (fallback cho React Router) |
| `/manifest.webmanifest` | JSON manifest |
| `/sw.js` | service worker |
| `/api/scoring/me` | `401` khi chưa đăng nhập |

> Lúc **phát triển** vẫn dùng `npx vite` với proxy như cũ. Bước này chỉ cần khi muốn chạy
> thật trên điện thoại.

---

## Bước 2 — Đưa lên HTTPS

Chọn **một** trong ba cách. Cách A là nhanh nhất cho buổi demo.

### A. Đường hầm tạm — cho demo, 2 phút

Máy tính vẫn chạy backend; đường hầm cho nó một địa chỉ HTTPS công khai.

```powershell
winget install --id Cloudflare.cloudflared     # chỉ lần đầu
cloudflared tunnel --url http://localhost:8080
```

In ra một địa chỉ dạng `https://<ngẫu-nhiên>.trycloudflare.com`. Mở địa chỉ đó **bằng Chrome
trên điện thoại**.

| | |
|---|---|
| **Được** | HTTPS thật, cài được ngay, không cần deploy gì |
| **Mất** | Địa chỉ đổi mỗi lần chạy lại → app đã cài **trỏ vào địa chỉ chết**. Máy tính phải bật |

> **Cho buổi bảo vệ:** mở đường hầm **trước** buổi, cài app lên điện thoại demo, và **đừng
> tắt terminal**. Tắt là app trắng trang.

### B. Cáp USB — không cần mạng, không cần HTTPS

Cách chắc chắn nhất cho phòng bảo vệ sóng yếu. Chrome trên máy tính chuyển tiếp một cổng sang
điện thoại, và điện thoại thấy nó ở **`http://localhost:8080`**.

Mấu chốt: **`localhost` là secure context.** Trình duyệt đối xử với nó y như HTTPS — nên
**camera chạy** và **nút cài đặt xuất hiện**, dù không có chứng chỉ nào.

1. Trên điện thoại: **Cài đặt → Giới thiệu → bấm 7 lần vào "Số bản dựng"** để bật Tuỳ chọn
   nhà phát triển, rồi bật **Gỡ lỗi USB**.
2. Cắm cáp USB vào máy tính, chọn **Cho phép** khi điện thoại hỏi.
3. Trên máy tính, mở Chrome → `chrome://inspect/#devices` → tích **Discover USB devices**.
4. Bấm **Port forwarding…** → thêm dòng:

   | Port | IP address and port |
   |---|---|
   | `8080` | `localhost:8080` |

   → tích **Enable port forwarding** → **Done**.
5. Trên điện thoại, mở Chrome vào **`http://localhost:8080`**.

| | |
|---|---|
| **Được** | Không cần mạng, không cần internet, không cần cài gì thêm. Camera chạy |
| **Mất** | Phải cắm cáp. Rút cáp là app không mở được nữa |

> ⚠️ Chrome có thể tạo **lối tắt** thay vì WebAPK đầy đủ khi nguồn là `localhost` — vẫn có
> biểu tượng và vẫn mở toàn màn hình, nhưng đây không phải bản cài "thật". Dùng cách này để
> **kiểm thử và quay video**; muốn bản cài thật thì dùng cách A hoặc C.

### B2. Cùng mạng LAN, không HTTPS — chỉ để xem thử

Điện thoại cùng Wi-Fi mở `http://<IP-máy-tính>:8080` là thấy giao diện ngay. Nhưng vì là
`http://`:

- ❌ **không cài được** (không có mục "Cài đặt ứng dụng")
- ❌ **camera không bật được** → không quét QR được

Chỉ dùng để trả lời câu "điện thoại có chạm được vào máy chủ không". Nếu bước này hỏng thì
kiểm tường lửa Windows cho cổng 8080 và xem hai máy có cùng mạng không.

Dùng chứng chỉ **tự ký** cho LAN cũng không cứu được: Chrome coi nó là không hợp lệ và **vẫn
từ chối cài PWA**, kể cả khi bấm bỏ qua cảnh báo. Ghi ra để khỏi mất thời gian thử.

### C. Deploy thật — bền, cần thêm việc

Backend + MySQL lên một máy chủ có tên miền và chứng chỉ (Railway, Render, Fly.io, hoặc VPS +
Caddy tự lấy Let's Encrypt). Đây là cách duy nhất để app sống độc lập với máy tính cá nhân.

Nằm ngoài phạm vi 8 tuần; ghi vào hướng phát triển.

---

## Bước 3 — Cài trên điện thoại

1. Mở địa chỉ **HTTPS** bằng **Chrome** trên Android (Firefox/Samsung Internet có cài được
   nhưng không sinh WebAPK thật).
2. Đăng nhập một lần để chắc backend thông.
3. Menu **⋮** → **Cài đặt ứng dụng** *(hoặc "Thêm vào Màn hình chính")*.
   - Nếu **không thấy mục đó**, xem phần gỡ rối bên dưới.
4. Biểu tượng xuất hiện trên màn hình chính. Mở từ đó — **không còn thanh URL**.

### Kiểm tra đã cài đúng chưa

- Mở app từ màn hình chính → **không thấy thanh địa chỉ** ⇒ WebAPK thật.
- Vẫn thấy thanh địa chỉ mờ ở trên ⇒ chỉ là lối tắt (shortcut), không phải WebAPK. Thường do
  thiếu HTTPS hoặc manifest không đạt.

---

## Gỡ rối

| Triệu chứng | Nguyên nhân gần như chắc chắn |
|---|---|
| Chỉ có "Thêm lối tắt", **không có** "Cài đặt ứng dụng" | Trang chạy `http://`. Hoặc manifest sai kiểu MIME — xem mục dưới |
| Đã sửa xong mà điện thoại **vẫn** chỉ mời tạo lối tắt | Chrome còn giữ bản cũ trong bộ nhớ đệm. Phải xóa dữ liệu trang, xem mục dưới |
| Cài được nhưng **đăng nhập lỗi mạng** | App và API khác origin → mixed content. Phải chạy qua bước 1 |
| Mở app ra **trắng trang** | Đường hầm đã tắt, hoặc backend không chạy |
| Tải lại ở `/sv/diem` bị 404 | Thiếu fallback SPA — kiểm `WebAppConfig` còn không |
| Camera không bật được | Chrome chỉ cho camera trên **HTTPS**. Cũng phải cấp quyền cho trang |
| Sửa giao diện xong app không đổi | Service worker giữ bản cũ. Chạy lại `build-pwa.ps1`, rồi đóng hẳn app và mở lại |

### Bẫy đã sập thật: manifest sai kiểu MIME

Triệu chứng: menu Chrome trên Android **chỉ có "Thêm lối tắt"**, không có "Cài đặt ứng dụng".
Lối tắt vẫn mở được app nhưng **còn thanh địa chỉ** — nó không phải WebAPK.

Nguyên nhân: bảng MIME mặc định của Tomcat không có đuôi `.webmanifest`, nên manifest bị trả
về `application/octet-stream`. Spring Security lại gắn `X-Content-Type-Options: nosniff` vào
mọi phản hồi, nên Chrome không được phép đoán lại kiểu và **bỏ luôn manifest**.

Rất khó lần ra: trang tải bình thường, DevTools không báo lỗi đỏ nào, chỉ mất đúng nút cài.

Đã sửa trong `WebAppConfig.mimeChoManifest()`. Kiểm nhanh:

```bash
curl -I https://<địa-chỉ>/manifest.webmanifest | grep -i content-type
# phải ra: application/manifest+json
```

> ⚠️ **Bên trong bản sửa còn một bẫy thứ hai.** `setMimeMappings()` **thay thế** toàn bộ bảng
> chứ không cộng thêm, mà `new MimeMappings(MimeMappings.DEFAULT)` lại cho ra **bảng rỗng** vì
> `DEFAULT` nạp lười. Đo được: DEFAULT có 1021 mục, bản sao có 0. Phải **duyệt bằng vòng lặp**
> mới kích hoạt việc nạp. Mất bảng mặc định còn hỏng nặng hơn: `.js` rơi về `octet-stream` và
> trình duyệt từ chối chạy service worker. `WebAppConfigMimeTest` canh cả hai chiều.

### Cách kiểm "trang có đủ điều kiện cài không" mà không cần điện thoại

Mở địa chỉ bằng Chrome trên máy tính, rồi dán vào Console:

```js
addEventListener('beforeinstallprompt', () => console.log('DU DIEU KIEN CAI'));
location.reload();
```

Chrome **chỉ phát** sự kiện này khi trang đạt đủ mọi tiêu chí cài đặt. Không thấy dòng log
nghĩa là còn thiếu tiêu chí nào đó, và Android sẽ chỉ mời tạo lối tắt.

### Xóa bản cũ trên điện thoại trước khi thử lại

Chrome nhớ manifest và service worker cũ. Sửa xong ở máy chủ mà không xóa thì điện thoại vẫn
dùng bản hỏng:

1. Gỡ lối tắt đã tạo: giữ biểu tượng trên màn hình chính → **Gỡ cài đặt**.
2. Chrome → **⋮** → **Cài đặt** → **Cài đặt trang web** → **Toàn bộ trang web** → chọn địa chỉ
   → **Xóa và đặt lại**.
3. Mở lại địa chỉ, chờ trang tải xong hẳn, rồi mới mở menu **⋮**.

---

## Còn thiếu một thứ, thuần thẩm mỹ

Manifest **chưa có icon `maskable`**. Android sẽ bo biểu tượng trong một hình tròn trắng có
lề, trông kém hơn app thật một chút.

Sửa được trong ~15 phút, nhưng phải **vẽ lại icon có lề**, không phải chỉ gắn cờ: icon hiện
tại có khung xanh sát mép, gắn `purpose: "maskable"` vào thẳng sẽ **bị cắt bốn góc**. Yêu cầu:
mọi nội dung nằm gọn trong đường tròn đường kính **80%** của ảnh.

Sau khi có tệp mới, thêm vào `app/vite.config.ts`:

```js
{ src: '/icon-512-maskable.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' }
```

---

## Nói đúng mức trong báo cáo

> Hệ thống truy cập qua trình duyệt và **cài được lên màn hình chính Android dưới dạng PWA
> (WebAPK)**, chạy toàn màn hình không thanh địa chỉ. Đóng gói thành APK phát hành trên CH
> Play qua Trusted Web Activity là hướng phát triển; ở phiên bản hiện tại điều đó **không
> thêm khả năng nào** vì TWA vẫn là Chrome render cùng trang đó.

**Đừng viết** "ứng dụng Android" mà không nói rõ là PWA — hội đồng sẽ hỏi tệp APK đâu, và câu
trả lời phải là một quyết định có lý do, không phải một chỗ còn thiếu.
