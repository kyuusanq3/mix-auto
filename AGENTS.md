# MixAuto — Agent Guide

Custom Android **Car Launcher** for an **Eonon head unit**. This app replaces the default home screen with a landscape dashboard: map placeholder, media player placeholder, and system app shortcuts.

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
| Orientation | Landscape only (`AndroidManifest.xml`) |
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
│   └── PlaceResult.kt           # Nominatim search result
├── data/map/
│   └── MapLibreEngineImpl.kt    # MapLibre + OSRM + Nominatim adapter
└── ui/
    ├── components/
    │   ├── CarMapViewContainer.kt       # AndroidView bridge + HUD + search button
    │   └── NavigationSearchOverlay.kt   # Destination search dialog
    ├── theme/                   # Color, CarDimensions, Type, Theme
    └── dashboard/
        ├── DashboardScreen.kt   # Map + media + shortcut dock layouts
        └── ShortcutDock.kt      # System app shortcuts
```

## Architecture

```
MainActivity (private val mapEngine: CarMapEngine = MapLibreEngineImpl())
  └── MixAutoTheme
        └── DashboardScreen(mapEngine)
              ├── CarMapViewContainer — map pane (60% or CarDimensions.MapWeight)
              └── MediaPlayerPane + ShortcutDock
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
- Real media session / album art in `MediaPlayerPane`
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

## Related agent resources

- Cursor rules: `.cursor/rules/mix-auto-android.mdc`
- Session archive: `C:/dev/skills/session-history/mix-auto/`
