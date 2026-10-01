#requires -Version 5.1
<#
.SYNOPSIS
  Release-сборка Android (APK + AAB). macOS — inline; Windows — отдельное окно.
#>
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradlew = if ($IsWindows -or $env:OS -eq 'Windows_NT') { '.\gradlew.bat' } else { './gradlew' }

function Get-HostPlatform {
    if ($PSVersionTable.PSVersion.Major -ge 6) {
        if ($IsWindows) { return 'Windows' }
        if ($IsMacOS) { return 'macOS' }
    }
    $os = [System.Runtime.InteropServices.OSPlatform]
    $rt = [System.Runtime.InteropServices.RuntimeInformation]
    if ($rt::IsOSPlatform($os::Windows)) { return 'Windows' }
    if ($rt::IsOSPlatform($os::OSX)) { return 'macOS' }
    throw 'Unsupported operating system.'
}

function Open-OutputFolder {
    param([string]$OutputFolder)
    $path = Join-Path $repoRoot $OutputFolder
    New-Item -ItemType Directory -Force -Path $path | Out-Null
    if ($script:Platform -eq 'macOS') {
        & open $path
    } else {
        Invoke-Item -LiteralPath $path
    }
    Write-Host "Opened: $path" -ForegroundColor Cyan
}

function Invoke-Build {
    param(
        [string]$Title,
        [string]$GradleTask,
        [string]$OutputFolder
    )

    Write-Host "Starting $Title..." -ForegroundColor Cyan
    Push-Location -LiteralPath $repoRoot
    try {
        Invoke-Expression "$gradlew $GradleTask"
        if ($LASTEXITCODE -ne 0) { throw "$Title failed (exit $LASTEXITCODE)" }
        if ($OutputFolder) { Open-OutputFolder -OutputFolder $OutputFolder }
        Write-Host "$Title completed." -ForegroundColor Green
    } finally {
        Pop-Location
    }
}

function Start-BuildInNewWindow {
    param(
        [string]$Title,
        [string]$GradleTask,
        [string]$OutputFolder
    )
    $path = Join-Path $repoRoot $OutputFolder
    $open = if ($OutputFolder) { " && explorer `"$path`"" } else { '' }
    $cmdLine = "cd /d `"$repoRoot`" && $gradlew $GradleTask$open && pause"
    Start-Process -FilePath 'cmd.exe' -ArgumentList '/k', $cmdLine -WindowStyle Normal
    Write-Host "Started $Title in new window." -ForegroundColor Cyan
}

function Sync-AdsRuleSet {
    & (Join-Path $PSScriptRoot 'sync_ads_ruleset.ps1')
    if ($LASTEXITCODE -ne 0) { throw "sync_ads_ruleset.ps1 failed (exit $LASTEXITCODE)" }
}

$script:Platform = Get-HostPlatform
Write-Host "Host: $script:Platform" -ForegroundColor Green
Sync-AdsRuleSet

$builds = @(
    @{ Title = 'APK Release'; Task = ':app:assembleRelease'; Output = 'app\build\outputs\apk\release' }
    @{ Title = 'AAB Release'; Task = ':app:bundleRelease'; Output = 'app\build\outputs\bundle\release' }
)

foreach ($b in $builds) {
    if ($script:Platform -eq 'macOS') {
        Invoke-Build -Title $b.Title -GradleTask $b.Task -OutputFolder $b.Output
    } else {
        Start-BuildInNewWindow -Title $b.Title -GradleTask $b.Task -OutputFolder $b.Output
    }
}

Write-Host ''
Write-Host 'Done.' -ForegroundColor Green
