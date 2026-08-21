#requires -Version 5.1
<#
.SYNOPSIS
  Собирает release и ставит APK на Android, подключённый по USB к Mac (adb -d).
.PARAMETER SkipBuild
  Только установить уже собранный app/build/outputs/apk/release/app-release.apk.
.PARAMETER NoLaunch
  Не запускать MainActivity после установки.
.PARAMETER Replace
  Удалить com.demar.vpn перед установкой (нужно при смене debug → release).
#>
param(
    [switch]$SkipBuild, 
    [switch]$NoLaunch,
    [switch]$Replace
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $repoRoot 'app/build/outputs/apk/release/app-release.apk'
$package = 'com.demar.vpn'
$activity = "$package/.MainActivity"
$gradlew = if ($IsWindows -or $env:OS -eq 'Windows_NT') { '.\gradlew.bat' } else { './gradlew' }

function Assert-AdbUsbDevice {
    if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
        throw 'adb not found. Install Android SDK platform-tools and add to PATH.'
    }
    $null = & adb -d get-state 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw @"
No single USB Android device (adb -d).

Connect the phone by cable, enable USB debugging, and run: adb devices
"@
    }
    $serial = (& adb -d get-serialno).Trim()
    $model = (& adb -d shell getprop ro.product.model).Trim()
    Write-Host "Device: $model ($serial)" -ForegroundColor Green
    return $serial
}

function Test-SignatureConflict {
    param([string]$Text)
    return $Text -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match'
}

function Uninstall-AppPackage {
    Write-Host "Uninstalling $package (signature conflict or -Replace)…" -ForegroundColor Yellow
    & adb -d uninstall $package 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "adb uninstall $package failed (exit $LASTEXITCODE)" }
}

function Install-ReleaseApk {
    param([string]$ApkPath)
    $out = & adb -d install -r $ApkPath 2>&1 | Out-String
    if ($LASTEXITCODE -eq 0) { return }
    if (Test-SignatureConflict $out) {
        Uninstall-AppPackage
        $out = & adb -d install -r $ApkPath 2>&1 | Out-String
        if ($LASTEXITCODE -eq 0) { return }
    }
    throw "adb install failed (exit $LASTEXITCODE)`n$out"
}

function Invoke-GradleInstallRelease {
    $out = & $gradlew :app:installRelease 2>&1 | Out-String
    if ($LASTEXITCODE -eq 0) { return }
    if (Test-SignatureConflict $out) {
        Uninstall-AppPackage
        $out = & $gradlew :app:installRelease 2>&1 | Out-String
        if ($LASTEXITCODE -eq 0) { return }
    }
    throw "installRelease failed (exit $LASTEXITCODE)`n$out"
}

$serial = Assert-AdbUsbDevice
$env:ANDROID_SERIAL = $serial

Push-Location -LiteralPath $repoRoot
try {
    if ($Replace) { Uninstall-AppPackage }

    if (-not $SkipBuild) {
        Write-Host 'Building and installing release…' -ForegroundColor Cyan
        Invoke-GradleInstallRelease
    } else {
        if (-not (Test-Path -LiteralPath $apk)) {
            throw "APK not found: $apk`nRun without -SkipBuild or: pwsh scripts/build_android.ps1"
        }
        Write-Host "Installing $apk …" -ForegroundColor Cyan
        Install-ReleaseApk -ApkPath $apk
    }

    if (-not $NoLaunch) {
        & adb -d shell am start -n $activity | Out-Null
        Write-Host "Started $activity" -ForegroundColor Green
    }

    Write-Host 'Release installed on USB device.' -ForegroundColor Green
} finally {
    Pop-Location
}
