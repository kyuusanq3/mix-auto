package com.kyuusanq3.mixauto.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import com.kyuusanq3.mixauto.domain.media.MediaPlaybackState
import com.kyuusanq3.mixauto.ui.components.AddPlaceFromLinkContent
import com.kyuusanq3.mixauto.ui.components.AudioSettingsPanelContent
import com.kyuusanq3.mixauto.ui.components.MapSettingsPanelContent
import com.kyuusanq3.mixauto.ui.components.NavigationSearchContent
import com.kyuusanq3.mixauto.ui.components.PoiDetailPane
import com.kyuusanq3.mixauto.ui.settings.AppUpdateState
import com.kyuusanq3.mixauto.ui.settings.MapDataViewModel
import com.kyuusanq3.mixauto.ui.settings.SettingsContent
import java.io.File

private const val SAVED_PLACE_DEDUP_THRESHOLD_M = 50f

private fun isSavedPlaceMatch(a: SearchResultPlace, b: SearchResultPlace): Boolean {
    val distanceResults = FloatArray(1)
    android.location.Location.distanceBetween(
        a.latitude,
        a.longitude,
        b.latitude,
        b.longitude,
        distanceResults,
    )
    return distanceResults[0] < SAVED_PLACE_DEDUP_THRESHOLD_M
}

@Composable
internal fun DashboardSecondaryPane(
    activePanel: ActivePanel,
    mapEngine: CarMapEngine,
    mapDataViewModel: MapDataViewModel,
    onDismissPanel: () -> Unit,
    onOpenMapData: () -> Unit,
    onPreviewSearchPlace: (SearchResultPlace) -> Unit,
    onOpenAddFromLink: () -> Unit,
    onDismissAddPlacePanel: () -> Unit,
    onConfirmAddPlaceFromLink: (SearchResultPlace) -> Unit,
    recentDestinations: List<SearchResultPlace>,
    savedPlaces: List<SearchResultPlace>,
    onDestinationSelected: (SearchResultPlace) -> Unit,
    onToggleSavedPlace: (SearchResultPlace) -> Unit,
    onUpdateSavedPlace: (SearchResultPlace) -> Unit,
    onDismissSelectedPoi: () -> Unit,
    onClearPoiReturnToSearch: () -> Unit,
    mediaState: MediaPlaybackState,
    defaultAudioPackage: String,
    onSetDefaultAudioPackage: (String) -> Unit,
    onMediaPlayPause: () -> Unit,
    onMediaSkipPrevious: () -> Unit,
    onMediaSkipNext: () -> Unit,
    onMediaToggleLike: () -> Unit,
    albumArtMode: AlbumArtMode,
    onAlbumArtModeChange: (AlbumArtMode) -> Unit,
    isLeftHandDrive: Boolean,
    isShortcutsHorizontal: Boolean,
    limitSearchDistance: Boolean,
    useVectorTiles: Boolean,
    show3dBuildings: Boolean,
    showTraffic: Boolean,
    navigationVoiceEnabled: Boolean,
    navigationVoiceVolume: Float,
    tomTomApiKey: String,
    isLauncherMode: Boolean,
    shortcutIconSize: DockShortcutIconSize,
    drivingZoom: Float,
    drivingTilt: Float,
    puckHorizontalOffset: Float,
    puckVerticalOffset: Float,
    onToggleLhd: () -> Unit,
    onToggleShortcutsHorizontal: () -> Unit,
    onToggleLimitSearchDistance: () -> Unit,
    onToggleVectorTiles: () -> Unit,
    onToggleShow3dBuildings: () -> Unit,
    onToggleTraffic: () -> Unit,
    onToggleNavigationVoice: () -> Unit,
    onNavigationVoiceVolumeChange: (Float) -> Unit,
    onTestNavigationVoice: () -> Unit,
    onTomTomApiKeyChange: (String) -> Unit,
    onToggleLauncherMode: () -> Unit,
    onShortcutIconSizeChange: (DockShortcutIconSize) -> Unit,
    onDrivingZoomChange: (Float) -> Unit,
    onDrivingTiltChange: (Float) -> Unit,
    onPuckHorizontalOffsetChange: (Float) -> Unit,
    onPuckVerticalOffsetChange: (Float) -> Unit,
    puckScale: Float,
    onPuckScaleChange: (Float) -> Unit,
    showStatusStrip: Boolean,
    showSystemStatusBar: Boolean,
    onToggleShowStatusStrip: () -> Unit,
    onToggleShowSystemStatusBar: () -> Unit,
    reduceTopInset: Boolean,
    appUpdateState: AppUpdateState,
    onCheckForUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallApk: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    MediaOrSettingsPane(
        activePanel = activePanel,
        mapEngine = mapEngine,
        mapDataViewModel = mapDataViewModel,
        onDismissPanel = onDismissPanel,
        onOpenMapData = onOpenMapData,
        onPreviewSearchPlace = onPreviewSearchPlace,
        onOpenAddFromLink = onOpenAddFromLink,
        onDismissAddPlacePanel = onDismissAddPlacePanel,
        onConfirmAddPlaceFromLink = onConfirmAddPlaceFromLink,
        recentDestinations = recentDestinations,
        savedPlaces = savedPlaces,
        onDestinationSelected = onDestinationSelected,
        onToggleSavedPlace = onToggleSavedPlace,
        onUpdateSavedPlace = onUpdateSavedPlace,
        onDismissSelectedPoi = onDismissSelectedPoi,
        onClearPoiReturnToSearch = onClearPoiReturnToSearch,
        mediaState = mediaState,
        defaultAudioPackage = defaultAudioPackage,
        onSetDefaultAudioPackage = onSetDefaultAudioPackage,
        onMediaPlayPause = onMediaPlayPause,
        onMediaSkipPrevious = onMediaSkipPrevious,
        onMediaSkipNext = onMediaSkipNext,
        onMediaToggleLike = onMediaToggleLike,
        albumArtMode = albumArtMode,
        onAlbumArtModeChange = onAlbumArtModeChange,
        isLeftHandDrive = isLeftHandDrive,
        isShortcutsHorizontal = isShortcutsHorizontal,
        limitSearchDistance = limitSearchDistance,
        useVectorTiles = useVectorTiles,
        show3dBuildings = show3dBuildings,
        showTraffic = showTraffic,
        navigationVoiceEnabled = navigationVoiceEnabled,
        navigationVoiceVolume = navigationVoiceVolume,
        tomTomApiKey = tomTomApiKey,
        isLauncherMode = isLauncherMode,
        shortcutIconSize = shortcutIconSize,
        drivingZoom = drivingZoom,
        drivingTilt = drivingTilt,
        puckHorizontalOffset = puckHorizontalOffset,
        puckVerticalOffset = puckVerticalOffset,
        onToggleLhd = onToggleLhd,
        onToggleShortcutsHorizontal = onToggleShortcutsHorizontal,
        onToggleLimitSearchDistance = onToggleLimitSearchDistance,
        onToggleVectorTiles = onToggleVectorTiles,
        onToggleShow3dBuildings = onToggleShow3dBuildings,
        onToggleTraffic = onToggleTraffic,
        onToggleNavigationVoice = onToggleNavigationVoice,
        onNavigationVoiceVolumeChange = onNavigationVoiceVolumeChange,
        onTestNavigationVoice = onTestNavigationVoice,
        onTomTomApiKeyChange = onTomTomApiKeyChange,
        onToggleLauncherMode = onToggleLauncherMode,
        onShortcutIconSizeChange = onShortcutIconSizeChange,
        onDrivingZoomChange = onDrivingZoomChange,
        onDrivingTiltChange = onDrivingTiltChange,
        onPuckHorizontalOffsetChange = onPuckHorizontalOffsetChange,
        onPuckVerticalOffsetChange = onPuckVerticalOffsetChange,
        puckScale = puckScale,
        onPuckScaleChange = onPuckScaleChange,
        showStatusStrip = showStatusStrip,
        showSystemStatusBar = showSystemStatusBar,
        onToggleShowStatusStrip = onToggleShowStatusStrip,
        onToggleShowSystemStatusBar = onToggleShowSystemStatusBar,
        reduceTopInset = reduceTopInset,
        appUpdateState = appUpdateState,
        onCheckForUpdate = onCheckForUpdate,
        onDownloadUpdate = onDownloadUpdate,
        onInstallApk = onInstallApk,
        modifier = modifier,
    )
}

@Composable
private fun MediaOrSettingsPane(
    activePanel: ActivePanel,
    mapEngine: CarMapEngine,
    mapDataViewModel: MapDataViewModel,
    onDismissPanel: () -> Unit,
    onOpenMapData: () -> Unit,
    onPreviewSearchPlace: (SearchResultPlace) -> Unit,
    onOpenAddFromLink: () -> Unit,
    onDismissAddPlacePanel: () -> Unit,
    onConfirmAddPlaceFromLink: (SearchResultPlace) -> Unit,
    recentDestinations: List<SearchResultPlace>,
    savedPlaces: List<SearchResultPlace>,
    onDestinationSelected: (SearchResultPlace) -> Unit,
    onToggleSavedPlace: (SearchResultPlace) -> Unit,
    onUpdateSavedPlace: (SearchResultPlace) -> Unit,
    onDismissSelectedPoi: () -> Unit,
    onClearPoiReturnToSearch: () -> Unit,
    mediaState: MediaPlaybackState,
    defaultAudioPackage: String,
    onSetDefaultAudioPackage: (String) -> Unit,
    onMediaPlayPause: () -> Unit,
    onMediaSkipPrevious: () -> Unit,
    onMediaSkipNext: () -> Unit,
    onMediaToggleLike: () -> Unit,
    albumArtMode: AlbumArtMode,
    onAlbumArtModeChange: (AlbumArtMode) -> Unit,
    isLeftHandDrive: Boolean,
    isShortcutsHorizontal: Boolean,
    limitSearchDistance: Boolean,
    useVectorTiles: Boolean,
    show3dBuildings: Boolean,
    showTraffic: Boolean,
    navigationVoiceEnabled: Boolean,
    navigationVoiceVolume: Float,
    tomTomApiKey: String,
    isLauncherMode: Boolean,
    shortcutIconSize: DockShortcutIconSize,
    drivingZoom: Float,
    drivingTilt: Float,
    puckHorizontalOffset: Float,
    puckVerticalOffset: Float,
    onToggleLhd: () -> Unit,
    onToggleShortcutsHorizontal: () -> Unit,
    onToggleLimitSearchDistance: () -> Unit,
    onToggleVectorTiles: () -> Unit,
    onToggleShow3dBuildings: () -> Unit,
    onToggleTraffic: () -> Unit,
    onToggleNavigationVoice: () -> Unit,
    onNavigationVoiceVolumeChange: (Float) -> Unit,
    onTestNavigationVoice: () -> Unit,
    onTomTomApiKeyChange: (String) -> Unit,
    onToggleLauncherMode: () -> Unit,
    onShortcutIconSizeChange: (DockShortcutIconSize) -> Unit,
    onDrivingZoomChange: (Float) -> Unit,
    onDrivingTiltChange: (Float) -> Unit,
    onPuckHorizontalOffsetChange: (Float) -> Unit,
    onPuckVerticalOffsetChange: (Float) -> Unit,
    puckScale: Float,
    onPuckScaleChange: (Float) -> Unit,
    showStatusStrip: Boolean,
    showSystemStatusBar: Boolean,
    onToggleShowStatusStrip: () -> Unit,
    onToggleShowSystemStatusBar: () -> Unit,
    reduceTopInset: Boolean,
    appUpdateState: AppUpdateState,
    onCheckForUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallApk: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mapUiState by mapEngine.uiState.collectAsStateWithLifecycle()
    val selectedPoi = mapUiState.selectedPoi

    Box(modifier = modifier) {
        when (activePanel) {
            ActivePanel.SEARCH -> {
                NavigationSearchContent(
                    engine = mapEngine,
                    limitSearchDistance = limitSearchDistance,
                    recentDestinations = recentDestinations,
                    savedPlaces = savedPlaces,
                    onToggleSavedPlace = onToggleSavedPlace,
                    onPreviewPlace = onPreviewSearchPlace,
                    onDismiss = onDismissPanel,
                    onOpenMapData = onOpenMapData,
                    onOpenAddFromLink = onOpenAddFromLink,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ActivePanel.ADD_PLACE -> {
                AddPlaceFromLinkContent(
                    mapEngine = mapEngine,
                    onDismiss = onDismissAddPlacePanel,
                    onConfirmSuccess = onConfirmAddPlaceFromLink,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ActivePanel.POI_DETAIL -> {
                selectedPoi?.let { poi ->
                    PoiDetailPane(
                        poi = poi,
                        isStarred = savedPlaces.any { saved -> isSavedPlaceMatch(saved, poi) },
                        nearbyPois = mapUiState.nearbyPois,
                        onStar = { customName ->
                            val namedPoi = poi.copy(name = customName)
                            onToggleSavedPlace(namedPoi)
                        },
                        onNavigate = { customName ->
                            onClearPoiReturnToSearch()
                            val namedPoi = poi.copy(name = customName)
                            onDestinationSelected(namedPoi)
                            if (savedPlaces.any { saved -> isSavedPlaceMatch(saved, poi) }) {
                                onUpdateSavedPlace(namedPoi)
                            }
                            mapEngine.navigateToCoordinates(poi.latitude, poi.longitude)
                        },
                        onSelectNearby = { mapEngine.focusOnPoi(it) },
                        onDismiss = onDismissSelectedPoi,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            ActivePanel.SETTINGS -> {
                SettingsContent(
                    isLeftHandDrive = isLeftHandDrive,
                    isShortcutsHorizontal = isShortcutsHorizontal,
                    isLauncherMode = isLauncherMode,
                    shortcutIconSize = shortcutIconSize,
                    showStatusStrip = showStatusStrip,
                    showSystemStatusBar = showSystemStatusBar,
                    onToggleShowStatusStrip = onToggleShowStatusStrip,
                    onToggleShowSystemStatusBar = onToggleShowSystemStatusBar,
                    onToggleLhd = onToggleLhd,
                    onToggleShortcutsHorizontal = onToggleShortcutsHorizontal,
                    onToggleLauncherMode = onToggleLauncherMode,
                    onShortcutIconSizeChange = onShortcutIconSizeChange,
                    appUpdateState = appUpdateState,
                    onCheckForUpdate = onCheckForUpdate,
                    onDownloadUpdate = onDownloadUpdate,
                    onInstallApk = onInstallApk,
                    onDismiss = onDismissPanel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ActivePanel.MAP_DATA -> {
                MapSettingsPanelContent(
                    mapDataViewModel = mapDataViewModel,
                    mapEngine = mapEngine,
                    limitSearchDistance = limitSearchDistance,
                    useVectorTiles = useVectorTiles,
                    show3dBuildings = show3dBuildings,
                    showTraffic = showTraffic,
                    navigationVoiceEnabled = navigationVoiceEnabled,
                    navigationVoiceVolume = navigationVoiceVolume,
                    drivingZoom = drivingZoom,
                    drivingTilt = drivingTilt,
                    puckHorizontalOffset = puckHorizontalOffset,
                    puckVerticalOffset = puckVerticalOffset,
                    puckScale = puckScale,
                    tomTomApiKey = tomTomApiKey,
                    onToggleLimitSearchDistance = onToggleLimitSearchDistance,
                    onToggleVectorTiles = onToggleVectorTiles,
                    onToggleShow3dBuildings = onToggleShow3dBuildings,
                    onToggleTraffic = onToggleTraffic,
                    onToggleNavigationVoice = onToggleNavigationVoice,
                    onNavigationVoiceVolumeChange = onNavigationVoiceVolumeChange,
                    onTestNavigationVoice = onTestNavigationVoice,
                    onDrivingZoomChange = onDrivingZoomChange,
                    onDrivingTiltChange = onDrivingTiltChange,
                    onPuckHorizontalOffsetChange = onPuckHorizontalOffsetChange,
                    onPuckVerticalOffsetChange = onPuckVerticalOffsetChange,
                    onPuckScaleChange = onPuckScaleChange,
                    onTomTomApiKeyChange = onTomTomApiKeyChange,
                    onDismiss = onDismissPanel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ActivePanel.AUDIO_SETTINGS -> {
                AudioSettingsPanelContent(
                    onDismiss = onDismissPanel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ActivePanel.APP_DRAWER,
            ActivePanel.MEDIA,
            ActivePanel.HIDDEN,
            -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    MediaSessionGlanceWidget(
                        mapEngine = mapEngine,
                        showTraffic = showTraffic,
                        tomTomApiKey = tomTomApiKey,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.4f),
                    )
                    MediaPlayerPane(
                        mediaState = mediaState,
                        defaultAudioPackage = defaultAudioPackage,
                        onSetDefaultAudioPackage = onSetDefaultAudioPackage,
                        albumArtMode = albumArtMode,
                        onAlbumArtModeChange = onAlbumArtModeChange,
                        onPlayPause = onMediaPlayPause,
                        onSkipPrevious = onMediaSkipPrevious,
                        onSkipNext = onMediaSkipNext,
                        onToggleLike = onMediaToggleLike,
                        reduceTopInset = reduceTopInset,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.6f),
                    )
                }
            }
        }
    }
}
