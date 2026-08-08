# Chạy backend với cấu hình đọc từ .env.
#
#   .\scripts\run-backend.ps1
#
# VÌ SAO PHẢI CÓ SCRIPT NÀY thay vì gọi thẳng `mvnw spring-boot:run`:
#
# Spring Boot KHÔNG đọc file `.env` ở gốc repo. Nó chỉ đọc biến môi trường thật. Nên
# `application.yml` rơi về giá trị mặc định `${MYSQL_PORT:3306}` — trong khi `.env` của dự
# án đặt `MYSQL_PORT=3310` để né MySQL80 cài sẵn trên Windows đang chiếm cổng 3306.
#
# Hậu quả nếu chạy thẳng: ứng dụng nối vào MySQL80 của Windows, KHÔNG phải container.
# Nếu MySQL80 tình cờ cũng có user `drl` thì Flyway sẽ chạy migration lên NHẦM CƠ SỞ DỮ
# LIỆU và không có gì báo động. Đã dính một lần ngày 2026-08-05.
#
# Cùng cách nạp biến với scripts/reset-db.ps1 — khác ở chỗ script này KHÔNG xóa dữ liệu.
#
# ---------------------------------------------------------------------------------------
# -BatNeo — bật khả năng GỬI GIAO DỊCH lên Amoy
#
#   .\scripts\run-backend.ps1 -BatNeo
#
# Mặc định `drl.anchor.enabled=false`, nên backend chỉ ĐỌC chuỗi chứ không ghi. Đó là mặc
# định đúng: chạy thường ngày không có lý do gì để tiêu POL hay để một thao tác lỡ tay ghi
# vĩnh viễn lên chuỗi công khai.
#
# Nhưng endpoint THU HỒI credential (`POST /api/credentials/{id}/revoke`) thì bắt buộc phải
# ghi, vì nguồn sự thật về thu hồi là bit trên StatusList — thứ duy nhất verifier đọc. Không
# có cờ này thì endpoint đó trả lỗi.
#
# ⚠️ Bật cờ này cũng bật luôn job neo theo lịch 02:00. Đừng để backend chạy qua đêm với cờ
# này nếu chưa muốn neo — mỗi (miền, batchId) chỉ dùng được MỘT LẦN, vĩnh viễn.
# ---------------------------------------------------------------------------------------

[CmdletBinding()]
param(
    [switch]$BatNeo
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

$envFile = Join-Path $root ".env"
if (-not (Test-Path $envFile)) {
    throw "Khong tim thay .env. Copy tu .env.example truoc."
}

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

# Địa chỉ contract — backend cần để gọi web3j sau khi deploy.
# ISSUER_PRIVATE_KEY phải có mặt kể cả khi KHÔNG bật ghi chuỗi. Toàn bộ lớp CredentialConfig
# nằm sau @ConditionalOnExpression trên khóa này, nên thiếu nó thì không có IssuerSigner
# (không cấp được credential) và cũng không có StatusListClient (không thu hồi được).
# Ký credential là phép tính cục bộ, không chạm RPC, nên bật sẵn không tốn gì.
foreach ($key in @('AMOY_RPC_URL', 'CHAIN_ID', 'ANCHOR_REGISTRY_ADDRESS',
                   'ISSUER_REGISTRY_ADDRESS', 'STATUS_LIST_ADDRESS',
                   'ISSUER_PRIVATE_KEY')) {
    if ($cfg.ContainsKey($key)) { Set-Item -Path "env:$key" -Value $cfg[$key] }
}

# Mặc định TẮT ghi chuỗi. Chỉ bật khi gọi kèm -BatNeo, và phải đặt lại `false` tường minh
# mỗi lần: biến môi trường sống sót qua các lần chạy trong cùng cửa sổ PowerShell, nên bỏ
# dòng else đi thì một lần chạy có -BatNeo sẽ âm thầm bật cho mọi lần sau đó.
if ($BatNeo) {
    if (-not $cfg.ContainsKey('ANCHOR_PRIVATE_KEY') -or
        [string]::IsNullOrWhiteSpace($cfg['ANCHOR_PRIVATE_KEY'])) {
        throw "-BatNeo can ANCHOR_PRIVATE_KEY trong .env"
    }
    $env:ANCHOR_PRIVATE_KEY = $cfg['ANCHOR_PRIVATE_KEY']
    $env:ANCHOR_ENABLED = "true"
} else {
    $env:ANCHOR_ENABLED = "false"
}

Write-Host "MySQL  : localhost:$($cfg['MYSQL_PORT'])/$($cfg['MYSQL_DATABASE'])" -ForegroundColor Cyan
Write-Host "Swagger: http://localhost:8080/swagger-ui.html" -ForegroundColor Cyan
if ($BatNeo) {
    Write-Host "Ghi chuoi: BAT — endpoint thu hoi dung duoc, job neo 02:00 cung se chay" -ForegroundColor Yellow
} else {
    Write-Host "Ghi chuoi: tat (chi doc). Can thu hoi credential thi chay kem -BatNeo" -ForegroundColor DarkGray
}

Push-Location (Join-Path $root "backend")
try {
    & .\mvnw.cmd -B -ntp spring-boot:run
} finally {
    Pop-Location
}
