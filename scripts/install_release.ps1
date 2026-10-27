#requires -Version 5.1
<#
.SYNOPSIS
  Собирает debug и ставит APK на Android по USB (adb).
.PARAMETER SkipBuild
  Только установить уже собранный app/build/outputs/apk/debug/app-debug.apk.
.PARAMETER NoLaunch
  Не запускать MainActivity после установки.
.PARAMETER Replace
  Удалить com.demar.vpn перед установкой (нужно при смене release → debug).
.PARAMETER Serial
  Serial устройства (adb devices). Если не задан — единственный USB (-d), иначе ошибка при нескольких.
.PARAMETER SkipRuleSetSync
  Не скачивать rule-sets (.srs) перед сборкой. Alias: -SkipAdsSync.
#>
param(
    [switch]$SkipBuild,
    [switch]$NoLaunch,
    [switch]$Replace,
    [Alias('SkipAdsSync')][switch]$SkipRuleSetSync,
    [string]$Serial = ''
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $repoRoot 'app/build/outputs/apk/debug/app-debug.apk'
$package = 'com.demar.vpn'
$activity = "$package/.MainActivity"
$gradlew = if ($IsWindows -or $env:OS -eq 'Windows_NT') { '.\gradlew.bat' } else { './gradlew' }

function Get-AdbArgs {
    if ($script:DeviceSerial) { return @('-s', $script:DeviceSerial) }
    return @('-d')
}

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$AdbArgs)
    & adb @(Get-AdbArgs) @AdbArgs
}

function Assert-AdbUsbDevice {
    if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
        throw 'adb not found. Install Android SDK platform-tools and add to PATH.'
    }
    if ($Serial) {
        $script:DeviceSerial = $Serial.Trim()
        $null = & adb -s $script:DeviceSerial get-state 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "Device not ready: $script:DeviceSerial`nRun: adb devices"
        }
    } else {
        $script:DeviceSerial = $null
        $null = & adb -d get-state 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw @"
No single USB Android device (adb -d).

Connect one phone, or pass -Serial <id> from: adb devices
"@
        }
        $script:DeviceSerial = (& adb -d get-serialno).Trim()
    }
    $model = (Invoke-Adb shell getprop ro.product.model).Trim()
    Write-Host "Device: $model ($script:DeviceSerial)" -ForegroundColor Green
    return $script:DeviceSerial
}

function Test-SignatureConflict {
    param([string]$Text)
    return $Text -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match'
}

function Uninstall-AppPackage {
    Write-Host "Uninstalling $package (signature conflict or -Replace)…" -ForegroundColor Yellow
    Invoke-Adb uninstall $package 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "adb uninstall $package failed (exit $LASTEXITCODE)" }
}

function Install-DebugApk {
    param([string]$ApkPath)
    $out = Invoke-Adb install -r $ApkPath 2>&1 | Out-String
    if ($LASTEXITCODE -eq 0) { return }
    if (Test-SignatureConflict $out) {
        Uninstall-AppPackage
        $out = Invoke-Adb install -r $ApkPath 2>&1 | Out-String
        if ($LASTEXITCODE -eq 0) { return }
    }
    throw "adb install failed (exit $LASTEXITCODE)`n$out"
}

function Invoke-GradleInstallDebug {
    $out = & $gradlew :app:installDebug 2>&1 | Out-String
    if ($LASTEXITCODE -eq 0) { return }
    if (Test-SignatureConflict $out) {
        Uninstall-AppPackage
        $out = & $gradlew :app:installDebug 2>&1 | Out-String
        if ($LASTEXITCODE -eq 0) { return }
    }
    throw "installDebug failed (exit $LASTEXITCODE)`n$out"
}

$serial = Assert-AdbUsbDevice
$env:ANDROID_SERIAL = $serial

Push-Location -LiteralPath $repoRoot
try {
    if ($Replace) { Uninstall-AppPackage }

    if (-not $SkipBuild) {
        if (-not $SkipRuleSetSync) {
            $ans = Read-Host 'Download rule-sets (.srs)? 1=Yes 2=No'
            if ($ans -eq '2') { $SkipRuleSetSync = $true }
        }
        if (-not $SkipRuleSetSync) {
            Write-Host 'Syncing rule-sets…' -ForegroundColor Cyan
            & (Join-Path $PSScriptRoot 'sync_rulesets.ps1')
        } else {
            Write-Host 'Skipping rule-set download.' -ForegroundColor Yellow
        }
        Write-Host 'Building and installing debug…' -ForegroundColor Cyan
        Invoke-GradleInstallDebug
    } else {
        if (-not (Test-Path -LiteralPath $apk)) {
            throw "APK not found: $apk`nRun without -SkipBuild or: ./gradlew :app:assembleDebug"
        }
        Write-Host "Installing $apk …" -ForegroundColor Cyan
        Install-DebugApk -ApkPath $apk
    }

    if (-not $NoLaunch) {
        Invoke-Adb shell am start -n $activity | Out-Null
        Write-Host "Started $activity" -ForegroundColor Green
    }

    Write-Host 'Debug installed on USB device.' -ForegroundColor Green
} finally {
    Pop-Location
}
