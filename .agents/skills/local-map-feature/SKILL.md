---
name: local-map-feature
description: >-
  Implement new map/camera behavior in MixAuto (e.g. driving zoom, free-drive vs
  nav follow, GPS-tick camera effects). Use for add/extend/implement under
  data/map/** — not puck-hitch triage (local-map-triage) or release (local-apk).
---

# local-map-feature

Required workflow for local agents (OpenCode / Zed) **adding or extending** map/camera behavior in mix-auto.

**Rule globs do not auto-load** — read `.cursor/rules/` topic rules by path (line ranges if outline-only).

**Pair with:** `local-opencode-loop` (verify gate). **Not for bugs** — use `local-map-triage` for hitch, label stretch, rubber-band puck.

---

## Triggers

Use when the user asks to:

- implement / add / extend map camera, driving zoom, follow zoom, free-drive vs nav camera
- wire GPS-tick → camera effects under `data/map/**`
- combine new zoom/tilt/padding behavior with existing follow mode

Do **not** use for: puck hitch triage, label streaks, tilt bug fixes, or `/local-apk`.

---

## Stage multi-file camera features (required for ≤30B)

**One turn = one owner family.** Do not stitch math + free-drive + nav combine in one session.

| Turn | Deliverable | Touch only | Verify |
|------|-------------|------------|--------|
| **A** | Pure formula + unit tests | `NavigationZoom.kt` + `NavigationZoomTest.kt` | `verify-debug.ps1 -UnitTestCompile` (assemble alone misses broken `*Test.kt`) |
| **B** | Free-drive apply | `NavigationCameraController.updateDrivingZoomForSpeed` body (call site **already wired**) | `verify-debug.ps1` |
| **C** | Nav combine | `updateNavigationZoomForDistance` → `min(speed, maneuver)`; keep `targetZoomForManeuverDistance` | `verify-debug.ps1` |

Stop and verify after each turn. Escalate to next turn only after `MIXAUTO_VERIFY_DONE exit=0` **and** proof symbols exist in the tree (see Step 6b).

---

## Follow-zoom recipe card (speed + nearer turn)

Copy into the local prompt; fill blanks. Do **not** invent road class / street length / HUD speed bugs.

```text
EDIT Turn A: NavigationZoom.kt — append targetZoomForSpeed AFTER shouldApplyZoomChange (never nest inside targetZoomForManeuverDistance)
TEST: NavigationZoomTest.kt — speed curve + hysteresis; Turn C also min(speed,maneuver)
EDIT Turn B: NavigationCameraController.updateDrivingZoomForSpeed — applyFollowZoom(targetZoomForSpeed(...)); guards = canApplyFreeDriveFollowZoom (already exists)
EDIT Turn C: updateNavigationZoomForDistance — min(speedZoom, maneuverZoom); use lastSpeedMpsForZoom
ALREADY WIRED: LocationTrackingController → updateDrivingZoomForSpeed(newSpeedMps) next to lookahead padding; MapLibreEngineImpl named arg
CEILING: freeDriveZoom() / navZoom() from Driving Zoom slider — do not remove prefs
DO NOT EDIT: SmoothingLocationEngine, onDrivingTiltChanged, MapHostViewModel, currentSpeed HUD
VERIFY Turn A: powershell.exe -NoProfile -ExecutionPolicy Bypass -File "./scripts/verify-debug.ps1" -UnitTestCompile
VERIFY Turn B/C: powershell.exe -NoProfile -ExecutionPolicy Bypass -File "./scripts/verify-debug.ps1"
PROOF Turn A: fun targetZoomForSpeed in NavigationZoom.kt; class/object closes; tests call it
PROOF Turn B: applyFollowZoom(NavigationZoom.targetZoomForSpeed in updateDrivingZoomForSpeed
PROOF Turn C: minOf(speedZoom, maneuverZoom) inside updateNavigationZoomForDistance
```

Apply path: private `applyFollowZoom(target)` — hysteresis + `TRACKING_GPS` + `zoomWhileTracking`. Call it; do not duplicate that block.

---

## Step 1 — Route and discover

1. Read **`AGENTS.md`** → **Where to edit** (map camera row).
2. Skim **`llms.txt`** — data/map owners + follow-zoom hooks.
3. Open **`.cursor/rules/mix-auto-map-engine.mdc`** by path/line range (Golden examples + camera bullets; do not stop at outline).
4. **Grep before inventing** — search related symbols first:
   - `NavigationZoom`, `updateNavigationZoomForDistance`, `updateDrivingZoomForSpeed`, `applyFollowZoom`
   - `canApplyDynamicNavigationZoom`, `canApplyFreeDriveFollowZoom`, `resetDynamicNavigationZoom`
   - `LocationTrackingController`, `NavigationCameraController`
5. Files **>~300 lines**: read **hit context only**. Full-read **≤3** edit targets.

---

## Step 2 — Preserve live contracts

- **Extend** existing pure helpers and apply paths — do not delete or replace shipped curves (e.g. `NavigationZoom.targetZoomForManeuverDistance`) unless the user explicitly asked to remove them.
- **Pure math first** in `data/map/` objects (e.g. `NavigationZoom.kt`) with unit tests before wiring controllers.
- Test assertions must match the real formula — never assert contradictory bounds (e.g. `> ceiling` and `<= ceiling` together).
- **Apply** via `applyFollowZoom` / `zoomWhileTracking` when `CameraMode.TRACKING_GPS`.
- Do **not** grow `MapLibreEngineImpl` with new formulas — facade wiring only (speed callback already present).

---

## Step 3 — Wire with narrow callbacks

Speed zoom call site is **pre-wired**:

```kotlin
// LocationTrackingController (next to lookahead) — already calls:
updateDrivingZoomForSpeed(newSpeedMps)

// MapLibreEngineImpl — already:
updateDrivingZoomForSpeed = { speedMps -> navRef!!.updateDrivingZoomForSpeed(speedMps) }
```

Fill the **body** of `NavigationCameraController.updateDrivingZoomForSpeed` — do not re-add ctor params.

If a *new* GPS→camera callback is needed later:

1. Add the **constructor parameter** on `LocationTrackingController`.
2. Pass the **named argument** in `MapLibreEngineImpl` init **in the same patch**.

```kotlin
// DON'T — named arg in MapLibreEngineImpl without matching ctor param (compile break)
// DON'T — inject whole NavigationCameraController into LocationTrackingController
```

---

## Step 4 — Mode-specific guards (free-drive ≠ nav)

| Mode | Helper |
|------|--------|
| **Nav follow zoom** | `canApplyDynamicNavigationZoom` |
| **Free-drive follow zoom** | `canApplyFreeDriveFollowZoom` |

```kotlin
// DON'T — call canApplyDynamicNavigationZoom() from free-drive
// DON'T — apply zoomWhileTracking during route overview or nav dive handshake
```

Reset via `resetDynamicNavigationZoom()` on free-drive entry.

---

## Step 5 — No drive-by rewrites

While adding a feature, do **not** rewrite unrelated camera paths:

- `onDrivingTiltChanged` target/bearing logic
- Nav dive `enterNavigationCamera` / `CancelableCallback` session gating
- `invalidateCameraSession` / `cameraSessionId` contract
- `SmoothingLocationEngine` / puck HUD `currentSpeed`

One concern per patch.

---

## Step 6 — Verify (required)

From repo root — one command, no `&&` (bash-safe for Zed).

**Turn A / any `app/src/test/**` edit:**

```bash
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "./scripts/verify-debug.ps1" -UnitTestCompile
```

**Turn B / C (no test edits):**

```bash
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "./scripts/verify-debug.ps1"
```

Native PowerShell: `./scripts/verify-debug.ps1` (add `-UnitTestCompile` for Turn A).

- Expected: **`BUILD SUCCESSFUL`** then **`MIXAUTO_VERIFY_DONE exit=0`**
- Turn A stamp should include `unitTestCompile=true` — bare assembleDebug does **not** catch missing `}` in `*Test.kt`
- Never bare `./gradlew.bat` without JAVA_HOME — use the script
- **Gradle done = done** — do not wait if agent UI still shows Running. Fallback: Read `MIXAUTO_VERIFY_DONE.txt` at repo root.
- Never claim success after compile failure or missing ctor wiring.

No **`local-apk`** unless user explicitly asks for release after verify passes.

---

## Step 6b — Proof before claiming the turn done

If **any** `Edit … failed` / `Could not find oldString` appears, or the proof symbol for this turn is absent, the turn **failed** even when verify exit=0 (false success). Re-read the owner file; do not advance to the next turn. Paste the proof lines in the final message.

| Turn | Must exist in tree |
|------|--------------------|
| A | `fun targetZoomForSpeed` + closed `NavigationZoom` / `NavigationZoomTest` |
| B | `targetZoomForSpeed` call inside `updateDrivingZoomForSpeed` via `applyFollowZoom` |
| C | `minOf(speedZoom, maneuverZoom)` in `updateNavigationZoomForDistance` |

---

## Forbidden (from local-LLM failure runs)

- Replace `targetZoomForManeuverDistance` with ad-hoc boost math instead of combining with it
- Nest a new function **inside** `targetZoomForManeuverDistance` (append after `shouldApplyZoomChange`)
- Duplicate `applyFollowZoom` / `zoomWhileTracking` blocks in the controller
- Re-wire `updateDrivingZoomForSpeed` ctor when the callback already exists
- Reuse `canApplyDynamicNavigationZoom()` for free-drive
- Contradictory unit tests; analysis dumps in repo root
- Full-dump `MapLibreEngineImpl`; bash `&&`; skip verify after Kotlin edits
- Multi-turn feature (A+B+C) in one unattended session
- Claiming Turn done after failed Edit or without proof symbols (verify alone is not enough)
- Turn A verify without `-UnitTestCompile`

---

## Output template

```markdown
## Feature
<one line>

## Turn
A | B | C

## Discovery (grep hits)
- NavigationZoom / updateDrivingZoomForSpeed / …

## Owners (≤3)
- …

## Pure math + tests
- …

## Wiring
- filled existing updateDrivingZoomForSpeed body / …
- guards: canApplyFreeDriveFollowZoom vs canApplyDynamicNavigationZoom

## Proof
<paste live lines: targetZoomForSpeed / applyFollowZoom+speed / minOf>

## Verify
BUILD SUCCESSFUL / MIXAUTO_VERIFY_DONE exit=0
(Turn A: unitTestCompile=true)

## Impact
<formula or behavior; not guessed deltas>
```
