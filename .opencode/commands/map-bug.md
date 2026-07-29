---
description: Triage+fix map driving bugs (labels, puck, tilt) from a short symptom — loads local skills
agent: build
---
Short user report (treat as the only task — do not ask clarifying component questions):

$ARGUMENTS

Required before any read/edit:
  skill local-opencode-loop
  skill local-map-triage

ONE class only — if two bugs, pick one and defer the other. Label stretch: python tools/fix_driving_text_pitch.py (sets layout text-pitch-alignment=viewport — never layer root; never MAP; never mix poiTextOnly flip; never treat minified JSON as binary; no *.backup / indent=2). Rubber-band: at most ONE SmoothingLocationEngine constant.

From repo root run ONLY .\scripts\verify-debug.ps1 (no cd, no &&, no &). If stream/metadata OR MIXAUTO_VERIFY_DONE.txt shows exit=0 / BUILD SUCCESSFUL — STOP even if bash still says Running. Do not start the deferred bug.
