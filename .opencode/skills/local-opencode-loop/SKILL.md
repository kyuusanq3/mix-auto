---
name: local-opencode-loop
description: >-
  ALWAYS load first on mix-auto for any code edit, bug fix, diagnose, plan, or
  verify (OpenCode + Ollama deepseek/qwen). Short user prompts still need this.
  Classify → grep ≤3 files → one patch → .\scripts\verify-debug.ps1 → STOP on
  MIXAUTO_VERIFY_DONE. Map bugs also need local-map-triage; new map features
  need local-map-feature.
---

# local-opencode-loop

Ultra-short loop for OpenCode + Ollama (deepseek-15b, qwen3-coder-30b) on mix-auto.

**OpenCode does not auto-load Cursor rule `globs:`** — open topic rules by path.

---

## Triggers

Use for any mix-auto task that touches code, builds, or diagnoses a bug.

Map puck/camera/label **bug** work: also load **`.opencode/skills/local-map-triage`** first.

New map/camera **feature** (implement / add / extend zoom, follow, GPS-tick camera): also load **`.opencode/skills/local-map-feature`**.

---

## Step 1 — Route the task

1. Read **`AGENTS.md`** → **Where to edit** (one row only).
2. Open the matching **`.cursor/rules/mix-auto-*.mdc`** by path (not by glob).
3. For map **bugs** (hitch, labels, rubber-band): **`.opencode/skills/local-map-triage`** before editing `data/map/**`.
4. For map **features** (implement / add camera behavior): **`.opencode/skills/local-map-feature`** before editing `data/map/**`.

Package index: **`llms.txt`** at repo root — do not dump full trees.

---

## Step 2 — Grep before read

1. **Grep** the symbol or constant.
2. Files **>~300 lines**: read **hit context only** (signatures + ~20 lines around match).
3. **Full-read ≤3 owner files** — only the file(s) you will edit.
4. Do not search `domain/map/` for `MapLibreEngineImpl` (lives in `data/map/`).

---

## Step 3 — Smallest patch

- One concern, one file family.
- Change only constants tied to the hypothesis — no companion carpet bombing.
- Edit the **live owner** (grep all hits; prefer controllers when duplicated).
- Exception: `NAV_TILT_OFFSET` on `MapLibreEngineImpl` companion is live.

---

## Step 4 — Verify (required)

From repo root (PowerShell only — **no bash `&&`**):

```powershell
.\scripts\verify-debug.ps1
```

Fallback:

```powershell
if (-not $env:JAVA_HOME) {
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
}
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat assembleDebug
```

- Expected: **`BUILD SUCCESSFUL`** then **`MIXAUTO_VERIFY_DONE exit=0`**
- **Gradle done = done** — do not wait if agent UI still shows Running after those lines.
- **OpenCode stuck-running:** bash may stay `running` with empty `state.output` even after success — treat **any** streamed/metadata chunk containing `MIXAUTO_VERIFY_DONE exit=0` or `BUILD SUCCESSFUL` as finished. Fallback: **Read** repo-root `MIXAUTO_VERIFY_DONE.txt` (written by the script); if it says `exit=0`, **STOP**.
- Never claim success after Gradle failure.
- No **`local-apk`** until verify passes **and** the user explicitly asks to ship a **GitHub release** / signed release APK — local-apk is never a debug build (use `verify-debug.ps1` for that)

---

## Step 4b — End turn after verify (required)

When you see **`BUILD SUCCESSFUL`** and/or **`MIXAUTO_VERIFY_DONE exit=0`** (in tool output, streaming metadata, **or** `MIXAUTO_VERIFY_DONE.txt`):

- **Stop** — end the turn; do not keep planning, diagnosing, or starting a second concern
- Do not wait because the agent UI still shows Running / `esc interrupt` — Gradle output / stamp file is authoritative
- Do **not** re-run verify just because the tool never flipped to `completed`
- If the user listed two map bugs (e.g. labels + rubber-band): one classified patch + verify, then **stop and report** — defer the second symptom to the next turn

---

## Step 5 — Report impact correctly

When changing tilt or other formula constants, state the **real formula**, not guessed deltas:

- Nav tilt = `freeDriveTilt + NAV_TILT_OFFSET` (default 40° + 15° = **55°** nav; free-drive stays **40°**)
- MapLibre: **0° = overhead**; higher = more windshield pitch
- More nav pitch → increase `NAV_TILT_OFFSET`; overhead look-ahead → decrease

After constant edits, sync docs that hardcode the old value (or reference the constant by name).

---

## Forbidden

- Bash `&&` / `cd … &&` on Windows — run `.\scripts\verify-debug.ps1` alone from repo root
- Skipping `JAVA_HOME` after a known Gradle failure
- Waiting forever because bash status is still `running` after sentinel/stamp exists
- Full-dumping `MapLibreEngineImpl` when grep finds the constant in the companion
- **`local-apk`** during diagnosis (that skill is GitHub release/publish + `assembleRelease` only — not `assembleDebug`)
- Wrong owners: `MapHostViewModel` for map labels; `RouteRenderer` for puck glide
- Leaving `*.backup` / `*.json.backup` of assets in the tree

---

## Output template

```markdown
## Classification
<one cause>

## Owner files (≤3)
- ...

## Patch
<one-line summary>

## Verify
BUILD SUCCESSFUL / MIXAUTO_VERIFY_DONE exit=0

## Impact
<formula-based, e.g. nav 50° → 55°; free-drive unchanged at 40°>
```
