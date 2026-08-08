# Cài app lên Android

Có **hai đường**, dùng chung một backend và một bản PWA. Cả hai đều bắt buộc HTTPS.

| Đường | Khi nào dùng | Lệnh |
|---|---|---|
| **APK** (Trusted Web Activity) | Mặc định từ 2026-08-08. Có tệp `.apk` cầm tay, cài không cần Chrome | `.\scripts\build-apk.ps1` |
| **PWA** (WebAPK qua Chrome) | Dự phòng. Không cần build gì, nhưng phải thao tác trên menu Chrome | `.\scripts\build-pwa.ps1` |

> **Phạm vi đã đổi ngày 2026-08-08.** Trước đó `/scope-guard` từ chối APK hai lần
> (2026-08-05, 2026-08-07). Người dùng quyết định làm, và quyền quyết định phạm vi thuộc về
> người dùng. Ghi trong `docs/scope.md` và `PROJECT.md` §2.4.
>
> Nói đúng bản chất khi báo cáo: **về bản chất vẫn là một trang web được render trong một
> WebView**, dù từ 2026-08-08 không còn chắc chắn Chrome là bên render (xem mục "WebView
> riêng" bên dưới — lý do phải tự viết). APK không thêm năng lực nào so với PWA, nó chỉ đổi
> cách phân phối và cách cài. Đừng viết "ứng dụng Android native" — hội đồng hỏi một câu về
> vòng đời Activity là lộ.

---

## Đọc trước nếu app đang báo lỗi

Nếu app đã cài mà mở ra báo **`ERR_NAME_NOT_RESOLVED`** hoặc trắng trang, gần như chắc chắn là
domain đường hầm đã chết. Không phải app hỏng.

```powershell
# 1. Mở đường hầm mới (để nguyên cửa sổ này, đừng tắt)
cloudflared tunnel --url http://localhost:8080

# 2. Cửa sổ khác: build lại APK trỏ domain mới
.\scripts\build-apk.ps1

# 3. Chép APK sang điện thoại và cài đè
```

> ⚠️ **Bẫy cốt lõi: tiến trình `cloudflared` còn chạy KHÔNG có nghĩa là domain còn sống.**
>
> Ngày 2026-08-08 đã sập đúng bẫy này. Tiến trình vẫn chạy, vẫn khai hostname cũ qua endpoint
> metrics, nhưng Cloudflare đã thu hồi đường hầm — tra DNS thật ra `Non-existent domain`.
> Nhìn Task Manager thấy `cloudflared.exe` nên tưởng mọi thứ bình thường.
>
> Phép kiểm đúng duy nhất là **phân giải DNS từ ngoài rồi gọi thật vào domain**.
> `build-apk.ps1` làm đúng việc đó trước khi build, nên nó không bao giờ sinh ra một APK
> trỏ vào domain chết.

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

- ❌ **không cài được** (bảng chọn chỉ mời "Tạo lối tắt", không có "Cài đặt")
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

## Đường APK — build và cài

### Chạy một lệnh

```powershell
.\scripts\build-apk.ps1
```

Script hỏi mật khẩu keystore rồi tự làm hết. Muốn bỏ bước build lại giao diện cho nhanh thì
thêm `-BoQuaPwa`. Muốn chỉ định thẳng domain thì `-Domain abc.trycloudflare.com`.

Năm bước nó chạy:

| Bước | Việc | Vì sao cần |
|---|---|---|
| 1 | Build PWA, chép vào backend | `assetlinks.json` đi theo đường này vào `static/` |
| 2 | Tìm domain đường hầm **còn sống** | Hỏi `cloudflared` lấy ứng viên, rồi **tự xác nhận bằng DNS + gọi thật** |
| 3 | Kiểm `manifest.webmanifest` | Sai kiểu MIME là mất nút cài của PWA |
| 4 | Vá domain vào `twa-manifest.json` | Domain nằm ở **bốn** chỗ: `host`, `webManifestUrl`, `fullScopeUrl`, `iconUrl` |
| 5 | `bubblewrap update` rồi `build` | `update` phải chạy trước, xem bẫy bên dưới |

Sau khi build, script **so vân tay APK vừa ký với `assetlinks.json` mà domain đang phục vụ**.
Lệch một ký tự là nó dừng và báo đỏ. Đây là phép kiểm quan trọng nhất, vì lệch vân tay không
làm app hỏng — app vẫn chạy, chỉ **hiện thanh địa chỉ**, nên rất dễ tưởng là bình thường.

### Cài lên điện thoại

1. Chép `android-twa/app-release-signed.apk` sang điện thoại. USB, Telegram, Drive đều được.
2. Mở tệp đó trên điện thoại. Android hỏi quyền **"Cài đặt ứng dụng không rõ nguồn gốc"** —
   cấp cho ứng dụng bạn đang dùng để mở tệp.
3. Cài đè lên bản cũ được, không cần gỡ. `bubblewrap update` tự tăng `appVersionCode` mỗi lần
   build, và chữ ký không đổi nên Android chấp nhận.

### Kiểm đã đúng chưa

Mở app từ màn hình chính:

- **Không thấy thanh địa chỉ** ⇒ TWA xác minh thành công, đúng thứ cần đạt.
- **Thấy thanh địa chỉ, có nút X và nút chia sẻ** ⇒ Chrome đã tụt về **Custom Tab**. Nghĩa là
  Digital Asset Links không khớp. Chạy lại `build-apk.ps1` và đọc kỹ phần so vân tay ở cuối.

### Sáu bẫy đã sập thật

Cả sáu đều đã gặp trong lúc dựng `build-apk.ps1`. Script hiện đã xử lý hết, nhưng ghi ra để
hiểu vì sao nó dài như vậy, và để không gỡ bỏ các bước trông thừa.

> **1. Tiến trình tunnel còn sống ≠ domain còn sống.** Xem mục đầu tài liệu. Đây là nguyên nhân
> của sự cố `ERR_NAME_NOT_RESOLVED` ban đầu.

> **2. `bubblewrap build` DỪNG LẠI HỎI khi `twa-manifest.json` đổi.** Nó so với
> `manifest-checksum.txt`, thấy khác thì hỏi có muốn cập nhật dự án không — và treo script chờ
> người gõ phím. Vì vậy `build-apk.ps1` luôn chạy **`bubblewrap update` trước**: lệnh đó sinh
> lại dự án Android và ghi lại checksum, nên `build` không còn gì để hỏi.

> **3. `Set-Content -Encoding utf8` của PowerShell 5.1 ghi kèm BOM, và `JSON.parse` của Node vỡ
> vì BOM.** Bẫy này khó thấy vì `Get-Content -Raw` **âm thầm cắt BOM lúc đọc**. Nên một vòng
> đọc–sửa–ghi tự nó thêm BOM vào file trước đó không có, rồi bubblewrap tắt với lỗi
> `Unexpected token '﻿' ... is not valid JSON`. Phải dùng
> `[System.IO.File]::WriteAllText($p, $t, (New-Object System.Text.UTF8Encoding($false)))`.
> Cùng họ với bẫy cột `JSON` của MySQL trong `docs/canonicalization.md`: công cụ trung gian
> tự ý sửa byte.

> **4. `bubblewrap update` HỎI TƯƠNG TÁC `versionName` và chết ở cửa sổ không có stdin**
> (`ERR_USE_AFTER_CLOSE: readline was closed`). Khi để nó tự tăng version mà `appVersionName`
> không phải dạng số chuẩn, nó dừng hỏi tên bản mới. `build-apk.ps1` tự tăng `appVersionCode`
> và đặt `appVersionName` **trong JSON ở bước 4**, rồi gọi `update --skipVersionUpgrade` để nó
> không hỏi. `appVersionCode` vẫn tăng đều — cần cho cài đè bản cũ.

> **5. `gradlew.bat` báo `'gradlew.bat' is not recognized` dù file nằm ngay đó.** Máy này đặt
> biến hệ thống `NoDefaultCurrentDirectoryInExePath=1`, một thiết lập bảo mật khiến `cmd.exe`
> **không chạy file từ thư mục hiện tại** nếu chỉ gõ tên trần. bubblewrap gọi `gradlew.bat`
> trần. `build-apk.ps1` vá bằng cách **thêm thư mục `android-twa` vào `PATH`** trước khi gọi,
> để tên trần tìm thấy qua PATH.

> **6. Gradle chết vì JVM 32-bit: `Could not reserve enough space for 1572864KB object heap`.**
> bubblewrap chỉ kèm **JDK 17 bản 32-bit**, không cấp nổi heap `-Xmx1536m`. Phải trỏ
> `org.gradle.java.home` sang JDK 64-bit (dùng chung với backend). Nhưng **`bubblewrap update`
> regenerate `gradle.properties` mỗi lần chạy, xoá sạch dòng vá đó** — nên không thể vá một
> lần rồi thôi. `build-apk.ps1` ghi lại dòng đó **ngay sau `update`, trước `build`**, mỗi lần.

### WebView riêng — vì sao không để thư viện tự chọn trình duyệt

Trên máy không cài (hoặc tắt) Chrome, mở app hiện dòng **"Đang chạy trên [tên trình duyệt
khác]"** kèm thanh công cụ — mất đúng thứ APK hứa hẹn.

**Nguyên nhân, xác nhận bằng cách đọc bytecode thật của `androidbrowserhelper:2.6.2`, không
đoán:** `TwaProviderPicker` quét **mọi trình duyệt đã cài**, ưu tiên trình duyệt nào khai báo
category `androidx.browser.trusted.category.TrustedWebActivities` trong manifest của chính
nó. Không tìm được cái nào → tụt về Custom Tab của trình duyệt bất kỳ, hiện banner tên trình
duyệt đó. Việc này quét *toàn bộ* app đã cài, không riêng trình duyệt mặc định — nên nếu Chrome
có cài, dù không đặt mặc định, vẫn được tìm thấy. Thấy banner nghĩa là **máy đó không có Chrome
khả dụng**.

**Đường tắt `fallbackType: webview` của chính thư viện không dùng được.** Nó tự vẽ bằng
`WebViewFallbackActivity`, nhưng `WebChromeClient` nội bộ của class đó **không override
`onPermissionRequest`** — hành vi mặc định của Android là từ chối thẳng `getUserMedia()`, nên
**camera quét QR chết hoàn toàn**. Hàm tạo `WebChromeClient` của nó còn là `private`, không kế
thừa để sửa được. Không có điểm mở rộng chính thức nào của thư viện cho việc này.

**Đã sửa bằng cách tự viết một Activity WebView riêng** (`CameraWebViewActivity.java`): tự
quản lý `WebView`, tự override `onPermissionRequest` để xin quyền `CAMERA` runtime rồi
`request.grant(...)`, tự giới hạn điều hướng trong đúng một origin (`shouldOverrideUrlLoading`
— thiếu bước này là lỗ hổng thật, WebView có thể bị dẫn sang trang giả mạo mà vẫn trông như
đang ở trong app). `LauncherActivity.java` override `launchTwa()` (điểm mở rộng `protected`
chính thức của thư viện, không phải hack) để chuyển thẳng sang Activity này, không bao giờ để
thư viện chạy tới bước chọn trình duyệt — banner không có cơ hội xuất hiện dù chỉ thoáng qua.

> ⚠️ **Đây là quyết định tốn chi phí thật, đã cân nhắc qua `/scope-guard` và người dùng xác
> nhận hai lần.** Nó không phục vụ luận điểm blockchain nào, không sinh số liệu cho chương 11
> — thuần là sửa trải nghiệm trên thiết bị thiếu Chrome. Phương án rẻ hơn nhiều (cài Chrome
> lên máy demo, 0 dòng code) vẫn là lựa chọn đúng cho **máy dùng để bảo vệ đề tài**. Chỉ giữ
> `CameraWebViewActivity` cho trường hợp không kiểm soát được máy người dùng cuối.

**Bẫy khi vá — `bubblewrap update` XOÁ SẠCH cả thư mục mã Java rồi sinh lại**, không chỉ ghi
đè từng file nó biết. Giả định ban đầu "file lạ không bị đụng tới" **sai** — đã bắt được thật
khi `CameraWebViewActivity.java` biến mất sau một lần `update`, làm build lỗi
`cannot find symbol`. `build-apk.ps1` giờ **ghi lại toàn bộ nội dung** cả hai file
(`LauncherActivity.java`, `CameraWebViewActivity.java`) và vá `AndroidManifest.xml` (thêm
quyền `CAMERA` + khai báo activity) **sau mỗi lần `update`, trước `build`** — cùng khuôn với
`gradle.properties`. Sửa trực tiếp các file `.java` trong `android-twa/` mà quên sửa lại
template trong `build-apk.ps1` là sửa vô ích, mất ngay lần build sau.

### Mật khẩu keystore

`android-twa/android.keystore` **đã gitignore** cùng cả thư mục `android-twa/`. Mất tệp này,
hoặc quên mật khẩu, là **không ký được bản cập nhật nữa** — Android từ chối cài đè khi chữ ký
đổi, phải gỡ rồi cài lại từ đầu. Sao lưu nó ra ngoài repo, cùng chỗ với backup master key
(xem `PROJECT.md` §9).

> **Keystore đã tạo lại ngày 2026-08-08.** Bản đầu (bubblewrap sinh 08-07) mất mật khẩu — nó
> chỉ tồn tại trong lúc `bubblewrap init` hỏi, không lưu đâu cả. Đã tạo keystore mới bằng
> `keytool`, alias `android`, mật khẩu **`Demo@123`** (dùng lại mật khẩu demo của app cho dễ
> nhớ; keystore này là artifact dev đã gitignore, không phát hành CH Play nên mức bảo mật
> thấp). Vân tay đổi nên đã cập nhật `app/public/.well-known/assetlinks.json`. Bản keystore
> cũ vô dụng còn giữ ở `android.keystore.bak-*` — xoá được bất cứ lúc nào.
>
> Mật khẩu này **không phải bí mật thật**: `Demo@123` vốn đã nằm công khai trong
> `DevSeeder.java`. Đừng dùng lại kiểu này nếu có ngày phát hành thật.

---

## Đường PWA — cài qua Chrome

Vẫn dùng được, và là phương án dự phòng tốt khi không kịp build APK.

## Bước 3 — Cài trên điện thoại

1. Mở địa chỉ **HTTPS** bằng **Chrome** trên Android (Firefox/Samsung Internet có cài được
   nhưng không sinh WebAPK thật).
2. Đăng nhập một lần để chắc backend thông.
3. Menu **⋮** → **Cài đặt và tạo lối tắt**.
4. Chrome mở một bảng trượt lên với hai lựa chọn. Chọn **Cài đặt**, đừng chọn "Tạo lối tắt".
   - Nếu bảng đó **chỉ có "Tạo lối tắt"**, trang chưa đủ điều kiện cài. Xem phần gỡ rối.
5. Biểu tượng xuất hiện trên màn hình chính. Mở từ đó — **không còn thanh URL**.

> **Tên mục đã đổi, đừng tìm chữ "Cài đặt ứng dụng".** Chrome bản mới gộp hai mục cũ là "Cài
> đặt ứng dụng" và "Thêm vào Màn hình chính" thành một mục duy nhất tên **"Cài đặt và tạo lối
> tắt"**. Chữ "Cài đặt ứng dụng" không còn nằm trong menu nữa. Phải bấm vào mục gộp rồi mới
> thấy lựa chọn cài thật. Đã mất một buổi vì chuyện này: nhìn menu không thấy tên cũ nên tưởng
> trang hỏng, trong khi mục cài vẫn nằm đó.

### Kiểm tra đã cài đúng chưa

- Mở app từ màn hình chính → **không thấy thanh địa chỉ** ⇒ WebAPK thật.
- Vẫn thấy thanh địa chỉ mờ ở trên ⇒ chỉ là lối tắt (shortcut), không phải WebAPK. Thường do
  thiếu HTTPS hoặc manifest không đạt.

---

## Gỡ rối

| Triệu chứng | Nguyên nhân gần như chắc chắn |
|---|---|
| Menu **không có** mục "Cài đặt ứng dụng" | Không phải lỗi. Tên mục đã đổi thành **"Cài đặt và tạo lối tắt"** — xem mục dưới |
| Bấm vào mục đó nhưng bảng chọn **chỉ có "Tạo lối tắt"** | Trang chạy `http://`. Hoặc manifest sai kiểu MIME — xem mục dưới |
| Đã sửa xong mà điện thoại **vẫn** chỉ mời tạo lối tắt | Chrome còn giữ bản cũ trong bộ nhớ đệm. Phải xóa dữ liệu trang, xem mục dưới |
| Cài được nhưng **đăng nhập lỗi mạng** | App và API khác origin → mixed content. Phải chạy qua bước 1 |
| Mở app ra **trắng trang** | Đường hầm đã tắt, hoặc backend không chạy |
| **APK:** mở ra báo `ERR_NAME_NOT_RESOLVED` | Domain đường hầm đã bị thu hồi. Mở hầm mới rồi chạy lại `build-apk.ps1` |
| **APK:** mở ra **có thanh địa chỉ** | Chrome tụt về Custom Tab vì `assetlinks.json` không khớp vân tay. `build-apk.ps1` bắt được lỗi này ở bước cuối |
| **APK:** cài báo "ứng dụng không được cài đặt" | Đang cài đè bản ký bằng keystore **khác**. Gỡ bản cũ rồi cài lại |
| `build-apk.ps1` báo `Unexpected token '﻿'` | `twa-manifest.json` bị dính BOM. Xem bẫy số 3 ở mục đường APK |
| Tải lại ở `/sv/diem` bị 404 | Thiếu fallback SPA — kiểm `WebAppConfig` còn không |
| Camera không bật được | Chrome chỉ cho camera trên **HTTPS**. Cũng phải cấp quyền cho trang |
| Sửa giao diện xong app không đổi | Service worker giữ bản cũ. Chạy lại `build-pwa.ps1`, rồi đóng hẳn app và mở lại |

### Trước hết: Chrome đã đổi tên mục cài đặt

Nếu bạn mở menu **⋮** và không thấy chữ "Cài đặt ứng dụng" thì **chưa có gì hỏng cả**. Chrome
bản mới bỏ hẳn chữ đó. Hai mục cũ giờ gộp làm một, tên là **"Cài đặt và tạo lối tắt"**, nằm gần
cuối menu ngay trên "Trang cho máy tính".

Bấm vào nó, Chrome mở một bảng trượt từ dưới lên:

| Lựa chọn | Kết quả |
|---|---|
| **Cài đặt** | WebAPK thật. Biểu tượng vào ngăn ứng dụng, mở ra **không có** thanh địa chỉ |
| **Tạo lối tắt** | Chỉ là phím tắt. Mở ra **vẫn còn** thanh địa chỉ |

Chrome chỉ hiện lựa chọn **Cài đặt** khi trang đủ điều kiện. Vậy nên phép thử đúng là *bấm vào
mục đó rồi xem bảng chọn có gì*, chứ không phải đọc lướt tên các mục trong menu. Chừng nào bảng
chọn còn thiếu "Cài đặt" thì mới đi tìm nguyên nhân ở các mục bên dưới.

### Bẫy đã sập thật: manifest sai kiểu MIME

Triệu chứng: bảng chọn **chỉ mời "Tạo lối tắt"**, không có "Cài đặt". Lối tắt vẫn mở được app
nhưng **còn thanh địa chỉ** — nó không phải WebAPK.

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

> Hệ thống được đóng gói thành **ứng dụng Android (APK)** qua Trusted Web Activity, cài trực
> tiếp lên thiết bị và chạy toàn màn hình không thanh địa chỉ. Về bản chất TWA vẫn dùng engine
> của Chrome để render giao diện web, nên **khả năng chức năng tương đương bản PWA**; đóng gói
> APK phục vụ mục đích phân phối và trải nghiệm cài đặt, **không mở rộng năng lực hệ thống**.
> Phát hành trên CH Play là hướng phát triển.

**Đừng viết "ứng dụng Android native".** Hội đồng hỏi một câu về vòng đời Activity, về JNI, hay
về việc gọi API hệ thống nào là lộ ngay. Nói đúng bản chất TWA thì không ai bắt bẻ được, và nó
còn cho thấy bạn hiểu công cụ mình dùng.

**Cũng đừng giấu chuyện app phụ thuộc máy tính đang chạy.** Ở phiên bản hiện tại, APK trỏ vào
một đường hầm HTTPS tạm; tắt máy là app không mở được. Đây là giới hạn triển khai, viết thẳng
vào phần hạn chế. Deploy lên máy chủ có tên miền cố định là hướng phát triển — xem cách C.
