# Chạy job neo NGAY LẬP TỨC, không đợi lịch 02:00.
#
#   .\scripts\anchor-now.ps1
#
# ⚠️ THAO TÁC KHÔNG THỂ HOÀN TÁC. `AnchorRegistry` cố ý không cho ghi đè, nên mỗi
# (domain, batchId) chỉ dùng được đúng một lần — vĩnh viễn, kể cả admin cũng không sửa được.
# Đó là chỗ luận điểm "chống sửa hồi tố" sống hay chết.
#
# Trước khi chạy, kiểm ba thứ:
#   1. `.env` có ANCHOR_REGISTRY_ADDRESS trỏ đúng contract trên Amoy
#   2. Ví ANCHOR_PRIVATE_KEY có ANCHOR_ROLE và còn POL
#   3. Dữ liệu định neo là dữ liệu THẬT, không phải dữ liệu thử
#
# Muốn thử trước thì trỏ vào chuỗi cục bộ:
#   cd contracts; npx hardhat node; npm run deploy:local

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

$envFile = Join-Path $root ".env"
if (-not (Test-Path $envFile)) { throw "Khong tim thay .env." }

$cfg = @{}
Get-Content $envFile | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
    $k, $v = $_ -split '=', 2
    $cfg[$k.Trim()] = $v.Trim()
}

foreach ($key in @('MYSQL_PORT', 'MYSQL_DATABASE', 'MYSQL_USER', 'MYSQL_PASSWORD', 'JWT_SECRET',
                   'AMOY_RPC_URL', 'CHAIN_ID', 'ANCHOR_PRIVATE_KEY', 'ANCHOR_REGISTRY_ADDRESS')) {
    if (-not $cfg.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($cfg[$key])) {
        throw "Thieu $key trong .env"
    }
    Set-Item -Path "env:$key" -Value $cfg[$key]
}
$env:ANCHOR_ENABLED = "true"

Write-Host "Mang    : chainId $($cfg['CHAIN_ID'])" -ForegroundColor Cyan
Write-Host "Contract: $($cfg['ANCHOR_REGISTRY_ADDRESS'])" -ForegroundColor Cyan
Write-Host "KHONG THE HOAN TAC. Ctrl+C de dung." -ForegroundColor Yellow
Start-Sleep -Seconds 3

Push-Location (Join-Path $root "backend")
try {
    & .\mvnw.cmd -B -ntp spring-boot:run "-Dspring-boot.run.profiles=anchor-now"
} finally {
    Pop-Location
}
