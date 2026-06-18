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
| Launcher role | HOME + DEFAULT + LAUNCHER intent-filters |

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

## Project layout

```
app/src/main/java/com/kyuusanq3/mixauto/
├── MainActivity.kt              # mapEngine = MapLibreEngineImpl(); location permission launcher
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
    │   ├── NavigationSearchOverlay.kt   # Destination search dialog
    │   └── MapDataOverlay.kt            # Offline Overture POI download UI
    ├── theme/                   # Color, CarDimensions, Type, Theme
    └── dashboard/
        ├── DashboardScreen.kt   # Map + media + shortcut dock layouts; settings overlay
        ├── MediaPlayerPane.kt   # Now playing UI (media session driven)
        └── ShortcutDock.kt      # System app shortcuts
```

## Architecture

```
MainActivity (localPlacesRepo + mapEngine = MapLibreEngineImpl(localPlacesRepo))
  └── MixAutoTheme
        └── DashboardScreen(mapEngine, mapDataViewModel)
              ├── CarMapViewContainer — map pane (60% or CarDimensions.MapWeight)
              ├── MediaPlayerPane + ShortcutDock (Map Data + Launcher settings)
              └── MapDataOverlay — offline POI country download
```

Swap map provider: change one line in `MainActivity.kt` to a new `CarMapEngine` implementation.

## Design constraints (automotive)

- **Background:** pure OLED black `#000000`; cards use `#121212` / `#1C1B1F`
- **Accent:** electric cyan `#00E5FF` for high visibility
- **Tap targets:** minimum 64dp (`CarDimensions.MinTapTarget`); primary controls 84dp
- **Typography:** use `CarHeadlineText`, `CarBodyText`, `CarLabelText` from `Type.kt` — they enforce `maxLines` and `TextOverflow.Ellipsis`
- **Import rule:** any file using theme text composables must import them explicitly (e.g. `CarLabelText` in `DashboardScreen.kt`)

## Shortcut dock behavior

`ShortcutDock.kt` resolves launchable apps via `PackageManager`:

| Label | Package candidates | Fallback |
|-------|-------------------|----------|
| Settings | `com.android.settings` | — |
| Radio | `com.android.fmradio`, `com.caf.fmradio`, `com.eonon.radio` | — |
| Bluetooth | `com.android.settings` | `Settings.ACTION_BLUETOOTH_SETTINGS` |

Icons: `Drawable.toImageBitmap()` extension (BitmapDrawable fast path + canvas fallback for adaptive icons). Launch: `getLaunchIntentForPackage()` wrapped in try/catch.

Dock also includes **Map Data** (offline Overture POI download) and **Launcher** (layout settings) shortcuts.

## Offline Overture POI data

Country POI databases live in a separate GitHub repo **`mix-auto-overture-maps`**. The app fetches `countries.json` from the latest release and lists every country for download. Assets are **gzip-compressed** SQLite files (`ph_places.db.gz`, ~137 MB download → ~263 MB installed). Build scripts live in that repo under `tools/`.

### Catalog URL (hardcoded in app)

```
https://github.com/kyuusanq3/mix-auto-overture-maps/releases/latest/download/countries.json
```

Per-country download: `{RELEASE_BASE}/{asset}` e.g. `ph_places.db.gz`. Uses GitHub `/releases/latest/download/` — no tag update needed in the app when you publish a new release.

### Build and publish (dev machine)

Clone **`mix-auto-overture-maps`** as a sibling of this repo (`../mix-auto-overture-maps`). See its README and run:

```powershell
cd ..\mix-auto-overture-maps
pip install overturemaps
.\publish_philippines.ps1
```

Output: `places-dist/ph_places.db.gz` + `countries.json`. Upload both to a GitHub release.

Bundled sample asset: `python tools/build_sample_places_db.py` (writes to `mix-auto/app/src/main/assets/places/ph_sample.db`).

### countries.json schema

```json
[
  {
    "iso": "PH",
    "name": "Philippines",
    "asset": "ph_places.db.gz",
    "size_compressed_mb": 137,
    "size_db_mb": 263
  }
]
```

### On-device install

- **Map Data** opens → `MapDataViewModel.loadCatalog()` fetches manifest → country list with **Installed** badge or download button.
- Download uses `GZIPInputStream` when URL ends with `.gz`.
- **Import** / **Sample** remain as fallbacks for Philippines.

**On-device schema** (`filesDir/places/{iso}.db`):

- `places` table: id, name, category, address, city, lat, lng, confidence
- `places_fts` FTS5 virtual table (external content) for fast text search
- `meta` table: country_iso, country_name, record_count, generated_date

**Search integration:** `MapLibreEngineImpl.searchDestination()` fans out to local SQLite (FTS5 + bbox) and Photon in parallel, deduplicates within 50 m, sorts by distance. Optional 500 km cap (default ON) via Launcher Settings **"Nearby results only (within 500 km)"** — persisted in `LauncherPreferences.limitSearchDistance`, passed as `limitDistance` to the engine.

## Manifest / launcher setup

`MainActivity` is registered as a system home launcher:

- `android:launchMode="singleTask"`
- `android:screenOrientation="landscape"`
- Categories: `HOME`, `DEFAULT`, `LAUNCHER`

After install on the head unit, select **MixAuto** when prompted for the default home app.

## Conventions for agents

1. **Stay on Compose** — do not reintroduce XML layouts for dashboard UI unless explicitly requested.
2. **Use theme tokens** — colors from `Color.kt`, sizes from `CarDimensions.kt`, text via `Car*Text` composables.
3. **Keep theme separate from layout** — dashboard logic in `ui/dashboard/`, theme in `ui/theme/`.
4. **Match Kotlin ↔ Compose Compiler** — if bumping Kotlin, update `composeCompiler` in `gradle/libs.versions.toml` using the [official compatibility map](https://developer.android.com/jetpack/androidx/releases/compose-kotlin).
5. **Eonon-specific packages** — radio/Bluetooth package names may differ by firmware; extend `shortcutTargets` in `ShortcutDock.kt` rather than hardcoding in UI composables.
6. **No commits unless asked** — user prefers explicit commit requests.

## Planned / not yet implemented

- Route re-routing when driver deviates from the drawn path
- Dynamic discovery of all launchable apps (currently fixed shortcut list)
- Release signing / Play Store config
- Vector map style for sharper 3D driving perspective (OSM raster limits tilt quality)

## Troubleshooting

| Issue | Fix |
|-------|-----|
| `JAVA_HOME is not set` | Point `JAVA_HOME` at Android Studio `jbr` or JDK 17+ (see Build section) |
| `Unresolved reference: CarLabelText` | Add `import com.kyuusanq3.mixauto.ui.theme.CarLabelText` |
| Compose compiler version mismatch | Align `kotlin` and `composeCompiler` in `libs.versions.toml` |
| App not offered as home launcher | Verify HOME + DEFAULT categories in manifest; reinstall APK |
| Map shows green/blank (no streets) | OSM tiles loading — ensure INTERNET; demotiles style has no PH coverage (use OSM raster in engine) |
| "Location unavailable" on emulator | Grant **Precise** location permission; Extended Controls → Location → Set Location while app is open (after `GPS location listener registered` appears in Logcat) |
| Emulator GPS never fires despite Set Location | AVD-level failure — `adb emu geo fix` returns OK but `dumpsys location` shows `Number of location reports: 0`; cold boot AVD or test on physical device |
| AppOps `op=GPS: Operation not started` in Logcat | Location listener being torn down and re-registered too rapidly; `MapLibreEngineImpl` uses `listenersRegistered` flag — do not reset it on resume |
| Navigation shows "Zoom map to your area" | GPS unavailable — pan/zoom map to location (zoom ≥ 10), then search destination; routing uses map center as origin |
| `OSRM HTTP error: 403` in Logcat | `router.project-osrm.org` blocks third-party apps — use `routing.openstreetmap.de/routed-car` in `MapLibreEngineImpl.fetchOsrmRoute()` with `User-Agent: MixAutoCarLauncher/1.0` |
| Map frozen at US/Philippines overview | Wait for GPS snap or set mock location; engine snaps on first `requestLocationUpdates` fix |
| `Unresolved reference: LocalLifecycleOwner` | Add `androidx.lifecycle:lifecycle-runtime-compose` dependency |
| Crash: `Key "Settings" was already used` on settings toggle | `ShortcutDock.kt`: use `key = { it.id }` not `it.label`; wrap dock in `key(isHorizontal)` |
| Crash: `child already has a parent` on settings toggle | `MapLibreEngineImpl`: detach cached MapView before reuse; `CarMapViewContainer`: `onDestroy` only on activity `ON_DESTROY` |
| HUD stuck on first turn / street name shows "Current location" while navigating | `applyAndroidLocation()` must call `evaluateStepAdvancement()` when `isNavigating`, not overwrite `streetName` |
| Cyan route line persists after ending navigation | `startFreeDrive()` must `removeLayer(ROUTE_LAYER_ID)` and `removeSource(ROUTE_SOURCE_ID)` on map style |
| Camera snaps flat immediately after route draw | Use `showRouteThenDive()` — 2 s bounds overview then `enterNavigationCamera()` with `TRACKING_GPS`; do not set `TRACKING` right after route fetch |
| Map zoomed out on startup despite GPS fixes | Style callback must call `startFreeDrive()` after `activateLocationTracking()`; do not enable `TRACKING` before snap — fallback zoom 6 locks wide view |
| Free drive camera twitching/jitter | Do not re-snap on every GPS fix — `hasSnappedCameraToGps` + one `moveCamera` then `TRACKING_GPS`; no duplicate `startFreeDrive()` from `retryLocationActivation()` |
| Free drive looks flat (not Android Auto slanted) | Free drive uses `FREE_DRIVE_TILT` 45° and `CameraMode.TRACKING_GPS` with GPS bearing; nav uses tilt 55° / zoom 17.5 |
| Nav camera frozen while GPS puck moves | `enterNavigationCamera()` must use `zoomWhileTracking`/`tiltWhileTracking`, not `map.animateCamera()` — direct animate cancels `TRACKING_GPS` |
| Map doesn't rotate to direction of travel | Use `RenderMode.GPS` not `COMPASS` in `activateLocationTracking()` and camera entry |
| UI cut off by status/nav bars on phone | `MainActivity` is edge-to-edge (`setDecorFitsSystemWindows(false)`); add `systemBarsPadding()` on `DashboardScreen` root `Box` |
| Night theme missing fullscreen flags | Add `windowFullscreen` + `windowContentOverlay` to `res/values-night/themes.xml` |
| Media pane shows "Enable notification access" | Settings → Special app access → Notification access → enable MixAuto; start playback in YT Music/Spotify then return to launcher |
| Now playing not updating | `MainActivity.onResume()` refreshes sessions; ensure `MixAutoNotificationListenerService` is enabled |
| Skip-next media button shrinks in narrow pane | `MediaPlayerPane.kt`: use `weight(1f)` slots + `requiredSize(MinTapTarget)` on all three controls |
| `setMapMediaRatio` JVM signature clash | Use `updateMapMediaRatio()` in `LauncherViewModel` — property already generates `setMapMediaRatio` setter |
| Philippines download returns HTTP 404 | Attach `ph_places.db.gz` + `countries.json` as **release assets** on `mix-auto-overture-maps` (committing to repo is not enough); verify `/releases/latest/download/countries.json` returns 200 |
| Catalog loads but download 404 | Release exists but assets array empty — edit release and upload both files from `places-dist/` |
| Manual import of `.gz` fails | Import path expects uncompressed `.db`; use in-app Download or decompress first |
| Search shows overseas / 5000 km destinations | Default 500 km cap is ON — disable **Nearby results only (within 500 km)** in Launcher Settings to include farther Photon results; routing still may fail for long/cross-water trips |

## Related agent resources

- Cursor rules: `.cursor/rules/mix-auto-android.mdc`
- Session archive: `C:/dev/skills/session-history/mix-auto/`
