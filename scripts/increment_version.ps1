$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradleProps = Join-Path $repoRoot 'gradle.properties'

if (!(Test-Path $gradleProps)) {
    Write-Host "gradle.properties not found!" -ForegroundColor Red
    exit 1
}

$lines = Get-Content $gradleProps
$versionNameLine = -1
$versionCodeLine = -1

for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '^VERSION_NAME\s*=\s*(.+)$') { $versionNameLine = $i }
    if ($lines[$i] -match '^VERSION_CODE\s*=\s*(\d+)$') { $versionCodeLine = $i }
}

if ($versionNameLine -eq -1 -or $versionCodeLine -eq -1) {
    Write-Host "VERSION_NAME or VERSION_CODE not found!" -ForegroundColor Red
    exit 1
}

$currentName = ($lines[$versionNameLine] -replace '^VERSION_NAME\s*=\s*', '').Trim()
$currentCode = [int]($lines[$versionCodeLine] -replace '^VERSION_CODE\s*=\s*', '').Trim()
Write-Host "Current version: $currentName (code $currentCode)" -ForegroundColor Yellow
Write-Host ""
Write-Host "Enter new version (X.Y.Z):" -ForegroundColor Cyan
$newVersionInput = (Read-Host "New version").Trim()

if ($newVersionInput -match '^(\d+)\.(\d+)\.(\d+)$') {
    $newName = "$($matches[1]).$($matches[2]).$($matches[3])"
    $newCode = $currentCode + 1
    $lines[$versionNameLine] = "VERSION_NAME=$newName"
    $lines[$versionCodeLine] = "VERSION_CODE=$newCode"
    Set-Content -Path $gradleProps -Value $lines -Encoding UTF8
    Write-Host "Updated: $newName (code $newCode)" -ForegroundColor Green
} else {
    Write-Host "Invalid format: $newVersionInput (expected X.Y.Z)" -ForegroundColor Red
    exit 1
}
