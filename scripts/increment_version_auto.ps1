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
    Write-Host "VERSION_NAME or VERSION_CODE not found in gradle.properties!" -ForegroundColor Red
    exit 1
}

$currentName = ($lines[$versionNameLine] -replace '^VERSION_NAME\s*=\s*', '').Trim()
$currentCode = [int]($lines[$versionCodeLine] -replace '^VERSION_CODE\s*=\s*', '').Trim()
Write-Host "Current version: $currentName (code $currentCode)" -ForegroundColor Yellow

function Assert-VersionSegmentRange {
    param([int]$Value, [string]$Name)
    if ($Value -lt 0 -or $Value -gt 99) {
        Write-Host "Invalid version segment '$Name'=$Value. Each segment must be 0-99." -ForegroundColor Red
        exit 1
    }
}

function Increment-SemVer99 {
    param([int]$Major, [int]$Minor, [int]$Patch)
    Assert-VersionSegmentRange -Value $Major -Name "major"
    Assert-VersionSegmentRange -Value $Minor -Name "minor"
    Assert-VersionSegmentRange -Value $Patch -Name "patch"
    $Patch++
    if ($Patch -gt 99) { $Patch = 0; $Minor++ }
    if ($Minor -gt 99) { $Minor = 0; $Major++ }
    if ($Major -gt 99) {
        Write-Host "Version overflow: major cannot exceed 99." -ForegroundColor Red
        exit 1
    }
    return @($Major, $Minor, $Patch)
}

if ($currentName -match '^(\d+)\.(\d+)\.(\d+)$') {
    $nextParts = Increment-SemVer99 -Major ([int]$matches[1]) -Minor ([int]$matches[2]) -Patch ([int]$matches[3])
} else {
    Write-Host "Invalid VERSION_NAME format: $currentName (expected X.Y.Z)" -ForegroundColor Red
    exit 1
}

$newName = "$($nextParts[0]).$($nextParts[1]).$($nextParts[2])"
$newCode = $currentCode + 1

$lines[$versionNameLine] = "VERSION_NAME=$newName"
$lines[$versionCodeLine] = "VERSION_CODE=$newCode"
Set-Content -Path $gradleProps -Value $lines -Encoding UTF8

Write-Host "New version: $newName (code $newCode)" -ForegroundColor Green
