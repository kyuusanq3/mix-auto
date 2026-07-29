# MixAuto debug verify gate — run from repo root or any cwd.
# Sets JAVA_HOME if missing, runs assembleDebug, prints a terminal sentinel,
# and writes MIXAUTO_VERIFY_DONE.txt so agents can Read the stamp if the bash
# tool UI stays "Running" after Gradle finishes (OpenCode stuck-running bug).
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
}
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

& "$repoRoot\gradlew.bat" assembleDebug
$exitCode = $LASTEXITCODE

$stampPath = Join-Path $repoRoot "MIXAUTO_VERIFY_DONE.txt"
@(
    "MIXAUTO_VERIFY_DONE exit=$exitCode"
    "timestamp=$(Get-Date -Format o)"
) | Set-Content -Path $stampPath -Encoding utf8

Write-Host "MIXAUTO_VERIFY_DONE exit=$exitCode"
exit $exitCode
