# MixAuto tool runner — invoke tools/*.py without python -c quoting issues.
# Usage (repo root or any cwd):
#   .\scripts\run-tool.ps1                    # list recipes
#   .\scripts\run-tool.ps1 check_driving_text_pitch
#   .\scripts\run-tool.ps1 fix_driving_text_pitch
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

Write-Host "host=windows"

function Resolve-Python {
    foreach ($candidate in @("python", "py -3", "python3")) {
        if ($candidate -eq "py -3") {
            $py = Get-Command py -ErrorAction SilentlyContinue
            if ($py) {
                return @{ Exe = "py"; Args = @("-3") }
            }
            continue
        }
        $cmd = Get-Command $candidate -ErrorAction SilentlyContinue
        if ($cmd) {
            return @{ Exe = $cmd.Source; Args = @() }
        }
    }
    Write-Error "No Python found (tried python, py -3, python3)"
}

function Get-RecipeNames {
    Get-ChildItem -Path (Join-Path $repoRoot "tools") -Filter "*.py" -File |
        Where-Object {
            $_.Name -notmatch '^_' -and
            $_.Name -notmatch '^gen_' -and
            $_.Name -match '^(check_|fix_)'
        } |
        ForEach-Object { $_.BaseName } |
        Sort-Object -Unique
}

$pythonInfo = Resolve-Python
$pythonExe = $pythonInfo.Exe
$pythonPrefix = $pythonInfo.Args

if ($args.Count -eq 0) {
    Write-Host "python=$pythonExe"
    Write-Host "recipes:"
    Get-RecipeNames | ForEach-Object { Write-Host "  $_" }
    exit 0
}

$toolName = $args[0]
if ($toolName -match '[/\\]' -or $toolName -match '\.\.') {
    Write-Error "Invalid tool name (no path separators): $toolName"
}

$scriptPath = Join-Path $repoRoot "tools\$toolName.py"
if (-not (Test-Path $scriptPath)) {
    Write-Error "Unknown tool: $toolName (expected $scriptPath)"
}

Write-Host "python=$pythonExe tool=$toolName"

$toolArgs = @()
if ($args.Count -gt 1) {
    $toolArgs = $args[1..($args.Count - 1)]
}

if ($pythonPrefix.Count -gt 0) {
    & $pythonExe @pythonPrefix $scriptPath @toolArgs
} else {
    & $pythonExe $scriptPath @toolArgs
}
exit $LASTEXITCODE
