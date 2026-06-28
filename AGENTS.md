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

## Design constraints (automotive)

- **Background:** pure OLED black `#000000`; cards use `#121212` / `#1C1B1F`
- **Accent:** electric cyan `#00E5FF` for high visibility
- **Tap targets:** minimum 64dp (`CarDimensions.MinTapTarget`); primary controls 84dp
- **Typography:** use `CarHeadlineText`, `CarBodyText`, `CarLabelText` from `Type.kt` — they enforce `maxLines` and `TextOverflow.Ellipsis`
- **Import rule:** any file using theme text composables must import them explicitly (e.g. `CarLabelText` in `DashboardScreen.kt`)

## Shortcut dock behavior

`ShortcutDock.kt` dock layout: driver cluster (App Drawer + Mic) | **pinned apps (up to 5)** in center | **music side control** (`DockMusicSideControl`). **Launcher settings** is in the app drawer header (gear icon). **Destination search** is a circular FAB at **top-center** of the map (`DestinationSearchFab`, 84 dp); end nav / recenter / top-view stay top-right. **Map settings** toggle is bottom-left on the map (above MapLibre watermark); panel still opens in media pane via `ActivePanel.MAP_DATA`.

| Icon / control | Panel / action |
|------|----------------|
| App Drawer | `ActivePanel.APP_DRAWER` — full map+media overlay (`AppDrawerOverlay.kt`) |
| Pinned apps (0–5) | User-added from app drawer long-press; **audio apps** show music-note badge — tap sets source (or toggles media pane if already active); other apps tap launch; long-press → Add/Remove shortcut bar, App Info, Uninstall |
| Mic (Voice Search) | Opens destination search + voice when closed; closes search or POI detail when open; hidden when no speech recognizer |
| Music side control | **Horizontal collapsed:** LHD = right-aligned title + artist (marquee); RHD = left-aligned — both with faint full-width visualizer (`DockMiniVisualizer(wide=true, 32 bars, barAlpha 0.22`). **Horizontal open / vertical:** icon-sized thick 6-bar visualizer. Tap toggles `ActivePanel.MEDIA`. Fixed 280dp slot width. |
| App drawer Settings (gear) | Opens `ActivePanel.SETTINGS` in media pane; closes drawer |

**Driver cluster order** (`DriverSideCluster`): LHD = `[App Drawer][Mic]`; RHD = `[Mic][App Drawer]` (horizontal and vertical).

**App Drawer icon position** (via `dockItemOrder()`): vertical side dock → **top** (first item); horizontal bottom dock LHD → **left** (first); RHD → **right** (last).

**App Drawer overlay** lists launchable apps from `LaunchableAppsRepository` (background preload in `LauncherViewModel`, cached for process lifetime; lazy icons via `rememberAppIcon` per row). Excludes Mix Auto (`BuildConfig.APPLICATION_ID`). Sorted by name with search. Header has **Settings** gear (opens Launcher Settings) + close. **Audio apps** show trailing music-note icon; tap sets default/preferred audio source and opens media pane (no launch). Other apps tap launch. Long-press → **Add/Remove from shortcut bar** (up to 5 pinned), App Info, or Uninstall. Overlay covers map+media split only — shortcut bar remains visible; `SplitScreenAppDrawerSlot` uses `fillMaxSize()` + `zIndex(1f)` with no outer padding (map bleeds through if padded).

**Pinned shortcut bar apps:** `LauncherPreferences.dockPinnedPackages` (JSON, max 5). Long-press in drawer to pin; icons appear in dock center. Audio apps get scaled music-note badge (`iconSize * 0.55f`); tap → `handleSelectAudioSource` (toggle media pane if already active, else set default + `setPreferredAudioSource` + open pane). App drawer uses `openAudioSource` (always set + open). Long-press pinned icon for same menu. `LauncherViewModel.toggleDockPinnedPackage()`.

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

**On-device schema** (`filesDir/places/{iso}.db`):

- `places` table: id, name, category, address, city, lat, lng, confidence
- `places_fts` FTS5 virtual table (external content) for fast text search
- `meta` table: country_iso, country_name, record_count, generated_date

**Search integration:** `MapLibreEngineImpl.searchDestination()` fans out to local SQLite (FTS5 + bbox) and Photon in parallel, deduplicates within 50 m, sorts by distance. Optional 500 km cap (default ON) via Launcher Settings **"Nearby results only (within 500 km)"** — persisted in `LauncherPreferences.limitSearchDistance`, passed as `limitDistance` to the engine.

**Recent destinations:** Up to 10 saved in `LauncherPreferences.recentDestinations` (JSON in SharedPreferences). `LauncherViewModel.addRecentDestination()` prepends, dedupes within 50 m, persists. Flow: `MainActivity` → `DashboardScreen` → `CarMapViewContainer` → `NavigationSearchOverlay`; any selection calls `onDestinationSelected` before `navigateToCoordinates`.

**Saved places:** Up to 50 in `LauncherPreferences.savedPlaces` (same JSON schema + `isDroppedPin`). `LauncherViewModel.toggleSavedPlace()` dedupes at 50 m. Starred POIs show gold teardrop icons on map via `mapEngine.setSavedPlaces()` synced from `DashboardScreen`. Empty search field shows **Recent + Nearby** suggestions; header **star toggle** switches to saved-only list (typed search filters saved locally). Each row has a star button. **Short map tap** selects saved pin, active custom pin, or POI pin (`handleMapPointSelection()`). **Long press** on empty map drops a custom pin (`startCustomPinDraft()`); long press in free-drive GPS tracking exits to top-down view at the drop point. POI detail opens in media pane via `ActivePanel.POI_DETAIL`.

**Default audio source:** `LauncherPreferences.defaultAudioPackage` — first launch with no default shows inline app list in media pane; tap sets default and launches app. Boot: `MainActivity.refreshMediaSessionsAndBootAudio()` → `ensureDefaultPlayerIfNeeded()` wakes default via `BackgroundAudioLauncher.wakeWithForegroundFallback()` (MediaBrowser bind + play, not foreground launch) when no active session; user taps still use `launchAppByPackage()`. Overlay picker: tap launches app; long-press row sets default. Source button: tap launches active app or opens picker when idle; **long-press always opens picker**.

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
- Dynamic discovery of all launchable apps (currently fixed shortcut list) — **app drawer implemented**; user can pin up to 5 apps to dock center
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
| Emulator "already running" with no window | Stale AVD lock — delete `%USERPROFILE%\.android\avd\{AVD_NAME}.avd\*.lock` (e.g. `multiinstance.lock`) when no `emulator`/`qemu` process is running |
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
| Cyan route line persists after ending navigation | `startFreeDrive()` → `removeRouteLayers()` removes all four route layers + two sources (legacy single-source IDs too) |
| Camera snaps flat immediately after route draw | `showRouteThenDive()` — 2 s bounds animation (`ROUTE_OVERVIEW_ANIMATION_MS`) then 10 s hold (`ROUTE_OVERVIEW_HOLD_MS`) with timer bar before `enterNavigationCamera()`; set `CameraMode.NONE` before overview |
| Map zoomed out on startup despite GPS fixes | Style callback must call `startFreeDrive()` after `activateLocationTracking()`; do not enable `TRACKING` before snap — fallback zoom 6 locks wide view |
| Free drive camera twitching/jitter | Do not re-snap on every GPS fix — `hasSnappedCameraToGps` + one `moveCamera` then `TRACKING_GPS`; no duplicate `startFreeDrive()` from `retryLocationActivation()` |
| Free drive looks flat (not Android Auto slanted) | Free drive uses `FREE_DRIVE_TILT` 50° and `CameraMode.TRACKING_GPS` with GPS bearing; nav uses tilt 63° / zoom 18.5 |
| Nav camera frozen while GPS puck moves / dive never zooms in | `enterNavigationCamera()`: `CameraMode.NONE` → `map.animateCamera()` to zoom 18.5 / tilt 63° → `TRACKING_GPS` in callback; do NOT use `zoomWhileTracking`/`tiltWhileTracking` immediately after mode change (ignored during transition) |
| Route overview shows POI area not full route | Navigate from POI preview left `pendingPoiPreviewTarget` — `startNavigation()` must call `clearRoutePreviewState()`; `handleMapLayoutChange()` re-fits via `fitRouteOverviewCamera()` while `routeOverviewJob` active |
| Nav stays flat after route overview (HUD active, no 63° tilt) | `handleMapLayoutChange`/`applyDrivingTrackingPadding` cancelled dive animation — guard with `navigationCameraTransitionActive`; `activateNavigationTracking()` snaps nav zoom/tilt before `TRACKING_GPS` if animation interrupted |
| Map doesn't rotate to direction of travel | Use `RenderMode.GPS` not `COMPASS` in `activateLocationTracking()` and camera entry |
| UI cut off by status/nav bars on phone | `MainActivity` is edge-to-edge (`setDecorFitsSystemWindows(false)`); add `systemBarsPadding()` on `DashboardScreen` root `Box` |
| Status bar hidden on Eonon / head unit | Remove `android:windowFullscreen` from `themes.xml` (day + night); `MainActivity` calls `WindowInsetsControllerCompat.show(systemBars())` — do not re-add fullscreen theme flag |
| Media pane shows "Enable notification access" | Settings → Special app access → Notification access → enable MixAuto; start playback in YT Music/Spotify then return to launcher |
| Transport row shows dim MusicNote instead of app icon | Active session: first slot shows source app icon and launches via `launchAppByPackage()`; no session: cyan `MusicNote` opens `AudioPlayerPickerOverlay` in media pane (tap row to launch YT Music/Spotify/etc.) |
| Audio player list empty on API 30+ | Manifest `<queries>` must include `CATEGORY_APP_MUSIC` and `android.media.browse.MediaBrowserService`; discovery in `AudioPlayerUtils.loadAudioPlayerApps()` uses `MATCH_ALL` on both |
| Music dock badge missing | Removed — use dock music side control or pinned audio apps with music-note badge |
| Skip-next media button shrinks in narrow pane | `MediaPlayerPane.kt`: use `weight(1f)` slots + `requiredSize(MinTapTarget)` on all three controls |
| `setMapMediaRatio` JVM signature clash | Use `updateMapMediaRatio()` in `LauncherViewModel` — property already generates `setMapMediaRatio` setter |
| Map/media divider only moves on repeated swipes | Do not include `mapMediaRatio` in `pointerInput` keys in `MapMediaDividerHandle` — use `rememberUpdatedState` + accumulate drag from `onDragStart` |
| Wide black gap between map and media panes | Divider touch target must not use `MinTapTarget` as Row/Column layout size — use `seamTouchTarget` (0 dp seam, 64 dp hit area overlapping panes) in `DashboardScreen.kt` |
| Resize map vs media | Drag the cyan 3-dot handle on the pane border (not Settings); ratio 0.3–0.8, persisted in `LauncherPreferences.mapMediaRatio` |
| Philippines download returns HTTP 404 | Attach `ph_places.db.gz` + `countries.json` as **release assets** on `mix-auto-overture-maps` (committing to repo is not enough); verify `/releases/latest/download/countries.json` returns 200 |
| Catalog loads but download 404 | Release exists but assets array empty — edit release and upload both files from `places-dist/` |
| Manual import of `.gz` fails | Import path expects uncompressed `.db`; use in-app Download or decompress first |
| Search shows overseas / 5000 km destinations | Default 500 km cap is ON — disable **Nearby results only (within 500 km)** in Launcher Settings to include farther Photon results; routing still may fail for long/cross-water trips |
| Empty search shows no Nearby rows | Nearby loads from offline DB at panel open (±0.5° bbox) merged with `poiCache` — install Map Data (PH pack); snapshot origin on open; no pan/zoom required |
| Vector tiles show POI labels but no teardrop pins | `queryTilePois()` must use `map.queryRenderedFeatures` on Liberty layer IDs (`poi_r1`, `poi_r7`, `poi_r20`, `poi_transit`) — `VectorSource.querySourceFeatures("poi")` misses rendered POIs; zoom ≥ 15 for Liberty POI labels |
| Pin tap does not center map | `animateTopDownCamera()` must call `clearViewportPaddingForPreview()` (zero map + tracking padding), defer animation via `mapView.post { }` so it runs after POI detail 40/60 split resize, and `handleMapLayoutChange()` must re-center on `selectedPoi` when `isInTopDownView` — do not re-apply driving padding from `OnLayoutChangeListener` during POI preview |
| Saved tab empty | Star a place from map tap drawer or search row star button — `LauncherPreferences.savedPlaces` persists up to 50 entries |
| Map tap shows Dropped Pin instead of POI name | Short tap no longer drops pins — use **long press** on empty map; short tap on POI pin selects that POI |
| Long press does not drop custom pin | Disabled during navigation; must long-press empty map (not on existing pin icon) |
| Long press in free drive still follows GPS | Only exits to top view when `!isCameraDetached` (active GPS tracking); manual pan already sets detached — camera stays put, pin still drops |
| Tapping near saved custom pin re-opens it instead of new draft | Do not add geographic proximity fallback before draft — use layer hit + `isTapNearPinIcon()` screen radius only |
| Route line disappears while navigating | Raster style: `ensureRouteLayers()` must `addLayerAbove(..., "osm")` — plain `addLayer()` puts line under tiles |
| Route line too thin | `ROUTE_WIDTH` 14f with 18f dark casing per half (traveled + remaining) in `ensureRouteLayers()` |
| Puck renders under route line during nav | Route layers added after LocationComponent activation — call `ensurePuckAboveOverlays()` after `drawRoute()`; uses `resolvePuckLayerAnchorId()` → `layerAbove(ROUTE_REMAINING_LAYER_ID)` |
| Passed route segment not greyed | `updateRouteProgress()` splits geometry into `ROUTE_TRAVELED_*` (grey) and `ROUTE_REMAINING_*` (cyan) on road-snapped GPS fix; monotonic advance only |
| Puck pauses/jumps between GPS fixes | Dead-reckoning `forceLocationUpdate` loop disabled (native render crash); free drive relies on LocationEngine; ensure `hasSpeed()` on fixes for bearing; head-unit gap between 1 Hz fixes is expected until custom LocationEngine interpolator added |
| App SIGSEGV on emulator RenderThread after GPS fix | `libmaplibre.so` `MapRenderer::render` fault addr `0x30` — do NOT call high-frequency `forceLocationUpdate` in free drive; stay on MapLibre 11.5.1; try disabling TomTom traffic on emulator; crashes may be emulator-only — validate on Eonon |
| Saved pins show colored streaks while driving | Use `poiIconOnlyLayerProperties()` for `mix-saved-layer` — no on-map `textField` on tilted camera |
| Puck spins when stopped in traffic | `enrichLocationWithBearing()` freezes bearing via `lastStableBearing` when speed < `STOPPED_SPEED_MPS` (1.4 m/s) |
| Puck size slider tap/skip has no effect | Do NOT use `onValueChangeFinished` with captured `puckScale` — stale closure reverts value same frame; use `onValueChange = onPuckScaleChange` only in `DashboardScreen.kt` |
| Puck size slider jolts camera | `setPuckScale(scale)` is style-only via `applyStyle()` — do NOT call `forceLocationUpdateForImmediateRender()` for scale changes (unlike viewport padding) |
| Compass hidden under search icon | `MapLibreEngineImpl.configureMapUiChrome()` moves compass to bottom-right via `setCompassGravity(BOTTOM \| END)` |
| MapLibre ℹ overlaps logo text | `configureMapUiChrome()` must use **92 dp left** on `setAttributionMargins` (`ATTRIBUTION_LEFT_MARGIN_DP`) — SDK default clears logo width; uniform margins collapse ℹ onto logo |
| Map settings button crowds watermark | `MapSettingsFab` in `CarMapViewContainer.kt` is bottom-start with `MapLibreAttributionReserveDp` (40 dp); bump to 44–48 dp on device if needed |
| Nav doesn't re-route after wrong turn | Off-route reroute needs 2 GPS fixes >75 m from `routeGeometryPoints`; 5 s cooldown between reroutes; check Logcat for `Off route` / `Re-routing` |
| Minimized music pane reopens after navigation | Do not call `updateMusicPaneEnabled(true)` in nav overlay entry (`onToggleSearch`, route picker `LaunchedEffect`, POI `LaunchedEffect`, etc.) — only dock music / audio-source actions should restore `ActivePanel.MEDIA` |
| Music player reopens after cold start when minimized | `DashboardScreen` must init `activePanel` from `LauncherPreferences.musicPaneEnabled` (not hard-coded `ActivePanel.MEDIA`); persist via `LauncherViewModel.updateMusicPaneEnabled()` on dock music toggle |
| Music doesn't auto-play on launch | Enable notification access; set **default audio source** in media pane (first-run list or long-press source icon → picker); boot wake uses `BackgroundAudioLauncher` MediaBrowser connect + play; `maybeAutoPlay()` runs once per process when paused session has metadata — Logcat `BackgroundAudioLauncher`: `Background wake succeeded` / `MediaSessionRepository`: `Auto-playing paused media session on launch` |
| YT Music steals foreground on launcher boot | Boot should use `BackgroundAudioLauncher` not `launchAppByPackage`; if MediaBrowser fails, brief flash then Mix Auto refocus — check Logcat `Background wake failed, using foreground launch + refocus` |
| No audio on cold boot until car Next pressed | YT Music not running yet — passive `getActiveSessions()` empty; set default in Mix Auto so boot launch runs; car media keys use OS pipeline, not Mix Auto |
| Can't open audio source list after default set | **Long-press** source app icon in transport row (tap launches active app when session exists); short tap opens picker only when no session |
| Shortcut bar too small on head unit | Settings → **Shortcut Icon Size** slider: Small (1×), Medium (1.5×), Large (2×) on `DockHorizontalTapTarget` / `DockHorizontalIconSize` in both horizontal and vertical dock |
| Vertical shortcut bar too wide / large side padding | Vertical dock must use `wrapContentWidth()` in `ShortcutDock` + `DashboardScreen` — not `.weight(DockVerticalWeight)`; icon-only, no labels |
| Pinned shortcut icons left of screen center (horizontal) | Do NOT center pinned apps in a `Row` middle `weight(1f)` column — 280 dp music slot vs ~2-icon driver cluster skews center left; use full-width `Box` with `CenterDockCluster` at `Alignment.Center` and edge clusters overlaid in `ShortcutDock.kt` |
| App drawer malforms vertical shortcut bar (floating dock, black gap) | Do NOT stack `AppDrawerOverlay` above `ShortcutDock` in a side Column — overlay map+media `Box` only via `SplitScreenAppDrawerSlot`; dock stays `wrapContentWidth().fillMaxHeight()` sibling |
| App drawer long-press menu at top-left / app names missing | Per-item `Box` + `DropdownMenu` in `AppDrawerItem` — not hoisted to overlay root; anchor `Box` = `fillMaxWidth()` only (no `wrapContentSize` on grid cells) |
| App drawer opens slowly / freezes | Old path loaded every icon on main thread each open — use `LaunchableAppsRepository` + `LauncherViewModel.ensureLaunchableAppsLoaded()` (background metadata) and `rememberAppIcon` per row |
| App drawer gap at top shows map | Do NOT put `.padding(PaneGap)` on `SplitScreenAppDrawerSlot` overlay modifier — padding shrinks Surface and map shows in margin; use inner Column padding only |
| Drawer/panel header too tall | Use shared `PanelHeaderRow` (`ui/components/PanelHeaderRow.kt`); close button is 48 dp not 64 dp; Destination Search + Map Settings + Route Picker use `compact = true` (40 dp); destination field must not use fixed 92 dp height or floating label — placeholder only |
| Route picker closes then second 10s timer before nav | `confirmRouteSelection()` must call `beginNavigationAfterRouteSelection()` → `enterNavigationCamera()` — do NOT call `showRouteThenDive()` after multi-route picker (that runs a second overview hold) |
| TomTom route missing in picker (only OSRM cards) | Traffic Flow key test ≠ Routing API — enable TomTom **Routing** on developer key; `TomTomRoutingClient.fetchRoute()` fails silently and omits card |
| Only one route, no picker shown | Expected when OSRM returns no distinct alternative and TomTom absent/fails — single route uses `showRouteThenDive()` once |
| TomTom traffic HTTP 400 `Invalid style: relative-delay` | Orbis v2 raster tiles only accept `style=light` or `style=dark` — not legacy styles (`relative`, `relative-delay`, etc.); see `MapLibreEngineImpl.applyTrafficOverlay()` |
| TomTom traffic not showing / key unknown | Map Settings (layers FAB) → paste key → tap **NetworkCheck** icon on key field; enable **Traffic Overlay** toggle directly under key field; enable Traffic Flow product on key; Logcat tag `TomTomTrafficClient` |
| Traffic tiles fail with HTTP 403 | Invalid key or Traffic Flow API not enabled for that TomTom developer key |
| Update check fails HTTP 404 | App uses GitHub Releases API (`AppUpdateRepository.RELEASES_API_URL`), not a `version.json` asset — attach `mix-auto.apk` to the release and set tag to semver (e.g. `0.0.5`); download URL is `releases/latest/download/mix-auto.apk` |
| No update banner on launch | Boot check deferred until onboarding completes; banner only when GitHub `tag_name` is newer than `BuildConfig.VERSION_NAME` — temporarily lower `versionName` or publish newer release to test |
| Update snackbar reappears on every rotation | Boot `checkForUpdate(autoPrompt=true)` must run only when `savedInstanceState == null`; `AppUpdateViewModel.bootAutoCheckDone` guards repeat auto-prompt per process |
| Navigation / search resets on rotation | Map engine must live in `MapHostViewModel` (not `MainActivity.onCreate`); `activePanel` + `destinationSearchState` in `LauncherViewModel`; `CarMapViewContainer` must not call `engine.onDestroy()` on activity destroy |
| Install button does nothing | API 26+ may need **Install unknown apps** for Mix Auto — `launchApkInstall()` opens that settings screen when permission missing |
| Update snackbar hidden under dock | `AppUpdatePrompts` snackbar uses `padding(bottom = 88.dp)` above shortcut bar |
| Vinyl album art shows as square/rectangle | `VinylAlbumArt`: use `clip(CircleShape)` then `graphicsLayer { rotationZ }` — not `Modifier.rotate` before clip |
| Long-press on album art does nothing / swipes conflict with picker | Long-press opens `AlbumArtModePicker`; normal swipe gestures disabled while `isPickerOpen` in `MediaPlayerPane.kt` |
| Album art too small in tall portrait pane | Size via `BoxWithConstraints`: `(if (maxWidth < maxHeight) maxWidth else maxHeight) * 0.92f` |
| Album art mode (Vinyl/Visualizer) resets after Settings/search/rotate/restart | Persist via `LauncherPreferences.albumArtMode` + `LauncherViewModel.updateAlbumArtMode()` — do NOT use `remember` in `MediaPlayerPane`; panel/orientation changes dispose the composable |
| Visualizer bars twitch too fast or move in lockstep | `AnimatedSpectrumVisualizer` uses frame smoothing + slow band-blend waves; album = `VisualizerBarLayout.THIN` (24 bars); dock = `VisualizerBarLayout.DOCK` (6 thick bars) — do not share one bar width formula for both |
| Visualizer not reacting to real bass/treble | MediaSession provides `playbackPositionMs` only — no FFT/Visualizer API for YT Music on AOSP; motion is position-anchored simulation, not audio analysis |
| Unlike skips track in YT Music | `MediaSessionRepository.toggleLike()` never sends dislike/thumb_down — use unrated or re-toggle like action only |
| Like heart not lit when returning to song | Per-track `likedTrackCache` in `MediaSessionRepository` keyed by media ID / title\|artist |
| Voice button opens Assistant instead of search | Hardware key is often wired to Gemini and never reaches the app — use **shortcut dock mic** (Voice destination search); grant **Microphone** permission; Eonon may send `KEYCODE_SEARCH` instead of `KEYCODE_VOICE_ASSIST` (both handled in `MainActivity.onKeyDown` when search already open) |
| Mic listening stops immediately | Consecutive dock mic: do not call `startListening()` when UI `isListening` is false — always cancel-then-restart via `pendingVoiceRestart` + `onError` in `NavigationSearchOverlay.kt`; also Gemini may hold the speech engine; check mic permission; `SpeechRecognizer.onError` is silent — conflicts show as brief “Listening…” flash |
| Mic button missing in search overlay / dock | `SpeechRecognizer.isRecognitionAvailable()` false on bare AOSP — install Google app; mic hidden in overlay and dock when no recognizer |
| Nav TTS reads "800m…500m…200m" in one breath | **Removed** — Option A uses ahead/prepare/turn only (`NavigationVoiceController` + `NavTtsPhrases.kt`); one cue per GPS tick via threshold crossing |
| No nav-start traffic line | Needs TomTom API key + **Traffic Incidents** product; Tier 1 `findJamOnRoute()` or Tier 2 `trafficDelaySeconds ≥ 120`; prefetch during route overview — skip if IO not done before dive |
| Maneuver TTS too early/late | Constants in `NavigationVoiceController`: early 35 s, prepare 12 s / 200 m, turn 3 s / 40 m; continue phrase still uses distance on long segments |
| No nav voice / robotic voice | Map Settings → **Voice navigation** ON; install a better system TTS (Google TTS APK, NekoSpeak/Piper) — app uses `TextToSpeech`, no bundled voice |
| Nav voice too quiet | Map Settings → **Voice volume** slider (50–100%, default 100%); tap speaker icon to preview "In 500 meters, turn right"; also check head-unit guidance volume in system settings |
| Nav voice during route overview | By design silent until `activateNavigationTracking()` after 10 s overview + camera dive — do not speak from route fetch |
| Onboarding wizard shows icon only, no title/body | `OnboardingWizard` root must be `Surface(color = OledBlack)` — bare `Box` leaves `LocalContentColor` black and `Car*Text` invisible on OLED background |
| Re-test permission onboarding wizard | Clear app data or set `launcher_prefs` key `onboarding_version` to `0`; wizard shows when `onboardingVersion < CURRENT_ONBOARDING_VERSION` in `OnboardingWizard.kt` |
| Search/POI pane too narrow or divider still draggable | Destination Search + POI Details lock split to **40% map / 60% secondary** via `OVERLAY_MAP_MEDIA_RATIO` (0.4f) in `DashboardScreen.kt`; `MapMediaDividerHandle` hidden (`showMapMediaDivider`); do NOT persist 0.4 to `LauncherPreferences` — closing restores saved ratio |
| Destination search empty after app update (PH DB installed) | Origin could be `(0,0)` before GPS (PH bbox + Photon bias miss user area; 500 km filter drops all); DB file Installed but connection closed — `ensureDatabaseOpen()` on search; sideload latest build with hardened `resolveSearchOrigin()` + `refreshSearchOrigin()` on search open |
| Typed search "No results" while driving (puck moving) | Fixed: snapshot origin on panel open; do not key search `LaunchedEffect` on live GPS (was cancelling Photon mid-flight); voice sets query on `onResults` only; Logcat should show one `searchDestination` per query change |
| Map blank when tapping search result | Do not `onDismiss()` before POI detail — use `onPreviewSearchPlace` to set `POI_DETAIL` + `focusOnPoi()`; engine retries preview camera via `onMapHostLayoutChanged()` |
| Close POI after search preview returns to media not search | Fixed via `poiReturnToSearch` in `DashboardScreen.kt` — set in `onPreviewSearchPlace`, branch `LaunchedEffect(selectedPoi)` on dismiss; clear flag on Navigate |
| Two close buttons on POI details | Use header `OverlayCloseButton` only — no Close in `PoiDetailCardContent` action row |
| Map partially blank (tiles in corner, beige elsewhere) | Lazy `setPadding` vs full MapView — use `CameraUpdateFactory.paddingTo()` in `applyMapPaddingImmediate()`; top-view layout change → `syncTopDownViewportPaddingOnly()` (padding only, preserve pan/zoom); `scheduleTopDownViewportSync()` on next frame — padding only, not camera |
| Top view snaps back to puck on pan/zoom | Do NOT recenter on `lastKnownLocation` during top-view explore — removed `refreshTopDownExploreCamera()`; guard `snapCameraToGpsIfNeeded`/`activateFreeDriveTrackingMode` when `isInTopDownView`; cancel pending viewport sync on user gesture |
| CropFree / free-drive pan feels stiff or fights finger | Manual pan must call `ensureTopDownCameraDetached()` + cancel dead reckoning on `REASON_API_GESTURE`; `topDownExploreUserAdjusted` skips layout padding sync in CropFree explore; `applyDrivingTrackingPadding`/`activateFreeDriveTrackingMode`/`handleMapLayoutChange` no-op when `isCameraDetached` — do not re-apply `moveCamera(paddingTo)` or `forceLocationUpdate` while user is exploring |
| Top view too zoomed out / POI labels overlap | `TOP_DOWN_EXPLORE_ZOOM` is **15.0** (CropFree); mix POI overlay hidden in top-view — native Liberty labels only |
| Can't pin more than 5 apps to shortcut bar | Max **5** pinned apps (`LauncherPreferences.MAX_DOCK_PINNED_APPS`); Add to shortcut bar is disabled in long-press menu when full |
| Pinned shortcut missing after app uninstall | `LauncherViewModel.loadValidatedDockPinnedPackages()` removes unlaunchable packages on init |
| Free drive camera doesn't follow puck | `applyDrivingTrackingPadding()` must skip `moveCamera(paddingTo)` when `cameraMode == TRACKING_GPS` — use `paddingWhileTracking` + `forceLocationUpdateForImmediateRender()` only; `applyAndroidLocation()` re-calls `activateFreeDriveTrackingMode()` when `!isCameraDetached`; clear `isInTopDownView` in `recenterCamera()` (before snap), `dismissSelectedPoi()`, and cancel `topDownViewportSync` in `startFreeDrive()` |
| Puck under TomTom traffic lines | `resolvePuckLayerAnchorId()` + `ensurePuckAboveOverlays()` — puck must `layerAbove(mix-traffic-layer)` in free drive, above `ROUTE_REMAINING_LAYER_ID` when navigating; call after `applyTrafficOverlay()` and `drawRoute()` |
| Speed readout position / size | Bottom-center `SpeedCircle` in `CarMapViewContainer.kt` — 54dp circle, 4dp bottom inset, speed 17sp + `km/h` 10sp; not puck-attached |
| "Free Drive" text in top-left HUD | Top-left HUD shows **only when navigating** — free drive has no street placeholder; speed is on bottom-center `SpeedCircle` |
| Status strip clock invisible | `DashboardStatusBar` must use `Surface(color = OledBlack, contentColor = OnDark)` — bare background leaves black text on black |
| Status strip weather/city empty | Needs GPS fix in `MapUiState.currentLat/currentLng`; city from `NominatimReverseGeocodeClient`, weather from Open-Meteo; shows `—` until fetch completes |
| Status strip traffic reel empty / "Traffic on" fallback | Reel needs Traffic **Incidents** on TomTom key (Flow tile test alone insufficient); enable overlay + key; Logcat `TomTomTrafficClient`; 403 → incidents product missing; no jam incidents in bbox → green "No major traffic detected" |
| Status strip traffic text truncated | Reel uses `basicMarquee` — do not re-add `MAX_HEADLINE_CHARS` client truncation or `TextOverflow.Ellipsis` on reel; keep `StatusStripTrafficReelGap` so clock/weather don't steal width |
| Gap between status strip and map/media | Strip has no bottom padding; map uses `reduceTopInset = showStatusStrip` (`StatusStripAdjacentGap` 0dp top); landscape media uses `reduceTopInset = showStatusStrip && !isPortrait` — do not zero portrait media top gap |
| Map settings icon cluttered (map + pencil) | `MapSettingsFab` in `CarMapViewContainer.kt` uses single **`Icons.Filled.Layers`** — do not overlay Map + Edit badges |
| MapLibre watermark touches ℹ button | `MapLibreEngineImpl.configureMapUiChrome()`: `ATTRIBUTION_LEFT_MARGIN_DP` **98f** (logo ~92 dp + gap) on `setAttributionMargins` — bump to 100–104 dp if still tight on device |

## Related agent resources

- Cursor rules: `.cursor/rules/mix-auto-android.mdc`
- Session archive: `C:/dev/skills/session-history/mix-auto/`
