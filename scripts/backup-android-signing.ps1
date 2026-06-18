# Backs up Android release signing files into a timestamped zip under scripts/build/.

$ErrorActionPreference = 'Stop'

function Read-KeyProperties {
    param([string]$Path)
    $props = @{}
    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#') -and $line -match '^([^=]+)=(.*)$') {
            $props[$Matches[1].Trim()] = $Matches[2].Trim()
        }
    }
    return $props
}

function Get-KeystoreSha256 {
    param([string]$KeystorePath, [string]$Alias, [string]$StorePassword)
    $output = & keytool -list -v -keystore $KeystorePath -alias $Alias -storepass $StorePassword 2>&1
    if ($LASTEXITCODE -ne 0) { throw "keytool failed: $output" }
    foreach ($line in $output) {
        if ($line -match 'SHA256:\s*(.+)') { return $Matches[1].Trim() }
    }
    throw 'SHA256 fingerprint not found in keytool output'
}

function Remove-OldSigningBackups {
    param([string]$Directory, [int]$Keep = 3)
    $backups = @(Get-ChildItem -LiteralPath $Directory -Filter 'android-signing-backup_*.zip' -File |
        Sort-Object LastWriteTime -Descending)
    if ($backups.Count -le $Keep) { return }
    foreach ($file in ($backups | Select-Object -Skip $Keep)) {
        Remove-Item -LiteralPath $file.FullName -Force
        Write-Host "Removed old backup: $($file.Name)" -ForegroundColor DarkYellow
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$keyProps = Join-Path $repoRoot 'key.properties'
$keystore = Join-Path $repoRoot 'upload-keystore.jks'
$example = Join-Path $repoRoot 'key.properties.example'

$missing = @()
if (-not (Test-Path -LiteralPath $keyProps)) { $missing += $keyProps }
if (-not (Test-Path -LiteralPath $keystore)) { $missing += $keystore }

if ($missing.Count -gt 0) {
    Write-Host 'Missing signing files:' -ForegroundColor Red
    foreach ($p in $missing) { Write-Host "  $p" }
    Write-Host 'See readme-signing.md for setup.' -ForegroundColor Yellow
    exit 1
}

$props = Read-KeyProperties -Path $keyProps
$alias = $props['keyAlias']
$storePassword = $props['storePassword']
if (-not $alias -or -not $storePassword) {
    Write-Host 'key.properties must define keyAlias and storePassword.' -ForegroundColor Red
    exit 1
}

$sha256 = Get-KeystoreSha256 -KeystorePath $keystore -Alias $alias -StorePassword $storePassword
$outDir = Join-Path $repoRoot 'scripts\build'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$stamp = Get-Date -Format 'yyyy-MM-dd_HHmmss'
$zipPath = Join-Path $outDir "android-signing-backup_$stamp.zip"
$tempDir = Join-Path ([System.IO.Path]::GetTempPath()) "demar-vpn-android-signing-$stamp"
New-Item -ItemType Directory -Force -Path $tempDir | Out-Null

try {
    Copy-Item -LiteralPath $keyProps -Destination (Join-Path $tempDir 'key.properties')
    Copy-Item -LiteralPath $keystore -Destination (Join-Path $tempDir 'upload-keystore.jks')
    if (Test-Path -LiteralPath $example) {
        Copy-Item -LiteralPath $example -Destination (Join-Path $tempDir 'key.properties.example')
    }

    @"
SHA-256 certificate fingerprint
Alias: $alias
Keystore: upload-keystore.jks

$sha256
"@ | Set-Content -Path (Join-Path $tempDir 'sha256.txt') -Encoding UTF8

    @"
Demar VPN Android — signing backup
Created: $(Get-Date -Format 'u')
SHA-256: $sha256

Restore: copy key.properties and upload-keystore.jks to repo root — see readme-signing.md
"@ | Set-Content -Path (Join-Path $tempDir 'RESTORE.txt') -Encoding UTF8

    if (Test-Path -LiteralPath $zipPath) { Remove-Item -LiteralPath $zipPath -Force }
    Compress-Archive -Path (Join-Path $tempDir '*') -DestinationPath $zipPath -CompressionLevel Optimal
    Remove-OldSigningBackups -Directory $outDir -Keep 3

    Write-Host "SHA-256: $sha256" -ForegroundColor Cyan
    Write-Host "Backup created: $zipPath" -ForegroundColor Green
}
finally {
    if (Test-Path -LiteralPath $tempDir) { Remove-Item -LiteralPath $tempDir -Recurse -Force }
}
