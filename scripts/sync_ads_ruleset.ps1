#requires -Version 5.1
<#
.SYNOPSIS
  Download full ads.srs into app assets (required for any Android build).
#>
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
# Broad ads/tracker list (sing-box binary). Kept as geosite-category-ads.srs for stable asset path/tag.
$url = 'https://github.com/DustinWin/ruleset_geodata/releases/download/sing-box-ruleset/ads.srs'
$dest = Join-Path $repoRoot 'app/src/main/assets/rule-sets/geosite-category-ads.srs'

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $dest) | Out-Null
Write-Host "Fetching ads rule-set..." -ForegroundColor Cyan
Invoke-WebRequest -Uri $url -OutFile $dest -UseBasicParsing
$item = Get-Item -LiteralPath $dest
if ($item.Length -le 0) { throw "Ads rule-set download empty: $dest" }
$kb = [math]::Round($item.Length / 1KB, 1)
Write-Host "Ads rule-set: $kb KB -> $dest" -ForegroundColor Green
