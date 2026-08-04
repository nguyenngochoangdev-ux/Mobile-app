# Xóa sạch schema và seed lại dữ liệu demo.
#
# Buổi bảo vệ cần dữ liệu sạch, tái lập được. Chạy script này trước khi demo.
# Seeder dùng seed cố định nên hai lần chạy cho ra cùng dữ liệu — cần thiết khi
# so sánh số liệu benchmark ở chương 11.
#
#   .\scripts\reset-db.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

# Đọc cấu hình từ .env
$envFile = Join-Path $root ".env"
if (-not (Test-Path $envFile)) {
    throw "Không tìm thấy .env. Copy từ .env.example trước."
}
$cfg = @{}
Get-Content $envFile | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
    $k, $v = $_ -split '=', 2
    $cfg[$k.Trim()] = $v.Trim()
}

$db   = $cfg['MYSQL_DATABASE']
$user = $cfg['MYSQL_USER']
$root_pw = $cfg['MYSQL_ROOT_PASSWORD']
$port = $cfg['MYSQL_PORT']

Write-Host "Reset schema '$db'..." -ForegroundColor Yellow
docker exec drl-mysql mysql -uroot -p"$root_pw" -e @"
DROP DATABASE IF EXISTS $db;
CREATE DATABASE $db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL ON $db.* TO '$user'@'%';
FLUSH PRIVILEGES;
"@
if ($LASTEXITCODE -ne 0) { throw "Reset schema that bai" }

Write-Host "Chay Flyway + seed..." -ForegroundColor Yellow
$env:MYSQL_PORT     = $port
$env:MYSQL_DATABASE = $db
$env:MYSQL_USER     = $user
$env:MYSQL_PASSWORD = $cfg['MYSQL_PASSWORD']
$env:JWT_SECRET     = $cfg['JWT_SECRET']

Push-Location (Join-Path $root "backend")
try {
    & .\mvnw.cmd -B -ntp spring-boot:run "-Dspring-boot.run.profiles=seed"
} finally {
    Pop-Location
}
