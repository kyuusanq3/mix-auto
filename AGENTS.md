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

Release sideload copy: `mix-auto-v{version}.apk` at project root (e.g. `mix-auto-v1.0.0.apk`). No release keystore yet — debug APK only.

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
MainActivity (localPlacesRepo + mapEngine = MapLibreEngineImpl(localPlacesRepo))
  └── MixAutoTheme
        └── DashboardScreen(mapEngine, mapDataViewModel)
              ├── CarMapViewContainer — map pane (60% or CarDimensions.MapWeight)
              ├── MediaPlayerPane + ShortcutDock (Map Data + Launcher settings)
              ├── PoiDetailDrawer — full-screen overlay when map POI/pin selected
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

`ShortcutDock.kt` provides four dock toggles (no fixed system app shortcuts):

| Icon | Panel / action |
|------|----------------|
| App Drawer | `ActivePanel.APP_DRAWER` — full map+media overlay (`AppDrawerOverlay.kt`) |
| Music | Toggle media pane / collapse to map-only |
| Map Data | Offline Overture POI download panel |
| Launcher (Tune) | Launcher layout settings |

**App Drawer icon position** (via `dockItemOrder()`): vertical side dock → **top** (first item); horizontal bottom dock LHD → **left** (first); RHD → **right** (last).

**App Drawer overlay** lists all launchable apps (`queryIntentActivities`), sorted by name, with search. Tap launches; long-press → App Info or Uninstall. Overlay covers map+media split only — shortcut bar remains visible and unchanged in position.

Dock also includes **Map Data** and **Launcher** settings shortcuts.

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

**Recent destinations:** Up to 10 saved in `LauncherPreferences.recentDestinations` (JSON in SharedPreferences). `LauncherViewModel.addRecentDestination()` prepends, dedupes within 50 m, persists. Flow: `MainActivity` → `DashboardScreen` → `CarMapViewContainer` → `NavigationSearchOverlay`; any selection calls `onDestinationSelected` before `navigateToCoordinates`.

**Saved places:** Up to 50 in `LauncherPreferences.savedPlaces` (same JSON schema + `isDroppedPin`). `LauncherViewModel.toggleSavedPlace()` dedupes at 50 m. Starred POIs show gold teardrop icons on map via `mapEngine.setSavedPlaces()` synced from `DashboardScreen`. Empty search field shows **Recent | Saved | Nearby** tabs; each row has a star button. **Short map tap** selects saved pin, active custom pin, or POI pin (`handleMapPointSelection()`). **Long press** on empty map drops a custom pin (`startCustomPinDraft()`); long press in free-drive GPS tracking exits to top-down view at the drop point. POI detail opens in media pane via `ActivePanel.POI_DETAIL`.

## Manifest / launcher setup

`MainActivity` is the main entry with `singleTask` and `sensor` orientation:

- **Navigation App Mode (default):** `MainActivity` has `LAUNCHER` only — app appears in the app drawer, does not replace home screen on first install
- **Launcher Mode (opt-in):** Settings → **Launcher Mode (replaces home screen)** enables `LauncherModeAlias` (`HOME` + `DEFAULT`, targets `MainActivity`) via `PackageManager.setComponentEnabledSetting`
- `LauncherModeAlias` is `android:enabled="false"` in manifest; preference `LauncherPreferences.isLauncherMode` restored in `onCreate()`

After enabling Launcher Mode, press Home and select **Mix Auto** as the default home app.

## Conventions for agents

1. **Stay on Compose** — do not reintroduce XML layouts for dashboard UI unless explicitly requested.
2. **Use theme tokens** — colors from `Color.kt`, sizes from `CarDimensions.kt`, text via `Car*Text` composables.
3. **Keep theme separate from layout** — dashboard logic in `ui/dashboard/`, theme in `ui/theme/`.
4. **Match Kotlin ↔ Compose Compiler** — if bumping Kotlin, update `composeCompiler` in `gradle/libs.versions.toml` using the [official compatibility map](https://developer.android.com/jetpack/androidx/releases/compose-kotlin).
5. **Eonon-specific packages** — radio/Bluetooth package names may differ by firmware; extend `shortcutTargets` in `ShortcutDock.kt` rather than hardcoding in UI composables.
6. **No commits unless asked** — user prefers explicit commit requests.

## Planned / not yet implemented

- Route re-routing when driver deviates from the drawn path — **implemented** (75 m threshold, OSRM re-fetch)
- Dynamic discovery of all launchable apps (currently fixed shortcut list)
- Release signing / Play Store config
- Vector map style (OpenFreeMap Liberty) is the first-launch default; OSM raster remains available via Settings toggle

## Troubleshooting

| Issue | Fix |
|-------|-----|
| `JAVA_HOME is not set` | Point `JAVA_HOME` at Android Studio `jbr` or JDK 17+ (see Build section) |
| `Unresolved reference: CarLabelText` | Add `import com.kyuusanq3.mixauto.ui.theme.CarLabelText` |
| Compose compiler version mismatch | Align `kotlin` and `composeCompiler` in `libs.versions.toml` |
| App not offered as home launcher | Enable **Launcher Mode** in Settings first; verify `LauncherModeAlias` HOME+DEFAULT in manifest; reinstall if needed |
| Map shows green/blank (no streets) | OSM tiles loading — ensure INTERNET; demotiles style has no PH coverage (use OSM raster in engine) |
| "Location unavailable" on emulator | Grant **Precise** location permission; Extended Controls → Location → Set Location while app is open (after `GPS location listener registered` appears in Logcat) |
| Emulator GPS never fires despite Set Location | `adb emu geo fix` is broken on ALL x86_64 AVD images (Google Play and AOSP, Android 11–13) — GNSS HAL delivers zero fixes; use `avd-gps.ps1` at repo root instead (test provider API bypasses GNSS HAL); requires AOSP image for `adb root` |
| GPS working in Genymotion but not Android Studio AVD | See above — use `.\avd-gps.ps1` after each emulator cold boot; run `.\avd-gps.ps1 -Drive` to simulate road-snapped driving route (Gov Center → SM City Bacolod) |
| AppOps `op=GPS: Operation not started` in Logcat | Location listener being torn down and re-registered too rapidly; `MapLibreEngineImpl` uses `listenersRegistered` flag — do not reset it on resume |
| Navigation shows "Zoom map to your area" | GPS unavailable — pan/zoom map to location (zoom ≥ 10), then search destination; routing uses map center as origin |
| `OSRM HTTP error: 403` in Logcat | `router.project-osrm.org` blocks third-party apps — use `routing.openstreetmap.de/routed-car` in `MapLibreEngineImpl.fetchOsrmRoute()` with `User-Agent: MixAutoCarLauncher/1.0` |
| Map frozen at US/Philippines overview | Wait for GPS snap or set mock location; engine snaps on first `requestLocationUpdates` fix |
| `Unresolved reference: LocalLifecycleOwner` | Add `androidx.lifecycle:lifecycle-runtime-compose` dependency |
| Crash: `Key "Settings" was already used` on settings toggle | `ShortcutDock.kt`: use `key = { it.id }` not `it.label`; wrap dock in `key(isHorizontal)` |
| Crash: `child already has a parent` on settings toggle | `MapLibreEngineImpl`: detach cached MapView before reuse; `CarMapViewContainer`: `onDestroy` only on activity `ON_DESTROY` |
| HUD stuck on first turn / street name shows "Current location" while navigating | `applyAndroidLocation()` must call `evaluateStepAdvancement()` when `isNavigating`, not overwrite `streetName` |
| Cyan route line persists after ending navigation | `startFreeDrive()` must `removeLayer(ROUTE_LAYER_ID)` and `removeSource(ROUTE_SOURCE_ID)` on map style |
| Camera snaps flat immediately after route draw | `showRouteThenDive()` — 2 s bounds animation (`ROUTE_OVERVIEW_ANIMATION_MS`) then 10 s hold (`ROUTE_OVERVIEW_HOLD_MS`) with timer bar before `enterNavigationCamera()`; set `CameraMode.NONE` before overview |
| Map zoomed out on startup despite GPS fixes | Style callback must call `startFreeDrive()` after `activateLocationTracking()`; do not enable `TRACKING` before snap — fallback zoom 6 locks wide view |
| Free drive camera twitching/jitter | Do not re-snap on every GPS fix — `hasSnappedCameraToGps` + one `moveCamera` then `TRACKING_GPS`; no duplicate `startFreeDrive()` from `retryLocationActivation()` |
| Free drive looks flat (not Android Auto slanted) | Free drive uses `FREE_DRIVE_TILT` 50° and `CameraMode.TRACKING_GPS` with GPS bearing; nav uses tilt 58° / zoom 18.5 |
| Nav camera frozen while GPS puck moves / dive never zooms in | `enterNavigationCamera()`: `CameraMode.NONE` → `map.animateCamera()` to zoom 18.5 / tilt 58° → `TRACKING_GPS` in callback; do NOT use `zoomWhileTracking`/`tiltWhileTracking` immediately after mode change (ignored during transition) |
| Map doesn't rotate to direction of travel | Use `RenderMode.GPS` not `COMPASS` in `activateLocationTracking()` and camera entry |
| UI cut off by status/nav bars on phone | `MainActivity` is edge-to-edge (`setDecorFitsSystemWindows(false)`); add `systemBarsPadding()` on `DashboardScreen` root `Box` |
| Status bar hidden on Eonon / head unit | Remove `android:windowFullscreen` from `themes.xml` (day + night); `MainActivity` calls `WindowInsetsControllerCompat.show(systemBars())` — do not re-add fullscreen theme flag |
| Media pane shows "Enable notification access" | Settings → Special app access → Notification access → enable MixAuto; start playback in YT Music/Spotify then return to launcher |
| Transport row shows dim MusicNote instead of app icon | No active session or `sourcePackage` blank — enable notification access and start playback in YT Music/Spotify; first slot launches source app via `AppIconUtils.launchAppByPackage()` |
| Music dock badge missing | Badge only shows when `rememberAppIcon(sourcePackage)` succeeds; no fallback icon — pan away and back after session attaches if package just changed |
| Skip-next media button shrinks in narrow pane | `MediaPlayerPane.kt`: use `weight(1f)` slots + `requiredSize(MinTapTarget)` on all three controls |
| `setMapMediaRatio` JVM signature clash | Use `updateMapMediaRatio()` in `LauncherViewModel` — property already generates `setMapMediaRatio` setter |
| Map/media divider only moves on repeated swipes | Do not include `mapMediaRatio` in `pointerInput` keys in `MapMediaDividerHandle` — use `rememberUpdatedState` + accumulate drag from `onDragStart` |
| Wide black gap between map and media panes | Divider touch target must not use `MinTapTarget` as Row/Column layout size — use `seamTouchTarget` (0 dp seam, 64 dp hit area overlapping panes) in `DashboardScreen.kt` |
| Resize map vs media | Drag the cyan 3-dot handle on the pane border (not Settings); ratio 0.3–0.8, persisted in `LauncherPreferences.mapMediaRatio` |
| Philippines download returns HTTP 404 | Attach `ph_places.db.gz` + `countries.json` as **release assets** on `mix-auto-overture-maps` (committing to repo is not enough); verify `/releases/latest/download/countries.json` returns 200 |
| Catalog loads but download 404 | Release exists but assets array empty — edit release and upload both files from `places-dist/` |
| Manual import of `.gz` fails | Import path expects uncompressed `.db`; use in-app Download or decompress first |
| Search shows overseas / 5000 km destinations | Default 500 km cap is ON — disable **Nearby results only (within 500 km)** in Launcher Settings to include farther Photon results; routing still may fail for long/cross-water trips |
| Empty search shows no Nearby rows | Nearby tab uses `poiCache` only — pan/zoom map (zoom ≥ 13) so camera-idle POI load runs; no network fetch for empty-query nearby list |
| Vector tiles show POI labels but no teardrop pins | `queryTilePois()` must use `map.queryRenderedFeatures` on Liberty layer IDs (`poi_r1`, `poi_r7`, `poi_r20`, `poi_transit`) — `VectorSource.querySourceFeatures("poi")` misses rendered POIs; zoom ≥ 15 for Liberty POI labels |
| Pin tap does not center map | `handleMapPointSelection()` should call `focusOnPoi(..., moveCamera = true)` → `animateTopDownCamera` at `POI_PREVIEW_ZOOM` |
| Saved tab empty | Star a place from map tap drawer or search row star button — `LauncherPreferences.savedPlaces` persists up to 50 entries |
| Map tap shows Dropped Pin instead of POI name | Short tap no longer drops pins — use **long press** on empty map; short tap on POI pin selects that POI |
| Long press does not drop custom pin | Disabled during navigation; must long-press empty map (not on existing pin icon) |
| Long press in free drive still follows GPS | Only exits to top view when `!isCameraDetached` (active GPS tracking); manual pan already sets detached — camera stays put, pin still drops |
| Top-view tooltip pushes map buttons toward center | Top-right `Column` in `CarMapViewContainer.kt` must use `horizontalAlignment = Alignment.End` — `CenterHorizontally` centers Search/Recenter when tooltip `Row` is widest |
| Top-view tooltip text touches bubble edge | `TooltipBubble` inner padding must be `horizontal = 14.dp` (not 8.dp) — body ends where tail starts |
| Top-view tooltip tail gap wrong | Tail-to-button spacing is layout in `TooltipBubble` (`padding(end = tailHeight)`, tail to `size.width`) — sibling spacers do not affect `drawBehind` tail position |
| Tapping near saved custom pin re-opens it instead of new draft | Do not add geographic proximity fallback before draft — use layer hit + `isTapNearPinIcon()` screen radius only |
| Route line disappears while navigating | Raster style: route layer must use `addLayerAbove(..., "osm")` in `drawRoute()` — plain `addLayer()` puts line under tiles |
| Route line too thin | `ROUTE_WIDTH` is 14f with `ROUTE_CASING_LAYER_ID` (18f dark casing) below cyan line in `drawRoute()` |
| Puck pauses/jumps between GPS fixes | `startDeadReckoning()` extrapolates puck at 16 ms using last speed + bearing; ensure `hasSpeed()` is present on fixes |
| Puck spins when stopped in traffic | `enrichLocationWithBearing()` freezes bearing via `lastStableBearing` when speed < `STOPPED_SPEED_MPS` (1.4 m/s) |
| Puck size slider tap/skip has no effect | Do NOT use `onValueChangeFinished` with captured `puckScale` — stale closure reverts value same frame; use `onValueChange = onPuckScaleChange` only in `DashboardScreen.kt` |
| Puck size slider jolts camera | `setPuckScale(scale)` is style-only via `applyStyle()` — do NOT call `forceLocationUpdateForImmediateRender()` for scale changes (unlike viewport padding) |
| Compass hidden under search icon | `MapLibreEngineImpl.configureCompassPosition()` moves compass to bottom-right via `setCompassGravity(BOTTOM \| END)` |
| Nav doesn't re-route after wrong turn | Off-route reroute needs 2 GPS fixes >75 m from `routeGeometryPoints`; 5 s cooldown between reroutes; check Logcat for `Off route` / `Re-routing` |
| Music doesn't auto-play on launch | Enable notification access; `maybeAutoPlay()` runs once per process when paused session has metadata — also retried from `onMetadataChanged()` (late metadata) and same-token `attachController()` re-attach; Logcat `MediaSessionRepository`: `Auto-playing paused media session on launch` |
| Shortcut bar too small on head unit | Settings → **Large Shortcut Icons** doubles tap target and icon size in both horizontal and vertical dock (uses `DockHorizontalTapTarget` / `DockHorizontalIconSize`) |
| Vertical shortcut bar too wide / large side padding | Vertical dock must use `wrapContentWidth()` in `ShortcutDock` + `DashboardScreen` — not `.weight(DockVerticalWeight)`; icon-only, no labels |
| App drawer malforms vertical shortcut bar (floating dock, black gap) | Do NOT stack `AppDrawerOverlay` above `ShortcutDock` in a side Column — overlay map+media `Box` only via `SplitScreenAppDrawerSlot`; dock stays `wrapContentWidth().fillMaxHeight()` sibling |
| TomTom traffic HTTP 400 `Invalid style: relative-delay` | Orbis v2 raster tiles only accept `style=light` or `style=dark` — not legacy styles (`relative`, `relative-delay`, etc.); see `MapLibreEngineImpl.applyTrafficOverlay()` |
| TomTom traffic not showing / key unknown | Map Data shortcut → paste key from developer.tomtom.com → **Test TomTom API Key**; enable **Traffic Overlay** in Launcher Settings; enable Traffic Flow product on key; Logcat tag `TomTomTrafficClient` |
| Traffic tiles fail with HTTP 403 | Invalid key or Traffic Flow API not enabled for that TomTom developer key |
| Update check fails HTTP 404 | App uses GitHub Releases API (`AppUpdateRepository.RELEASES_API_URL`), not a `version.json` asset — attach `mix-auto.apk` to the release and set tag to semver (e.g. `0.0.5`); download URL is `releases/latest/download/mix-auto.apk` |
| Vinyl album art shows as square/rectangle | `VinylAlbumArt`: use `clip(CircleShape)` then `graphicsLayer { rotationZ }` — not `Modifier.rotate` before clip |
| Long-press on album art does nothing / swipes conflict with picker | Long-press opens `AlbumArtModePicker`; normal swipe gestures disabled while `isPickerOpen` in `MediaPlayerPane.kt` |
| Album art too small in tall portrait pane | Size via `BoxWithConstraints`: `(if (maxWidth < maxHeight) maxWidth else maxHeight) * 0.92f` |
| Unlike skips track in YT Music | `MediaSessionRepository.toggleLike()` never sends dislike/thumb_down — use unrated or re-toggle like action only |
| Like heart not lit when returning to song | Per-track `likedTrackCache` in `MediaSessionRepository` keyed by media ID / title\|artist |
| Voice button opens Assistant instead of search | Voice key only intercepted when destination search overlay is open — open map search first; grant **Microphone** permission on first use; Eonon may send `KEYCODE_SEARCH` instead of `KEYCODE_VOICE_ASSIST` (both handled in `MainActivity.onKeyDown`) |
| Mic button missing in search overlay | `SpeechRecognizer.isRecognitionAvailable()` false on bare AOSP — install Google app or use typed search; mic hidden when no recognizer |
| Onboarding wizard shows icon only, no title/body | `OnboardingWizard` root must be `Surface(color = OledBlack)` — bare `Box` leaves `LocalContentColor` black and `Car*Text` invisible on OLED background |
| Re-test permission onboarding wizard | Clear app data or set `launcher_prefs` key `onboarding_version` to `0`; wizard shows when `onboardingVersion < CURRENT_ONBOARDING_VERSION` in `OnboardingWizard.kt` |

## Related agent resources

- Cursor rules: `.cursor/rules/mix-auto-android.mdc`
- Session archive: `C:/dev/skills/session-history/mix-auto/`
