# Sinh TypeScript client cho PWA từ OpenAPI spec (quyết định số 3, tiết kiệm ~3 ngày).
#
# Backend phải đang chạy. Spec được lưu ra app/openapi.json rồi mới sinh client —
# generator không đọc được URL trực tiếp, và có file spec trong repo giúp sinh lại
# client mà không cần dựng server.
#
#   .\scripts\gen-api-client.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$specPath = Join-Path $root "app\openapi.json"

Write-Host "Tai spec tu http://localhost:8080/v3/api-docs ..." -ForegroundColor Yellow
try {
    $res = Invoke-WebRequest "http://localhost:8080/v3/api-docs" -UseBasicParsing -TimeoutSec 15
} catch {
    throw "Khong tai duoc spec. Backend da chay chua? (cd backend; .\mvnw.cmd spring-boot:run)"
}

# Ghi UTF-8 khong BOM: generator khong doc duoc file co BOM.
$json = [System.Text.Encoding]::UTF8.GetString($res.RawContentStream.ToArray())
[System.IO.File]::WriteAllText($specPath, $json, [System.Text.UTF8Encoding]::new($false))
Write-Host "Da luu $specPath ($($json.Length) ky tu)" -ForegroundColor Green

Push-Location $root
try {
    & npx --yes openapi-typescript-codegen `
        --input app/openapi.json `
        --output app/src/api/generated `
        --client fetch
    if ($LASTEXITCODE -ne 0) { throw "Sinh client that bai" }
} finally {
    Pop-Location
}

Write-Host "Xong. Client nam o app/src/api/generated (gitignore - sinh lai khi can)." -ForegroundColor Green
