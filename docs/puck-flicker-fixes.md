# Puck flicker — fixes already in the tree

Flicker was still reported on 2026-11-19 after every item below. Read this before changing padding, `applyStyle`, `setCameraMode`, or `forceLocationUpdate`.

Do not re-apply these. Do not retune `blendDurationForSpeed`, `EXTRAPOLATION_MAX_MS`, `EXTRAPOLATION_MAX_M`, or puck offset fractions as a flicker fix. A new change needs a new hypothesis and a log (`MixAutoPuck`, caller string) that names a writer these guards do not already cover.

## Current fix (do not repeat)

**Driving padding dedup ignores map size** (2026-10, v0.1.4).

`DrivingViewportPaddingController.drivingPaddingNeedsUpdate()` used to compare raw `map.width` / `map.height`. A 1px Compose layout jitter (status strip, divider, system bars) looked like a padding change and re-pushed `paddingWhileTracking()` with the same 4dp-quantized offset. MapLibre restarts the `TRACKING_GPS` follow camera on that call, which looks like puck flicker while driving.

Live behavior: dedup on the quantized padding key only (`lastEngagedTrackingPadding` / `lastAppliedTrackingPadding`). No `lastPaddingMapWidth` / `lastPaddingMapHeight`. A real resize still changes the key.

Owner: `DrivingViewportPaddingController.kt`.

## Earlier flicker fixes (also do not repeat)

1. **Resume must not rebuild the puck** (2026-10, v0.1.4). `LocationTrackingController.activateLocationTracking()` skips `applyStyle` and engine recreate when `alreadyActivated`. Resume only sets `isLocationComponentEnabled = true`. `schedulePuckPaddingRestore()` re-applies padding and does not call `forceLocationUpdateForImmediateRender`.

2. **Do not re-enter `TRACKING_GPS` when already there** (2026-08). `NavigationCameraController.engageTrackingGpsWithPuckPadding()` returns after reapplying padding when `cameraMode == TRACKING_GPS`. Calling `setCameraMode(TRACKING_GPS)` again restarts the transition and the puck blinks.

3. **One GPS subscription** (2026-07). `BearingEnrichedLocationEngine.onRawFix` feeds HUD and nav. Do not add a second `requestLocationUpdates` on `rawLocationEngine`. While `shouldSmoothPuckMotion()`, skip `forceLocationUpdateForImmediateRender` (slider path uses `allowDuringSmoothing=true` plus `currentDisplayLocation()`). Suppress bearing-only puck emits while stopped (`SmoothingLocationEngine`).

4. **Layout padding only when the key changes** (2026-07). `handleMapLayoutChange` must not reapply padding on every layout tick. `activateFreeDriveTrackingMode` is a mode change, not a per-fix camera restart.

## Related centering fixes (not flicker, do not reuse them as a flicker patch)

These fixed the puck jumping to screen center. Re-applying them does not stop flicker, and doing the work twice (`applyStyle` or `setCameraMode` after the guard) brings flicker back.

- `ensurePuckAboveOverlays()` re-pushes `paddingWhileTracking` immediately after `applyStyle()`, because `applyStyle()` drops that offset.
- `MapLibreEngineImpl.startFreeDrive()` calls `refreshTrafficOverlay()` before `freeDriveSessionCoordinator.startFreeDrive()`, so the traffic `applyStyle()` is not in the middle of `engageTrackingGpsWithPuckPadding()`.
- `activateLocationTracking()` invalidates the padding cache before the first apply so a pause/resume native reset does not stick. Repeat activation reuses `locationEngine`.
- Do not call `paddingWhileTracking` while a `TRACKING_GPS` transition is in flight (`pendingPuckTrackingTransition`).
- `schedulePuckPaddingRestore()` runs from `MapViewHostCoordinator.onResume()` after `MapView.onResume()`, not from `MainActivity.onResume()` before the native camera is restored.

## Still open

User still sees flicker after the list above. The note from the 2026-08 blink fix is still the unused suspect: `ensurePuckAboveOverlays()` → `component.applyStyle()` from traffic or route restack (`MapStyleController.applyTrafficOverlay`, `RouteRenderer.drawRoute`, `showLighterTrafficAlternate`, `showManeuverAlternate`). That path was not changed as a flicker fix. Confirm it with `MixAutoPuck` logs before editing it. Do not start by changing smoothing or padding constants.
