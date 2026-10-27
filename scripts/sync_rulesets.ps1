#requires -Version 5.1
<#
.SYNOPSIS
  Download binary rule-sets into app assets (packaging only; shipped in APK).
  On download failure: keep existing file; if none — fail the build.
#>
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$destDir = Join-Path $repoRoot 'app/src/main/assets/rule-sets'

# tag = basename (.srs). URLs used only at packaging time — runtime is type:local.
$ruleSets = @(
    @{
        Tag = 'geosite-category-ads'
        Url = 'https://github.com/DustinWin/ruleset_geodata/releases/download/sing-box-ruleset/ads.srs'
    }
    @{
        Tag = 'geosite-category-ru'
        Url = 'https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-ru.srs'
    }
    @{
        Tag = 'geosite-ir'
        Url = 'https://raw.githubusercontent.com/Chocolate4U/Iran-sing-box-rules/rule-set/geosite-ir.srs'
    }
    @{
        Tag = 'geosite-cn'
        Url = 'https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-cn.srs'
    }
    @{
        Tag = 'geoip-ru'
        Url = 'https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-ru.srs'
    }
    @{
        Tag = 'geoip-ir'
        Url = 'https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-ir.srs'
    }
    @{
        Tag = 'geoip-cn'
        Url = 'https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-cn.srs'
    }
)

New-Item -ItemType Directory -Force -Path $destDir | Out-Null
$failed = @()

foreach ($rs in $ruleSets) {
    $dest = Join-Path $destDir "$($rs.Tag).srs"
    $tmp = Join-Path $destDir ("$($rs.Tag).srs.tmp.{0}" -f [guid]::NewGuid().ToString('N'))
    Write-Host "Fetching $($rs.Tag)..." -ForegroundColor Cyan
    try {
        Invoke-WebRequest -Uri $rs.Url -OutFile $tmp -UseBasicParsing
        $item = Get-Item -LiteralPath $tmp
        if ($item.Length -le 0) { throw "Rule-set download empty." }
        Move-Item -LiteralPath $tmp -Destination $dest -Force
        $kb = [math]::Round((Get-Item -LiteralPath $dest).Length / 1KB, 1)
        Write-Host "  $kb KB -> $dest" -ForegroundColor Green
    }
    catch {
        Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
        if (Test-Path -LiteralPath $dest) {
            $kb = [math]::Round((Get-Item -LiteralPath $dest).Length / 1KB, 1)
            Write-Host "  download failed ($($_.Exception.Message)); keeping existing $kb KB" -ForegroundColor Yellow
        }
        else {
            $failed += "$($rs.Tag): $($_.Exception.Message)"
            Write-Host "  FAILED (no cache): $($_.Exception.Message)" -ForegroundColor Red
        }
    }
}

if ($failed.Count -gt 0) {
    throw "Rule-set download failed and no cached file:`n$($failed -join "`n")"
}
