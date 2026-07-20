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

## Build

**Android Studio (recommended):**

1. Open project root in Android Studio
2. **File → Settings → Build Tools → Gradle → Gradle JDK** → Embedded JDK / jbr-17
3. Gradle Sync, then **Build → Make Project** (`Ctrl+F9`)

**Command line (Windows):**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
cd C:\dev\proj\mix-auto
.\gradlew.bat assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

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
│   └── MapLibreEngineImpl.kt    # MapLibre + OSRM + Photon adapter
├── data/places/
│   ├── LocalPlacesRepository.kt # Offline Overture POI SQLite search + HTTP download
│   └── LocalDbMeta.kt           # Installed database metadata
└── ui/
    ├── components/
    │   ├── CarMapViewContainer.kt       # AndroidView bridge + HUD + search button
    │   ├── NavigationSearchOverlay.kt   # Destination search dialog (Recent/Saved/Nearby tabs)
    │   ├── PoiDetailDrawer.kt           # Full-screen POI/dropped-pin details + star + navigate
    │   ├── RoutePickerPane.kt           # Multi-route selection in media pane (OSRM + TomTom alternates)
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
              ├── RoutePickerPane — media pane when ≥2 routes (`ActivePanel.ROUTE_PICKER`)
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

## Planned / not yet implemented

- Release signing / Play Store config (personal sideload signing already in place, see `mix-auto-build-release.mdc`)

## Detailed rules (topic-scoped, load automatically by file glob)

This guide stays high-level on purpose — implementation lessons, gotchas, and troubleshooting live in topic-scoped Cursor rules so only the relevant ones load for the file you're editing:

- `.cursor/rules/mix-auto-core.mdc` (always applied) — stack, theme rules, shortcut packages, manifest
- `.cursor/rules/mix-auto-build-release.mdc` (always applied) — build, signing, release, emulator GPS tooling
- `.cursor/rules/mix-auto-map-engine.mdc` — camera, GPS/puck, route rendering, style/traffic overlay, custom pins, search origin (`data/map/**`, `domain/map/**`)
- `.cursor/rules/mix-auto-navigation.mdc` — turn-by-turn TTS, reroute, multi-route selection/picker (`data/navigation/**`, `RoutePickerPane.kt`)
- `.cursor/rules/mix-auto-media.mdc` — media session, album art, audio source picker (`MediaPlayerPane.kt`, `AlbumArtDisplay.kt`, `data/media/**`)
- `.cursor/rules/mix-auto-dashboard-ui.mdc` — dashboard layout, shortcut dock, panels, status bar, onboarding, app drawer (`ui/dashboard/**`, `ui/components/**`)
- `.cursor/rules/mix-auto-places.mdc` — offline Overture POI database + offline map tile regions (`data/places/**`, `MapDataOverlay.kt`)

## Related agent resources

- Session archive: `C:/dev/skills/session-history/mix-auto/`
