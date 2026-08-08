# Build APK (TWA) tro vao domain duong ham DANG SONG.
#
#   .\scripts\build-apk.ps1                                # tu do domain tu cloudflared dang chay
#   .\scripts\build-apk.ps1 -Domain abc.trycloudflare.com   # chi dinh thang
#   .\scripts\build-apk.ps1 -BoQuaPwa                       # bo buoc build lai giao dien
#
# ---------------------------------------------------------------------------------------
# VI SAO PHAI CO SCRIPT NAY
#
# APK nung cung domain vao trong luc build. `twa-manifest.json` ghi domain vao BON cho —
# `host`, `webManifestUrl`, `fullScopeUrl`, `iconUrl` — va bubblewrap dong tat ca vao APK.
# Doi domain la phai build lai VA cai lai len dien thoai, khong co duong tat.
#
# Du an dung quick tunnel (*.trycloudflare.com). Loai domain nay doi moi lan chay lai va bi
# Cloudflare thu hoi sau vai gio, nen vong "domain chet -> build lai -> cai lai" lap lai kha
# thuong xuyen. Da can nhac mua domain co dinh de cat han vong nay; nguoi dung chon khong.
# Ghi trong docs/scope.md muc 2026-08-08.
#
# ---------------------------------------------------------------------------------------
# BAY DA SAP THAT — 2026-08-08
#
# App cai xong mo ra bao ERR_NAME_NOT_RESOLVED. Luc do tien trinh cloudflared VAN DANG CHAY,
# va no van khai hostname cu qua endpoint metrics. Nhung Cloudflare da thu hoi quick tunnel:
# tra DNS that qua 1.1.1.1 ra `Non-existent domain`.
#
#   => TIEN TRINH TUNNEL CON SONG KHONG CO NGHIA LA DOMAIN CON SONG.
#
# cloudflared khong tu biet minh da bi thu hoi. Moi phep kiem dua vao "tunnel co chay khong"
# deu cho ket qua xanh trong khi app da chet tu lau.
#
# Vi vay script nay KHONG TIN cloudflared. No chi hoi cloudflared de lay DANH SACH UNG VIEN,
# roi tu xac nhan bang cach phan giai DNS that va goi thang vao
# https://<domain>/.well-known/assetlinks.json xem co ra dung app nay khong.
# ---------------------------------------------------------------------------------------

[CmdletBinding()]
param(
    [string]$Domain,
    [switch]$BoQuaPwa,
    [string]$MatKhau
)

$ErrorActionPreference = "Stop"

# PowerShell 5.1 mac dinh con dam phan TLS 1.0 voi mot so endpoint. Cloudflare tu choi thang,
# va loi hien ra la "khong ket noi duoc" — nghe giong het domain chet, nen rat de chan doan sai.
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$root        = Split-Path -Parent $PSScriptRoot
$twaDir      = Join-Path $root "android-twa"
$manifestTwa = Join-Path $twaDir "twa-manifest.json"

if (-not (Test-Path $manifestTwa)) {
    throw "Khong thay $manifestTwa — chua khoi tao du an TWA (bubblewrap init)"
}

$twa       = Get-Content $manifestTwa -Raw | ConvertFrom-Json
$packageId = $twa.packageId
$domainCu  = $twa.host

Write-Host ""
Write-Host "=== Build APK TWA ===" -ForegroundColor Cyan
Write-Host "  Package : $packageId"
Write-Host "  Domain cu: $domainCu"
Write-Host ""


# --- Buoc 1: dua giao dien moi nhat vao backend ------------------------------------------
# Phai lam TRUOC khi kiem domain: assetlinks.json di theo duong PWA (app/public/.well-known)
# vao backend/src/main/resources/static. Chua build thi domain phuc vu ban cu.
if ($BoQuaPwa) {
    Write-Host "[1/5] Bo qua build PWA (-BoQuaPwa)" -ForegroundColor DarkGray
} else {
    Write-Host "[1/5] Build PWA va chep vao backend..." -ForegroundColor Cyan
    & (Join-Path $PSScriptRoot "build-pwa.ps1")
    Write-Host ""
    Write-Host "     LUU Y: backend phai duoc KHOI DONG LAI thi moi phuc vu ban vua build," -ForegroundColor Yellow
    Write-Host "     vi static/ duoc nap tu classpath luc khoi dong." -ForegroundColor Yellow
    Write-Host ""
}


# --- Buoc 2: tim domain ung vien ----------------------------------------------------------
function Tim-UngVienDomain {
    # Chi LIET KE thu cloudflared tuong minh dang giu. Khong ket luan gi o day.
    $ungVien = @()
    $tienTrinh = Get-Process cloudflared -ErrorAction SilentlyContinue
    if (-not $tienTrinh) { return $ungVien }

    foreach ($tt in $tienTrinh) {
        $cong = Get-NetTCPConnection -OwningProcess $tt.Id -State Listen -ErrorAction SilentlyContinue
        foreach ($c in $cong) {
            try {
                $r = Invoke-RestMethod -Uri "http://127.0.0.1:$($c.LocalPort)/quicktunnel" -TimeoutSec 3
                if ($r.hostname) { $ungVien += $r.hostname }
            } catch {
                # Cong nay khong phai endpoint metrics. Bo qua.
            }
        }
    }
    return @($ungVien | Select-Object -Unique)
}

# Xac nhan that: domain phai phan giai duoc VA phuc vu dung assetlinks cua app nay.
# Tra ve van tay SHA-256 (chu thuong, khong dau hai cham) neu dat, $null neu khong.
function Xac-NhanDomain {
    param([string]$Ten)

    try {
        Resolve-DnsName -Name $Ten -Type A -ErrorAction Stop | Out-Null
    } catch {
        Write-Host "     $Ten -> DNS khong phan giai duoc (tunnel da bi thu hoi)" -ForegroundColor DarkYellow
        return $null
    }

    try {
        $r = Invoke-WebRequest -Uri "https://$Ten/.well-known/assetlinks.json" -TimeoutSec 15 -UseBasicParsing
    } catch {
        Write-Host "     $Ten -> DNS song nhung goi assetlinks that bai: $($_.Exception.Message)" -ForegroundColor DarkYellow
        return $null
    }

    try {
        $al = $r.Content | ConvertFrom-Json
    } catch {
        Write-Host "     $Ten -> assetlinks.json khong phai JSON hop le" -ForegroundColor DarkYellow
        return $null
    }

    $khop = $al | Where-Object { $_.target.package_name -eq $packageId }
    if (-not $khop) {
        Write-Host "     $Ten -> phuc vu assetlinks cua app KHAC (khong co $packageId)" -ForegroundColor DarkYellow
        return $null
    }

    $vanTay = @($khop.target.sha256_cert_fingerprints)[0]
    return ($vanTay -replace ':', '').ToLower()
}

Write-Host "[2/5] Tim domain duong ham..." -ForegroundColor Cyan

if ($Domain) {
    $ungVien = @($Domain)
    Write-Host "     Dung domain chi dinh: $Domain"
} else {
    $ungVien = Tim-UngVienDomain
    if ($ungVien.Count -eq 0) {
        Write-Host ""
        Write-Host "KHONG THAY cloudflared nao dang chay." -ForegroundColor Red
        Write-Host ""
        Write-Host "Mo mot cua so PowerShell KHAC va chay:" -ForegroundColor Yellow
        Write-Host "  cloudflared tunnel --url http://localhost:8080"
        Write-Host ""
        Write-Host "De nguyen cua so do, roi chay lai script nay." -ForegroundColor Yellow
        throw "Khong co duong ham nao dang chay"
    }
    Write-Host "     cloudflared khai $($ungVien.Count) hostname. Dang xac nhan tung cai..."
}

$domainMoi = $null
$vanTayPhucVu = $null
foreach ($uv in $ungVien) {
    $vt = Xac-NhanDomain -Ten $uv
    if ($vt) {
        $domainMoi = $uv
        $vanTayPhucVu = $vt
        Write-Host "     $uv -> SONG, phuc vu dung $packageId" -ForegroundColor Green
        break
    }
}

if (-not $domainMoi) {
    Write-Host ""
    Write-Host "KHONG CO domain nao dung duoc." -ForegroundColor Red
    Write-Host ""
    Write-Host "Nguyen nhan thuong gap, theo thu tu de kiem:" -ForegroundColor Yellow
    Write-Host "  1. Quick tunnel da bi Cloudflare thu hoi. Tat cloudflared roi chay lai:"
    Write-Host "       cloudflared tunnel --url http://localhost:8080"
    Write-Host "  2. Backend chua chay. Kiem: curl http://localhost:8080/.well-known/assetlinks.json"
    Write-Host "  3. Duong ham dang tro vao CONG KHAC (vi du 8099), khong phai 8080."
    Write-Host ""
    throw "Khong xac nhan duoc domain nao con song"
}


# --- Buoc 3: kiem manifest PWA -----------------------------------------------------------
# Sai kieu MIME o day tung lam mat nut cai dat cua PWA. Voi TWA no khong chan build, nhung
# van dang bao vi no bao truoc mot lop hong khac.
Write-Host "[3/5] Kiem manifest PWA tren domain..." -ForegroundColor Cyan
try {
    $mf = Invoke-WebRequest -Uri "https://$domainMoi/manifest.webmanifest" -TimeoutSec 15 -UseBasicParsing
    $kieu = $mf.Headers['Content-Type']
    if ($kieu -like '*application/manifest+json*') {
        Write-Host "     manifest.webmanifest -> 200, $kieu" -ForegroundColor Green
    } else {
        Write-Host "     CANH BAO: manifest tra ve kieu '$kieu', dung phai la application/manifest+json" -ForegroundColor Yellow
        Write-Host "     Xem WebAppConfig.mimeChoManifest()." -ForegroundColor Yellow
    }
} catch {
    Write-Host "     CANH BAO: khong goi duoc manifest.webmanifest — $($_.Exception.Message)" -ForegroundColor Yellow
}


# --- Buoc 4: va domain + tang version vao twa-manifest.json --------------------------------
# Thay chuoi thay vi doc-ghi lai JSON: ConvertTo-Json cua PS 5.1 escape ky tu tieng Viet
# thanh \uXXXX va xao tron thu tu khoa, lam file kho doc va kho so sanh trong git.
Write-Host "[4/5] Va domain + tang version vao twa-manifest.json..." -ForegroundColor Cyan

$noiDung = Get-Content $manifestTwa -Raw

# 4a. Va domain (neu doi).
if ($domainCu -eq $domainMoi) {
    Write-Host "     Domain khong doi ($domainMoi)." -ForegroundColor DarkGray
} else {
    $noiDung = $noiDung.Replace($domainCu, $domainMoi)
    Write-Host "     Domain: $domainCu -> $domainMoi" -ForegroundColor Green
}

# 4b. Tu tang appVersionCode va dat appVersionName TRONG JSON, roi buoc 5 goi update voi
# --skipVersionUpgrade. Vi sao tu lam thay vi de bubblewrap lo:
#   `bubblewrap update` khong co --appVersionName thi HOI TUONG TAC "versionName for the new
#   App version", va trong cua so khong co stdin (chay nen) no chet luon voi
#   ERR_USE_AFTER_CLOSE('readline'). Tu quan version o day thi update chay im, va appVersionCode
#   VAN tang deu — can cho viec cai de len ban cu (Android tu choi cai de khi versionCode khong
#   tang). appVersionName chi la chuoi hien thi, dat "1.0.<code>" cho de doc.
$codeCu = [int]([regex]::Match($noiDung, '"appVersionCode"\s*:\s*(\d+)').Groups[1].Value)
$codeMoi = $codeCu + 1
$tenMoi = "1.0.$codeMoi"
$noiDung = [regex]::Replace($noiDung, '"appVersionCode"\s*:\s*\d+', "`"appVersionCode`": $codeMoi")
$noiDung = [regex]::Replace($noiDung, '"appVersionName"\s*:\s*"[^"]*"', "`"appVersionName`": `"$tenMoi`"")
$noiDung = [regex]::Replace($noiDung, '"appVersion"\s*:\s*"[^"]*"', "`"appVersion`": `"$tenMoi`"")
Write-Host "     Version: code $codeCu -> $codeMoi · name $tenMoi" -ForegroundColor Green

# KHONG dung Set-Content -Encoding utf8. Tren PowerShell 5.1 no ghi kem BOM, ma JSON.parse cua
# Node VO khi gap BOM: SyntaxError: Unexpected token '﻿' ... is not valid JSON. Get-Content -Raw
# thi lai AM THAM cat BOM luc doc, nen vong doc-sua-ghi tu no them BOM vao file truoc do khong co.
# WriteAllText voi UTF8Encoding($false) ghi UTF-8 khong BOM.
[System.IO.File]::WriteAllText($manifestTwa, $noiDung, (New-Object System.Text.UTF8Encoding($false)))


# --- Buoc 5: build + ky -------------------------------------------------------------------
Write-Host "[5/5] Build va ky APK..." -ForegroundColor Cyan

if (-not $MatKhau) {
    $baoMat = Read-Host -Prompt "     Mat khau keystore (android.keystore)" -AsSecureString
    $MatKhau = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($baoMat))
}

# bubblewrap chi dung mat khau tu moi truong khi CA HAI bien deu duoc dat. Thieu mot cai la
# no quay ra hoi tuong tac va treo script.
$env:BUBBLEWRAP_KEYSTORE_PASSWORD = $MatKhau
$env:BUBBLEWRAP_KEY_PASSWORD      = $MatKhau

# bubblewrap goi `gradlew.bat` TRAN (khong co .\) qua cmd.exe. May nay dat bien he thong
# NoDefaultCurrentDirectoryInExePath=1 — mot thiet lap bao mat khien cmd.exe KHONG chay file
# tu thu muc hien tai neu chi go ten tran; no chi tim trong PATH. Nen `gradlew.bat` bao
# "'gradlew.bat' is not recognized" du file nam ngay do. `.\gradlew.bat` thi chay, nhung minh
# khong sua duoc lenh ben trong bubblewrap. Cach vao duoc: them thu muc android-twa vao PATH
# de ten tran tim thay qua PATH. Chi song trong tien trinh script nay, khong ro ri ra ngoai.
$pathCu = $env:PATH
$env:PATH = "$twaDir;$env:PATH"

# Tim JDK 64-bit cho gradle. Xem khoi va gradle.properties ngay ben duoi de biet vi sao can.
$jdkHome = $null
foreach ($ung in @($env:JAVA_HOME, "C:\Program Files\Java\jdk-21",
                   "C:\Program Files\Eclipse Adoptium\jdk-21", "C:\Program Files\Java\jdk-17")) {
    if ($ung -and (Test-Path (Join-Path $ung "bin\java.exe"))) { $jdkHome = $ung; break }
}
if (-not $jdkHome) {
    throw "Khong tim thay JDK 64-bit. bubblewrap chi co JDK 17 BAN 32-BIT, khong du heap. Cai JDK 21 hoac dat JAVA_HOME."
}

Push-Location $twaDir
try {
    # Ha ErrorActionPreference quanh lenh ngoai: PS 5.1 boc moi dong stderr cua chuong trinh
    # ngoai thanh ErrorRecord, va voi 'Stop' thi mot dong canh bao cung lam script dung du
    # build thanh cong. Ma thoat moi la thu noi that bai hay khong.
    $cu = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        # `update` phai chay TRUOC `build`. No sinh lai du an Android tu twa-manifest.json va
        # ghi lai manifest-checksum.txt. Khong co buoc nay thi `build` phat hien manifest doi
        # va DUNG LAI HOI nguoi dung — treo script.
        #
        # --skipVersionUpgrade: KHONG cho bubblewrap tu tang version, vi khi tu tang no HOI
        # tuong tac "versionName for the new App version" va chet o cua so khong co stdin
        # (ERR_USE_AFTER_CLOSE readline). Version da duoc tang tay trong buoc 4, bubblewrap chi
        # viec dung so trong JSON.
        & bubblewrap update --manifest="$manifestTwa" --skipVersionUpgrade | ForEach-Object { "$_" }
        if ($LASTEXITCODE -ne 0) { throw "bubblewrap update that bai (ma thoat $LASTEXITCODE)" }

        # VA LAI gradle.properties SAU update, TRUOC build.
        #
        # bubblewrap chi kem mot JDK 17 BAN 32-BIT. Gradle mac dinh xin heap -Xmx1536m, ma JVM
        # 32-bit khong cap phat noi tren 1.5GB khong gian dia chi lien tuc, nen chet ngay khi
        # khoi dong daemon: "Could not reserve enough space for 1572864KB object heap". Cach va
        # la tro `org.gradle.java.home` sang JDK 64-bit (dung chung voi backend Spring Boot).
        #
        # Nhung `bubblewrap update` REGENERATE gradle.properties moi lan chay, xoa sach dong vá
        # do. Nen phai ghi lai NGAY SAU update va TRUOC build, khong the vá mot lan roi thoi.
        # Dung dau / (forward slash): trong file .properties cua Java, \ la ky tu escape.
        $gp = Join-Path $twaDir "gradle.properties"
        $jdkForward = $jdkHome.Replace('\', '/')
        $noiDungGp = Get-Content $gp -Raw
        if ($noiDungGp -notmatch 'org\.gradle\.java\.home') {
            $noiDungGp = $noiDungGp.TrimEnd() + "`norg.gradle.java.home=$jdkForward`n"
            [System.IO.File]::WriteAllText($gp, $noiDungGp, (New-Object System.Text.UTF8Encoding($false)))
        }
        Write-Host "     gradle.properties -> java.home = $jdkForward" -ForegroundColor DarkGray

        # Khong truyen --skipPwaValidation: cai co do con sot trong `bubblewrap help` nhung
        # ban 1.25.0 khong doc no, va build cung khong con chay kiem dinh PWA nua.
        & bubblewrap build --manifest="$manifestTwa" | ForEach-Object { "$_" }
        if ($LASTEXITCODE -ne 0) { throw "bubblewrap build that bai (ma thoat $LASTEXITCODE)" }
    } finally {
        $ErrorActionPreference = $cu
    }
} finally {
    Pop-Location
    $env:PATH = $pathCu
    # Khong de mat khau song trong moi truong sau khi script ket thuc.
    Remove-Item Env:\BUBBLEWRAP_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\BUBBLEWRAP_KEY_PASSWORD      -ErrorAction SilentlyContinue
}


# --- Kiem chung sau build ------------------------------------------------------------------
# Day moi la phep kiem co gia tri that: van tay APK VUA BUILD phai khop voi assetlinks ma
# DOMAIN DANG PHUC VU. Lech mot ky tu la Chrome tut ve Custom Tab, hien thanh dia chi, va
# mat dung thu duy nhat APK mang lai — nhung app van chay nen rat de tuong la binh thuong.
$apk = Join-Path $twaDir "app-release-signed.apk"
if (-not (Test-Path $apk)) { throw "Build xong nhung khong thay $apk" }

$buildTools = Get-ChildItem "$env:USERPROFILE\.bubblewrap\android_sdk\build-tools" -Directory |
              Sort-Object Name -Descending | Select-Object -First 1
$apksigner = Join-Path $buildTools.FullName "apksigner.bat"

$ketQua = & $apksigner verify --print-certs $apk
$dongVanTay = $ketQua | Select-String -Pattern "SHA-256 digest:" | Select-Object -First 1
$vanTayApk = ($dongVanTay -split ':')[-1].Trim().ToLower()

Write-Host ""
Write-Host "=== Ket qua ===" -ForegroundColor Cyan
Write-Host "  APK        : $apk"
Write-Host "  Kich thuoc : $([math]::Round((Get-Item $apk).Length / 1MB, 2)) MB"
Write-Host "  Domain     : https://$domainMoi"
Write-Host ""
Write-Host "  Van tay APK       : $vanTayApk"
Write-Host "  Van tay domain khai: $vanTayPhucVu"

if ($vanTayApk -eq $vanTayPhucVu) {
    Write-Host "  => KHOP. TWA se xac minh duoc, app mo ra KHONG co thanh dia chi." -ForegroundColor Green
} else {
    Write-Host "  => LECH!" -ForegroundColor Red
    Write-Host ""
    Write-Host "  App van cai va chay duoc, nhung Chrome se tut ve Custom Tab va HIEN THANH" -ForegroundColor Red
    Write-Host "  DIA CHI — mat dung thu duy nhat APK mang lai." -ForegroundColor Red
    Write-Host ""
    Write-Host "  Sua: chep van tay APK o tren vao app/public/.well-known/assetlinks.json" -ForegroundColor Yellow
    Write-Host "  (dinh dang HOA, ngan cach bang dau hai cham), roi chay lai script nay." -ForegroundColor Yellow
    throw "Van tay APK khong khop assetlinks dang phuc vu"
}

Write-Host ""
Write-Host "Buoc tiep theo — cai len dien thoai:" -ForegroundColor Cyan
Write-Host "  1. Chep app-release-signed.apk sang dien thoai (USB, Telegram, Drive...)"
Write-Host "  2. Mo file do tren dien thoai, cho phep 'Cai dat ung dung khong ro nguon goc'"
Write-Host "  3. Mo app: KHONG duoc thay thanh dia chi. Thay tuc la assetlinks chua an."
Write-Host ""
Write-Host "DUNG TAT cua so cloudflared. Tat la app chet." -ForegroundColor Yellow
Write-Host ""
