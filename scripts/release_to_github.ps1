#requires -Version 5.1
<#
.SYNOPSIS
  Release Android APK → GitHub Releases (demartltd/VpnClientAndroid).
.PARAMETER SkipBuild
  Публиковать уже собранный app-release.apk.
#>
param([switch]$SkipBuild)

$ErrorActionPreference = 'Stop'

$repo = 'demartltd/VpnClientAndroid'
$root = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $root 'app/build/outputs/apk/release/app-release.apk'
$gradlew = if ($IsWindows -or $env:OS -eq 'Windows_NT') { '.\gradlew.bat' } else { './gradlew' }

$line = Get-Content (Join-Path $root 'gradle.properties') |
    Where-Object { $_ -match '^VERSION_NAME\s*=' } | Select-Object -First 1
if (-not $line) { throw 'VERSION_NAME not found in gradle.properties' }
$version = ($line -replace '^VERSION_NAME\s*=\s*', '').Trim()

Write-Host "Version: $version" -ForegroundColor Cyan

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw 'gh CLI not found. Install: https://cli.github.com'
}

$latestRelease = git -C $root ls-remote --tags --refs origin 2>$null |
    ForEach-Object { if ($_ -match 'refs/tags/(.+)$') { $Matches[1] } } |
    Where-Object { $_ -match '^\d+\.\d+\.\d+' } |
    Sort-Object { [version]($_ -replace '[^\d.].*$', '') } -Descending |
    Select-Object -First 1
if (-not $latestRelease) { $latestRelease = '(none)' }

$title = 'Confirm GitHub release'
$message = "New release: $version`nLatest on GitHub: $latestRelease`nRepo: $repo`nAsset: APK"
$confirmed = $false

if ($IsMacOS) {
    $osa = "display alert `"$title`" message `"New release: $version`" & return & `"Latest on GitHub: $latestRelease`" & return & `"Repo: $repo`" & return & `"Asset: APK`" buttons {`"Cancel`", `"Yes`"} default button `"Yes`" cancel button `"Cancel`""
    $null = & osascript -e $osa 2>$null
    $confirmed = ($LASTEXITCODE -eq 0)
} elseif ($IsWindows -or $PSVersionTable.PSEdition -eq 'Desktop') {
    Add-Type -AssemblyName System.Windows.Forms
    $confirmed = ([System.Windows.Forms.MessageBox]::Show(
        $message, $title, 'YesNo', 'Question') -eq 'Yes')
} else {
    Write-Host $message
    $confirmed = ((Read-Host "$title — Yes/No") -match '^(y|yes)$')
}

if (-not $confirmed) {
    Write-Host 'Cancelled.' -ForegroundColor Yellow
    exit 0
}

$ans = Read-Host "Publish $version as [r]elease / [p]re-release / [c]ancel"
if ($ans -match '^(c|cancel)$') {
    Write-Host 'Cancelled.' -ForegroundColor Yellow
    exit 0
}
if ($ans -match '^(p|pre|prerelease|pre-release)$') {
    $prerelease = $true
} elseif ($ans -match '^(r|release)?$') {
    $prerelease = $false
} else {
    Write-Host 'Cancelled (unknown choice).' -ForegroundColor Yellow
    exit 0
}

$kind = if ($prerelease) { 'pre-release' } else { 'release' }
Write-Host "Selected: $kind" -ForegroundColor Cyan

if (-not $SkipBuild) {
    Write-Host 'Syncing rule-sets…' -ForegroundColor Cyan
    & (Join-Path $PSScriptRoot 'sync_rulesets.ps1')
    Write-Host 'Building release APK...' -ForegroundColor Cyan
    Push-Location -LiteralPath $root
    try {
        & $gradlew :app:assembleRelease
        if ($LASTEXITCODE) { throw "Build failed (exit $LASTEXITCODE)" }
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path -LiteralPath $apk)) { throw "APK not found: $apk" }
$mb = [math]::Round((Get-Item $apk).Length / 1MB, 2)
Write-Host "Publishing app-release.apk ($mb MB) → $repo@$version ($kind)..." -ForegroundColor Cyan

gh release view $version -R $repo 2>$null | Out-Null
if ($LASTEXITCODE -eq 0) {
    gh release upload $version $apk -R $repo --clobber
    if ($LASTEXITCODE) { throw "gh release upload failed (exit $LASTEXITCODE)" }
    if ($prerelease) {
        gh release edit $version -R $repo --prerelease --latest=false
    } else {
        gh release edit $version -R $repo --prerelease=false --latest
    }
} else {
    if ($prerelease) {
        gh release create $version $apk -R $repo -t $version -n '' --prerelease
    } else {
        gh release create $version $apk -R $repo -t $version -n '' --latest
    }
}
if ($LASTEXITCODE) { throw "gh release failed (exit $LASTEXITCODE)" }

Write-Host "Published ($kind): https://github.com/$repo/releases/tag/$version" -ForegroundColor Green
