# Opens Android build output folders under the repo root.

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot

$folders = @(
    @{ Label = 'APK Release'; Path = (Join-Path $repoRoot 'app\build\outputs\apk\release') }
    @{ Label = 'AAB Release'; Path = (Join-Path $repoRoot 'app\build\outputs\bundle\release') }
)

Write-Host 'Which folder to open?'
for ($i = 0; $i -lt $folders.Count; $i++) {
    Write-Host ("  {0}. {1}" -f ($i + 1), $folders[$i].Label)
}

$choice = Read-Host ("Enter number (1-{0})" -f $folders.Count)
$num = $null
if (-not [int]::TryParse($choice.Trim(), [ref]$num) -or $num -lt 1 -or $num -gt $folders.Count) {
    Write-Host 'Invalid input.' -ForegroundColor Red
    exit 1
}

$selected = $folders[$num - 1]
New-Item -ItemType Directory -Force -Path $selected.Path | Out-Null
Invoke-Item -LiteralPath $selected.Path
