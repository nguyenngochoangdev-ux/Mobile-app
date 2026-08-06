# Build PWA rồi chép vào backend để một origin phục vụ cả app lẫn API.
#
#   .\scripts\build-pwa.ps1
#
# VÌ SAO GỘP MỘT ORIGIN: điện thoại Android chỉ CÀI ĐƯỢC PWA khi trang chạy HTTPS. Nếu app
# nằm ở một origin HTTPS còn API ở http://192.168.x.x:8080 thì trình duyệt CHẶN THẲNG mọi
# lời gọi API vì mixed content — app cài được nhưng đăng nhập không nổi.
#
# Gộp lại xóa cả mixed content lẫn CORS. `app/src/lib/auth.ts` vốn đã đặt OpenAPI.BASE = ''
# nên phía client không phải đổi gì. Xem backend/.../common/config/WebAppConfig.java.
#
# Lúc PHÁT TRIỂN vẫn dùng `npx vite` với proxy như cũ — script này chỉ dùng khi muốn chạy
# thật trên điện thoại hoặc deploy.

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$dich = Join-Path $root "backend\src\main\resources\static"

Push-Location (Join-Path $root "app")
try {
    Write-Host "Build PWA..." -ForegroundColor Cyan

    # Hạ ErrorActionPreference quanh lời gọi native.
    #
    # PowerShell 5.1 bọc MỌI dòng stderr của một chương trình ngoài thành ErrorRecord, và với
    # `Stop` thì một dòng CẢNH BÁO cũng làm script dừng. Vite in cảnh báo "chunk lớn hơn
    # 500 kB" ra stderr trong khi build hoàn toàn thành công.
    #
    # Mã thoát mới là thứ nói build hỏng hay không — kiểm nó, đừng kiểm stderr.
    $cu = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & npx vite build 2>&1 | ForEach-Object { "$_" }
    } finally {
        $ErrorActionPreference = $cu
    }
    if ($LASTEXITCODE -ne 0) { throw "vite build that bai (ma thoat $LASTEXITCODE)" }
} finally {
    Pop-Location
}

$nguon = Join-Path $root "app\dist"
if (-not (Test-Path $nguon)) { throw "Khong thay $nguon" }

# Xóa nội dung chứ không xóa thư mục: trên Windows/OneDrive, xóa cả thư mục hay báo EPERM
# khi có tiến trình khác đang mở nó. Cùng lý do với verifier/scripts/build-web.mjs.
if (Test-Path $dich) {
    Get-ChildItem $dich -Force | ForEach-Object {
        Remove-Item $_.FullName -Recurse -Force -ErrorAction SilentlyContinue
    }
} else {
    New-Item -ItemType Directory -Force $dich | Out-Null
}

Copy-Item (Join-Path $nguon "*") $dich -Recurse -Force

$soTep = (Get-ChildItem $dich -Recurse -File | Measure-Object).Count
$kb = [math]::Round(((Get-ChildItem $dich -Recurse -File | Measure-Object Length -Sum).Sum / 1KB), 0)

Write-Host ""
Write-Host "Da chep PWA -> $dich" -ForegroundColor Green
Write-Host "  $soTep tep, $kb KB"
Write-Host ""
Write-Host "Chay backend, no se phuc vu luon giao dien:" -ForegroundColor Cyan
Write-Host "  .\scripts\run-backend.ps1"
Write-Host "  rồi mở http://localhost:8080"
Write-Host ""
Write-Host "De cai duoc tren Android, trang phai chay HTTPS." -ForegroundColor Yellow
Write-Host "Xem docs/cai-dat-android.md"
