---
name: local-map-triage
description: Classify and plan fixes for MixAuto map driving issues — puck rubber-banding, high-speed hitch, stretched/streaked labels, nav camera/GPS smoothness. Use before editing data/map/** or proposing map-engine patches. Do NOT use for release builds — use local-apk after a verified fix.
---

# local-map-triage

Mandatory triage for OpenCode / local LLM agents working on map puck, camera, or label issues in mix-auto.

**OpenCode does not auto-load Cursor rule `globs:`** — you must read topic rules by path.

---

## Triggers

Use this skill when the user mentions any of:

- puck rubber-band, bounce, catch-up, hitch, lag at speed
- stretched, streaked, smeared, or elongated map labels (especially during navigation)
- nav camera smoothness, GPS puck, driving view, extrapolation
- "plan fix for map driving issues"

---

## Step 1 — Open the map-engine rule by path

Read **`.cursor/rules/mix-auto-map-engine.mdc`** in full (at least **Agent front matter** and the symptom section matching the report).

Also skim **`AGENTS.md`** → **Where to edit** and **Agent loop**.

---

## Step 2 — Classify one primary cause

Pick exactly one before proposing edits:

| Class | When |
|-------|------|
| Smoothing lag | rubber-band / bounce catch-up; display ahead of GPS |
| Camera churn | tracking mode flips, padding during `TRACKING_GPS`, dive/overview races |
| Tile/render hitch | RenderThread, style load, prefetch at speed |
| Main-thread work | GeoJSON rebuild, throttled UI state, heavy work on GPS tick |
| Label artifact | stretched/streaked/smeared text under tilted nav camera |

Do not blend multiple hypotheses in one patch.

---

## Step 3 — Name ≤3 owner files

Examples (grep to confirm, read ≤3 files total before editing):

| Symptom | Owner files |
|---------|-------------|
| Rubber-band puck | `SmoothingLocationEngine.kt`, `LocationTrackingController.kt`, `OffRouteDetector.kt` |
| Streaked labels | `PoiOverlayRenderer.kt`, `MapLibreEngineImpl.kt`, `MapStyleController.kt` / style JSON |

**Wrong owners (reject):**

- `MapHostViewModel` for map label stretch (Compose is not map symbol text)
- `NavigationRouteFetcher` / TomTom alternate for puck bounce
- Camera "position bounds" as first move
- `RouteRenderer` for puck glide (route greying only)

Also skim **Golden examples** in `.cursor/rules/mix-auto-map-engine.mdc` (puck vs route, camera session, POI symbol split).

---

## Step 3b — Skeleton / signature skim (files >~400 lines)

Large owners (`MapLibreEngineImpl`, camera/location controllers, big UI) burn local-model attention if fully dumped.

1. **Grep** the symbol or constant first.
2. Read **function signatures + surrounding hit context** (or class/companion headers) — not the entire file.
3. **Full-read only** the file(s) you will edit (still ≤3 owners total).
4. Treat `MapLibreEngineImpl` as a **wiring facade** unless grep proves the logic still lives there; prefer collaborators listed in `llms.txt` / Where to edit.

---

## Step 4 — Forbidden first moves

Do **not** as the first patch:

- Raise `DRIVING_ANIMATION_FPS` or tighten `LOCATION_ENGINE_*`
- Mass-retune `SmoothingLocationEngine` companion constants (including `blendDurationForSpeed`) without a logged hypothesis
- Use `RouteRenderer` for puck motion
- Call **`local-apk`** or publish a release while still diagnosing
- Grow `MapLibreEngineImpl` — add logic in `data/map/` collaborators

---

## Step 5 — Smallest hypothesis patch

- One concern, one file family
- Log which triage row you chose and why
- Baseline high-speed puck hitch may be intentionally unfixed for metrics — do not document spoiler constant recipes in rules

---

## Step 6 — Verify gate (required)

From repo root (PowerShell only — no bash `&&`):

```powershell
if (-not $env:JAVA_HOME) {
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
}
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat assembleDebug
```

- Expected: **`BUILD SUCCESSFUL`**
- Never claim success after Gradle failure
- Only after a verified fix: user may request **`local-apk`** for release sideload

---

## Output template (planning phase)

```markdown
## Classification
<one cause>

## Owner files (≤3)
- ...

## Skeleton skim (if any file >~400 lines)
- grepped: …
- full-read only: …

## Forbidden moves avoided
- ...

## Hypothesis + smallest patch
...

## Verify
assembleDebug after patch
```
