---
description: Triage+fix map driving bugs (labels, puck, tilt) from a short symptom — loads local skills
agent: build
---
Short user report (treat as the only task — do not ask clarifying component questions):

$ARGUMENTS

Required: call skill local-opencode-loop then skill local-map-triage first. One class, one patch, then .\scripts\verify-debug.ps1 and STOP on MIXAUTO_VERIFY_DONE.
