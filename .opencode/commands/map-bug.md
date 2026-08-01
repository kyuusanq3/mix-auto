---
description: Triage+fix map driving bugs (labels, puck, tilt) from a short symptom — loads local skills
agent: build
---
Short user report (treat as the only task — do not ask clarifying component questions):

$ARGUMENTS

Required before any read/edit:
  skill local-opencode-loop
  skill local-map-triage

ONE class only — if two bugs, pick one and defer the other.

Label stretch (run-tool only — no python -c / temp_fix.py; bash-safe paths for Zed Git Bash):
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "./scripts/run-tool.ps1" check_driving_text_pitch
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "./scripts/run-tool.ps1" fix_driving_text_pitch
(layout pitch viewport — never layer root; never MAP; never mix poiTextOnly flip)

Rubber-band: at most ONE SmoothingLocationEngine constant.

From repo root run ONLY:
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "./scripts/verify-debug.ps1"
(no cd, no &&, no &; never .\scripts\ backslashes in Git Bash). If stream/metadata OR MIXAUTO_VERIFY_DONE.txt shows exit=0 / BUILD SUCCESSFUL — STOP even if bash still says Running. Do not start the deferred bug.
