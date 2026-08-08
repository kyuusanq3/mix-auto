# MixAuto � Agent Guide

## Local agent cold start (Zed / OpenCode, ?30B � short user prompts)

User messages may be one line. Do **not** ask which component.

**Zed Agent:** profile **MixAuto Write** (fixes) or **MixAuto Ask** (plan) � both disable `list_directory` (empty `.` listings stall local models). Model: `mixauto-qwen3-coder:30b`. First tool: `skill` / slash � `/local-opencode-loop` then `/local-map-triage` or `/map-bug <symptom>`; plan ? `/map-plan`. Skills: `.agents/skills/` (junctions to `.opencode/skills/`). See [`.zed/README.md`](.zed/README.md).

**OpenCode:** `skill` ? `local-opencode-loop`; map bug ? `local-map-triage`; new map/camera feature ? `local-map-feature`; commands `/map-bug` / `/map-plan`.

**Always-on (both):** read [`llms.txt`](llms.txt) for package index. One class per turn. **Do not** `list_directory` on `.` (if empty, never retry � use `grep` / `find_path` / `read_file` / `terminal`). Verify (bash-safe for Zed Git Bash): `powershell.exe -NoProfile -ExecutionPolicy Bypass -File "./scripts/verify-debug.ps1"` (no `cd`, no `&&`; never `.\scripts\�`). Stop on `MIXAUTO_VERIFY_DONE`. Label stretch: `"./scripts/run-tool.ps1" check_driving_text_pitch` / `fix_driving_text_pitch` � layout pitch only, never layer root. Large rules: outline ? next read **must** set `start_line`/`end_line` (map-engine L9�52). Repo paths from **worktree root**. Qwen tools: full `<tool_call><function=�>�</function></tool_call>` (`mixauto-qwen3-coder:*` via `.\scripts\create-mixauto-ollama-models.ps1`). Never create existing `.cursor/rules` files.

Then follow **Agent loop** below.

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
2. **Open** the matching topic rule under `.cursor/rules/` (do not dump all rules). For map puck/camera/label **bugs**, open `.opencode/skills/local-map-triage` first. For map/camera **features** (implement / add / extend), open `.opencode/skills/local-map-feature`.
3. **Grep** the symbol; for files **>~300 lines**, skim signatures / hit context first � full-read only the edit target. Read ?3 files before proposing a fix.
4. **Smallest change** that tests the hypothesis � do not mass-retune unrelated companion constants.
5. **Never claim success** without `BUILD SUCCESSFUL`. Feed compiler errors back and fix; do not skip the verify gate.
6. **Shell:** one command, no `&&`. Prefer bash-safe `powershell.exe -NoProfile -ExecutionPolicy Bypass -File "./scripts/verify-debug.ps1"` (Zed Git Bash). Native PowerShell: `./scripts/verify-debug.ps1`. Never `.\scripts\�` under bash.

**Local ?30B (OpenCode / Zed + deepseek/qwen):** load `local-opencode-loop` first. Map **bugs** ? `local-map-triage`; map **features** ? `local-map-feature`. Short form: **Where to edit** ? skill/rule by path (line ranges if outline-only) ? grep ? one-concern patch ? **proof-grep goal symbols** ? bash-safe verify ? stop on `MIXAUTO_VERIFY_DONE` ? report impact with real formula (nav = free-drive + `NAV_TILT_OFFSET`). `BUILD SUCCESSFUL` alone ? feature landed; failed `Edit`/`oldString` ? do not claim done.

### Package map

One-line package index for local LLMs: [`llms.txt`](llms.txt) at repo root. Prefer it over dumping large trees into the prompt.

### Where to edit

| Symptom / area | Owner files | Topic rule |
|----------------|-------------|------------|
| developer toggles | `ui/settings/DeveloperSettings.kt` | `mix-auto-core.mdc` |
| Puck / camera / GPS / map style | `SmoothingLocationEngine`, `LocationTrackingController`, `NavigationCameraController`, `MapStyleController` | `mix-auto-map-engine.mdc` |
| GPS acquisition / permission retry | `LocationAcquisitionHelper` | `mix-auto-map-engine.mdc` |
| Nav step advance / arrival / off-route tick | `NavigationProgressEvaluator`, `OffRouteDetector` | `mix-auto-map-engine.mdc` / `mix-auto-navigation.mdc` |
| Camera padding / lookahead | `DrivingViewportPaddingController` | `mix-auto-map-engine.mdc` |
| Top-down / POI preview camera | `TopDownPoiCameraController` | `mix-auto-map-engine.mdc` |
| Free drive / recenter sequencing | `FreeDriveSessionCoordinator` | `mix-auto-map-engine.mdc` |
| POI selection / saved pins / custom pin | `PoiSelectionController`, `MapInteractionController` | `mix-auto-map-engine.mdc` |
| TomTom traffic / incidents / route jam | `TomTomTrafficClient`, `TomTomIncidentHeadlines`, `TomTomRouteJamFinder` | `mix-auto-map-engine.mdc` |
| Nav-mode hide traffic raster / congestion-tinted route | `MapLibreEngineImpl` (`effectiveTrafficVisible`), `TomTomRoutingClient` (`sectionType=traffic`), `RouteCongestion`, `RouteRenderer` remaining line | `mix-auto-map-engine.mdc` / `mix-auto-navigation.mdc` |
| Rubber-band / bounce / catch-up puck at speed | `SmoothingLocationEngine`, `LocationTrackingController`, `OffRouteDetector` (nav snap) | `mix-auto-map-engine.mdc` |
| Stretched / streaked / smeared map labels (tilted nav) | **Nav:** `powershell.exe -File "./scripts/run-tool.ps1" check_driving_text_pitch` / `fix_driving_text_pitch` on [`mix-auto-driving.json`](app/src/main/assets/map/mix-auto-driving.json) (layout pitch � never layer root; never `python -c`; never `.\scripts\` in Git Bash). **Detached pan only:** `PoiOverlayRenderer` (`poiTextOnly*` = VIEWPORT) � **not** Compose / `MapHostViewModel` | `mix-auto-map-engine.mdc` |
| Turn-by-turn, reroute, route line | `NavigationRouteFetcher`, `ConventionalRouteSelector`, `RouteRenderer`, `data/navigation/` | `mix-auto-navigation.mdc` |
| Now playing, album art, audio resume | `MediaPlayerPane`, `AlbumArtDisplay`, `data/media/` | `mix-auto-media.mdc` |
| Dashboard layout, panels, search UI | `DashboardScreen`, `ui/components/`, `LauncherViewModel` | `mix-auto-dashboard-ui.mdc` |
| Offline POI DB, offline map tiles | `LocalPlacesRepository`, `OfflineMapRepository`, `OfflineRegionDownloadSession`, `OfflineProgressFormatting`, `MapDataOverlay` | `mix-auto-places.mdc` |
| Debug verify (`assembleDebug`) | `powershell.exe -File "./scripts/verify-debug.ps1"`; after test / Turn A edits add `-UnitTestCompile` | `mix-auto-build-release.mdc` |
| GitHub release / signed APK (not debug) | `.opencode/skills/local-apk` (`assembleRelease` ? `mix-auto.apk` ? optional publish) | `mix-auto-build-release.mdc` |
| Emulator GPS | `avd-gps.ps1` | `mix-auto-build-release.mdc` |

## Build

**Android Studio (recommended):**

1. Open project root in Android Studio
2. **File ? Settings ? Build Tools ? Gradle ? Gradle JDK** ? Embedded JDK / jbr-17
3. Gradle Sync, then **Build ? Make Project** (`Ctrl+F9`)

**Command line (Windows) � verify gate (required after code changes):**

From repo root (agent shells are usually already there). Do not hardcode personal clone paths in docs or commands.

Native PowerShell:

```powershell
./scripts/verify-debug.ps1
```

Zed / Git Bash (required � backslash `.\scripts\�` paths get mangled):

```bash
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "./scripts/verify-debug.ps1"
```

Fallback inline (native PowerShell):

```powershell
# Set JAVA_HOME to Android Studio's embedded JBR if unset.
if (-not $env:JAVA_HOME) {
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
}
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
./gradlew.bat assembleDebug
```

Expected: `BUILD SUCCESSFUL` then `MIXAUTO_VERIFY_DONE exit=0`. APK output: `app/build/outputs/apk/debug/app-debug.apk`. Script also writes gitignored `MIXAUTO_VERIFY_DONE.txt` at repo root. If the agent UI still shows Running after those lines (or the stamp says `exit=0`), treat the build as finished � do not wait or re-run.

Release sideload copy: `mix-auto.apk` at project root (see `.cursor/rules/mix-auto-build-release.mdc` for signing/release workflow).

## Project layout

```
app/src/main/java/com/kyuusanq3/mixauto/
??? MainActivity.kt              # MapHostViewModel; location permission launcher
??? ui/map/
?   ??? MapHostViewModel.kt      # Activity-scoped map engine + nav TTS + places repo (survives rotation)
??? domain/map/
?   ??? CarMapEngine.kt          # Swappable map contract
?   ??? MapUiState.kt            # Speed, street, navigation HUD state
?   ??? SearchResultPlace.kt     # Destination search result
??? data/map/
?   ??? MapLibreEngineImpl.kt    # CarMapEngine facade; delegates to controllers below
?   ??? LocationTrackingController.kt / NavigationCameraController.kt
?   ??? MapStyleController.kt / RouteRenderer.kt / ConventionalRouteSelector.kt / �
??? data/places/
?   ??? LocalPlacesRepository.kt # Offline Overture POI SQLite search + HTTP download
?   ??? LocalDbMeta.kt           # Installed database metadata
??? ui/
    ??? components/
    ?   ??? CarMapViewContainer.kt       # AndroidView bridge + HUD + search button
    ?   ??? NavigationSearchOverlay.kt   # Destination search dialog (Recent/Saved/Nearby tabs)
    ?   ??? PoiDetailDrawer.kt           # Full-screen POI/dropped-pin details + star + navigate
    ?   ??? MapDataOverlay.kt            # Offline Overture POI download UI
    ??? theme/                   # Color, CarDimensions, Type, Theme
    ??? onboarding/
    ?   ??? OnboardingWizard.kt  # First-run / update permission wizard (location, notification access, mic)
    ??? dashboard/
        ??? DashboardScreen.kt   # Map + media + shortcut dock layouts; draggable map/media divider; settings overlay
        ??? MediaPlayerPane.kt   # Now playing UI (media session driven; source-app icon + transport row; album art gestures + mode picker)
        ??? AlbumArtDisplay.kt   # Plain / Vinyl / Visualizer modes + Galaxy Watch-style picker carousel
        ??? ShortcutDock.kt      # System app shortcuts
```

## Architecture

```
MainActivity (MapHostViewModel � mapEngine, navigationVoice, localPlaces)
  ??? MixAutoTheme
        ??? DashboardScreen(mapEngine, mapDataViewModel)
              ??? CarMapViewContainer � map pane (60% or CarDimensions.MapWeight)
              ??? MediaPlayerPane + ShortcutDock (Map Data on map toolbar; Launcher settings in app drawer)
              ??? PoiDetailDrawer � full-screen overlay when map POI/pin selected
              ??? Lighter-traffic chip in CarMapViewContainer when TomTom alternate qualifies
              ??? MapDataOverlay � offline POI country download

LauncherViewModel � activePanel, poiReturnToSearch, destinationSearchState (rotation-safe UI)
```

Swap map provider: change `MapHostViewModel` to construct a new `CarMapEngine` implementation.

## Conventions for agents

1. **Stay on Compose** � do not reintroduce XML layouts for dashboard UI unless explicitly requested.
2. **Use theme tokens** � colors from `Color.kt`, sizes from `CarDimensions.kt`, text via `Car*Text` composables.
3. **Keep theme separate from layout** � dashboard logic in `ui/dashboard/`, theme in `ui/theme/`.
4. **Match Kotlin ? Compose Compiler** � if bumping Kotlin, update `composeCompiler` in `gradle/libs.versions.toml` using the [official compatibility map](https://developer.android.com/jetpack/androidx/releases/compose-kotlin).
5. **Eonon-specific packages** � radio/Bluetooth package names may differ by firmware; extend the shortcut target resolution in `ShortcutDock.kt` rather than hardcoding in UI composables.
6. **No commits unless asked** � user prefers explicit commit requests.
7. **No Gradle multi-module split** � this app stays a single `:app` module; decompose within packages, not new Gradle modules.
8. **Soft file size for new map/UI collaborators** � prefer **under ~300 lines**; cohesive ~350 OK if split breaks a bug class. Do not grow `MapLibreEngineImpl`; extract only when a class has a clear second responsibility. Do **not** blanket-rewrite existing controllers to a hard 150-line limit.
9. **Explicit return types** on new `public`/`internal` APIs; skip mass-annotating private helpers.
10. **Golden examples** in topic rules (especially `mix-auto-map-engine.mdc`) beat long prose � match those snippets when patching.

## Planned / not yet implemented

- Release signing / Play Store config (personal sideload signing already in place, see `mix-auto-build-release.mdc`)

## Detailed rules (topic-scoped, load automatically by file glob)

This guide stays high-level on purpose � implementation lessons, gotchas, and troubleshooting live in topic-scoped Cursor rules so only the relevant ones load for the file you're editing:

- `.cursor/rules/mix-auto-core.mdc` (always applied) � stack, theme rules, shortcut packages, manifest
- `.cursor/rules/mix-auto-build-release.mdc` (always applied) � build, signing, release, emulator GPS tooling
- `.cursor/rules/mix-auto-map-engine.mdc` � camera, GPS/puck, route rendering, style/traffic overlay, custom pins, search origin (`data/map/**`, `domain/map/**`)
- `.cursor/rules/mix-auto-navigation.mdc` � turn-by-turn TTS, reroute, Conventional OSRM routing, in-nav lighter-traffic alternate (`data/navigation/**`, `ConventionalRouteSelector.kt`, `NavigationRouteFetcher.kt`, `RouteRenderer.kt`)
- `.cursor/rules/mix-auto-media.mdc` � media session, album art, audio source picker (`MediaPlayerPane.kt`, `AlbumArtDisplay.kt`, `data/media/**`)
- `.cursor/rules/mix-auto-dashboard-ui.mdc` � dashboard layout, shortcut dock, panels, status bar, onboarding, app drawer (`ui/dashboard/**`, `ui/components/**`)
- `.cursor/rules/mix-auto-places.mdc` � offline Overture POI database + offline map tile regions (`data/places/**`, `MapDataOverlay.kt`)

## Related agent resources

- Package index (local LLM): [`llms.txt`](llms.txt)
- Session archive: `C:/dev/skills/session-history/mix-auto/`
- **Zed Agent skills** (`.agents/skills/` � junctions to OpenCode bodies): `/local-opencode-loop` (verify gate), `/local-map-triage` (map bugs), `/local-map-feature` (new map/camera behavior), `/local-apk` (GitHub release only), `/map-bug` / `/map-plan` (slash shortcuts)
- **Zed Ollama + profiles:** `mixauto-qwen3-coder:30b` / `:14b` ([`tools/ollama/`](tools/ollama/), [`scripts/create-mixauto-ollama-models.ps1`](scripts/create-mixauto-ollama-models.ps1)); user Zed profiles **MixAuto Write** / **MixAuto Ask** (disable `list_directory`); notes in [`.zed/README.md`](.zed/README.md)
- OpenCode: same skill bodies under `.opencode/skills/`; `opencode.json` agent prompts + `.opencode/commands/` for `/map-bug` / `/map-plan`
- OpenCode junior training / postmortems / scorecard (not product): `C:\dev\proj\opencode-training\` — see `postmortems/README.md`
- Cursor review of local-LLM diffs: `/local-llm-review` ? `~/.cursor/skills/local-llm-review` (personal; not in repo)

## Lessons learned

- **OpenCode / local-LLM process (moved 2026-08):** Failure modes, conductor pipeline, Edit/dry-run/proof anti-patterns → `C:\dev\proj\opencode-training\postmortems\`. Scorecard → `runs.csv`. Locked handoff template → `templates\locked-handoff.md`. Keep product skills (`.opencode/skills/*`) and verify gate in this repo.

- **Audio Settings drawer:** `ActivePanel.AUDIO_SETTINGS` � overflow ? in `MediaPlayerPane`; 60% pane / 40% map split (`isSplitLockedForOverlay`); map tap dismiss via `setMapTapDismissHandler(onDismissPanel)` in `DashboardScreen.kt`; panel in `ui/components/AudioSettingsPanel.kt`
- **Startup / manual audio resume:** `MediaSessionRepository.ensureDefaultPlayerIfNeeded()` runs once per process at boot; `attemptResumeNow()` runs when Audio Settings closes (`DisposableEffect` in `AudioSettingsPanelContent`) � not gated by `hasAttemptedBootLaunch`; fallback link via `BackgroundAudioLauncher.launchFallbackResumeLink()` (`ACTION_VIEW`); plain web share URLs may open the app without autoplay
- **Album art gestures:** When `showAlbumArtControls` is off (default), Info button in media header shows gesture help dialog; any value read inside `pointerInput` that is not a key must use `rememberUpdatedState` � `albumArtMode` in long-press had stale-closure bug
- **LLM-friendly map layout (2026-07):** `MapLibreEngineImpl` is a facade (~1.3k lines after 2026-07 wave 2) � add map/GPS/camera logic in `data/map/` collaborators (`LocationTrackingController`, `NavigationCameraController`, `MapStyleController`, `RouteRenderer`, `PoiOverlayRenderer`, `PoiOverlayCoordinator`, `RouteOverviewController`, `DestinationSearchCoordinator`, `OffRouteDetector`, `NavigationSessionCoordinator`, `MapInteractionController`, `PoiQueryCoordinator`, etc.) via callback injection; do not grow the engine class again. Cross-cutting flags (`isCameraDetached`, `hasSnappedCameraToGps`, nav state) stay on the engine and pass through getters/setters.
- **Map facade extraction (2026-07):** Pulled search origin, Photon fetch, POI query, lighter-traffic helper, map tap/custom pin, nav session orchestration, and offline catalog/metadata into dedicated `data/map/` files; **wave 2** added `RouteModels.kt`, `PoiOverlayCoordinator`, `RouteOverviewController`, `DestinationSearchCoordinator`; `OfflineMapRepository` slimmed to download/observe only. Opportunistic splits only � no hard 150-line rewrite of camera/GPS controllers.
- **Facade extraction order (2026-07):** Wave 1: pure helpers ? `MapInteractionController` ? `NavigationSessionCoordinator` ? offline catalog/metadata. Wave 2: `RouteModels.kt` ? `PoiOverlayCoordinator` ? `RouteOverviewController` ? `DestinationSearchCoordinator`. When editing `NavigationSessionCoordinator`, preserve `ConventionalRouteSelector` + OSRM `alternatives=3` + reroute bearings � see Conventional routing regression note. Result: facade ~1.3k lines (was ~2.9k pre-2026-07, ~1.7k after wave 1); collaborators listed in `llms.txt`.
- **Facade + UI file-size wave 3 (2026-08):** Map: `MapOverlayIds.kt` (layer IDs + anchor resolver), `MapViewHostCoordinator.kt` (MapView/lifecycle/style bootstrap), `InitialLocationResolver.kt`; removed camera/GPS one-liner pass-through wrappers on the facade (~1121?~1018 lines; init wiring block unchanged). UI: `VoiceDestinationSearch.kt` + `SearchPlaceHelpers.kt` from destination search; `MediaPlayerIdleArt.kt` + `MediaTransportControls.kt` + `AlbumArtVinyl.kt` + `AlbumArtVisualizer.kt` from media pane. Did **not** split `LocationTrackingController`, `NavigationCameraController`, or `SmoothingLocationEngine`. Package-level duplicate `private const` names conflict with new shared files � consolidate into `MapOverlayIds` instead.
- **LLM-friendly rules (2026-07):** Tribal knowledge lives in topic-scoped `.cursor/rules/mix-auto-*.mdc` with `globs:` � only `mix-auto-core.mdc` and `mix-auto-build-release.mdc` are always applied. Edit the scoped rule for the subsystem you touch; keep `AGENTS.md` as layout + architecture + pointers.
- **Detekt size guardrails:** `config/detekt/detekt.yml` enforces `LargeClass` / `TooManyFunctions` / `LongMethod` on new code; existing debt in `config/detekt/baseline.xml` � regenerate baseline only when intentionally accepting new size debt.
- **Dashboard UI decomposition:** Portrait/landscape dock layouts in `DashboardLayouts.kt`; secondary pane in `DashboardSecondaryPane.kt`; shared props holders avoid repeating huge argument lists across three layout branches.
- **Destination search empty flicker (2026-07):** Typed search in `NavigationSearchContent` (`NavigationSearchOverlay.kt`) must not flash "No results found" during debounce or Photon � hoist `isSearching`/`isLoadingRemote` on `DestinationSearchUiState` (not `remember` in overlay); set pending **before** `delay(300)`; do not clear `results` on re-query; in `LaunchedEffect` `finally`, clear loading only when `coroutineContext.isActive` (`import kotlinx.coroutines.isActive`) so cancelled keystrokes do not wipe the new effect's flags; voice mic ? `VoiceDestinationSearch.kt`; row/dedup helpers ? `SearchPlaceHelpers.kt`; see `mix-auto-dashboard-ui.mdc` destination search session bullet
- **Conventional routing (2026-07):** Primary nav uses `ConventionalRouteSelector` among OSRM `alternatives=3` (+12% duration cap) � no route picker; `startNavigation()` ? `applyActiveRoute(conventional)` ? `RouteOverviewController.showRouteThenDive()`. Reroute uses same scorer. **Regression note:** July 20 v0.0.34 `NavigationRouteFetcher` extraction accidentally reverted July 6 Conventional work � when extracting from `MapLibreEngineImpl`, preserve scorer wiring and `alternatives=3`, not just fetch/parse helpers.
- **In-nav lighter-traffic alternate (2026-07):** Parallel TomTom fetch on initial navigate; if geometry differs and ETA/traffic qualifies, grey line via `RouteRenderer.showLighterTrafficAlternate()` + `MapUiState.lighterTrafficAlternateActive` chip in `CarMapViewContainer`; tap chip or `ROUTE_TOMTOM_LAYER_ID` hit ? `switchToLighterTrafficAlternate()`. Cleared on free-drive/reroute/switch. No `RoutePickerPane` / `ActivePanel.ROUTE_PICKER`.
- **Driving camera tilt slider (2026-07):** Map Settings **Driving View** **Tilt** slider (20�60�, default **40�**, pref `driving_tilt`) � same wiring as Zoom: `LauncherPreferences` ? `LauncherViewModel` ? `MainActivity` ? `CarMapEngine.setDrivingTilt()` ? `NavigationCameraController` injectable `freeDriveTilt()` / `navTilt()` (nav = free-drive **+ NAV_TILT_OFFSET**, default 40� + 15� = **55�**); live apply via `animateCamera` (free-drive) or `onDrivingTiltChanged()` (nav); top-down/route overview stay 0�; do not use `tiltWhileTracking` during nav dive handshake
- **Idle media manual-play fallbacks (2026-07):** When `!hasActiveSession`, `MediaPlayerPane` shows tappable **Play the album manually** if `audioFallbackResumeLink` is set, else default player icon + **Start music on the player manually**; manual taps use `launchManualFallbackLink()` in `MediaPlayerIdleArt.kt` (`ACTION_VIEW` only) � not `BackgroundAudioLauncher.launchFallbackResumeLink()` (boot/resume refocuses Mix Auto). Transport row ? `MediaTransportControls.kt`; vinyl/visualizer ? `AlbumArtVinyl.kt` / `AlbumArtVisualizer.kt`. See `mix-auto-media.mdc`.
- **Close parallel-street reroute / stuck greying (2026-07):** Urban parallels are often 20�40 m � old **75 m** off-route + **40 m** road-snap caused TTS �recalculating� with no visible refresh (OSRM snapped start back) and a stuck cyan traveled line. Use **35 m** / 4 confirms, snap **18/22 m**, reroute OSRM `radiuses=25;unlimited` + bearings (`NavigationRouteFetcher.buildOsrmRouteUrl`), unconstrained fallback; `updateRouteProgress` on **raw** GPS with freeze `>25 m` and on-route resync `?18 m` (`decideRouteProgressUpdate`). Details in `mix-auto-navigation.mdc` / `mix-auto-map-engine.mdc`; tests in `OffRouteParallelStreetTest.kt`.
- **Map pan / End-nav crash races (2026-07):** Intermittent MapLibre crashes when panning during nav dive/overview or tapping End nav � dive `CancelableCallback` was re-engaging `TRACKING_GPS` after detach, and free-drive snap did not `cancelTransitions`. Fix: `NavigationCameraController.cameraSessionId` + `invalidateCameraSession()` / `prepareForFreeDriveCamera()`; gate `activateNavigationTracking` on navigating + `!isCameraDetached`; `MapLibreEngineImpl.mapReleased` + `withMapStyle` for post-teardown style callbacks. Full contract in `mix-auto-map-engine.mdc` camera-session bullet.
- **Facade wave 2 init handshakes (2026-07):** `PoiOverlayCoordinator` ? `PoiQueryCoordinator` and `RouteOverviewController` ? `NavigationCameraController` use the same `poiCoordRef`/`poiQueryRef` and `routeOverviewRef`/`navRef` two-phase `init` pattern as `navRef`/`locRef` � do not make either side `lazy` if the other needs it at construction time. `PoiOverlayCoordinator.poiCacheMap()` must return `MutableMap` (not `Map`) for `MapInteractionController`'s callback type.
- **Map style constants (2026-07):** Raster fallback JSON lives in `MapStyleConstants.OSM_STYLE_JSON` as **`val`** (not `const val`) � `trimIndent()` is not a compile-time constant. Symptom?owner after wave 2: POI cache/overlay/preview ? `PoiOverlayCoordinator`; route overview bounds/hold/dive ? `RouteOverviewController`; typed destination search merge ? `DestinationSearchCoordinator`; route types/layer IDs ? `RouteModels.kt` � grep `llms.txt` before opening `MapLibreEngineImpl`.
- **Follow-zoom product signal (2026-08):** Free-drive �main vs barangay / street length� as *sole* zoom driver is weak (PH tagging uneven; length proxies fail at junctions). Prefer **speed + nearer-turn** (`min(speedZoom, maneuverZoom)`); optional road-class bias later; keep Driving Zoom slider as ceiling.
- **Follow-zoom LLM scaffold (2026-08):** Pre-wire GPS ? `updateDrivingZoomForSpeed`; shared `applyFollowZoom` + `canApplyFreeDriveFollowZoom`; stage Turns **A?B?C** in `local-map-feature` (math ? free-drive body ? nav `min`); append formulas **after** `shouldApplyZoomChange` � never nest inside `targetZoomForManeuverDistance`.
- **Follow-zoom speed + nearer turn (2026-08, shipped via `/local-opencode`):** `NavigationZoom.targetZoomForSpeed` (?2 m/s ceiling ? ?20 m/s floor 15); free-drive `updateDrivingZoomForSpeed` ? `applyFollowZoom`; nav `finalZoom = min(speedZoom, targetZoomForManeuverDistance)`; Driving Zoom slider stays ceiling (`freeDriveZoom` / `navZoom`). Keep `targetZoomForManeuverDistance`; stage A?B?C in `local-map-feature`.
- **DeveloperSettings hardcoded flags (2026-08):** `ui/settings/DeveloperSettings.kt` � compile-time toggles (not prefs/UI). `MANUAL_DRIVING_ZOOM` **false** (default) = hide Map Settings Zoom slider + free-drive speed follow-zoom; **true** = show slider + skip free-drive speed curve. `SHOW_POI_SOURCE` **false** = hide source next to search-row distance; **true** = show `overture|vector|�`. `FILTER_LOW_CONFIDENCE_POIS` **true** + `MIN_POI_CONFIDENCE` **0.5f** = SQL-filter Overture rows in `LocalPlacesRepository`. `APPROXIMATE_POI_CONFIDENCE_CEILING` **0.7f** = show approximate icon when Overture `confidence` is set and (`!hasStreetAddress` or conf below ceiling). Prefs/`setDrivingZoom` stay wired for manual. Agents: add future developer flags here; grep `DeveloperSettings`.
- **Overture search rank + approximate icon (2026-08):** Confidence floor alone does not remove city-only mid-confidence junk (e.g. Bacolod PLDT empty address at **0.533** still passes ?0.5; Galo St is **0.97** ~4 km away � 50 m dedupe will not merge). Do **not** hard-filter all empty-address rows (~124k PH places at conf?0.5 are still useful schools/churches/etc.). Rank via `rankSearchResults` in `PhotonResponseParser.kt` (street address ? higher confidence ? nearer); plumb `SearchResultPlace.confidence` / `hasStreetAddress` from `LocalPlacesRepository`. UI: `PlaceSubTitleWithApproximateIcon` in `SearchPlaceHelpers.kt` � `subTitle` + `\u00B7` + `Icons.AutoMirrored.Outlined.NotListedLocation` (not Warning/Info/GpsNotFixed); also used in `PoiDetailDrawer`.
- **Overture same-name pin desync (2026-08):** Same distance + name with different map pins is often **two Overture rows**, not vector vs Photon � distance is per-row haversine; 50 m dedupe will not merge. City-only subtitle (`Bacolod City`) = empty street `address` + city; treat as the weak duplicate. Prefer filter/rank over color-coding on the head unit. See DeveloperSettings / rank + approximate-icon bullets.
- **Map package wave 3�4 restructure (2026-08):** TomTom ? `TomTomTrafficModels` / `TomTomApiKeyVerifier` / `TomTomIncidentHeadlines` / `TomTomRouteJamFinder` + thin `TomTomTrafficClient`; offline ? `OfflineProgressFormatting` / `OfflineRegionDownloadSession`; facade ? `PoiSelectionController` / `FreeDriveSessionCoordinator`; camera ? `DrivingViewportPaddingController` / `TopDownPoiCameraController`; location ? `LocationAcquisitionHelper` / `NavigationProgressEvaluator`. `SmoothingLocationEngine` frame loop not split. Facade ~1.1k (was ~1.3k); `MapViewHost` extract deferred. Soft collaborator budget **~300 lines** (cohesive ~350 OK if split breaks bug class) � grep `llms.txt` before opening `MapLibreEngineImpl`.
- **Facade extraction compile gotchas (2026-08):** When wiring new collaborators via ctor callbacks: (1) Kotlin **function types cannot use named args** at call sites � use positional; (2) inject `withMapStyle` as `((Style) -> Unit) -> Unit`, not `(Style) -> Unit`; (3) private **inline** `withMapStyle` cannot be passed as `::withMapStyle` � use `{ block -> withMapStyle(block) }`; (4) `NavigationSessionCoordinator.drawRoute` is `() -> Unit` � keep a no-arg facade wrapper; (5) public API must not return internal nested types � e.g. `PendingOfflineResume` top-level in `OfflineProgressFormatting.kt`; (6) `internal` param types force `internal` on the enclosing public function (`DrivingTilePrefetcher.maybePrefetchWhileDriving`).
- **Nav hide traffic + congestion route (2026-08, v0.0.39):** While `isNavigating`, TomTom flow raster is forced off via `MapLibreEngineImpl.effectiveTrafficVisible()` / `refreshTrafficOverlay()` (pref still stored; restore on free-drive / route-fail). Congestion tint: request `sectionType=traffic`, parse `TomTomTrafficSection`, `RouteCongestion` FeatureCollection + data-driven remaining `line-color` (`congestionColor`). Colors only when `trafficSections` non-empty (TomTom-backed active route / lighter-traffic switch); OSRM conventional stays cyan until sections are matched onto that geometry. Owners: `TomTomRoutingClient`, `RouteCongestion`, `RouteRenderer`, `NavigationSessionCoordinator.applyActiveRoute`.
- **Offline POI SQL LIMIT before distance (2026-08):** Symptom: SM/Savemore search shows only ~40+ km hits. `FILTER_LOW_CONFIDENCE_POIS` / `MIN_POI_CONFIDENCE` 0.5 is usually **not** the cause (most brand rows pass). Root cause: `LocalPlacesRepository.searchWithFts5` / `searchWithLike` applied `LIMIT` (`LOCAL_RESULT_LIMIT` = 15) inside `BBOX_DELTA` +/-0.5 deg **before** distance sort � dense queries return arbitrary far candidates; Kotlin `rankSearchResults` only reorders that set. Fix: pass origin lat/lng and `ORDER BY ((lat - ?) * (lat - ?) + (lng - ?) * (lng - ?))` then `LIMIT`. Diagnose against `mix-auto-overture-maps/places-dist/ph_places.db` (or installed `files/places/*.db`). Session: `C:/dev/skills/session-history/mix-auto/poi-search-limit-before-distance_2026-08-07_0515.md`.
