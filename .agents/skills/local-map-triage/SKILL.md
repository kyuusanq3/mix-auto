---
name: local-map-triage
description: >-
  ALWAYS load before editing data/map/** for MixAuto map BUGS — even if the user
  prompt is only a short symptom. Triggers: puck rubber-band/bounce/catch-up,
  high-speed hitch, stretched/streaked/smeared labels, nav tilt/camera/GPS
  smoothness. One class, ≤3 owners, no ask-loops. Verify with
  ./scripts/verify-debug.ps1. Not for new features (use local-map-feature). Not
  local-apk (release only).
---

# local-map-triage

Mandatory triage for local agents (OpenCode / Zed + Ollama) working on map puck, camera, or label **bugs** in mix-auto.

**New map/camera features** (implement / add / extend zoom, follow behavior) → use **`local-map-feature`**, not this skill.

**Rule globs do not auto-load** in OpenCode/Zed — open topic rules under `.cursor/rules/` by path (those are repo files, not Cursor-only).

**Zed path roots:** skill `<directory>` is only for skill assets. Repo paths (`.cursor/rules/…`, `./scripts/…`, `AGENTS.md`, `app/…`) resolve from the **worktree root** (`mix-auto`), never from `.agents/skills/…`. Do not invent paths like `.dev/proj/…`.

**Zed / Qwen tool calls:** Zed only executes Ollama `message.tool_calls`. Prefer the official Qwen3-Coder XML shape so the `qwen3-coder` PARSER can convert it (use `mixauto-qwen3-coder:*` tags from `tools/ollama/`):

```
<tool_call>
<function=read_file>
<parameter=path>
.cursor/rules/mix-auto-map-engine.mdc
</parameter>
<parameter=start_line>
9
</parameter>
<parameter=end_line>
52
</parameter>
</function>
</tool_call>
```

A lone `<function=…>` **without** a surrounding `<tool_call>…</tool_call>` is left as chat text and stalls — always open `<tool_call>` first. Never create a new rule file; `mix-auto-map-engine.mdc` already exists.

**Zed list_directory trap:** Do **not** call `list_directory` on `.` / project root (often returns empty and causes ask-loops). First tools for map bugs: `skill` → `local-map-triage` (already loading) then known paths / `grep` / `terminal`. If any `list_directory` returns empty text, **stop retrying it** — use `grep`, `find_path`, `read_file`, or `terminal` (`ls` / `Get-ChildItem`) instead. Never ask the user if the project “exists.”

**Label stretch fast path (after this skill loads):** classify Label artifact → run bash-safe check/fix recipes (Step 2 DO block) → verify. Do not restart with project exploration.

---

## Triggers

Use this skill when the user mentions any of:

- puck rubber-band, bounce, catch-up, hitch, lag at speed
- stretched, streaked, smeared, or elongated map labels (especially during navigation)
- nav camera smoothness, GPS puck, driving view, extrapolation
- driving tilt, nav pitch, overhead view, route look-ahead angle
- "plan fix for map driving issues"

---

## Step 1 — Open the map-engine rule by path

Open **`.cursor/rules/mix-auto-map-engine.mdc`** from the **worktree root** by **line range**. Zed often returns outline-only — do **not** re-call without `start_line`/`end_line` (same outline forever). Do **not** invent/create this file.

**Required next tool call after an outline** (label stretch example):

```
read_file path=.cursor/rules/mix-auto-map-engine.mdc start_line=9 end_line=52
```

Then if needed:

```
read_file path=.cursor/rules/mix-auto-map-engine.mdc start_line=52 end_line=130
```

1. Agent front matter (~L9–52) — label artifact + nav-mode stretch bullets  
2. Golden examples (~L52–130) if needed  
3. Style / chrome section if label-related  

Also skim **`AGENTS.md`** (worktree root) → cold-start + **Where to edit** (`start_line`/`end_line` if outline-only).

---

## Step 2 — Classify one primary cause

Pick exactly one before proposing edits:

| Class | When |
|-------|------|
| Smoothing lag | rubber-band / bounce catch-up; display ahead of GPS |
| Camera churn | tracking mode flips, padding during `TRACKING_GPS`, dive/overview races |
| Driving / nav tilt | pitch too flat or too steep; overhead vs windshield look-ahead |
| Tile/render hitch | RenderThread, style load, prefetch at speed |
| Main-thread work | GeoJSON rebuild, throttled UI state, heavy work on GPS tick |
| Label artifact | stretched/streaked/smeared text under tilted nav camera |

Do not blend multiple hypotheses in one patch.

**Two symptoms in one user message** (e.g. label stretch **and** rubber-band puck): still pick **one** class for the first patch; explicitly defer the other to a separate turn after verify.

---

## Reject these plans (common local-LLM mistakes)

Do **not** adopt or execute plans that:

- Blend label stretch + puck rubber-band into one patch or one companion/style dump
- Point at wrong first owners: `MapStyleConstants.kt`, `LauncherPreferences`, `MapHostViewModel`, GPU/CPU profiling, or "adaptive controls from metrics"
- Fix **nav-mode** label stretch by editing `poiTextOnlyLayerProperties` first — mix labels are hidden while navigating (`shouldShowMixPoiLabels()` requires `!isNavigating`); grep that gate, then inspect style / `mix-auto-driving.json` / `MapStyleController` for missing `text-pitch-alignment` on road/place/`poi_r*` symbol layers
- Change mix text pitch/rotation from **`VIEWPORT` to `MAP`** — golden example requires **VIEWPORT** for MapLibre #2788 streaks; MAP is the wrong direction
- Raise `EXTRAPOLATION_MAX_MS`, `EXTRAPOLATION_MAX_M`, or `EXTRAPOLATION_SNAP_BACK_MAX_M` as a first rubber-band fix — high-speed bounce is often display ran **ahead** via extrapolation then snapped in `resolveBlendStart`; increasing ahead limits usually **worsens** bounce
- Mass-edit 3+ `SmoothingLocationEngine` companion constants in one patch
- Treat `mix-auto-driving.json` as “binary” / uneditable because it is **one minified line** — use **`./scripts/run-tool.ps1`** (never `python -c`); do not leave `*.backup` / `temp_fix.py`
- Hand-edit via Read line offsets on the minified style (offsets fail on a 1-line file)
- Set `layer["text-pitch-alignment"]` on the **layer root** — MapLibre ignores it; must be `layer["layout"]["text-pitch-alignment"]`
- `json.dump(..., indent=2)` the driving style (explodes a 1-line asset into thousands of lines) — keep minified or use run-tool fix recipe
- Use backslash script paths (`.\scripts\...`) in Git Bash / Zed — `\s`/`\r` get eaten → `.scriptsrun-tool.ps1`

**DO (label artifact when pitch missing):** put `"text-pitch-alignment": "viewport"` under **`layout`** on every `symbol` layer that has `text-field`. Prefer run-tool (bash-safe; works in Zed Git Bash and PowerShell):

```bash
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "./scripts/run-tool.ps1" check_driving_text_pitch
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "./scripts/run-tool.ps1" fix_driving_text_pitch
```

Native PowerShell (already in pwsh/powershell, cwd = repo root) may use: `./scripts/run-tool.ps1 check_driving_text_pitch`  
Linux/macOS: `./scripts/run-tool.sh check_driving_text_pitch` then `fix_driving_text_pitch`.

Golden rewrite (layout only — never root):

```python
layout = layer.setdefault("layout", {})
if layer.get("type") == "symbol" and "text-field" in layout:
    layout["text-pitch-alignment"] = "viewport"
layer.pop("text-pitch-alignment", None)
```

Confirm: `check_driving_text_pitch` exits 0. Then verify with the bash-safe command in Step 6.

---

## Step 3 — Name ≤3 owner files

Examples (grep to confirm, read ≤3 files total before editing):

| Symptom | Owner files |
|---------|-------------|
| Rubber-band puck | `SmoothingLocationEngine.kt`, `LocationTrackingController.kt`, `OffRouteDetector.kt` |
| GPS acquisition / no fix / permission retry | `LocationAcquisitionHelper.kt` |
| Nav step / arrival / off-route tick | `NavigationProgressEvaluator.kt`, `OffRouteDetector.kt` |
| Camera padding / lookahead | `DrivingViewportPaddingController.kt` |
| Top-down / POI preview camera | `TopDownPoiCameraController.kt` |
| Free drive / recenter | `FreeDriveSessionCoordinator.kt` |
| Streaked labels (nav / tilted driving) | **First:** `./scripts/run-tool.ps1` → `check_driving_text_pitch` / `fix_driving_text_pitch` on `mix-auto-driving.json` (bash-safe: `powershell.exe -File "./scripts/run-tool.ps1" …`). **Detached pan only:** `PoiOverlayRenderer.kt` (`poiTextOnly*` = VIEWPORT). Not `MapLibreEngineImpl` facade dump |
| Driving / nav tilt | **Edit:** `MapLibreEngineImpl` companion `NAV_TILT_OFFSET` + `setDrivingTilt()`. **Apply:** `NavigationCameraController.navTilt()` callback only. **Pref:** `LauncherPreferences.driving_tilt` = free-drive only (not nav delta) |

**Wrong owners (reject):**

- `MapHostViewModel` for map label stretch (Compose is not map symbol text)
- `MapHostViewModel` or Compose for camera pitch / MapLibre tilt
- `domain/map/` for `MapLibreEngineImpl` (implementation is in `data/map/`)
- `NavigationRouteFetcher` / TomTom alternate for puck bounce
- Camera "position bounds" as first move
- `RouteRenderer` for puck glide (route greying only)

Also skim **Golden examples** in `.cursor/rules/mix-auto-map-engine.mdc` (puck vs route, camera session, POI symbol split).

---

## Step 3b — Skeleton / signature skim (files >~300 lines)

Large owners (`MapLibreEngineImpl`, camera/location controllers, big UI) burn local-model attention if fully dumped.

1. **Grep** the symbol or constant first.
2. Read **function signatures + surrounding hit context** (or class/companion headers) — not the entire file.
3. **Full-read only** the file(s) you will edit (still ≤3 owners total).
4. Treat `MapLibreEngineImpl` as a **wiring facade** unless grep proves the logic still lives there; prefer collaborators listed in `llms.txt` / Where to edit. Exception: `NAV_TILT_OFFSET` on the facade companion is live — grep hit context only, do not full-dump the file.

---

## Step 4 — Forbidden first moves

Do **not** as the first patch:

- Raise `DRIVING_ANIMATION_FPS` or tighten `LOCATION_ENGINE_*`
- Mass-retune `SmoothingLocationEngine` companion constants (including `blendDurationForSpeed`) without a logged hypothesis — especially do not raise `EXTRAPOLATION_MAX_*` / snap-back as a first rubber-band move
- Mass-edit 3+ `SmoothingLocationEngine` companion constants in one patch
- Change `poiTextOnlyLayerProperties` text pitch/rotation from VIEWPORT to MAP
- Use `RouteRenderer` for puck motion
- Call **`local-apk`** or publish a GitHub release while still diagnosing (local-apk = release/GitHub only, not debug)
- Grow `MapLibreEngineImpl` — add logic in `data/map/` collaborators
- Change `driving_tilt` slider default when user asked for nav-only delta (edit `NAV_TILT_OFFSET` instead)
- Report tilt impact as "free-drive 40→45" when only offset changed — nav = free-drive + offset; free-drive stays at pref

---

## Step 5 — Smallest hypothesis patch

- One concern, one file family
- Log which triage row you chose and why
- After editing a constant, sync docs that hardcode its value (or reference `NAV_TILT_OFFSET` by name instead of magic numbers)
- Baseline high-speed puck hitch may be intentionally unfixed for metrics — do not document spoiler constant recipes in rules

---

## Step 6 — Verify gate (required)

Preferred (bash-safe for Zed Git Bash; also fine in PowerShell) — cwd = repo root, **one command, no `&&`**:

```bash
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "./scripts/verify-debug.ps1"
```

Native PowerShell shortcut: `./scripts/verify-debug.ps1`

Fallback (native PowerShell only):

```powershell
if (-not $env:JAVA_HOME) {
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
}
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
./gradlew.bat assembleDebug
```

- Expected: **`BUILD SUCCESSFUL`** then **`MIXAUTO_VERIFY_DONE exit=0`**
- **Gradle done = done:** if you see `BUILD SUCCESSFUL`, `BUILD FAILED`, or `MIXAUTO_VERIFY_DONE` in any tool output/stream, the command finished — do not wait because the agent UI still shows Running / `esc interrupt`
- **Stuck-running terminals:** OpenCode/Zed bash may never flip to `completed` even after success. Fallback: **Read** `MIXAUTO_VERIFY_DONE.txt` at repo root; `exit=0` ⇒ **STOP** (do not re-run)
- Never claim success after Gradle failure
- Only after a verified fix **and** an explicit user request: **`local-apk`** for signed release APK / GitHub publish (not debug)

---

## Output template (planning phase)

```markdown
## Classification
<one cause>

## Owner files (≤3)
- ...

## Skeleton skim (if any file >~300 lines)
- grepped: …
- full-read only: …

## Forbidden moves avoided
- ...

## Hypothesis + smallest patch
...

## Verify
assembleDebug after patch
```
