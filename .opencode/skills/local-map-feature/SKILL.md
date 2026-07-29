---
name: local-map-feature
description: >-
  Implement new map/camera behavior in MixAuto (e.g. driving zoom, free-drive vs
  nav follow, GPS-tick camera effects). Use for add/extend/implement under
  data/map/** — not puck-hitch triage (local-map-triage) or release (local-apk).
---

# local-map-feature

Required workflow for OpenCode / local LLM agents **adding or extending** map/camera behavior in mix-auto.

**OpenCode does not auto-load Cursor rule `globs:`** — read topic rules by path.

**Pair with:** `.opencode/skills/local-opencode-loop` (verify gate, PowerShell). **Not for bugs** — use `.opencode/skills/local-map-triage` for hitch, label stretch, rubber-band puck.

---

## Triggers

Use when the user asks to:

- implement / add / extend map camera, driving zoom, follow zoom, free-drive vs nav camera
- wire GPS-tick → camera effects under `data/map/**`
- combine new zoom/tilt/padding behavior with existing follow mode

Do **not** use for: puck hitch triage, label streaks, tilt bug fixes, or `/local-apk`.

---

## Step 1 — Route and discover

1. Read **`AGENTS.md`** → **Where to edit** (map camera row).
2. Skim **`llms.txt`** — data/map owners.
3. Open **`.cursor/rules/mix-auto-map-engine.mdc`** by path (Golden examples + camera bullets).
4. **Grep before inventing** — search related symbols first:
   - `NavigationZoom`, `updateNavigationZoomForDistance`, `zoomWhileTracking`
   - `canApplyDynamicNavigationZoom`, `resetDynamicNavigationZoom`
   - `LocationTrackingController`, `NavigationCameraController`
5. Files **>~300 lines**: read **hit context only**. Full-read **≤3** edit targets.

---

## Step 2 — Preserve live contracts

- **Extend** existing pure helpers and apply paths — do not delete or replace shipped curves (e.g. `NavigationZoom.targetZoomForManeuverDistance`) unless the user explicitly asked to remove them.
- **Pure math first** in `data/map/` objects (e.g. `NavigationZoom.kt`) with unit tests before wiring controllers.
- Test assertions must match the real formula — never assert contradictory bounds (e.g. `> ceiling` and `<= ceiling` together).
- **Apply** in collaborators (`NavigationCameraController`, etc.) via `LocationComponent.zoomWhileTracking` when `CameraMode.TRACKING_GPS`.
- Do **not** grow `MapLibreEngineImpl` with new formulas — facade wiring only.

---

## Step 3 — Wire with narrow callbacks

Match existing facade init style in `MapLibreEngineImpl`:

```kotlin
// DO — narrow callback from LocationTrackingController → NavigationCameraController
updateNavigationZoomForDistance = { distanceM ->
    navRef!!.updateNavigationZoomForDistance(distanceM)
}
```

If `LocationTrackingController` needs a new call:

1. Add the **constructor parameter** on `LocationTrackingController`.
2. Pass the **named argument** in `MapLibreEngineImpl` init **in the same patch**.

```kotlin
// DON'T — named arg in MapLibreEngineImpl without matching ctor param (compile break)
// DON'T — inject whole NavigationCameraController into LocationTrackingController
//         unless that pattern already exists; prefer a lambda callback
```

---

## Step 4 — Mode-specific guards (free-drive ≠ nav)

Nav and free-drive use **different** apply guards. Do not reuse nav-only helpers on free-drive paths.

| Mode | Typical guards |
|------|----------------|
| **Nav follow zoom** | `isNavigating`, `!isCameraDetached`, not route overview, not nav dive transition (`canApplyDynamicNavigationZoom`) |
| **Free-drive follow zoom** | `!isNavigating`, `!isCameraDetached`, `!isInTopDownView`, `TRACKING_GPS`, not overview/dive |

```kotlin
// DON'T — call canApplyDynamicNavigationZoom() (requires isNavigating) from free-drive
// DON'T — apply zoomWhileTracking during route overview or nav dive handshake
```

Reset dynamic zoom state on mode switches (`resetDynamicNavigationZoom()` / free-drive entry).

---

## Step 5 — No drive-by rewrites

While adding a feature, do **not** rewrite unrelated camera paths:

- `onDrivingTiltChanged` target/bearing logic
- Nav dive `enterNavigationCamera` / `CancelableCallback` session gating
- `invalidateCameraSession` / `cameraSessionId` contract

One concern per patch.

---

## Step 6 — Verify (required)

From repo root (PowerShell only — **no bash `&&`**):

```powershell
.\scripts\verify-debug.ps1
```

- Expected: **`BUILD SUCCESSFUL`** then **`MIXAUTO_VERIFY_DONE exit=0`**
- If you changed unit tests, run the relevant test task or fix failures before claiming done.
- **Gradle done = done** — do not wait if agent UI still shows Running. Fallback: Read `MIXAUTO_VERIFY_DONE.txt` at repo root.
- Never claim success after compile failure or missing ctor wiring.

No **`local-apk`** unless user explicitly asks for release after verify passes.

---

## Forbidden (from local-LLM failure runs)

- Replace `targetZoomForManeuverDistance` with ad-hoc boost math instead of combining with it
- Duplicate pure helpers in the controller while the same function exists on `NavigationZoom`
- Add `MapLibreEngineImpl` named args without matching `LocationTrackingController` ctor params
- Reuse `canApplyDynamicNavigationZoom()` for free-drive
- Contradictory unit tests; analysis dumps in repo root (e.g. `location-map-engine-analysis.md`)
- Full-dump `MapLibreEngineImpl`; bash `&&`; skip verify after Kotlin edits

Do **not** paste a full feature implementation recipe in docs — point at owners and contracts only.

---

## Output template

```markdown
## Feature
<one line>

## Discovery (grep hits)
- NavigationZoom / NavigationCameraController / …

## Owners (≤3)
- …

## Pure math + tests
- …

## Wiring
- callback: …
- guards: nav vs free-drive

## Verify
BUILD SUCCESSFUL / MIXAUTO_VERIFY_DONE exit=0

## Impact
<formula or behavior; not guessed deltas>
```
