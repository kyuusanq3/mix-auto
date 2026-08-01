---
name: map-plan
description: Plan-only map triage from a short symptom — no edits, no ask-loops. User slash only; use Zed Ask profile.
disable-model-invocation: true
---

Short user report (plan only — no file edits):

Required: call skill local-opencode-loop then skill local-map-triage. Output the triage template only. One class; defer extras. Do not ask which files to open. Large rules: read by line range if outline-only. Label stretch owner: mix-auto-driving.json via bash-safe `powershell.exe -File "./scripts/run-tool.ps1" check_driving_text_pitch` (never `.\scripts\`).

Output ONLY:
## Classification
## Owner files (≤3)
## Skeleton skim
## Forbidden moves avoided
## Hypothesis + smallest patch
## Verify
assembleDebug after patch
