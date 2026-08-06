# Cấp credential cho một sinh viên từ dữ liệu điểm danh THẬT, rồi xuất bundle.
#
#   .\scripts\credential-now.ps1 B21DCCN002                  # cấp
#   .\scripts\credential-now.ps1 B21DCCN002 -Bundle          # xuất bundle (phải neo xong)
#   .\scripts\credential-now.ps1 B21DCCN002 -Semester 2026-1
#
# Chạy lại được: đã có credential cho cặp (sinh viên, học kỳ) thì dùng lại, không cấp bản
# thứ hai. Cấp trùng KHÔNG bị cây Merkle bắt (mỗi credential có nonce riêng nên hai bản nội
# dung giống hệt vẫn ra hai lá khác nhau), nên chốt chặn nằm ở tầng ứng dụng.
#
# Cấp credential là thao tác KÝ bằng khóa của tổ chức. Bản thân nó chưa chạm chuỗi — neo là
# việc của `.\scripts\anchor-now.ps1`, và ĐÓ mới là thao tác không hoàn tác được.
#
# VÌ SAO PHẢI CÓ SCRIPT NÀY: Spring Boot không đọc `.env`, nên `mvnw` chạy trực tiếp sẽ nối
# vào MySQL của Windows ở cổng 3306 thay vì container ở 3310.

param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Mssv,

    [string]$Semester = "2026-1",

    [switch]$Bundle,

    [string]$OutFile = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

$envFile = Join-Path $root ".env"
if (-not (Test-Path $envFile)) { throw "Khong tim thay .env. Copy tu .env.example truoc." }

$cfg = @{}
Get-Content $envFile | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
    $k, $v = $_ -split '=', 2
    $cfg[$k.Trim()] = $v.Trim()
}

foreach ($key in @('MYSQL_PORT', 'MYSQL_DATABASE', 'MYSQL_USER', 'MYSQL_PASSWORD', 'JWT_SECRET')) {
    if (-not $cfg.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($cfg[$key])) {
        throw "Thieu $key trong .env"
    }
    Set-Item -Path "env:$key" -Value $cfg[$key]
}

if (-not $cfg.ContainsKey('ISSUER_PRIVATE_KEY') -or [string]::IsNullOrWhiteSpace($cfg['ISSUER_PRIVATE_KEY'])) {
    throw "Thieu ISSUER_PRIVATE_KEY trong .env — khong ky duoc credential."
}
$env:ISSUER_PRIVATE_KEY = $cfg['ISSUER_PRIVATE_KEY']

# Bundle mang dia chi contract, nen can ca ba dia chi + chainId ke ca khi khong cham chuoi.
foreach ($key in @('CHAIN_ID', 'ANCHOR_REGISTRY_ADDRESS', 'ISSUER_REGISTRY_ADDRESS', 'STATUS_LIST_ADDRESS')) {
    if ($cfg.ContainsKey($key)) { Set-Item -Path "env:$key" -Value $cfg[$key] }
}

if ([string]::IsNullOrWhiteSpace($OutFile)) {
    $OutFile = Join-Path $root "bundles\$Mssv-$Semester.json"
}

# Truyền qua BIẾN MÔI TRƯỜNG, không qua `-Dspring-boot.run.arguments`.
#
# Plugin spring-boot tách chuỗi arguments theo KHOẢNG TRẮNG, và đường dẫn repo này có dấu
# cách ("Mobile APP"). Lần đầu viết bằng arguments, `drl.run.bundle-out` bị cắt thành
# "C:/Users/l/OneDrive/Desktop/Mobile" và ghi ra một tệp rác ngoài Desktop.
#
# Spring dùng relaxed binding nên DRL_RUN_BUNDLE_OUT ánh xạ thẳng sang `drl.run.bundle-out`,
# và biến môi trường không bị tách bởi bất cứ gì.
$env:DRL_RUN_MSSV = $Mssv
$env:DRL_RUN_SEMESTER = $Semester
$env:DRL_RUN_BUNDLE_OUT = if ($Bundle) { $OutFile } else { "" }

Write-Host "MySQL     : localhost:$($cfg['MYSQL_PORT'])/$($cfg['MYSQL_DATABASE'])" -ForegroundColor Cyan
Write-Host "Sinh vien : $Mssv · hoc ky $Semester" -ForegroundColor Cyan
if ($Bundle) { Write-Host "Bundle    : $OutFile" -ForegroundColor Cyan }

Push-Location (Join-Path $root "backend")
try {
    & .\mvnw.cmd -B -ntp spring-boot:run "-Dspring-boot.run.profiles=credential-now"
} finally {
    Pop-Location
}
