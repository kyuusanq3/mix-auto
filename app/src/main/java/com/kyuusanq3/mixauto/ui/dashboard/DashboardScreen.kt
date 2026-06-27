package com.kyuusanq3.mixauto.ui.dashboard

import android.content.res.Configuration
import android.speech.SpeechRecognizer
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import com.kyuusanq3.mixauto.domain.media.MediaPlaybackState
import com.kyuusanq3.mixauto.ui.components.CarMapViewContainer
import com.kyuusanq3.mixauto.ui.components.DashboardStatusBar
import com.kyuusanq3.mixauto.ui.components.carScrollbar
import com.kyuusanq3.mixauto.ui.components.MapSettingsPanelContent
import com.kyuusanq3.mixauto.ui.components.NavigationSearchContent
import com.kyuusanq3.mixauto.ui.components.OverlayCloseButton
import com.kyuusanq3.mixauto.ui.components.PoiDetailPane
import com.kyuusanq3.mixauto.ui.components.SettingsSwitchRow
import com.kyuusanq3.mixauto.ui.settings.AppUpdateState
import com.kyuusanq3.mixauto.ui.settings.AppUpdateViewModel
import com.kyuusanq3.mixauto.ui.settings.LauncherPreferences
import com.kyuusanq3.mixauto.ui.settings.LauncherViewModel
import com.kyuusanq3.mixauto.ui.settings.MapDataViewModel
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarHeadlineText
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import java.io.File
import kotlin.math.roundToInt

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

private fun dismissToBasePanel(musicPaneEnabled: Boolean): ActivePanel =
    if (musicPaneEnabled) ActivePanel.MEDIA else ActivePanel.HIDDEN

@Composable
private fun BoxScope.SplitScreenAppDrawerSlot(
    visible: Boolean,
    dockPinnedPackages: List<String>,
    onToggleDockPin: (String) -> Unit,
    onSelectAudioSource: (String) -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    AppDrawerOverlay(
        dockPinnedPackages = dockPinnedPackages,
        maxDockPinnedApps = LauncherPreferences.MAX_DOCK_PINNED_APPS,
        onToggleDockPin = onToggleDockPin,
        onSelectAudioSource = onSelectAudioSource,
        onOpenLauncherSettings = onOpenLauncherSettings,
        onDismiss = onDismiss,
        modifier = Modifier
            .fillMaxSize()
            .padding(CarDimensions.PaneGap),
    )
}

@Composable
fun DashboardScreen(
    mapEngine: CarMapEngine,
    mapDataViewModel: MapDataViewModel,
    mediaState: MediaPlaybackState,
    defaultAudioPackage: String,
    onSetDefaultAudioPackage: (String) -> Unit,
    onSelectAudioSource: (String) -> Unit,
    onMediaPlayPause: () -> Unit,
    onMediaSkipPrevious: () -> Unit,
    onMediaSkipNext: () -> Unit,
    onMediaToggleLike: () -> Unit,
    albumArtMode: AlbumArtMode,
    onAlbumArtModeChange: (AlbumArtMode) -> Unit,
    isLeftHandDrive: Boolean,
    isShortcutsHorizontal: Boolean,
    mapMediaRatio: Float,
    limitSearchDistance: Boolean,
    recentDestinations: List<SearchResultPlace>,
    savedPlaces: List<SearchResultPlace>,
    onDestinationSelected: (SearchResultPlace) -> Unit,
    onToggleSavedPlace: (SearchResultPlace) -> Unit,
    onUpdateSavedPlace: (SearchResultPlace) -> Unit,
    useVectorTiles: Boolean,
    show3dBuildings: Boolean,
    showTraffic: Boolean,
    tomTomApiKey: String,
    isLauncherMode: Boolean,
    isLargeShortcutIcons: Boolean,
    dockPinnedPackages: List<String>,
    onToggleDockPin: (String) -> Unit,
    drivingZoom: Float,
    puckHorizontalOffset: Float,
    puckVerticalOffset: Float,
    puckScale: Float,
    onToggleLhd: () -> Unit,
    onToggleShortcutsHorizontal: () -> Unit,
    onMapMediaRatioChange: (Float) -> Unit,
    onToggleLimitSearchDistance: () -> Unit,
    onToggleVectorTiles: () -> Unit,
    onToggleShow3dBuildings: () -> Unit,
    onToggleTraffic: () -> Unit,
    onTomTomApiKeyChange: (String) -> Unit,
    onToggleLauncherMode: () -> Unit,
    onToggleLargeShortcutIcons: () -> Unit,
    onDrivingZoomChange: (Float) -> Unit,
    onPuckHorizontalOffsetChange: (Float) -> Unit,
    onPuckVerticalOffsetChange: (Float) -> Unit,
    onPuckScaleChange: (Float) -> Unit,
    showStatusStrip: Boolean,
    showSystemStatusBar: Boolean,
    onToggleShowStatusStrip: () -> Unit,
    onToggleShowSystemStatusBar: () -> Unit,
    onInstallApk: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activePanel by remember { mutableStateOf(ActivePanel.MEDIA) }
    var musicPaneEnabled by remember { mutableStateOf(true) }
    var poiReturnToSearch by remember { mutableStateOf(false) }
    val onClearPoiReturnToSearch = { poiReturnToSearch = false }
    val launcherViewModel: LauncherViewModel = viewModel()
    val showSecondaryPane = activePanel != ActivePanel.HIDDEN
    val onTogglePanel: (ActivePanel) -> Unit = { target ->
        when (target) {
            ActivePanel.MEDIA -> when (activePanel) {
                ActivePanel.MEDIA -> {
                    musicPaneEnabled = false
                    activePanel = ActivePanel.HIDDEN
                }
                ActivePanel.HIDDEN -> {
                    musicPaneEnabled = true
                    activePanel = ActivePanel.MEDIA
                }
                else -> {
                    musicPaneEnabled = true
                    activePanel = ActivePanel.MEDIA
                }
            }
            ActivePanel.SETTINGS -> when (activePanel) {
                ActivePanel.SETTINGS -> activePanel = dismissToBasePanel(musicPaneEnabled)
                else -> activePanel = ActivePanel.SETTINGS
            }
            ActivePanel.APP_DRAWER -> when (activePanel) {
                ActivePanel.APP_DRAWER -> activePanel = dismissToBasePanel(musicPaneEnabled)
                else -> activePanel = ActivePanel.APP_DRAWER
            }
            ActivePanel.SEARCH,
            ActivePanel.POI_DETAIL,
            ActivePanel.MAP_DATA,
            -> Unit
            ActivePanel.HIDDEN -> {
                musicPaneEnabled = true
                activePanel = ActivePanel.MEDIA
            }
        }
    }
    val handleSelectAudioSource: (String) -> Unit = { packageName ->
        val isActiveSource = if (mediaState.hasActiveSession) {
            mediaState.sourcePackage == packageName
        } else {
            defaultAudioPackage == packageName
        }
        if (isActiveSource) {
            onTogglePanel(ActivePanel.MEDIA)
        } else {
            onSelectAudioSource(packageName)
            musicPaneEnabled = true
            activePanel = ActivePanel.MEDIA
        }
    }
    val openAudioSource: (String) -> Unit = { packageName ->
        onSelectAudioSource(packageName)
        musicPaneEnabled = true
        activePanel = ActivePanel.MEDIA
    }
    val onDismissPanel = {
        if (activePanel == ActivePanel.SEARCH) {
            launcherViewModel.isDestinationSearchOpen = false
        }
        activePanel = dismissToBasePanel(musicPaneEnabled)
    }
    val onDismissAppDrawer = { activePanel = dismissToBasePanel(musicPaneEnabled) }
    val onOpenLauncherSettingsFromDrawer = { activePanel = ActivePanel.SETTINGS }
    val isDestinationPanelOpen =
        activePanel == ActivePanel.SEARCH || activePanel == ActivePanel.POI_DETAIL
    val isMapSettingsPanelOpen = activePanel == ActivePanel.MAP_DATA
    val onToggleSearch = {
        musicPaneEnabled = true
        when (activePanel) {
            ActivePanel.SEARCH -> {
                launcherViewModel.isDestinationSearchOpen = false
                activePanel = dismissToBasePanel(musicPaneEnabled)
            }
            ActivePanel.POI_DETAIL -> mapEngine.dismissSelectedPoi()
            else -> {
                launcherViewModel.isDestinationSearchOpen = true
                activePanel = ActivePanel.SEARCH
            }
        }
    }
    val onToggleMapSettings = {
        musicPaneEnabled = true
        when (activePanel) {
            ActivePanel.MAP_DATA -> activePanel = dismissToBasePanel(musicPaneEnabled)
            ActivePanel.POI_DETAIL -> mapEngine.dismissSelectedPoi()
            else -> activePanel = ActivePanel.MAP_DATA
        }
    }
    val onVoiceSearch = {
        musicPaneEnabled = true
        if (activePanel == ActivePanel.POI_DETAIL) {
            mapEngine.dismissSelectedPoi()
        }
        if (activePanel == ActivePanel.SEARCH) {
            launcherViewModel.triggerVoiceSearch()
        } else {
            launcherViewModel.setStartVoiceOnSearchOpen()
            launcherViewModel.isDestinationSearchOpen = true
            activePanel = ActivePanel.SEARCH
        }
    }
    val onPreviewSearchPlace: (SearchResultPlace) -> Unit = { place ->
        poiReturnToSearch = true
        mapEngine.focusOnPoi(place)
        musicPaneEnabled = true
        launcherViewModel.isDestinationSearchOpen = false
        activePanel = ActivePanel.POI_DETAIL
    }
    val mapUiState by mapEngine.uiState.collectAsStateWithLifecycle()
    val appUpdateViewModel: AppUpdateViewModel = viewModel()
    val appUpdateState by appUpdateViewModel.uiState.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val voiceSearchAvailable = remember(context) {
        SpeechRecognizer.isRecognitionAvailable(context)
    }
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val isSplitLockedForOverlay =
        activePanel == ActivePanel.SEARCH ||
            activePanel == ActivePanel.POI_DETAIL ||
            activePanel == ActivePanel.MAP_DATA
    val effectiveMapMediaRatio =
        if (isSplitLockedForOverlay) OVERLAY_MAP_MEDIA_RATIO else mapMediaRatio
    val effectiveMediaWeight = 1f - effectiveMapMediaRatio
    val showMapMediaDivider = showSecondaryPane && !isSplitLockedForOverlay
    var portraitMapMediaContainerPx by remember { mutableStateOf(0f) }
    var landscapeMapMediaContainerPx by remember { mutableStateOf(0f) }
    var verticalDockRowWidthPx by remember { mutableStateOf(0f) }
    var verticalDockWidthPx by remember { mutableStateOf(0f) }
    val verticalDockMapMediaContainerPx = (verticalDockRowWidthPx - verticalDockWidthPx)
        .coerceAtLeast(0f)

    LaunchedEffect(savedPlaces) {
        mapEngine.setSavedPlaces(savedPlaces)
    }

    LaunchedEffect(activePanel) {
        launcherViewModel.isDestinationSearchOpen = activePanel == ActivePanel.SEARCH
        when (activePanel) {
            ActivePanel.SEARCH,
            ActivePanel.MAP_DATA,
            -> mapEngine.setMapTapDismissHandler(onDismissPanel)
            else -> mapEngine.setMapTapDismissHandler(null)
        }
    }

    LaunchedEffect(mapUiState.selectedPoi) {
        if (mapUiState.selectedPoi != null) {
            musicPaneEnabled = true
            activePanel = ActivePanel.POI_DETAIL
        } else if (activePanel == ActivePanel.POI_DETAIL) {
            if (poiReturnToSearch) {
                poiReturnToSearch = false
                activePanel = ActivePanel.SEARCH
            } else {
                activePanel = dismissToBasePanel(musicPaneEnabled)
            }
        }
    }

    LaunchedEffect(activePanel, effectiveMapMediaRatio, mapUiState.selectedPoi) {
        mapEngine.onMapHostLayoutChanged()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack)
            .then(
                if (showSystemStatusBar) {
                    Modifier.systemBarsPadding()
                } else {
                    Modifier
                },
            ),
    ) {
        if (showStatusStrip) {
            DashboardStatusBar(
                mapEngine = mapEngine,
                showTraffic = showTraffic,
                tomTomApiKey = tomTomApiKey,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
        when {
            isPortrait -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { portraitMapMediaContainerPx = it.height.toFloat() },
                        ) {
                            CarMapViewContainer(
                                engine = mapEngine,
                                onToggleSearch = onToggleSearch,
                                isDestinationPanelOpen = isDestinationPanelOpen,
                                onToggleMapSettings = onToggleMapSettings,
                                isMapSettingsPanelOpen = isMapSettingsPanelOpen,
                                puckScale = puckScale,
                                modifier = Modifier
                                    .weight(if (showSecondaryPane) effectiveMapMediaRatio else 1f)
                                    .fillMaxWidth(),
                            )
                            if (showSecondaryPane) {
                                if (showMapMediaDivider) {
                                    MapMediaDividerHandle(
                                        isVertical = false,
                                        containerSizePx = portraitMapMediaContainerPx,
                                        mapMediaRatio = mapMediaRatio,
                                        onMapMediaRatioChange = onMapMediaRatioChange,
                                    )
                                }
                                DashboardSecondaryPane(
                                    activePanel = activePanel,
                                    mapEngine = mapEngine,
                                    mapDataViewModel = mapDataViewModel,
                                    onDismissPanel = onDismissPanel,
                                    onPreviewSearchPlace = onPreviewSearchPlace,
                                    recentDestinations = recentDestinations,
                                    savedPlaces = savedPlaces,
                                    onDestinationSelected = onDestinationSelected,
                                    onToggleSavedPlace = onToggleSavedPlace,
                                    onUpdateSavedPlace = onUpdateSavedPlace,
                                    onDismissSelectedPoi = { mapEngine.dismissSelectedPoi() },
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
                                    tomTomApiKey = tomTomApiKey,
                                    isLauncherMode = isLauncherMode,
                                    isLargeShortcutIcons = isLargeShortcutIcons,
                                    drivingZoom = drivingZoom,
                                    puckHorizontalOffset = puckHorizontalOffset,
                                    puckVerticalOffset = puckVerticalOffset,
                                    onToggleLhd = onToggleLhd,
                                    onToggleShortcutsHorizontal = onToggleShortcutsHorizontal,
                                    onToggleLimitSearchDistance = onToggleLimitSearchDistance,
                                    onToggleVectorTiles = onToggleVectorTiles,
                                    onToggleShow3dBuildings = onToggleShow3dBuildings,
                                    onToggleTraffic = onToggleTraffic,
                                    onTomTomApiKeyChange = onTomTomApiKeyChange,
                                    onToggleLauncherMode = onToggleLauncherMode,
                                    onToggleLargeShortcutIcons = onToggleLargeShortcutIcons,
                                    onDrivingZoomChange = onDrivingZoomChange,
                                    onPuckHorizontalOffsetChange = onPuckHorizontalOffsetChange,
                                    onPuckVerticalOffsetChange = onPuckVerticalOffsetChange,
                                    puckScale = puckScale,
                                    onPuckScaleChange = onPuckScaleChange,
                                    showStatusStrip = showStatusStrip,
                                    showSystemStatusBar = showSystemStatusBar,
                                    onToggleShowStatusStrip = onToggleShowStatusStrip,
                                    onToggleShowSystemStatusBar = onToggleShowSystemStatusBar,
                                    appUpdateState = appUpdateState,
                                    onCheckForUpdate = appUpdateViewModel::checkForUpdate,
                                    onDownloadUpdate = appUpdateViewModel::downloadUpdate,
                                    onInstallApk = onInstallApk,
                                    modifier = Modifier
                                        .weight(effectiveMediaWeight)
                                        .fillMaxWidth(),
                                )
                            }
                        }
                        SplitScreenAppDrawerSlot(
                            visible = activePanel == ActivePanel.APP_DRAWER,
                            dockPinnedPackages = dockPinnedPackages,
                            onToggleDockPin = onToggleDockPin,
                            onSelectAudioSource = openAudioSource,
                            onOpenLauncherSettings = onOpenLauncherSettingsFromDrawer,
                            onDismiss = onDismissAppDrawer,
                        )
                    }
                    ShortcutDock(
                        isHorizontal = true,
                        isLargeIcons = isLargeShortcutIcons,
                        isLeftHandDrive = isLeftHandDrive,
                        activePanel = activePanel,
                        mediaState = mediaState,
                        voiceSearchAvailable = voiceSearchAvailable,
                        defaultAudioPackage = defaultAudioPackage,
                        dockPinnedPackages = dockPinnedPackages,
                        onToggleDockPin = onToggleDockPin,
                        onSelectAudioSource = handleSelectAudioSource,
                        onTogglePanel = onTogglePanel,
                        onVoiceSearch = onVoiceSearch,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = CarDimensions.PaneGap)
                            .wrapContentHeight(),
                    )
                }
            }
            isShortcutsHorizontal -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { landscapeMapMediaContainerPx = it.width.toFloat() },
                        ) {
                        if (isLeftHandDrive) {
                            CarMapViewContainer(
                                engine = mapEngine,
                                onToggleSearch = onToggleSearch,
                                isDestinationPanelOpen = isDestinationPanelOpen,
                                onToggleMapSettings = onToggleMapSettings,
                                isMapSettingsPanelOpen = isMapSettingsPanelOpen,
                                puckScale = puckScale,
                                modifier = Modifier
                                    .weight(if (showSecondaryPane) effectiveMapMediaRatio else 1f)
                                    .fillMaxSize(),
                            )
                            if (showSecondaryPane) {
                                if (showMapMediaDivider) {
                                    MapMediaDividerHandle(
                                        isVertical = true,
                                        containerSizePx = landscapeMapMediaContainerPx,
                                        mapMediaRatio = mapMediaRatio,
                                        onMapMediaRatioChange = onMapMediaRatioChange,
                                    )
                                }
                                DashboardSecondaryPane(
                                    activePanel = activePanel,
                                    mapEngine = mapEngine,
                                    mapDataViewModel = mapDataViewModel,
                                    onDismissPanel = onDismissPanel,
                                    onPreviewSearchPlace = onPreviewSearchPlace,
                                    recentDestinations = recentDestinations,
                                    savedPlaces = savedPlaces,
                                    onDestinationSelected = onDestinationSelected,
                                    onToggleSavedPlace = onToggleSavedPlace,
                                onUpdateSavedPlace = onUpdateSavedPlace,
                                    onDismissSelectedPoi = { mapEngine.dismissSelectedPoi() },
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
                                    tomTomApiKey = tomTomApiKey,
                                    isLauncherMode = isLauncherMode,
                                    isLargeShortcutIcons = isLargeShortcutIcons,
                                    drivingZoom = drivingZoom,
                                    puckHorizontalOffset = puckHorizontalOffset,
                                    puckVerticalOffset = puckVerticalOffset,
                                    onToggleLhd = onToggleLhd,
                                    onToggleShortcutsHorizontal = onToggleShortcutsHorizontal,
                                    onToggleLimitSearchDistance = onToggleLimitSearchDistance,
                                    onToggleVectorTiles = onToggleVectorTiles,
                                    onToggleShow3dBuildings = onToggleShow3dBuildings,
                                    onToggleTraffic = onToggleTraffic,
                                    onTomTomApiKeyChange = onTomTomApiKeyChange,
                                    onToggleLauncherMode = onToggleLauncherMode,
                                    onToggleLargeShortcutIcons = onToggleLargeShortcutIcons,
                                    onDrivingZoomChange = onDrivingZoomChange,
                                    onPuckHorizontalOffsetChange = onPuckHorizontalOffsetChange,
                                    onPuckVerticalOffsetChange = onPuckVerticalOffsetChange,
                                    puckScale = puckScale,
                                    onPuckScaleChange = onPuckScaleChange,
                                    showStatusStrip = showStatusStrip,
                                    showSystemStatusBar = showSystemStatusBar,
                                    onToggleShowStatusStrip = onToggleShowStatusStrip,
                                    onToggleShowSystemStatusBar = onToggleShowSystemStatusBar,
                                    appUpdateState = appUpdateState,
                                    onCheckForUpdate = appUpdateViewModel::checkForUpdate,
                                    onDownloadUpdate = appUpdateViewModel::downloadUpdate,
                                    onInstallApk = onInstallApk,
                                    modifier = Modifier
                                        .weight(effectiveMediaWeight)
                                        .fillMaxSize(),
                                )
                            }
                        } else {
                            if (showSecondaryPane) {
                                DashboardSecondaryPane(
                                    activePanel = activePanel,
                                    mapEngine = mapEngine,
                                    mapDataViewModel = mapDataViewModel,
                                    onDismissPanel = onDismissPanel,
                                    onPreviewSearchPlace = onPreviewSearchPlace,
                                    recentDestinations = recentDestinations,
                                    savedPlaces = savedPlaces,
                                    onDestinationSelected = onDestinationSelected,
                                    onToggleSavedPlace = onToggleSavedPlace,
                                onUpdateSavedPlace = onUpdateSavedPlace,
                                    onDismissSelectedPoi = { mapEngine.dismissSelectedPoi() },
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
                                    tomTomApiKey = tomTomApiKey,
                                    isLauncherMode = isLauncherMode,
                                    isLargeShortcutIcons = isLargeShortcutIcons,
                                    drivingZoom = drivingZoom,
                                    puckHorizontalOffset = puckHorizontalOffset,
                                    puckVerticalOffset = puckVerticalOffset,
                                    onToggleLhd = onToggleLhd,
                                    onToggleShortcutsHorizontal = onToggleShortcutsHorizontal,
                                    onToggleLimitSearchDistance = onToggleLimitSearchDistance,
                                    onToggleVectorTiles = onToggleVectorTiles,
                                    onToggleShow3dBuildings = onToggleShow3dBuildings,
                                    onToggleTraffic = onToggleTraffic,
                                    onTomTomApiKeyChange = onTomTomApiKeyChange,
                                    onToggleLauncherMode = onToggleLauncherMode,
                                    onToggleLargeShortcutIcons = onToggleLargeShortcutIcons,
                                    onDrivingZoomChange = onDrivingZoomChange,
                                    onPuckHorizontalOffsetChange = onPuckHorizontalOffsetChange,
                                    onPuckVerticalOffsetChange = onPuckVerticalOffsetChange,
                                    puckScale = puckScale,
                                    onPuckScaleChange = onPuckScaleChange,
                                    showStatusStrip = showStatusStrip,
                                    showSystemStatusBar = showSystemStatusBar,
                                    onToggleShowStatusStrip = onToggleShowStatusStrip,
                                    onToggleShowSystemStatusBar = onToggleShowSystemStatusBar,
                                    appUpdateState = appUpdateState,
                                    onCheckForUpdate = appUpdateViewModel::checkForUpdate,
                                    onDownloadUpdate = appUpdateViewModel::downloadUpdate,
                                    onInstallApk = onInstallApk,
                                    modifier = Modifier
                                        .weight(effectiveMediaWeight)
                                        .fillMaxSize(),
                                )
                                if (showMapMediaDivider) {
                                    MapMediaDividerHandle(
                                        isVertical = true,
                                        containerSizePx = landscapeMapMediaContainerPx,
                                        mapMediaRatio = mapMediaRatio,
                                        onMapMediaRatioChange = onMapMediaRatioChange,
                                        invertDrag = true,
                                    )
                                }
                            }
                            CarMapViewContainer(
                                engine = mapEngine,
                                onToggleSearch = onToggleSearch,
                                isDestinationPanelOpen = isDestinationPanelOpen,
                                onToggleMapSettings = onToggleMapSettings,
                                isMapSettingsPanelOpen = isMapSettingsPanelOpen,
                                puckScale = puckScale,
                                modifier = Modifier
                                    .weight(if (showSecondaryPane) effectiveMapMediaRatio else 1f)
                                    .fillMaxSize(),
                            )
                        }
                        }
                        SplitScreenAppDrawerSlot(
                            visible = activePanel == ActivePanel.APP_DRAWER,
                            dockPinnedPackages = dockPinnedPackages,
                            onToggleDockPin = onToggleDockPin,
                            onSelectAudioSource = openAudioSource,
                            onOpenLauncherSettings = onOpenLauncherSettingsFromDrawer,
                            onDismiss = onDismissAppDrawer,
                        )
                    }
                    ShortcutDock(
                        isHorizontal = true,
                        isLargeIcons = isLargeShortcutIcons,
                        isLeftHandDrive = isLeftHandDrive,
                        activePanel = activePanel,
                        mediaState = mediaState,
                        voiceSearchAvailable = voiceSearchAvailable,
                        defaultAudioPackage = defaultAudioPackage,
                        dockPinnedPackages = dockPinnedPackages,
                        onToggleDockPin = onToggleDockPin,
                        onSelectAudioSource = handleSelectAudioSource,
                        onTogglePanel = onTogglePanel,
                        onVoiceSearch = onVoiceSearch,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = CarDimensions.PaneGap)
                            .wrapContentHeight(),
                    )
                }
            }
            else -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { verticalDockRowWidthPx = it.width.toFloat() },
                ) {
                    if (isLeftHandDrive) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                CarMapViewContainer(
                                    engine = mapEngine,
                                    onToggleSearch = onToggleSearch,
                                isDestinationPanelOpen = isDestinationPanelOpen,
                                onToggleMapSettings = onToggleMapSettings,
                                isMapSettingsPanelOpen = isMapSettingsPanelOpen,
                                    puckScale = puckScale,
                                    modifier = Modifier
                                        .weight(
                                            if (showSecondaryPane) {
                                                effectiveMapMediaRatio
                                            } else {
                                                effectiveMapMediaRatio + effectiveMediaWeight
                                            },
                                        )
                                        .fillMaxSize(),
                                )
                                if (showSecondaryPane) {
                                    if (showMapMediaDivider) {
                                        MapMediaDividerHandle(
                                            isVertical = true,
                                            containerSizePx = verticalDockMapMediaContainerPx,
                                            mapMediaRatio = mapMediaRatio,
                                            onMapMediaRatioChange = onMapMediaRatioChange,
                                        )
                                    }
                                    DashboardSecondaryPane(
                                        activePanel = activePanel,
                                        mapEngine = mapEngine,
                                        mapDataViewModel = mapDataViewModel,
                                        onDismissPanel = onDismissPanel,
                                        onPreviewSearchPlace = onPreviewSearchPlace,
                                        recentDestinations = recentDestinations,
                                        savedPlaces = savedPlaces,
                                        onDestinationSelected = onDestinationSelected,
                                        onToggleSavedPlace = onToggleSavedPlace,
                                        onUpdateSavedPlace = onUpdateSavedPlace,
                                        onDismissSelectedPoi = { mapEngine.dismissSelectedPoi() },
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
                                        tomTomApiKey = tomTomApiKey,
                                        isLauncherMode = isLauncherMode,
                                        isLargeShortcutIcons = isLargeShortcutIcons,
                                        drivingZoom = drivingZoom,
                                        puckHorizontalOffset = puckHorizontalOffset,
                                        puckVerticalOffset = puckVerticalOffset,
                                        onToggleLhd = onToggleLhd,
                                        onToggleShortcutsHorizontal = onToggleShortcutsHorizontal,
                                        onToggleLimitSearchDistance = onToggleLimitSearchDistance,
                                        onToggleVectorTiles = onToggleVectorTiles,
                                        onToggleShow3dBuildings = onToggleShow3dBuildings,
                                        onToggleTraffic = onToggleTraffic,
                                        onTomTomApiKeyChange = onTomTomApiKeyChange,
                                        onToggleLauncherMode = onToggleLauncherMode,
                                        onToggleLargeShortcutIcons = onToggleLargeShortcutIcons,
                                        onDrivingZoomChange = onDrivingZoomChange,
                                        onPuckHorizontalOffsetChange = onPuckHorizontalOffsetChange,
                                        onPuckVerticalOffsetChange = onPuckVerticalOffsetChange,
                                        puckScale = puckScale,
                                        onPuckScaleChange = onPuckScaleChange,
                                        showStatusStrip = showStatusStrip,
                                        showSystemStatusBar = showSystemStatusBar,
                                        onToggleShowStatusStrip = onToggleShowStatusStrip,
                                        onToggleShowSystemStatusBar = onToggleShowSystemStatusBar,
                                        appUpdateState = appUpdateState,
                                        onCheckForUpdate = appUpdateViewModel::checkForUpdate,
                                        onDownloadUpdate = appUpdateViewModel::downloadUpdate,
                                        onInstallApk = onInstallApk,
                                        modifier = Modifier
                                            .weight(effectiveMediaWeight)
                                            .fillMaxSize(),
                                    )
                                }
                            }
                            SplitScreenAppDrawerSlot(
                                visible = activePanel == ActivePanel.APP_DRAWER,
                                dockPinnedPackages = dockPinnedPackages,
                                onToggleDockPin = onToggleDockPin,
                                onSelectAudioSource = openAudioSource,
                                onOpenLauncherSettings = onOpenLauncherSettingsFromDrawer,
                                onDismiss = onDismissAppDrawer,
                            )
                        }
                        ShortcutDock(
                            isHorizontal = false,
                            isLargeIcons = isLargeShortcutIcons,
                            isLeftHandDrive = isLeftHandDrive,
                            activePanel = activePanel,
                            mediaState = mediaState,
                            voiceSearchAvailable = voiceSearchAvailable,
                            defaultAudioPackage = defaultAudioPackage,
                            dockPinnedPackages = dockPinnedPackages,
                            onToggleDockPin = onToggleDockPin,
                            onSelectAudioSource = handleSelectAudioSource,
                            onTogglePanel = onTogglePanel,
                            onVoiceSearch = onVoiceSearch,
                            modifier = Modifier
                                .wrapContentWidth()
                                .fillMaxHeight()
                                .onSizeChanged { verticalDockWidthPx = it.width.toFloat() },
                        )
                    } else {
                        ShortcutDock(
                            isHorizontal = false,
                            isLargeIcons = isLargeShortcutIcons,
                            isLeftHandDrive = isLeftHandDrive,
                            activePanel = activePanel,
                            mediaState = mediaState,
                            voiceSearchAvailable = voiceSearchAvailable,
                            defaultAudioPackage = defaultAudioPackage,
                            dockPinnedPackages = dockPinnedPackages,
                            onToggleDockPin = onToggleDockPin,
                            onSelectAudioSource = handleSelectAudioSource,
                            onTogglePanel = onTogglePanel,
                            onVoiceSearch = onVoiceSearch,
                            modifier = Modifier
                                .wrapContentWidth()
                                .fillMaxHeight()
                                .onSizeChanged { verticalDockWidthPx = it.width.toFloat() },
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                CarMapViewContainer(
                                    engine = mapEngine,
                                    onToggleSearch = onToggleSearch,
                                isDestinationPanelOpen = isDestinationPanelOpen,
                                onToggleMapSettings = onToggleMapSettings,
                                isMapSettingsPanelOpen = isMapSettingsPanelOpen,
                                    puckScale = puckScale,
                                    modifier = Modifier
                                        .weight(
                                            if (showSecondaryPane) {
                                                effectiveMapMediaRatio
                                            } else {
                                                effectiveMapMediaRatio + effectiveMediaWeight
                                            },
                                        )
                                        .fillMaxSize(),
                                )
                                if (showSecondaryPane) {
                                    if (showMapMediaDivider) {
                                        MapMediaDividerHandle(
                                            isVertical = true,
                                            containerSizePx = verticalDockMapMediaContainerPx,
                                            mapMediaRatio = mapMediaRatio,
                                            onMapMediaRatioChange = onMapMediaRatioChange,
                                        )
                                    }
                                    DashboardSecondaryPane(
                                        activePanel = activePanel,
                                        mapEngine = mapEngine,
                                        mapDataViewModel = mapDataViewModel,
                                        onDismissPanel = onDismissPanel,
                                        onPreviewSearchPlace = onPreviewSearchPlace,
                                        recentDestinations = recentDestinations,
                                        savedPlaces = savedPlaces,
                                        onDestinationSelected = onDestinationSelected,
                                        onToggleSavedPlace = onToggleSavedPlace,
                                        onUpdateSavedPlace = onUpdateSavedPlace,
                                        onDismissSelectedPoi = { mapEngine.dismissSelectedPoi() },
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
                                        tomTomApiKey = tomTomApiKey,
                                        isLauncherMode = isLauncherMode,
                                        isLargeShortcutIcons = isLargeShortcutIcons,
                                        drivingZoom = drivingZoom,
                                        puckHorizontalOffset = puckHorizontalOffset,
                                        puckVerticalOffset = puckVerticalOffset,
                                        onToggleLhd = onToggleLhd,
                                        onToggleShortcutsHorizontal = onToggleShortcutsHorizontal,
                                        onToggleLimitSearchDistance = onToggleLimitSearchDistance,
                                        onToggleVectorTiles = onToggleVectorTiles,
                                        onToggleShow3dBuildings = onToggleShow3dBuildings,
                                        onToggleTraffic = onToggleTraffic,
                                        onTomTomApiKeyChange = onTomTomApiKeyChange,
                                        onToggleLauncherMode = onToggleLauncherMode,
                                        onToggleLargeShortcutIcons = onToggleLargeShortcutIcons,
                                        onDrivingZoomChange = onDrivingZoomChange,
                                        onPuckHorizontalOffsetChange = onPuckHorizontalOffsetChange,
                                        onPuckVerticalOffsetChange = onPuckVerticalOffsetChange,
                                        puckScale = puckScale,
                                        onPuckScaleChange = onPuckScaleChange,
                                        showStatusStrip = showStatusStrip,
                                        showSystemStatusBar = showSystemStatusBar,
                                        onToggleShowStatusStrip = onToggleShowStatusStrip,
                                        onToggleShowSystemStatusBar = onToggleShowSystemStatusBar,
                                        appUpdateState = appUpdateState,
                                        onCheckForUpdate = appUpdateViewModel::checkForUpdate,
                                        onDownloadUpdate = appUpdateViewModel::downloadUpdate,
                                        onInstallApk = onInstallApk,
                                        modifier = Modifier
                                            .weight(effectiveMediaWeight)
                                            .fillMaxSize(),
                                    )
                                }
                            }
                            SplitScreenAppDrawerSlot(
                                visible = activePanel == ActivePanel.APP_DRAWER,
                                dockPinnedPackages = dockPinnedPackages,
                                onToggleDockPin = onToggleDockPin,
                                onSelectAudioSource = openAudioSource,
                                onOpenLauncherSettings = onOpenLauncherSettingsFromDrawer,
                                onDismiss = onDismissAppDrawer,
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun DashboardSecondaryPane(
    activePanel: ActivePanel,
    mapEngine: CarMapEngine,
    mapDataViewModel: MapDataViewModel,
    onDismissPanel: () -> Unit,
    onPreviewSearchPlace: (SearchResultPlace) -> Unit,
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
    tomTomApiKey: String,
    isLauncherMode: Boolean,
    isLargeShortcutIcons: Boolean,
    drivingZoom: Float,
    puckHorizontalOffset: Float,
    puckVerticalOffset: Float,
    onToggleLhd: () -> Unit,
    onToggleShortcutsHorizontal: () -> Unit,
    onToggleLimitSearchDistance: () -> Unit,
    onToggleVectorTiles: () -> Unit,
    onToggleShow3dBuildings: () -> Unit,
    onToggleTraffic: () -> Unit,
    onTomTomApiKeyChange: (String) -> Unit,
    onToggleLauncherMode: () -> Unit,
    onToggleLargeShortcutIcons: () -> Unit,
    onDrivingZoomChange: (Float) -> Unit,
    onPuckHorizontalOffsetChange: (Float) -> Unit,
    onPuckVerticalOffsetChange: (Float) -> Unit,
    puckScale: Float,
    onPuckScaleChange: (Float) -> Unit,
    showStatusStrip: Boolean,
    showSystemStatusBar: Boolean,
    onToggleShowStatusStrip: () -> Unit,
    onToggleShowSystemStatusBar: () -> Unit,
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
        onPreviewSearchPlace = onPreviewSearchPlace,
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
        tomTomApiKey = tomTomApiKey,
        isLauncherMode = isLauncherMode,
        isLargeShortcutIcons = isLargeShortcutIcons,
        drivingZoom = drivingZoom,
        puckHorizontalOffset = puckHorizontalOffset,
        puckVerticalOffset = puckVerticalOffset,
        onToggleLhd = onToggleLhd,
        onToggleShortcutsHorizontal = onToggleShortcutsHorizontal,
        onToggleLimitSearchDistance = onToggleLimitSearchDistance,
        onToggleVectorTiles = onToggleVectorTiles,
        onToggleShow3dBuildings = onToggleShow3dBuildings,
        onToggleTraffic = onToggleTraffic,
        onTomTomApiKeyChange = onTomTomApiKeyChange,
        onToggleLauncherMode = onToggleLauncherMode,
        onToggleLargeShortcutIcons = onToggleLargeShortcutIcons,
        onDrivingZoomChange = onDrivingZoomChange,
        onPuckHorizontalOffsetChange = onPuckHorizontalOffsetChange,
        onPuckVerticalOffsetChange = onPuckVerticalOffsetChange,
        puckScale = puckScale,
        onPuckScaleChange = onPuckScaleChange,
        showStatusStrip = showStatusStrip,
        showSystemStatusBar = showSystemStatusBar,
        onToggleShowStatusStrip = onToggleShowStatusStrip,
        onToggleShowSystemStatusBar = onToggleShowSystemStatusBar,
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
    onPreviewSearchPlace: (SearchResultPlace) -> Unit,
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
    tomTomApiKey: String,
    isLauncherMode: Boolean,
    isLargeShortcutIcons: Boolean,
    drivingZoom: Float,
    puckHorizontalOffset: Float,
    puckVerticalOffset: Float,
    onToggleLhd: () -> Unit,
    onToggleShortcutsHorizontal: () -> Unit,
    onToggleLimitSearchDistance: () -> Unit,
    onToggleVectorTiles: () -> Unit,
    onToggleShow3dBuildings: () -> Unit,
    onToggleTraffic: () -> Unit,
    onTomTomApiKeyChange: (String) -> Unit,
    onToggleLauncherMode: () -> Unit,
    onToggleLargeShortcutIcons: () -> Unit,
    onDrivingZoomChange: (Float) -> Unit,
    onPuckHorizontalOffsetChange: (Float) -> Unit,
    onPuckVerticalOffsetChange: (Float) -> Unit,
    puckScale: Float,
    onPuckScaleChange: (Float) -> Unit,
    showStatusStrip: Boolean,
    showSystemStatusBar: Boolean,
    onToggleShowStatusStrip: () -> Unit,
    onToggleShowSystemStatusBar: () -> Unit,
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
                    isLargeShortcutIcons = isLargeShortcutIcons,
                    showStatusStrip = showStatusStrip,
                    showSystemStatusBar = showSystemStatusBar,
                    onToggleShowStatusStrip = onToggleShowStatusStrip,
                    onToggleShowSystemStatusBar = onToggleShowSystemStatusBar,
                    onToggleLhd = onToggleLhd,
                    onToggleShortcutsHorizontal = onToggleShortcutsHorizontal,
                    onToggleLauncherMode = onToggleLauncherMode,
                    onToggleLargeShortcutIcons = onToggleLargeShortcutIcons,
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
                    limitSearchDistance = limitSearchDistance,
                    useVectorTiles = useVectorTiles,
                    show3dBuildings = show3dBuildings,
                    showTraffic = showTraffic,
                    drivingZoom = drivingZoom,
                    puckHorizontalOffset = puckHorizontalOffset,
                    puckVerticalOffset = puckVerticalOffset,
                    puckScale = puckScale,
                    tomTomApiKey = tomTomApiKey,
                    onToggleLimitSearchDistance = onToggleLimitSearchDistance,
                    onToggleVectorTiles = onToggleVectorTiles,
                    onToggleShow3dBuildings = onToggleShow3dBuildings,
                    onToggleTraffic = onToggleTraffic,
                    onDrivingZoomChange = onDrivingZoomChange,
                    onPuckHorizontalOffsetChange = onPuckHorizontalOffsetChange,
                    onPuckVerticalOffsetChange = onPuckVerticalOffsetChange,
                    onPuckScaleChange = onPuckScaleChange,
                    onTomTomApiKeyChange = onTomTomApiKeyChange,
                    onDismiss = onDismissPanel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ActivePanel.APP_DRAWER,
            ActivePanel.MEDIA,
            ActivePanel.HIDDEN,
            -> {
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
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun SettingsContent(
    isLeftHandDrive: Boolean,
    isShortcutsHorizontal: Boolean,
    isLauncherMode: Boolean,
    isLargeShortcutIcons: Boolean,
    showStatusStrip: Boolean,
    showSystemStatusBar: Boolean,
    onToggleShowStatusStrip: () -> Unit,
    onToggleShowSystemStatusBar: () -> Unit,
    onToggleLhd: () -> Unit,
    onToggleShortcutsHorizontal: () -> Unit,
    onToggleLauncherMode: () -> Unit,
    onToggleLargeShortcutIcons: () -> Unit,
    appUpdateState: AppUpdateState,
    onCheckForUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallApk: (File) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Surface(
        modifier = modifier.carScrollbar(scrollState),
        color = OledBlack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(CarDimensions.PaneGap * 2),
            verticalArrangement = Arrangement.spacedBy(CarDimensions.DockItemSpacing),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CarHeadlineText(
                    text = "Launcher Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                OverlayCloseButton(
                    onClick = onDismiss,
                    contentDescription = "Close settings",
                )
            }

            SettingsSwitchRow(
                    label = "Left-Hand Drive Layout",
                    checked = isLeftHandDrive,
                    onCheckedChange = { checked ->
                        if (checked != isLeftHandDrive) {
                            onToggleLhd()
                        }
                    },
                )

                SettingsSwitchRow(
                    label = "Horizontal Shortcuts",
                    checked = isShortcutsHorizontal,
                    onCheckedChange = { checked ->
                        if (checked != isShortcutsHorizontal) {
                            onToggleShortcutsHorizontal()
                        }
                    },
                )

                SettingsSwitchRow(
                    label = "Large Shortcut Icons",
                    checked = isLargeShortcutIcons,
                    onCheckedChange = { checked ->
                        if (checked != isLargeShortcutIcons) {
                            onToggleLargeShortcutIcons()
                        }
                    },
                )

                SettingsSwitchRow(
                    label = "Status strip (time, date, weather)",
                    checked = showStatusStrip,
                    onCheckedChange = { checked ->
                        if (checked != showStatusStrip) {
                            onToggleShowStatusStrip()
                        }
                    },
                )

                SettingsSwitchRow(
                    label = "System status bar",
                    checked = showSystemStatusBar,
                    onCheckedChange = { checked ->
                        if (checked != showSystemStatusBar) {
                            onToggleShowSystemStatusBar()
                        }
                    },
                )

                SettingsSwitchRow(
                    label = "Launcher Mode (replaces home screen)",
                    checked = isLauncherMode,
                    onCheckedChange = { checked ->
                        if (checked != isLauncherMode) {
                            onToggleLauncherMode()
                        }
                    },
                )

            AppUpdateSection(
                state = appUpdateState,
                onCheckForUpdate = onCheckForUpdate,
                onDownloadUpdate = onDownloadUpdate,
                onInstallApk = onInstallApk,
            )
        }
    }
}

@Composable
private fun AppUpdateSection(
    state: AppUpdateState,
    onCheckForUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallApk: (File) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CarBodyText(
            text = "App Update",
            style = MaterialTheme.typography.bodyLarge,
        )

        when (state) {
            AppUpdateState.Idle,
            AppUpdateState.UpToDate,
            -> {
                if (state is AppUpdateState.UpToDate) {
                    CarLabelText(
                        text = "You are on the latest version",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Button(
                    onClick = onCheckForUpdate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CarDimensions.MinTapTarget),
                ) {
                    CarBodyText(text = "Check for Updates")
                }
            }

            AppUpdateState.Checking -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CarDimensions.MinTapTarget),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(CarDimensions.MinTapTarget / 2),
                        color = ElectricCyan,
                    )
                    CarBodyText(text = "Checking...")
                }
            }

            is AppUpdateState.Available -> {
                Button(
                    onClick = onDownloadUpdate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CarDimensions.MinTapTarget),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = OledBlack,
                    ),
                ) {
                    CarBodyText(text = "v${state.versionName} available — Download")
                }
            }

            is AppUpdateState.Downloading -> {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CarDimensions.PaneGap),
                    color = ElectricCyan,
                )
                CarLabelText(
                    text = "Downloading ${(state.progress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            is AppUpdateState.ReadyToInstall -> {
                Button(
                    onClick = { onInstallApk(state.apkFile) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CarDimensions.PrimaryTapTarget),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = OledBlack,
                    ),
                ) {
                    CarBodyText(text = "Install Now")
                }
            }

            is AppUpdateState.Error -> {
                CarLabelText(
                    text = state.message,
                    style = MaterialTheme.typography.labelMedium,
                )
                Button(
                    onClick = onCheckForUpdate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CarDimensions.MinTapTarget),
                ) {
                    CarBodyText(text = "Retry")
                }
            }
        }
    }
}

private const val OVERLAY_MAP_MEDIA_RATIO = 0.4f

@Composable
private fun MapMediaDividerHandle(
    isVertical: Boolean,
    containerSizePx: Float,
    mapMediaRatio: Float,
    onMapMediaRatioChange: (Float) -> Unit,
    invertDrag: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val currentRatio by rememberUpdatedState(mapMediaRatio)
    val crossAxisModifier = if (isVertical) {
        Modifier.fillMaxHeight()
    } else {
        Modifier.fillMaxWidth()
    }

    Box(
        modifier = modifier
            .then(crossAxisModifier)
            .seamTouchTarget(isVertical = isVertical)
            .zIndex(1f)
            .pointerInput(containerSizePx, invertDrag, isVertical) {
                var dragStartRatio = mapMediaRatio
                var totalDragPx = 0f
                if (isVertical) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragStartRatio = currentRatio
                            totalDragPx = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (containerSizePx > 0f) {
                                totalDragPx += if (invertDrag) -dragAmount else dragAmount
                                val newRatio = (dragStartRatio + totalDragPx / containerSizePx)
                                    .coerceIn(0.3f, 0.8f)
                                onMapMediaRatioChange(newRatio)
                            }
                        },
                    )
                } else {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragStartRatio = currentRatio
                            totalDragPx = 0f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            if (containerSizePx > 0f) {
                                totalDragPx += dragAmount
                                val newRatio = (dragStartRatio + totalDragPx / containerSizePx)
                                    .coerceIn(0.3f, 0.8f)
                                onMapMediaRatioChange(newRatio)
                            }
                        },
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isVertical) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(ElectricCyan, CircleShape),
                    )
                }
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(ElectricCyan, CircleShape),
                    )
                }
            }
        }
    }
}

private fun Modifier.seamTouchTarget(
    isVertical: Boolean,
    layoutSeam: Dp = 0.dp,
    touchSize: Dp = CarDimensions.MinTapTarget,
): Modifier = layout { measurable, constraints ->
    val layoutPx = layoutSeam.roundToPx()
    val touchPx = touchSize.roundToPx()
    val placeable = measurable.measure(
        if (isVertical) {
            Constraints.fixed(
                width = touchPx,
                height = constraints.maxHeight,
            )
        } else {
            Constraints.fixed(
                width = constraints.maxWidth,
                height = touchPx,
            )
        },
    )
    if (isVertical) {
        layout(layoutPx, placeable.height) {
            placeable.place(-((touchPx - layoutPx) / 2f).roundToInt(), 0)
        }
    } else {
        layout(placeable.width, layoutPx) {
            placeable.place(0, -((touchPx - layoutPx) / 2f).roundToInt())
        }
    }
}
