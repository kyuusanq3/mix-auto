# Publishes mix-auto.apk to GitHub Releases after a local build (see ../SKILL.md).
# Usage:
#   .\publish-release.ps1 -Version "0.0.19" -CommitMessage "feat: ..." -NotesFile "C:\Temp\notes.md"
#
# Auth (pick one):
#   gh auth login                          # preferred after: winget install GitHub.cli
#   $env:GITHUB_TOKEN = "ghp_..."          # PAT with repo scope (REST API fallback)

param(
    [Parameter(Mandatory)]
    [string]$Version,

    [Parameter(Mandatory)]
    [string]$CommitMessage,

    [Parameter(Mandatory)]
    [string]$NotesFile,

    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")).Path,
    [string]$Branch = "dev",
    [string]$Repo = "kyuusanq3/mix-auto",

    [switch]$Draft
)

$ErrorActionPreference = "Stop"

function Add-GhToPath {
    if (Get-Command gh -ErrorAction SilentlyContinue) { return $true }
    $candidates = @(
        "$env:ProgramFiles\GitHub CLI\gh.exe",
        "${env:ProgramFiles(x86)}\GitHub CLI\gh.exe",
        "$env:LOCALAPPDATA\Programs\GitHub CLI\gh.exe"
    )
    foreach ($exe in $candidates) {
        if (Test-Path $exe) {
            $env:Path = "$(Split-Path $exe -Parent);$env:Path"
            return $true
        }
    }
    return $false
}

function Test-GhAuthenticated {
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { return $false }
    gh auth status 2>&1 | Out-Null
    return $LASTEXITCODE -eq 0
}

function Publish-ReleaseViaGh {
    param(
        [string]$Version,
        [string]$ApkPath,
        [string]$Repo,
        [string]$NotesFile,
        [switch]$Draft
    )

    $releaseArgs = @(
        "release", "create", $Version,
        $ApkPath,
        "--repo", $Repo,
        "--title", "Mix Auto v$Version",
        "--notes-file", $NotesFile
    )
    if ($Draft) { $releaseArgs += "--draft" }

    gh @releaseArgs
    if ($LASTEXITCODE -ne 0) { throw "gh release create failed" }
}

function Publish-ReleaseViaApi {
    param(
        [string]$Version,
        [string]$ApkPath,
        [string]$Repo,
        [string]$NotesFile,
        [switch]$Draft
    )

    $token = $env:GITHUB_TOKEN
    if ([string]::IsNullOrWhiteSpace($token)) {
        throw @"
GitHub auth not configured. Use one of:
  1. Install gh:  winget install --id GitHub.cli -e
     Then login:  gh auth login
  2. Set a PAT:   `$env:GITHUB_TOKEN = 'ghp_...'   (repo scope)
     Create at:   https://github.com/settings/tokens
"@
    }

    $apiHeaders = @{
        Authorization        = "Bearer $token"
        Accept               = "application/vnd.github+json"
        "X-GitHub-Api-Version" = "2022-11-28"
    }

    $notesBody = Get-Content -Path $NotesFile -Raw -Encoding UTF8
    $releasePayload = @{
        tag_name = $Version
        name     = "Mix Auto v$Version"
        body     = $notesBody
        draft    = [bool]$Draft
    } | ConvertTo-Json -Depth 4

    Write-Host "Creating GitHub release via API..."
    $release = Invoke-RestMethod `
        -Uri "https://api.github.com/repos/$Repo/releases" `
        -Method Post `
        -Headers $apiHeaders `
        -Body $releasePayload `
        -ContentType "application/json; charset=utf-8"

    $uploadBase = ($release.upload_url -split '\{')[0]
    $assetName = [uri]::EscapeDataString("mix-auto.apk")
    $uploadUri = "${uploadBase}?name=$assetName"

    Write-Host "Uploading mix-auto.apk ($('{0:N1}' -f ((Get-Item $ApkPath).Length / 1MB)) MB)..."
    $apkBytes = [System.IO.File]::ReadAllBytes($ApkPath)

    Invoke-RestMethod `
        -Uri $uploadUri `
        -Method Post `
        -Headers @{
            Authorization        = "Bearer $token"
            Accept               = "application/vnd.github+json"
            "X-GitHub-Api-Version" = "2022-11-28"
            "Content-Type"       = "application/vnd.android.package-archive"
        } `
        -Body $apkBytes | Out-Null
}

Set-Location $RepoRoot

$apkPath = Join-Path $RepoRoot "mix-auto.apk"
if (-not (Test-Path $apkPath)) {
    throw "mix-auto.apk not found - run the local-apk build steps first."
}

if (-not (Test-Path $NotesFile)) {
    throw "Release notes file not found: $NotesFile"
}

# Stage everything; gitignore excludes *.apk and *.jks
git add -A
git reset mix-auto.apk 2>$null
git reset app/mixauto-release.jks 2>$null

$pending = git status --porcelain
if ($pending) {
    git commit -m $CommitMessage
    if ($LASTEXITCODE -ne 0) { throw "git commit failed" }
} else {
    Write-Host "Nothing to commit - pushing existing HEAD and uploading APK."
}

git push origin $Branch
if ($LASTEXITCODE -ne 0) { throw "git push failed" }

$ghOnPath = Add-GhToPath
if ($ghOnPath -and (Test-GhAuthenticated)) {
    Publish-ReleaseViaGh -Version $Version -ApkPath $apkPath -Repo $Repo -NotesFile $NotesFile -Draft:$Draft
} else {
    Publish-ReleaseViaApi -Version $Version -ApkPath $apkPath -Repo $Repo -NotesFile $NotesFile -Draft:$Draft
}

Write-Host ""
Write-Host "Published Mix Auto v$Version to GitHub Releases."
Write-Host "Download: https://github.com/$Repo/releases/latest/download/mix-auto.apk"
