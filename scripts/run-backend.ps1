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
foreach ($key in @('AMOY_RPC_URL', 'CHAIN_ID', 'ANCHOR_REGISTRY_ADDRESS',
                   'ISSUER_REGISTRY_ADDRESS', 'STATUS_LIST_ADDRESS')) {
    if ($cfg.ContainsKey($key)) { Set-Item -Path "env:$key" -Value $cfg[$key] }
}

Write-Host "MySQL  : localhost:$($cfg['MYSQL_PORT'])/$($cfg['MYSQL_DATABASE'])" -ForegroundColor Cyan
Write-Host "Swagger: http://localhost:8080/swagger-ui.html" -ForegroundColor Cyan

Push-Location (Join-Path $root "backend")
try {
    & .\mvnw.cmd -B -ntp spring-boot:run
} finally {
    Pop-Location
}
