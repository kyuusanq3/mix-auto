package com.kyuusanq3.mixauto.ui.dashboard

import android.content.res.Configuration
import android.speech.SpeechRecognizer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyuusanq3.mixauto.data.apps.LaunchableAppEntry
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import com.kyuusanq3.mixauto.domain.media.MediaPlaybackState
import com.kyuusanq3.mixauto.ui.components.AppUpdatePrompts
import com.kyuusanq3.mixauto.ui.components.DashboardStatusBar
import com.kyuusanq3.mixauto.ui.settings.AppUpdateViewModel
import com.kyuusanq3.mixauto.ui.settings.LauncherViewModel
import com.kyuusanq3.mixauto.ui.settings.MapDataViewModel
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import java.io.File

private const val OVERLAY_MAP_MEDIA_RATIO = 0.4f

private fun dismissToBasePanel(musicPaneEnabled: Boolean): ActivePanel =
    if (musicPaneEnabled) ActivePanel.MEDIA else ActivePanel.HIDDEN

@Composable
private fun DashboardScreenEffects(
    mapEngine: CarMapEngine,
    launcherViewModel: LauncherViewModel,
    activePanel: ActivePanel,
    musicPaneEnabled: Boolean,
    savedPlaces: List<SearchResultPlace>,
    mapUiState: com.kyuusanq3.mixauto.domain.map.MapUiState,
    poiReturnToSearch: Boolean,
    effectiveMapMediaRatio: Float,
    onEnsureLaunchableAppsLoaded: () -> Unit,
    onDismissAddPlacePanel: () -> Unit,
    onDismissSelectedPoi: () -> Unit,
    onDismissPanel: () -> Unit,
) {
    LaunchedEffect(activePanel) {
        if (activePanel == ActivePanel.APP_DRAWER) {
            onEnsureLaunchableAppsLoaded()
        }
    }

    LaunchedEffect(savedPlaces) {
        mapEngine.setSavedPlaces(savedPlaces)
    }

    LaunchedEffect(activePanel) {
        launcherViewModel.isDestinationSearchOpen = activePanel == ActivePanel.SEARCH
        when (activePanel) {
            ActivePanel.ADD_PLACE -> mapEngine.setMapTapDismissHandler(onDismissAddPlacePanel)
            ActivePanel.POI_DETAIL -> mapEngine.setMapTapDismissHandler(onDismissSelectedPoi)
            ActivePanel.SEARCH,
            ActivePanel.MAP_DATA,
            ActivePanel.AUDIO_SETTINGS,
            -> mapEngine.setMapTapDismissHandler(onDismissPanel)
            else -> mapEngine.setMapTapDismissHandler(null)
        }
    }

    LaunchedEffect(mapUiState.selectedPoi) {
        if (mapUiState.selectedPoi != null) {
            launcherViewModel.setActivePanel(ActivePanel.POI_DETAIL)
        } else if (activePanel == ActivePanel.POI_DETAIL) {
            if (poiReturnToSearch) {
                launcherViewModel.clearPoiReturnToSearch()
                launcherViewModel.setActivePanel(ActivePanel.SEARCH)
            } else {
                launcherViewModel.setActivePanel(dismissToBasePanel(musicPaneEnabled))
            }
        }
    }

    LaunchedEffect(activePanel, effectiveMapMediaRatio, mapUiState.selectedPoi) {
        mapEngine.onMapHostLayoutChanged()
    }
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
    navigationVoiceEnabled: Boolean,
    navigationVoiceVolume: Float,
    tomTomApiKey: String,
    isLauncherMode: Boolean,
    shortcutIconSize: DockShortcutIconSize,
    dockPinnedPackages: List<String>,
    onToggleDockPin: (String) -> Unit,
    launchableApps: List<LaunchableAppEntry>,
    audioPlayerPackages: Set<String>,
    isAppDrawerLoading: Boolean,
    onEnsureLaunchableAppsLoaded: () -> Unit,
    drivingZoom: Float,
    drivingTilt: Float,
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
    onPuckScaleChange: (Float) -> Unit,
    showStatusStrip: Boolean,
    showSystemStatusBar: Boolean,
    onToggleShowStatusStrip: () -> Unit,
    onToggleShowSystemStatusBar: () -> Unit,
    onInstallApk: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    val launcherViewModel: LauncherViewModel = viewModel()
    val musicPaneEnabled = launcherViewModel.musicPaneEnabled
    val activePanel = launcherViewModel.activePanel
    val poiReturnToSearch = launcherViewModel.poiReturnToSearch
    val onClearPoiReturnToSearch = launcherViewModel::clearPoiReturnToSearch
    val showSecondaryPane = activePanel != ActivePanel.HIDDEN
    val onTogglePanel: (ActivePanel) -> Unit = { target ->
        when (target) {
            ActivePanel.MEDIA -> when (activePanel) {
                ActivePanel.MEDIA -> {
                    launcherViewModel.updateMusicPaneEnabled(false)
                    launcherViewModel.setActivePanel(ActivePanel.HIDDEN)
                }
                ActivePanel.HIDDEN -> {
                    launcherViewModel.updateMusicPaneEnabled(true)
                    launcherViewModel.setActivePanel(ActivePanel.MEDIA)
                }
                else -> {
                    launcherViewModel.updateMusicPaneEnabled(true)
                    launcherViewModel.setActivePanel(ActivePanel.MEDIA)
                }
            }
            ActivePanel.SETTINGS -> when (activePanel) {
                ActivePanel.SETTINGS -> launcherViewModel.setActivePanel(dismissToBasePanel(musicPaneEnabled))
                else -> launcherViewModel.setActivePanel(ActivePanel.SETTINGS)
            }
            ActivePanel.APP_DRAWER -> when (activePanel) {
                ActivePanel.APP_DRAWER -> launcherViewModel.setActivePanel(dismissToBasePanel(musicPaneEnabled))
                else -> launcherViewModel.setActivePanel(ActivePanel.APP_DRAWER)
            }
            ActivePanel.SEARCH,
            ActivePanel.ADD_PLACE,
            ActivePanel.POI_DETAIL,
            ActivePanel.MAP_DATA,
            ActivePanel.AUDIO_SETTINGS,
            -> Unit
            ActivePanel.HIDDEN -> {
                launcherViewModel.updateMusicPaneEnabled(true)
                launcherViewModel.setActivePanel(ActivePanel.MEDIA)
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
            launcherViewModel.updateMusicPaneEnabled(true)
            launcherViewModel.setActivePanel(ActivePanel.MEDIA)
        }
    }
    val openAudioSource: (String) -> Unit = { packageName ->
        onSelectAudioSource(packageName)
        launcherViewModel.updateMusicPaneEnabled(true)
        launcherViewModel.setActivePanel(ActivePanel.MEDIA)
    }
    val onDismissPanel = {
        if (activePanel == ActivePanel.SEARCH) {
            launcherViewModel.isDestinationSearchOpen = false
            launcherViewModel.clearDestinationSearchState()
        }
        launcherViewModel.setActivePanel(dismissToBasePanel(musicPaneEnabled))
    }
    val onOpenMapData = {
        if (activePanel == ActivePanel.SEARCH) {
            launcherViewModel.isDestinationSearchOpen = false
            launcherViewModel.clearDestinationSearchState()
        }
        launcherViewModel.setActivePanel(ActivePanel.MAP_DATA)
    }
    val onDismissAppDrawer = { launcherViewModel.setActivePanel(dismissToBasePanel(musicPaneEnabled)) }
    val onOpenLauncherSettingsFromDrawer = { launcherViewModel.setActivePanel(ActivePanel.SETTINGS) }

    val isDestinationPanelOpen =
        activePanel == ActivePanel.SEARCH ||
            activePanel == ActivePanel.ADD_PLACE ||
            activePanel == ActivePanel.POI_DETAIL
    val isMapSettingsPanelOpen = activePanel == ActivePanel.MAP_DATA
    val onToggleSearch = {
        when (activePanel) {
            ActivePanel.SEARCH,
            ActivePanel.ADD_PLACE,
            -> {
                launcherViewModel.isDestinationSearchOpen = false
                launcherViewModel.clearDestinationSearchState()
                launcherViewModel.clearAddPlaceLinkState()
                launcherViewModel.setActivePanel(dismissToBasePanel(musicPaneEnabled))
            }
            ActivePanel.POI_DETAIL -> mapEngine.dismissSelectedPoi()
            else -> {
                launcherViewModel.isDestinationSearchOpen = true
                launcherViewModel.setActivePanel(ActivePanel.SEARCH)
            }
        }
    }
    val onToggleMapSettings = {
        when (activePanel) {
            ActivePanel.MAP_DATA -> launcherViewModel.setActivePanel(dismissToBasePanel(musicPaneEnabled))
            ActivePanel.POI_DETAIL -> mapEngine.dismissSelectedPoi()
            else -> launcherViewModel.setActivePanel(ActivePanel.MAP_DATA)
        }
    }
    val onVoiceSearch = {
        if (activePanel == ActivePanel.POI_DETAIL) {
            mapEngine.dismissSelectedPoi()
        }
        if (activePanel == ActivePanel.SEARCH) {
            launcherViewModel.triggerVoiceSearch()
        } else {
            launcherViewModel.setStartVoiceOnSearchOpen()
            launcherViewModel.isDestinationSearchOpen = true
            launcherViewModel.setActivePanel(ActivePanel.SEARCH)
        }
    }
    val onPreviewSearchPlace: (SearchResultPlace) -> Unit = { place ->
        launcherViewModel.setPoiReturnToSearch(true)
        mapEngine.focusOnPoi(place)
        launcherViewModel.isDestinationSearchOpen = false
        launcherViewModel.setActivePanel(ActivePanel.POI_DETAIL)
    }
    val onOpenAddFromLink = {
        launcherViewModel.setActivePanel(ActivePanel.ADD_PLACE)
    }
    val onDismissAddPlacePanel = {
        launcherViewModel.clearAddPlaceLinkState()
        launcherViewModel.setActivePanel(ActivePanel.SEARCH)
    }
    val onDismissSelectedPoi = { mapEngine.dismissSelectedPoi() }
    val onConfirmAddPlaceFromLink: (SearchResultPlace) -> Unit = { place ->
        launcherViewModel.clearAddPlaceLinkState()
        onPreviewSearchPlace(place)
    }
    val mapUiState by mapEngine.uiState.collectAsStateWithLifecycle()
    val appUpdateViewModel: AppUpdateViewModel = viewModel()
    val appUpdateState by appUpdateViewModel.uiState.collectAsStateWithLifecycle()
    val showDownloadOffer by appUpdateViewModel.showDownloadOffer.collectAsStateWithLifecycle()
    val showInstallOffer by appUpdateViewModel.showInstallOffer.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val voiceSearchAvailable = remember(context) {
        SpeechRecognizer.isRecognitionAvailable(context)
    }
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val isSplitLockedForOverlay =
        activePanel == ActivePanel.SEARCH ||
            activePanel == ActivePanel.ADD_PLACE ||
            activePanel == ActivePanel.POI_DETAIL ||
            activePanel == ActivePanel.MAP_DATA ||
            activePanel == ActivePanel.AUDIO_SETTINGS
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

    DashboardScreenEffects(
        mapEngine = mapEngine,
        launcherViewModel = launcherViewModel,
        activePanel = activePanel,
        musicPaneEnabled = musicPaneEnabled,
        savedPlaces = savedPlaces,
        mapUiState = mapUiState,
        poiReturnToSearch = poiReturnToSearch,
        effectiveMapMediaRatio = effectiveMapMediaRatio,
        onEnsureLaunchableAppsLoaded = onEnsureLaunchableAppsLoaded,
        onDismissAddPlacePanel = onDismissAddPlacePanel,
        onDismissSelectedPoi = onDismissSelectedPoi,
        onDismissPanel = onDismissPanel,
    )

    val reduceTopInsetBelowStatusStrip = showStatusStrip
    val reduceMediaTopInsetBelowStatusStrip = showStatusStrip && !isPortrait

    val layoutProps = DashboardLayoutProps(
        map = DashboardMapPaneProps(
            mapEngine = mapEngine,
            onToggleSearch = onToggleSearch,
            isDestinationPanelOpen = isDestinationPanelOpen,
            onToggleMapSettings = onToggleMapSettings,
            isMapSettingsPanelOpen = isMapSettingsPanelOpen,
            reduceTopInsetBelowStatusStrip = reduceTopInsetBelowStatusStrip,
        ),
        secondaryPane = DashboardSecondaryPaneProps(
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
            reduceMediaTopInsetBelowStatusStrip = reduceMediaTopInsetBelowStatusStrip,
            appUpdateState = appUpdateState,
            onCheckForUpdate = appUpdateViewModel::checkForUpdate,
            onDownloadUpdate = appUpdateViewModel::downloadUpdate,
            onInstallApk = onInstallApk,
        ),
        dock = DashboardDockProps(
            shortcutIconSize = shortcutIconSize,
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
        ),
        appDrawer = DashboardAppDrawerProps(
            activePanel = activePanel,
            launchableApps = launchableApps,
            audioPlayerPackages = audioPlayerPackages,
            isAppDrawerLoading = isAppDrawerLoading,
            dockPinnedPackages = dockPinnedPackages,
            onToggleDockPin = onToggleDockPin,
            onSelectAudioSource = openAudioSource,
            onOpenLauncherSettings = onOpenLauncherSettingsFromDrawer,
            onDismiss = onDismissAppDrawer,
        ),
        showSecondaryPane = showSecondaryPane,
        showMapMediaDivider = showMapMediaDivider,
        effectiveMapMediaRatio = effectiveMapMediaRatio,
        effectiveMediaWeight = effectiveMediaWeight,
        mapMediaRatio = mapMediaRatio,
        onMapMediaRatioChange = onMapMediaRatioChange,
        isLeftHandDrive = isLeftHandDrive,
    )

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
                isPortrait -> PortraitDashboardLayout(
                    layout = layoutProps,
                    mapMediaContainerPx = portraitMapMediaContainerPx,
                    onMapMediaContainerSizeChanged = { portraitMapMediaContainerPx = it },
                )
                isShortcutsHorizontal -> LandscapeHorizontalDockLayout(
                    layout = layoutProps,
                    mapMediaContainerPx = landscapeMapMediaContainerPx,
                    onMapMediaContainerSizeChanged = { landscapeMapMediaContainerPx = it },
                )
                else -> LandscapeVerticalDockLayout(
                    layout = layoutProps,
                    verticalDockMapMediaContainerPx = verticalDockMapMediaContainerPx,
                    onRowWidthChanged = { verticalDockRowWidthPx = it },
                    onVerticalDockWidthChanged = { verticalDockWidthPx = it },
                )
            }
            AppUpdatePrompts(
                uiState = appUpdateState,
                showDownloadOffer = showDownloadOffer,
                showInstallOffer = showInstallOffer,
                onDownloadUpdate = appUpdateViewModel::downloadUpdate,
                onDismissDownloadOffer = appUpdateViewModel::dismissDownloadOffer,
                onInstallApk = onInstallApk,
                onDismissInstallOffer = appUpdateViewModel::dismissInstallOffer,
                modifier = Modifier.zIndex(3f),
            )
        }
    }
}
