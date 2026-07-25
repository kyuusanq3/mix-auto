# MixAuto — Agent Guide

Custom Android **Car Launcher** for an **Eonon head unit**. This app replaces the default home screen with a dashboard that adapts to portrait (stacked) or landscape (split) orientation: map, media player, and system app shortcuts.

## Quick facts

| Field | Value |
|-------|-------|
| Package | `com.kyuusanq3.mixauto` |
| Remote | `https://github.com/kyuusanq3/mix-auto.git` |
| UI stack | Jetpack Compose + Material 3 (no XML layouts for main UI) |
| minSdk / targetSdk / compileSdk | 24 / 34 / 34 |
| Kotlin | 1.9.24 |
| Compose Compiler | 1.5.14 (must match Kotlin version) |
| Compose BOM | 2024.06.00 |
| Orientation | Portrait + landscape (`android:screenOrientation="sensor"`) |
| Launcher role | LAUNCHER by default (Navigation App Mode); optional HOME via `LauncherModeAlias` |

## Agent loop (required)

1. **Classify** one primary cause: GPS gap | smoothing lag | camera churn | tile/render hitch | main-thread work | UI-only.
2. **Open** the matching topic rule under `.cursor/rules/` (do not dump all rules). For map puck/camera/labels, open `.opencode/skills/local-map-triage` first.
3. **Grep** the symbol; for files **>~400 lines**, skim signatures / hit context first — full-read only the edit target. Read ≤3 files before proposing a fix.
4. **Smallest change** that tests the hypothesis — do not mass-retune unrelated companion constants.
5. **Never claim success** without `BUILD SUCCESSFUL`. Feed compiler errors back and fix; do not skip the verify gate.
6. **Windows PowerShell only** — no `&&`. Set `JAVA_HOME` before Gradle (see verify gate below).

### Package map

One-line package index for local LLMs: [`llms.txt`](llms.txt) at repo root. Prefer it over dumping large trees into the prompt.

### Where to edit

| Symptom / area | Owner files | Topic rule |
|----------------|-------------|------------|
| Puck / camera / GPS / map style | `SmoothingLocationEngine`, `LocationTrackingController`, `NavigationCameraController`, `MapStyleController` | `mix-auto-map-engine.mdc` |
| Rubber-band / bounce / catch-up puck at speed | `SmoothingLocationEngine`, `LocationTrackingController`, `OffRouteDetector` (nav snap) | `mix-auto-map-engine.mdc` |
| Stretched / streaked / smeared map labels (tilted nav) | `PoiOverlayRenderer`, style pitch alignment / Liberty `poi_*` visibility — **not** Compose / `MapHostViewModel` | `mix-auto-map-engine.mdc` |
| Turn-by-turn, reroute, route line | `NavigationRouteFetcher`, `ConventionalRouteSelector`, `RouteRenderer`, `data/navigation/` | `mix-auto-navigation.mdc` |
| Now playing, album art, audio resume | `MediaPlayerPane`, `AlbumArtDisplay`, `data/media/` | `mix-auto-media.mdc` |
| Dashboard layout, panels, search UI | `DashboardScreen`, `ui/components/`, `LauncherViewModel` | `mix-auto-dashboard-ui.mdc` |
| Offline POI DB, offline map tiles | `LocalPlacesRepository`, `OfflineMapRepository`, `MapDataOverlay` | `mix-auto-places.mdc` |
| Build, release APK, emulator GPS | Gradle, `avd-gps.ps1`, `.opencode/skills/local-apk` | `mix-auto-build-release.mdc` |

## Build

**Android Studio (recommended):**

1. Open project root in Android Studio
2. **File → Settings → Build Tools → Gradle → Gradle JDK** → Embedded JDK / jbr-17
3. Gradle Sync, then **Build → Make Project** (`Ctrl+F9`)

**Command line (Windows) — verify gate (required after code changes):**

From repo root (agent shells are usually already there). Do not hardcode personal clone paths in docs or commands.

```powershell
# Set JAVA_HOME to Android Studio's embedded JBR if unset.
if (-not $env:JAVA_HOME) {
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
}
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat assembleDebug
```

Expected: `BUILD SUCCESSFUL`. APK output: `app/build/outputs/apk/debug/app-debug.apk`

Release sideload copy: `mix-auto.apk` at project root (see `.cursor/rules/mix-auto-build-release.mdc` for signing/release workflow).

## Project layout

```
app/src/main/java/com/kyuusanq3/mixauto/
├── MainActivity.kt              # MapHostViewModel; location permission launcher
├── ui/map/
│   └── MapHostViewModel.kt      # Activity-scoped map engine + nav TTS + places repo (survives rotation)
├── domain/map/
│   ├── CarMapEngine.kt          # Swappable map contract
│   ├── MapUiState.kt            # Speed, street, navigation HUD state
│   └── SearchResultPlace.kt     # Destination search result
├── data/map/
│   ├── MapLibreEngineImpl.kt    # CarMapEngine facade; delegates to controllers below
│   ├── LocationTrackingController.kt / NavigationCameraController.kt
│   ├── MapStyleController.kt / RouteRenderer.kt / ConventionalRouteSelector.kt / …
├── data/places/
│   ├── LocalPlacesRepository.kt # Offline Overture POI SQLite search + HTTP download
│   └── LocalDbMeta.kt           # Installed database metadata
└── ui/
    ├── components/
    │   ├── CarMapViewContainer.kt       # AndroidView bridge + HUD + search button
    │   ├── NavigationSearchOverlay.kt   # Destination search dialog (Recent/Saved/Nearby tabs)
    │   ├── PoiDetailDrawer.kt           # Full-screen POI/dropped-pin details + star + navigate
    │   └── MapDataOverlay.kt            # Offline Overture POI download UI
    ├── theme/                   # Color, CarDimensions, Type, Theme
    ├── onboarding/
    │   └── OnboardingWizard.kt  # First-run / update permission wizard (location, notification access, mic)
    └── dashboard/
        ├── DashboardScreen.kt   # Map + media + shortcut dock layouts; draggable map/media divider; settings overlay
        ├── MediaPlayerPane.kt   # Now playing UI (media session driven; source-app icon + transport row; album art gestures + mode picker)
        ├── AlbumArtDisplay.kt   # Plain / Vinyl / Visualizer modes + Galaxy Watch-style picker carousel
        └── ShortcutDock.kt      # System app shortcuts
```

## Architecture

```
MainActivity (MapHostViewModel — mapEngine, navigationVoice, localPlaces)
  └── MixAutoTheme
        └── DashboardScreen(mapEngine, mapDataViewModel)
              ├── CarMapViewContainer — map pane (60% or CarDimensions.MapWeight)
              ├── MediaPlayerPane + ShortcutDock (Map Data on map toolbar; Launcher settings in app drawer)
              ├── PoiDetailDrawer — full-screen overlay when map POI/pin selected
              ├── Lighter-traffic chip in CarMapViewContainer when TomTom alternate qualifies
              └── MapDataOverlay — offline POI country download

LauncherViewModel — activePanel, poiReturnToSearch, destinationSearchState (rotation-safe UI)
```

Swap map provider: change `MapHostViewModel` to construct a new `CarMapEngine` implementation.

## Conventions for agents

1. **Stay on Compose** — do not reintroduce XML layouts for dashboard UI unless explicitly requested.
2. **Use theme tokens** — colors from `Color.kt`, sizes from `CarDimensions.kt`, text via `Car*Text` composables.
3. **Keep theme separate from layout** — dashboard logic in `ui/dashboard/`, theme in `ui/theme/`.
4. **Match Kotlin ↔ Compose Compiler** — if bumping Kotlin, update `composeCompiler` in `gradle/libs.versions.toml` using the [official compatibility map](https://developer.android.com/jetpack/androidx/releases/compose-kotlin).
5. **Eonon-specific packages** — radio/Bluetooth package names may differ by firmware; extend the shortcut target resolution in `ShortcutDock.kt` rather than hardcoding in UI composables.
6. **No commits unless asked** — user prefers explicit commit requests.
7. **No Gradle multi-module split** — this app stays a single `:app` module; decompose within packages, not new Gradle modules.
8. **Soft file size for new map/UI collaborators** — prefer **under ~400 lines**; do not grow `MapLibreEngineImpl`; extract only when a class has a clear second responsibility. Do **not** blanket-rewrite existing controllers to a hard 150-line limit.
9. **Explicit return types** on new `public`/`internal` APIs; skip mass-annotating private helpers.
10. **Golden examples** in topic rules (especially `mix-auto-map-engine.mdc`) beat long prose — match those snippets when patching.

## Planned / not yet implemented

- Release signing / Play Store config (personal sideload signing already in place, see `mix-auto-build-release.mdc`)

## Detailed rules (topic-scoped, load automatically by file glob)

This guide stays high-level on purpose — implementation lessons, gotchas, and troubleshooting live in topic-scoped Cursor rules so only the relevant ones load for the file you're editing:

- `.cursor/rules/mix-auto-core.mdc` (always applied) — stack, theme rules, shortcut packages, manifest
- `.cursor/rules/mix-auto-build-release.mdc` (always applied) — build, signing, release, emulator GPS tooling
- `.cursor/rules/mix-auto-map-engine.mdc` — camera, GPS/puck, route rendering, style/traffic overlay, custom pins, search origin (`data/map/**`, `domain/map/**`)
- `.cursor/rules/mix-auto-navigation.mdc` — turn-by-turn TTS, reroute, Conventional OSRM routing, in-nav lighter-traffic alternate (`data/navigation/**`, `ConventionalRouteSelector.kt`, `NavigationRouteFetcher.kt`, `RouteRenderer.kt`)
- `.cursor/rules/mix-auto-media.mdc` — media session, album art, audio source picker (`MediaPlayerPane.kt`, `AlbumArtDisplay.kt`, `data/media/**`)
- `.cursor/rules/mix-auto-dashboard-ui.mdc` — dashboard layout, shortcut dock, panels, status bar, onboarding, app drawer (`ui/dashboard/**`, `ui/components/**`)
- `.cursor/rules/mix-auto-places.mdc` — offline Overture POI database + offline map tile regions (`data/places/**`, `MapDataOverlay.kt`)

## Related agent resources

- Package index (local LLM): [`llms.txt`](llms.txt)
- Session archive: `C:/dev/skills/session-history/mix-auto/`
- OpenCode map triage (puck hitch, label stretch, nav camera): `.opencode/skills/local-map-triage` — open before editing `data/map/**`

## Lessons learned

- **Audio Settings drawer:** `ActivePanel.AUDIO_SETTINGS` — overflow ⋮ in `MediaPlayerPane`; 60% pane / 40% map split (`isSplitLockedForOverlay`); map tap dismiss via `setMapTapDismissHandler(onDismissPanel)` in `DashboardScreen.kt`; panel in `ui/components/AudioSettingsPanel.kt`
- **Startup / manual audio resume:** `MediaSessionRepository.ensureDefaultPlayerIfNeeded()` runs once per process at boot; `attemptResumeNow()` runs when Audio Settings closes (`DisposableEffect` in `AudioSettingsPanelContent`) — not gated by `hasAttemptedBootLaunch`; fallback link via `BackgroundAudioLauncher.launchFallbackResumeLink()` (`ACTION_VIEW`); plain web share URLs may open the app without autoplay
- **Album art gestures:** When `showAlbumArtControls` is off (default), Info button in media header shows gesture help dialog; any value read inside `pointerInput` that is not a key must use `rememberUpdatedState` — `albumArtMode` in long-press had stale-closure bug
- **LLM-friendly map layout (2026-07):** `MapLibreEngineImpl` is a facade (~1.7k lines after 2026-07 extraction) — add map/GPS/camera logic in `data/map/` collaborators (`LocationTrackingController`, `NavigationCameraController`, `MapStyleController`, `RouteRenderer`, `PoiOverlayRenderer`, `OffRouteDetector`, `NavigationSessionCoordinator`, `MapInteractionController`, `PoiQueryCoordinator`, etc.) via callback injection; do not grow the engine class again. Cross-cutting flags (`isCameraDetached`, `hasSnappedCameraToGps`, nav state) stay on the engine and pass through getters/setters.
- **Map facade extraction (2026-07):** Pulled search origin, Photon fetch, POI query, lighter-traffic helper, map tap/custom pin, nav session orchestration, and offline catalog/metadata into dedicated `data/map/` files; `OfflineMapRepository` slimmed to download/observe only. Opportunistic splits only — no hard 150-line rewrite of camera/GPS controllers.
- **LLM-friendly rules (2026-07):** Tribal knowledge lives in topic-scoped `.cursor/rules/mix-auto-*.mdc` with `globs:` — only `mix-auto-core.mdc` and `mix-auto-build-release.mdc` are always applied. Edit the scoped rule for the subsystem you touch; keep `AGENTS.md` as layout + architecture + pointers.
- **Detekt size guardrails:** `config/detekt/detekt.yml` enforces `LargeClass` / `TooManyFunctions` / `LongMethod` on new code; existing debt in `config/detekt/baseline.xml` — regenerate baseline only when intentionally accepting new size debt.
- **Dashboard UI decomposition:** Portrait/landscape dock layouts in `DashboardLayouts.kt`; secondary pane in `DashboardSecondaryPane.kt`; shared props holders avoid repeating huge argument lists across three layout branches.
- **Destination search empty flicker (2026-07):** Typed search in `NavigationSearchOverlay.kt` must not flash "No results found" during debounce or Photon — hoist `isSearching`/`isLoadingRemote` on `DestinationSearchUiState` (not `remember` in overlay); set pending **before** `delay(300)`; do not clear `results` on re-query; in `LaunchedEffect` `finally`, clear loading only when `coroutineContext.isActive` (`import kotlinx.coroutines.isActive`) so cancelled keystrokes do not wipe the new effect's flags; see `mix-auto-dashboard-ui.mdc` destination search session bullet
- **Conventional routing (2026-07):** Primary nav uses `ConventionalRouteSelector` among OSRM `alternatives=3` (+12% duration cap) — no route picker; `startNavigation()` → `applyActiveRoute(conventional)` → `showRouteThenDive()`. Reroute uses same scorer. **Regression note:** July 20 v0.0.34 `NavigationRouteFetcher` extraction accidentally reverted July 6 Conventional work — when extracting from `MapLibreEngineImpl`, preserve scorer wiring and `alternatives=3`, not just fetch/parse helpers.
- **In-nav lighter-traffic alternate (2026-07):** Parallel TomTom fetch on initial navigate; if geometry differs and ETA/traffic qualifies, grey line via `RouteRenderer.showLighterTrafficAlternate()` + `MapUiState.lighterTrafficAlternateActive` chip in `CarMapViewContainer`; tap chip or `ROUTE_TOMTOM_LAYER_ID` hit → `switchToLighterTrafficAlternate()`. Cleared on free-drive/reroute/switch. No `RoutePickerPane` / `ActivePanel.ROUTE_PICKER`.
- **Driving camera tilt slider (2026-07):** Map Settings **Driving View** **Tilt** slider (20–60°, default **40°**, pref `driving_tilt`) — same wiring as Zoom: `LauncherPreferences` → `LauncherViewModel` → `MainActivity` → `CarMapEngine.setDrivingTilt()` → `NavigationCameraController` injectable `freeDriveTilt()` / `navTilt()` (nav = free-drive **+ 10°**); live apply via `animateCamera` (free-drive) or `onDrivingTiltChanged()` (nav); top-down/route overview stay 0°; do not use `tiltWhileTracking` during nav dive handshake
- **Idle media manual-play fallbacks (2026-07):** When `!hasActiveSession`, `MediaPlayerPane` shows tappable **Play the album manually** if `audioFallbackResumeLink` is set, else default player icon + **Start music on the player manually**; manual taps use local `launchManualFallbackLink()` (`ACTION_VIEW` only) — not `BackgroundAudioLauncher.launchFallbackResumeLink()` (boot/resume refocuses Mix Auto). See `mix-auto-media.mdc`.
- **Close parallel-street reroute / stuck greying (2026-07):** Urban parallels are often 20–40 m — old **75 m** off-route + **40 m** road-snap caused TTS “recalculating” with no visible refresh (OSRM snapped start back) and a stuck cyan traveled line. Use **35 m** / 4 confirms, snap **18/22 m**, reroute OSRM `radiuses=25;unlimited` + bearings (`NavigationRouteFetcher.buildOsrmRouteUrl`), unconstrained fallback; `updateRouteProgress` on **raw** GPS with freeze `>25 m` and on-route resync `≤18 m` (`decideRouteProgressUpdate`). Details in `mix-auto-navigation.mdc` / `mix-auto-map-engine.mdc`; tests in `OffRouteParallelStreetTest.kt`.
- **Map pan / End-nav crash races (2026-07):** Intermittent MapLibre crashes when panning during nav dive/overview or tapping End nav — dive `CancelableCallback` was re-engaging `TRACKING_GPS` after detach, and free-drive snap did not `cancelTransitions`. Fix: `NavigationCameraController.cameraSessionId` + `invalidateCameraSession()` / `prepareForFreeDriveCamera()`; gate `activateNavigationTracking` on navigating + `!isCameraDetached`; `MapLibreEngineImpl.mapReleased` + `withMapStyle` for post-teardown style callbacks. Full contract in `mix-auto-map-engine.mdc` camera-session bullet.
- **Local LLM agent loop (2026-07):** `AGENTS.md` carries always-on classify→grep≤3 files→smallest patch→`BUILD SUCCESSFUL` loop plus **Where to edit** table; OpenCode may not load Cursor rule `globs:` — open topic rules by path when needed. Committed docs/skills must not hardcode personal clone paths; verify gate = conditional `JAVA_HOME` + `.\gradlew.bat assembleDebug` from repo root (see `mix-auto-build-release.mdc`).
- **Local LLM docs shape (2026-07):** Prefer golden Kotlin DO/DON'T snippets in topic rules over long bullet lists; use root `llms.txt` as package index; soft under-~400-line budget for new collaborators (not a hard 150-line rewrite of existing debt); for files over ~400 lines, signature/grep skim before full body (`local-map-triage` Step 3b).
- **OpenCode local-LLM failure modes (2026-07):** Sample run on high-speed puck hitch retuned FPS/`LOCATION_ENGINE_*`/many `SmoothingLocationEngine` companions without diagnosis, misused `RouteRenderer` for puck glide, used bash `&&` and skipped `JAVA_HOME`, then claimed success after failed Gradle — anti-patterns now in `mix-auto-core.mdc` and topic-rule **Agent front matter**. Baseline hitch intentionally left unfixed for metrics; do not document a spoiler fix in rules.
- **Reject bad local-LLM plans:** Wrong owners include `MapHostViewModel` font metrics for map label stretch, camera position bounds for puck rubber-banding, TomTom alternate mode for puck bounce, or calling `/local-apk` during diagnosis/planning. Use `.opencode/skills/local-map-triage` and `mix-auto-map-engine.mdc` front matter + **Golden examples** instead.
