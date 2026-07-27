# MixAuto debug verify gate — run from repo root or any cwd.
# Sets JAVA_HOME if missing, runs assembleDebug, prints a terminal sentinel.
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
}
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

& "$repoRoot\gradlew.bat" assembleDebug
$exitCode = $LASTEXITCODE
Write-Host "MIXAUTO_VERIFY_DONE exit=$exitCode"
exit $exitCode
