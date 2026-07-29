---
description: Triage+fix map driving bugs (labels, puck, tilt) from a short symptom — loads local skills
agent: build
---
Short user report (treat as the only task — do not ask clarifying component questions):

$ARGUMENTS

Required before any read/edit:
  skill local-opencode-loop
  skill local-map-triage

ONE class only — if two bugs, pick one and defer the other. Label stretch: add text-pitch-alignment viewport on style layers in mix-auto-driving.json (never MAP; never mix poiTextOnly flip). Rubber-band: at most ONE SmoothingLocationEngine constant.

From repo root run ONLY .\scripts\verify-debug.ps1 (no cd, no &&, no &). STOP on MIXAUTO_VERIFY_DONE exit=0 — do not start the deferred bug.
