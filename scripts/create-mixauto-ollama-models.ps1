# Creates MixAuto Ollama tags with qwen3-coder RENDERER/PARSER for Zed tool calls.
# Run from repo root: .\scripts\create-mixauto-ollama-models.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Write-Host "Creating mixauto-qwen3-coder:30b (FROM qwen3-coder:30b)..."
ollama create "mixauto-qwen3-coder:30b" -f "tools/ollama/Modelfile.mixauto-qwen3-coder"
if ($LASTEXITCODE -ne 0) { throw "ollama create 30b failed exit=$LASTEXITCODE" }

Write-Host "Creating mixauto-qwen3-coder:14b (FROM freehuntx/qwen3-coder:14b)..."
ollama create "mixauto-qwen3-coder:14b" -f "tools/ollama/Modelfile.mixauto-qwen3-coder-14b"
if ($LASTEXITCODE -ne 0) { throw "ollama create 14b failed exit=$LASTEXITCODE" }

Write-Host @"

Done. In Zed Agent Panel use:
  Profile: MixAuto Write (or MixAuto Ask for plan-only)
  Model:   mixauto-qwen3-coder:30b  (or :14b)

User settings already target these when configured via MixAuto Zed port.
Recreate tags after any edit under tools/ollama/Modelfile.mixauto-*.
"@
