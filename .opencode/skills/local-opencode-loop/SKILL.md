---
name: local-opencode-loop
description: Required agent loop for OpenCode on mix-auto with local Ollama models (deepseek, qwen3-coder). Use for any code edit, verify, or fix task. Covers classify, grep, smallest patch, PowerShell verify gate, and correct impact reporting.
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
2. Files **>~400 lines**: read **hit context only** (signatures + ~20 lines around match).
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
- Never claim success after Gradle failure.
- No **`local-apk`** until verify passes **and** the user explicitly asks to ship a **GitHub release** / signed release APK — local-apk is never a debug build (use `verify-debug.ps1` for that)

---

## Step 5 — Report impact correctly

When changing tilt or other formula constants, state the **real formula**, not guessed deltas:

- Nav tilt = `freeDriveTilt + NAV_TILT_OFFSET` (default 40° + 15° = **55°** nav; free-drive stays **40°**)
- MapLibre: **0° = overhead**; higher = more windshield pitch
- More nav pitch → increase `NAV_TILT_OFFSET`; overhead look-ahead → decrease

After constant edits, sync docs that hardcode the old value (or reference the constant by name).

---

## Forbidden

- Bash `&&` on Windows
- Skipping `JAVA_HOME` after a known Gradle failure
- Full-dumping `MapLibreEngineImpl` when grep finds the constant in the companion
- **`local-apk`** during diagnosis (that skill is GitHub release/publish + `assembleRelease` only — not `assembleDebug`)
- Wrong owners: `MapHostViewModel` for map labels; `RouteRenderer` for puck glide

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
