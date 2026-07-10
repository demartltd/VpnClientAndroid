#requires -Version 5.1
<#
.SYNOPSIS
  При отсутствии .git инициализирует репозиторий (ветка main), настраивает origin,
  показывает изменения (включая снятие с индекса файлов из .gitignore), коммитит и push.
  Переменные окружения:
    GIT_COMMIT_MESSAGE=... — сообщение коммита без интерактивного ввода (на macOS иначе — диалог);
    GIT_ALLOW_DIRTY_PUSH=1 — не коммитить: пушить при локальных правках без коммита (выход 2, если не задано);
    GIT_PUSH_FORCE_LEASE=1 — push с --force-with-lease.
  Заметка: .gitignore не убирает уже отслеживаемые файлы с GitHub — скрипт находит их через
  «git ls-files -ci» и перед коммитом выполняет «git rm --cached».
#>
$ErrorActionPreference = 'Stop'

$OriginUrl = 'https://github.com/demartltd/VpnClientAndroid.git'
$DefaultBranch = 'main'

function Test-IsGitRepository {
    git rev-parse --git-dir 2>$null | Out-Null
    return ($LASTEXITCODE -eq 0)
}

function Initialize-GitRepository {
    if (Test-IsGitRepository) { return }

    Write-Host 'Git-репозиторий не найден — инициализация…' -ForegroundColor Yellow
    git init -b $DefaultBranch
    if ($LASTEXITCODE -ne 0) {
        git init
        if ($LASTEXITCODE -ne 0) {
            throw "git init завершился с кодом $LASTEXITCODE"
        }
        git checkout -b $DefaultBranch
        if ($LASTEXITCODE -ne 0) {
            throw "git checkout -b $DefaultBranch завершился с кодом $LASTEXITCODE"
        }
    }
    Write-Host "Создан локальный репозиторий (ветка $DefaultBranch)." -ForegroundColor Green
}

function Get-GitCurrentBranch {
    foreach ($source in @(
            { git branch --show-current 2>$null }
            { git symbolic-ref --short HEAD 2>$null }
            { git config --get init.defaultBranch 2>$null }
        )) {
        $branch = (& $source)
        if ($null -ne $branch) {
            $branch = $branch.Trim()
            if ($branch) { return $branch }
        }
    }
    return $DefaultBranch
}

function Set-GitRemoteOrigin {
    param(
        [Parameter(Mandatory)]
        [string]$Url
    )

    $hasOrigin = 'origin' -in @(git remote 2>$null | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    if ($hasOrigin) {
        git remote set-url origin $Url
        if ($LASTEXITCODE -ne 0) { throw "git remote set-url завершился с кодом $LASTEXITCODE" }
        Write-Host "origin → $Url" -ForegroundColor Cyan
    }
    else {
        git remote add origin $Url
        if ($LASTEXITCODE -ne 0) { throw "git remote add завершился с кодом $LASTEXITCODE" }
        Write-Host "origin added: $Url" -ForegroundColor Green
    }
}

function Get-GitTrackedIgnoredPaths {
    # Уже в индексе, но попадают под .gitignore — на GitHub останутся, пока не git rm --cached
    $paths = @(git ls-files -ci --exclude-standard 2>$null)
    # [string[]] — иначе PowerShell разворачивает один путь в string, а @paths в git rm
    # передаёт путь посимвольно (fatal: /: '/' is outside repository).
    # Запятая перед return — иначе один путь разворачивается в string (см. Remove-GitTrackedIgnoredPaths).
    return ,[string[]](@($paths | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Sort-Object))
}

function Remove-GitTrackedIgnoredPaths {
    $paths = Get-GitTrackedIgnoredPaths
    if ($paths.Count -eq 0) { return $false }

    Write-Host 'git rm --cached (файлы останутся на диске, уйдут из репозитория):' -ForegroundColor Cyan
    foreach ($path in $paths) {
        Write-Host "  $path"
        & git rm --cached --quiet -- $path
        if ($LASTEXITCODE -ne 0) {
            throw "git rm --cached завершился с кодом $LASTEXITCODE для $path"
        }
    }
    return $true
}

function Get-GitFileChangeSets {
    param(
        [string[]]$UntrackFromRepo = @()
    )

    $new = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $modified = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $deleted = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $untrack = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($path in $UntrackFromRepo) {
        if (-not [string]::IsNullOrWhiteSpace($path)) {
            [void]$untrack.Add($path)
        }
    }

    $lines = @(git status --porcelain)
    foreach ($line in $lines) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.Length -lt 4) { continue }

        $x = $line[0]
        $y = $line[1]
        $path = $line.Substring(3).Trim()
        if ($path -match ' -> ') {
            $path = ($path -split ' -> ', 2)[1].Trim()
        }

        $isUntracked = ($x -eq '?' -and $y -eq '?')
        $isDeleted = ($x -eq 'D' -or $y -eq 'D')
        $isRenamed = ($x -eq 'R' -or $y -eq 'R')
        $isAdded = ($x -eq 'A' -or $y -eq 'A')
        $isModified = ($x -eq 'M' -or $y -eq 'M')

        if ($isDeleted) {
            [void]$deleted.Add($path)
            [void]$new.Remove($path)
            [void]$modified.Remove($path)
            continue
        }

        if ($isUntracked -or ($isAdded -and -not $isModified)) {
            [void]$new.Add($path)
            continue
        }

        if ($isRenamed -or $isModified -or ($isAdded -and $isModified)) {
            [void]$modified.Add($path)
        }
    }

    [PSCustomObject]@{
        New              = @($new | Sort-Object)
        Modified         = @($modified | Sort-Object)
        Deleted          = @($deleted | Sort-Object)
        UntrackFromRepo  = @($untrack | Sort-Object)
    }
}

function Get-GitPushChangeSets {
    param(
        [Parameter(Mandatory)]
        [string]$Branch
    )

    $new = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $modified = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $deleted = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)

    git rev-parse --verify "$Branch@{upstream}" 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        return [PSCustomObject]@{
            New      = @()
            Modified = @()
            Deleted  = @()
            HasUpstream = $false
        }
    }

    $lines = @(git diff --name-status "$Branch@{upstream}..HEAD" 2>$null)
    foreach ($line in $lines) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $parts = $line -split "`t", 3
        if ($parts.Count -lt 2) { continue }

        $status = $parts[0]
        $path = $parts[-1].Trim()

        switch -Regex ($status) {
            '^A' { [void]$new.Add($path) }
            '^D' { [void]$deleted.Add($path) }
            default { [void]$modified.Add($path) }
        }
    }

    [PSCustomObject]@{
        New           = @($new | Sort-Object)
        Modified      = @($modified | Sort-Object)
        Deleted       = @($deleted | Sort-Object)
        HasUpstream   = $true
    }
}

function Write-GitChangeReport {
    param(
        [string]$Header,
        [array]$Sections,
        [switch]$SkipEmptySections,
        [string]$AllEmptyText
    )

    if ($Header) {
        Write-Host ''
        Write-Host $Header -ForegroundColor Cyan
    }

    $any = $false
    foreach ($section in $Sections) {
        if ($SkipEmptySections -and $section.Items.Count -eq 0) { continue }
        $any = $true
        Write-Host $section.Title -ForegroundColor $section.Color
        if ($section.Items.Count -eq 0) {
            Write-Host '  (нет)' -ForegroundColor DarkGray
        }
        else {
            $indent = if ($section.ItemIndent) { $section.ItemIndent } else { '  ' }
            foreach ($item in $section.Items) {
                Write-Host "$indent$item"
            }
        }
    }

    if (-not $any -and $AllEmptyText) {
        Write-Host $AllEmptyText -ForegroundColor DarkGray
    }
    Write-Host ''
}

function Write-GitFileChangeSets {
    param($ChangeSets)

    $sections = @(
        @{ Title = 'Новые';      Color = 'Green';  Items = $ChangeSets.New }
        @{ Title = 'Изменённые'; Color = 'Yellow'; Items = $ChangeSets.Modified }
        @{ Title = 'Удалённые';  Color = 'Red';    Items = $ChangeSets.Deleted }
    )
    if ($ChangeSets.PSObject.Properties.Name -contains 'UntrackFromRepo') {
        $sections += @{ Title = 'Уйдут с GitHub (.gitignore, git rm --cached)'; Color = 'Magenta'; Items = $ChangeSets.UntrackFromRepo }
    }
    Write-Host ''
    Write-GitChangeReport -Sections $sections
}

function Write-GitPushPreview {
    param($ChangeSets)

    if (-not $ChangeSets.HasUpstream) {
        Write-GitChangeReport -Header 'На origin после push (коммиты относительно upstream):' -Sections @() `
            -AllEmptyText '  upstream не настроен — будет первая публикация ветки'
        return
    }

    Write-GitChangeReport -Header 'На origin после push (коммиты относительно upstream):' -SkipEmptySections `
        -AllEmptyText '  (нет отличий от upstream в локальных коммитах)' -Sections @(
        @{ Title = '  + новые';      Color = 'Green';  Items = $ChangeSets.New;      ItemIndent = '    ' }
        @{ Title = '  ~ изменённые'; Color = 'Yellow'; Items = $ChangeSets.Modified; ItemIndent = '    ' }
        @{ Title = '  - удалённые';  Color = 'Red';    Items = $ChangeSets.Deleted;  ItemIndent = '    ' }
    )
}

function Test-GitHasCommitsToPush {
    param(
        [Parameter(Mandatory)]
        [string]$Branch
    )

    git rev-parse --verify "$Branch@{upstream}" 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        return $true
    }

    $ahead = (git rev-list --count "$Branch@{upstream}..HEAD" 2>$null).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($ahead)) {
        return $true
    }

    return ([int]$ahead -gt 0)
}

function Get-CommitMessageFromUser {
    if (-not [string]::IsNullOrWhiteSpace($env:GIT_COMMIT_MESSAGE)) {
        return $env:GIT_COMMIT_MESSAGE.Trim()
    }

    # macOS: диалог вместо TTY — Cursor/Python подставляет «source …/activate» в терминал.
    if (Get-Command osascript -ErrorAction SilentlyContinue) {
        while ($true) {
            $dialogResult = (osascript -e @'
set dlg to display dialog "Что сделано?" default answer "" with title "Сообщение коммита" buttons {"Отмена", "OK"} default button "OK"
return (button returned of dlg) & linefeed & (text returned of dlg)
'@ 2>$null)
            if ($LASTEXITCODE -ne 0) {
                Write-Host 'Отменено.' -ForegroundColor Yellow
                exit 0
            }
            $dialogParts = @($dialogResult -split "`n", 2)
            $button = $dialogParts[0].Trim()
            $msg = if ($dialogParts.Count -gt 1) { $dialogParts[1].Trim() } else { '' }
            if ($button -eq 'Отмена') {
                Write-Host 'Отменено.' -ForegroundColor Yellow
                exit 0
            }
            if ([string]::IsNullOrWhiteSpace($msg)) {
                [void](osascript -e 'display alert "Сообщение не может быть пустым." as warning')
                continue
            }
            Write-Host "Сообщение: $msg" -ForegroundColor Cyan
            return $msg
        }
    }

    while ($true) {
        $msg = (Read-Host 'Сообщение коммита (что сделано)').Trim()
        if ([string]::IsNullOrWhiteSpace($msg)) {
            Write-Host 'Сообщение не может быть пустым.' -ForegroundColor Yellow
            continue
        }
        Write-Host "Сообщение: $msg" -ForegroundColor Cyan
        $confirm = (Read-Host 'Подтвердить коммит? [y/N]').Trim()
        if ($confirm -notmatch '^[yY]$') {
            Write-Host 'Отменено.' -ForegroundColor Yellow
            exit 0
        }
        return $msg
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $repoRoot

Initialize-GitRepository
Set-GitRemoteOrigin -Url $OriginUrl

$branch = Get-GitCurrentBranch
if ([string]::IsNullOrWhiteSpace($branch)) {
    throw 'Не удалось определить текущую ветку (detached HEAD?): git switch -c main'
}

$trackedIgnored = Get-GitTrackedIgnoredPaths
$dirty = git status --porcelain
$needsCommit = ($dirty -or $trackedIgnored.Count -gt 0) -and $env:GIT_ALLOW_DIRTY_PUSH -ne '1'

if ($needsCommit) {
    $changeSets = Get-GitFileChangeSets -UntrackFromRepo $trackedIgnored
    Write-Host 'Планируемые изменения (рабочая копия + снятие с индекса):' -ForegroundColor Cyan
    Write-GitFileChangeSets -ChangeSets $changeSets

    $msg = Get-CommitMessageFromUser

    if ($trackedIgnored.Count -gt 0) {
        [void](Remove-GitTrackedIgnoredPaths)
    }

    Write-Host "git add -A && git commit -m `"$msg`"" -ForegroundColor Cyan
    git add -A
    git diff --cached --quiet
    if ($LASTEXITCODE -eq 1) {
        git commit -m $msg
        if ($LASTEXITCODE -ne 0) {
            throw "git commit завершился с кодом $LASTEXITCODE"
        }
    }
    else {
        Write-Host 'После git add -A нечего коммитить — пропускаем commit.' -ForegroundColor DarkGray
    }
}

$dirtyAfter = git status --porcelain
if ($dirtyAfter -and $env:GIT_ALLOW_DIRTY_PUSH -ne '1') {
    Write-Host @'

Остановка: остались незакоммиченные изменения.
Варианты:
  • закоммитить вручную и снова запустить скрипт;
  • пушить без коммита: GIT_ALLOW_DIRTY_PUSH=1 .\scripts\push-github.ps1

'@ -ForegroundColor Yellow
    exit 2
}

git fetch origin --prune 2>$null | Out-Null
$fetchFailed = ($LASTEXITCODE -ne 0)

if (-not (git status --porcelain) -and -not (Test-GitHasCommitsToPush -Branch $branch)) {
    Write-Host "Изменений нет: рабочая копия чистая, origin/$branch уже актуален." -ForegroundColor Green
    exit 0
}

$pushPreview = Get-GitPushChangeSets -Branch $branch
Write-GitPushPreview -ChangeSets $pushPreview

$remoteHeads = @(git ls-remote --heads origin $branch 2>$null)
$lsRemoteFailed = ($LASTEXITCODE -ne 0)
if (-not $fetchFailed -and -not $lsRemoteFailed -and $remoteHeads.Count -eq 0) {
    Write-Host "На origin нет ветки «$branch» — push создаст её заново." -ForegroundColor Yellow
}

$gitArgs = @('push', '-u', 'origin', $branch)
if ($env:GIT_PUSH_FORCE_LEASE -eq '1') {
    $gitArgs += '--force-with-lease'
    Write-Host 'GIT_PUSH_FORCE_LEASE=1: используется --force-with-lease' -ForegroundColor Yellow
}

Write-Host "git $($gitArgs -join ' ')" -ForegroundColor Cyan
& git @gitArgs
$pushExitCode = $LASTEXITCODE
if ($pushExitCode -ne 0) {
    Write-Host @'

Push отклонён (часто: на GitHub другая история после сброса ветки).
Варианты:
  • подтянуть и слить: git pull --rebase origin BRANCH
  • перезаписать удалённую ветку осторожно:
      $env:GIT_PUSH_FORCE_LEASE = '1'
      .\scripts\push-github.ps1
'@ -ForegroundColor DarkYellow
    exit $pushExitCode
}

Write-Host "Push выполнен: origin/$branch" -ForegroundColor Green
exit 0
