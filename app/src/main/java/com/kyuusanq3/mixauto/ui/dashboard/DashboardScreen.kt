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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.kyuusanq3.mixauto.ui.components.carScrollbar
import com.kyuusanq3.mixauto.ui.components.MapDataPanelContent
import com.kyuusanq3.mixauto.ui.components.NavigationSearchContent
import com.kyuusanq3.mixauto.ui.components.OverlayCloseButton
import com.kyuusanq3.mixauto.ui.components.PoiDetailPane
import com.kyuusanq3.mixauto.ui.settings.AppUpdateState
import com.kyuusanq3.mixauto.ui.settings.AppUpdateViewModel
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
    onDismiss: () -> Unit,
) {
    if (!visible) return
    AppDrawerOverlay(
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
    onMediaPlayPause: () -> Unit,
    onMediaSkipPrevious: () -> Unit,
    onMediaSkipNext: () -> Unit,
    onMediaToggleLike: () -> Unit,
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
    onInstallApk: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activePanel by remember { mutableStateOf(ActivePanel.MEDIA) }
    var musicPaneEnabled by remember { mutableStateOf(true) }
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
            ActivePanel.MAP_DATA -> when (activePanel) {
                ActivePanel.MAP_DATA -> activePanel = dismissToBasePanel(musicPaneEnabled)
                else -> activePanel = ActivePanel.MAP_DATA
            }
            ActivePanel.APP_DRAWER -> when (activePanel) {
                ActivePanel.APP_DRAWER -> activePanel = dismissToBasePanel(musicPaneEnabled)
                else -> activePanel = ActivePanel.APP_DRAWER
            }
            ActivePanel.SEARCH,
            ActivePanel.POI_DETAIL,
            -> Unit
            ActivePanel.HIDDEN -> {
                musicPaneEnabled = true
                activePanel = ActivePanel.MEDIA
            }
        }
    }
    val onDismissPanel = {
        if (activePanel == ActivePanel.SEARCH) {
            launcherViewModel.isDestinationSearchOpen = false
        }
        activePanel = dismissToBasePanel(musicPaneEnabled)
    }
    val onDismissAppDrawer = { activePanel = dismissToBasePanel(musicPaneEnabled) }
    val isDestinationPanelOpen =
        activePanel == ActivePanel.SEARCH || activePanel == ActivePanel.POI_DETAIL
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
    val onToggleVoiceSearch = {
        musicPaneEnabled = true
        when (activePanel) {
            ActivePanel.SEARCH -> {
                launcherViewModel.isDestinationSearchOpen = false
                activePanel = dismissToBasePanel(musicPaneEnabled)
            }
            ActivePanel.POI_DETAIL -> mapEngine.dismissSelectedPoi()
            else -> {
                launcherViewModel.setStartVoiceOnSearchOpen()
                launcherViewModel.isDestinationSearchOpen = true
                activePanel = ActivePanel.SEARCH
            }
        }
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
        activePanel == ActivePanel.SEARCH || activePanel == ActivePanel.POI_DETAIL
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
    }

    LaunchedEffect(mapUiState.selectedPoi) {
        if (mapUiState.selectedPoi != null) {
            musicPaneEnabled = true
            activePanel = ActivePanel.POI_DETAIL
        } else if (activePanel == ActivePanel.POI_DETAIL) {
            activePanel = dismissToBasePanel(musicPaneEnabled)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack)
            .systemBarsPadding(),
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
                                    recentDestinations = recentDestinations,
                                    savedPlaces = savedPlaces,
                                    onDestinationSelected = onDestinationSelected,
                                    onToggleSavedPlace = onToggleSavedPlace,
                                    onUpdateSavedPlace = onUpdateSavedPlace,
                                    onDismissSelectedPoi = { mapEngine.dismissSelectedPoi() },
                                    mediaState = mediaState,
                                    defaultAudioPackage = defaultAudioPackage,
                                    onSetDefaultAudioPackage = onSetDefaultAudioPackage,
                                    onMediaPlayPause = onMediaPlayPause,
                                    onMediaSkipPrevious = onMediaSkipPrevious,
                                    onMediaSkipNext = onMediaSkipNext,
                                    onMediaToggleLike = onMediaToggleLike,
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
                            onDismiss = onDismissAppDrawer,
                        )
                    }
                    ShortcutDock(
                        isHorizontal = true,
                        isLargeIcons = isLargeShortcutIcons,
                        isLeftHandDrive = isLeftHandDrive,
                        activePanel = activePanel,
                        voiceSearchAvailable = voiceSearchAvailable,
                        sourcePackage = mediaState.sourcePackage,
                        onTogglePanel = onTogglePanel,
                        onToggleVoiceSearch = onToggleVoiceSearch,
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
                                    recentDestinations = recentDestinations,
                                    savedPlaces = savedPlaces,
                                    onDestinationSelected = onDestinationSelected,
                                    onToggleSavedPlace = onToggleSavedPlace,
                                onUpdateSavedPlace = onUpdateSavedPlace,
                                    onDismissSelectedPoi = { mapEngine.dismissSelectedPoi() },
                                    mediaState = mediaState,
                                    defaultAudioPackage = defaultAudioPackage,
                                    onSetDefaultAudioPackage = onSetDefaultAudioPackage,
                                    onMediaPlayPause = onMediaPlayPause,
                                    onMediaSkipPrevious = onMediaSkipPrevious,
                                    onMediaSkipNext = onMediaSkipNext,
                                    onMediaToggleLike = onMediaToggleLike,
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
                                    recentDestinations = recentDestinations,
                                    savedPlaces = savedPlaces,
                                    onDestinationSelected = onDestinationSelected,
                                    onToggleSavedPlace = onToggleSavedPlace,
                                onUpdateSavedPlace = onUpdateSavedPlace,
                                    onDismissSelectedPoi = { mapEngine.dismissSelectedPoi() },
                                    mediaState = mediaState,
                                    defaultAudioPackage = defaultAudioPackage,
                                    onSetDefaultAudioPackage = onSetDefaultAudioPackage,
                                    onMediaPlayPause = onMediaPlayPause,
                                    onMediaSkipPrevious = onMediaSkipPrevious,
                                    onMediaSkipNext = onMediaSkipNext,
                                    onMediaToggleLike = onMediaToggleLike,
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
                                modifier = Modifier
                                    .weight(if (showSecondaryPane) effectiveMapMediaRatio else 1f)
                                    .fillMaxSize(),
                            )
                        }
                        }
                        SplitScreenAppDrawerSlot(
                            visible = activePanel == ActivePanel.APP_DRAWER,
                            onDismiss = onDismissAppDrawer,
                        )
                    }
                    ShortcutDock(
                        isHorizontal = true,
                        isLargeIcons = isLargeShortcutIcons,
                        isLeftHandDrive = isLeftHandDrive,
                        activePanel = activePanel,
                        voiceSearchAvailable = voiceSearchAvailable,
                        sourcePackage = mediaState.sourcePackage,
                        onTogglePanel = onTogglePanel,
                        onToggleVoiceSearch = onToggleVoiceSearch,
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
                                        recentDestinations = recentDestinations,
                                        savedPlaces = savedPlaces,
                                        onDestinationSelected = onDestinationSelected,
                                        onToggleSavedPlace = onToggleSavedPlace,
                                        onUpdateSavedPlace = onUpdateSavedPlace,
                                        onDismissSelectedPoi = { mapEngine.dismissSelectedPoi() },
                                        mediaState = mediaState,
                                        defaultAudioPackage = defaultAudioPackage,
                                        onSetDefaultAudioPackage = onSetDefaultAudioPackage,
                                        onMediaPlayPause = onMediaPlayPause,
                                        onMediaSkipPrevious = onMediaSkipPrevious,
                                        onMediaSkipNext = onMediaSkipNext,
                                        onMediaToggleLike = onMediaToggleLike,
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
                                onDismiss = onDismissAppDrawer,
                            )
                        }
                        ShortcutDock(
                            isHorizontal = false,
                            isLargeIcons = isLargeShortcutIcons,
                            isLeftHandDrive = isLeftHandDrive,
                            activePanel = activePanel,
                            voiceSearchAvailable = voiceSearchAvailable,
                            sourcePackage = mediaState.sourcePackage,
                            onTogglePanel = onTogglePanel,
                            onToggleVoiceSearch = onToggleVoiceSearch,
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
                            voiceSearchAvailable = voiceSearchAvailable,
                            sourcePackage = mediaState.sourcePackage,
                            onTogglePanel = onTogglePanel,
                            onToggleVoiceSearch = onToggleVoiceSearch,
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
                                        recentDestinations = recentDestinations,
                                        savedPlaces = savedPlaces,
                                        onDestinationSelected = onDestinationSelected,
                                        onToggleSavedPlace = onToggleSavedPlace,
                                        onUpdateSavedPlace = onUpdateSavedPlace,
                                        onDismissSelectedPoi = { mapEngine.dismissSelectedPoi() },
                                        mediaState = mediaState,
                                        defaultAudioPackage = defaultAudioPackage,
                                        onSetDefaultAudioPackage = onSetDefaultAudioPackage,
                                        onMediaPlayPause = onMediaPlayPause,
                                        onMediaSkipPrevious = onMediaSkipPrevious,
                                        onMediaSkipNext = onMediaSkipNext,
                                        onMediaToggleLike = onMediaToggleLike,
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
                                onDismiss = onDismissAppDrawer,
                            )
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
    recentDestinations: List<SearchResultPlace>,
    savedPlaces: List<SearchResultPlace>,
    onDestinationSelected: (SearchResultPlace) -> Unit,
    onToggleSavedPlace: (SearchResultPlace) -> Unit,
    onUpdateSavedPlace: (SearchResultPlace) -> Unit,
    onDismissSelectedPoi: () -> Unit,
    mediaState: MediaPlaybackState,
    defaultAudioPackage: String,
    onSetDefaultAudioPackage: (String) -> Unit,
    onMediaPlayPause: () -> Unit,
    onMediaSkipPrevious: () -> Unit,
    onMediaSkipNext: () -> Unit,
    onMediaToggleLike: () -> Unit,
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
        recentDestinations = recentDestinations,
        savedPlaces = savedPlaces,
        onDestinationSelected = onDestinationSelected,
        onToggleSavedPlace = onToggleSavedPlace,
        onUpdateSavedPlace = onUpdateSavedPlace,
        onDismissSelectedPoi = onDismissSelectedPoi,
        mediaState = mediaState,
        defaultAudioPackage = defaultAudioPackage,
        onSetDefaultAudioPackage = onSetDefaultAudioPackage,
        onMediaPlayPause = onMediaPlayPause,
        onMediaSkipPrevious = onMediaSkipPrevious,
        onMediaSkipNext = onMediaSkipNext,
        onMediaToggleLike = onMediaToggleLike,
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
    recentDestinations: List<SearchResultPlace>,
    savedPlaces: List<SearchResultPlace>,
    onDestinationSelected: (SearchResultPlace) -> Unit,
    onToggleSavedPlace: (SearchResultPlace) -> Unit,
    onUpdateSavedPlace: (SearchResultPlace) -> Unit,
    onDismissSelectedPoi: () -> Unit,
    mediaState: MediaPlaybackState,
    defaultAudioPackage: String,
    onSetDefaultAudioPackage: (String) -> Unit,
    onMediaPlayPause: () -> Unit,
    onMediaSkipPrevious: () -> Unit,
    onMediaSkipNext: () -> Unit,
    onMediaToggleLike: () -> Unit,
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
                    limitSearchDistance = limitSearchDistance,
                    useVectorTiles = useVectorTiles,
                    show3dBuildings = show3dBuildings,
                    showTraffic = showTraffic,
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
                    onToggleLauncherMode = onToggleLauncherMode,
                    onToggleLargeShortcutIcons = onToggleLargeShortcutIcons,
                    onDrivingZoomChange = onDrivingZoomChange,
                    onPuckHorizontalOffsetChange = onPuckHorizontalOffsetChange,
                    onPuckVerticalOffsetChange = onPuckVerticalOffsetChange,
                    puckScale = puckScale,
                    onPuckScaleChange = onPuckScaleChange,
                    appUpdateState = appUpdateState,
                    onCheckForUpdate = onCheckForUpdate,
                    onDownloadUpdate = onDownloadUpdate,
                    onInstallApk = onInstallApk,
                    onDismiss = onDismissPanel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ActivePanel.MAP_DATA -> {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = OledBlack,
                ) {
                    MapDataPanelContent(
                        viewModel = mapDataViewModel,
                        onDismiss = onDismissPanel,
                        tomTomApiKey = tomTomApiKey,
                        onTomTomApiKeyChange = onTomTomApiKeyChange,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            ActivePanel.APP_DRAWER,
            ActivePanel.MEDIA,
            ActivePanel.HIDDEN,
            -> {
                MediaPlayerPane(
                    mediaState = mediaState,
                    defaultAudioPackage = defaultAudioPackage,
                    onSetDefaultAudioPackage = onSetDefaultAudioPackage,
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
    limitSearchDistance: Boolean,
    useVectorTiles: Boolean,
    show3dBuildings: Boolean,
    showTraffic: Boolean,
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
    onToggleLauncherMode: () -> Unit,
    onToggleLargeShortcutIcons: () -> Unit,
    onDrivingZoomChange: (Float) -> Unit,
    onPuckHorizontalOffsetChange: (Float) -> Unit,
    onPuckVerticalOffsetChange: (Float) -> Unit,
    puckScale: Float,
    onPuckScaleChange: (Float) -> Unit,
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
                    label = "Nearby results only (within 500 km)",
                    checked = limitSearchDistance,
                    onCheckedChange = { checked ->
                        if (checked != limitSearchDistance) {
                            onToggleLimitSearchDistance()
                        }
                    },
                )

                SettingsSwitchRow(
                    label = "Vector Map Tiles (sharper 3D)",
                    checked = useVectorTiles,
                    onCheckedChange = { checked ->
                        if (checked != useVectorTiles) {
                            onToggleVectorTiles()
                        }
                    },
                )

                SettingsSwitchRow(
                    label = "3D Buildings (vector tiles only)",
                    checked = show3dBuildings,
                    enabled = useVectorTiles,
                    onCheckedChange = { checked ->
                        if (checked != show3dBuildings) {
                            onToggleShow3dBuildings()
                        }
                    },
                )

                SettingsSwitchRow(
                    label = "Traffic Overlay",
                    checked = showTraffic,
                    onCheckedChange = { checked ->
                        if (checked != showTraffic) {
                            onToggleTraffic()
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

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
                ) {
                    CarBodyText(
                        text = "Driving View",
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CarBodyText(
                            text = "Puck Left / Right",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Slider(
                            value = puckHorizontalOffset,
                            onValueChange = onPuckHorizontalOffsetChange,
                            valueRange = 0f..0.8f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(CarDimensions.MinTapTarget),
                        )
                        CarLabelText(
                            text = "Left ${(puckHorizontalOffset * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CarBodyText(
                            text = "Puck Up / Down",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Slider(
                            value = puckVerticalOffset,
                            onValueChange = onPuckVerticalOffsetChange,
                            valueRange = 0f..0.8f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(CarDimensions.MinTapTarget),
                        )
                        CarLabelText(
                            text = "Top ${(puckVerticalOffset * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CarBodyText(
                            text = "Puck Size",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Slider(
                            value = puckScale,
                            onValueChange = onPuckScaleChange,
                            valueRange = 0.5f..3.0f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(CarDimensions.MinTapTarget),
                        )
                        CarLabelText(
                            text = "${"%.1f".format(puckScale)}×",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CarBodyText(
                            text = "Zoom",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Slider(
                            value = drivingZoom,
                            onValueChange = onDrivingZoomChange,
                            valueRange = 15f..21f,
                            steps = 12,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(CarDimensions.MinTapTarget),
                        )
                        CarLabelText(
                            text = "Zoom ${"%.1f".format(drivingZoom)}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }

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

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CarDimensions.MinTapTarget)
            .then(if (!enabled) Modifier.alpha(0.38f) else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CarBodyText(
            text = label,
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 0.dp),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.size(CarDimensions.MinTapTarget),
        )
    }
}
