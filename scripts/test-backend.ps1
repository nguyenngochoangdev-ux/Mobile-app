# Chạy test backend với cấu hình đọc từ .env.
#
#   .\scripts\test-backend.ps1              # toàn bộ
#   .\scripts\test-backend.ps1 MerkleVectorTest
#
# VÌ SAO PHẢI CÓ SCRIPT NÀY: xem đầu file scripts/run-backend.ps1. Tóm tắt — Spring Boot
# không đọc `.env`, nên `mvnw test` rơi về `MYSQL_PORT=3306` và nối vào MySQL80 cài sẵn
# trên Windows thay vì container (container ở 3310).
#
# `DrlBackendApplicationTests` nạp cả context nên CẦN cơ sở dữ liệu thật; chạy sai cổng thì
# nó đỏ với "Access denied for user 'drl'@'localhost'" — trông hệt như sai mật khẩu.
# Các test thuần (canonicalization, Merkle, allocator, QR token) không cần CSDL và luôn xanh
# dù chạy kiểu nào.

param(
    [string]$Test = ""
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
    if ($cfg.ContainsKey($key)) { Set-Item -Path "env:$key" -Value $cfg[$key] }
}

Write-Host "MySQL: localhost:$($cfg['MYSQL_PORT'])/$($cfg['MYSQL_DATABASE'])" -ForegroundColor Cyan

Push-Location (Join-Path $root "backend")
try {
    if ([string]::IsNullOrWhiteSpace($Test)) {
        & .\mvnw.cmd -B -ntp test
    } else {
        & .\mvnw.cmd -B -ntp test "-Dtest=$Test"
    }
} finally {
    Pop-Location
}
