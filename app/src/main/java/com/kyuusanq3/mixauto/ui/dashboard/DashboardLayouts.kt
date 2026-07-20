package com.kyuusanq3.mixauto.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import com.kyuusanq3.mixauto.data.apps.LaunchableAppEntry
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import com.kyuusanq3.mixauto.domain.media.MediaPlaybackState
import com.kyuusanq3.mixauto.ui.components.CarMapViewContainer
import com.kyuusanq3.mixauto.ui.components.MapMediaDividerHandle
import com.kyuusanq3.mixauto.ui.settings.AppUpdateState
import com.kyuusanq3.mixauto.ui.settings.LauncherPreferences
import com.kyuusanq3.mixauto.ui.settings.MapDataViewModel
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import java.io.File

internal data class DashboardMapPaneProps(
    val mapEngine: CarMapEngine,
    val onToggleSearch: () -> Unit,
    val isDestinationPanelOpen: Boolean,
    val onToggleMapSettings: () -> Unit,
    val isMapSettingsPanelOpen: Boolean,
    val reduceTopInsetBelowStatusStrip: Boolean,
)

internal data class DashboardSecondaryPaneProps(
    val activePanel: ActivePanel,
    val mapEngine: CarMapEngine,
    val mapDataViewModel: MapDataViewModel,
    val onDismissPanel: () -> Unit,
    val onOpenMapData: () -> Unit,
    val onPreviewSearchPlace: (SearchResultPlace) -> Unit,
    val onOpenAddFromLink: () -> Unit,
    val onDismissAddPlacePanel: () -> Unit,
    val onConfirmAddPlaceFromLink: (SearchResultPlace) -> Unit,
    val recentDestinations: List<SearchResultPlace>,
    val savedPlaces: List<SearchResultPlace>,
    val onDestinationSelected: (SearchResultPlace) -> Unit,
    val onToggleSavedPlace: (SearchResultPlace) -> Unit,
    val onUpdateSavedPlace: (SearchResultPlace) -> Unit,
    val onClearPoiReturnToSearch: () -> Unit,
    val mediaState: MediaPlaybackState,
    val defaultAudioPackage: String,
    val onSetDefaultAudioPackage: (String) -> Unit,
    val onMediaPlayPause: () -> Unit,
    val onMediaSkipPrevious: () -> Unit,
    val onMediaSkipNext: () -> Unit,
    val onMediaToggleLike: () -> Unit,
    val albumArtMode: AlbumArtMode,
    val onAlbumArtModeChange: (AlbumArtMode) -> Unit,
    val isLeftHandDrive: Boolean,
    val isShortcutsHorizontal: Boolean,
    val limitSearchDistance: Boolean,
    val useVectorTiles: Boolean,
    val show3dBuildings: Boolean,
    val showTraffic: Boolean,
    val navigationVoiceEnabled: Boolean,
    val navigationVoiceVolume: Float,
    val tomTomApiKey: String,
    val isLauncherMode: Boolean,
    val shortcutIconSize: DockShortcutIconSize,
    val drivingZoom: Float,
    val drivingTilt: Float,
    val puckHorizontalOffset: Float,
    val puckVerticalOffset: Float,
    val onToggleLhd: () -> Unit,
    val onToggleShortcutsHorizontal: () -> Unit,
    val onToggleLimitSearchDistance: () -> Unit,
    val onToggleVectorTiles: () -> Unit,
    val onToggleShow3dBuildings: () -> Unit,
    val onToggleTraffic: () -> Unit,
    val onToggleNavigationVoice: () -> Unit,
    val onNavigationVoiceVolumeChange: (Float) -> Unit,
    val onTestNavigationVoice: () -> Unit,
    val onTomTomApiKeyChange: (String) -> Unit,
    val onToggleLauncherMode: () -> Unit,
    val onShortcutIconSizeChange: (DockShortcutIconSize) -> Unit,
    val onDrivingZoomChange: (Float) -> Unit,
    val onDrivingTiltChange: (Float) -> Unit,
    val onPuckHorizontalOffsetChange: (Float) -> Unit,
    val onPuckVerticalOffsetChange: (Float) -> Unit,
    val puckScale: Float,
    val onPuckScaleChange: (Float) -> Unit,
    val showStatusStrip: Boolean,
    val showSystemStatusBar: Boolean,
    val onToggleShowStatusStrip: () -> Unit,
    val onToggleShowSystemStatusBar: () -> Unit,
    val reduceMediaTopInsetBelowStatusStrip: Boolean,
    val appUpdateState: AppUpdateState,
    val onCheckForUpdate: () -> Unit,
    val onDownloadUpdate: () -> Unit,
    val onInstallApk: (File) -> Unit,
)

internal data class DashboardDockProps(
    val shortcutIconSize: DockShortcutIconSize,
    val isLeftHandDrive: Boolean,
    val activePanel: ActivePanel,
    val mediaState: MediaPlaybackState,
    val voiceSearchAvailable: Boolean,
    val defaultAudioPackage: String,
    val dockPinnedPackages: List<String>,
    val onToggleDockPin: (String) -> Unit,
    val onSelectAudioSource: (String) -> Unit,
    val onTogglePanel: (ActivePanel) -> Unit,
    val onVoiceSearch: () -> Unit,
)

internal data class DashboardAppDrawerProps(
    val activePanel: ActivePanel,
    val launchableApps: List<LaunchableAppEntry>,
    val audioPlayerPackages: Set<String>,
    val isAppDrawerLoading: Boolean,
    val dockPinnedPackages: List<String>,
    val onToggleDockPin: (String) -> Unit,
    val onSelectAudioSource: (String) -> Unit,
    val onOpenLauncherSettings: () -> Unit,
    val onDismiss: () -> Unit,
)

internal data class DashboardLayoutProps(
    val map: DashboardMapPaneProps,
    val secondaryPane: DashboardSecondaryPaneProps,
    val dock: DashboardDockProps,
    val appDrawer: DashboardAppDrawerProps,
    val showSecondaryPane: Boolean,
    val showMapMediaDivider: Boolean,
    val effectiveMapMediaRatio: Float,
    val effectiveMediaWeight: Float,
    val mapMediaRatio: Float,
    val onMapMediaRatioChange: (Float) -> Unit,
    val isLeftHandDrive: Boolean,
)

@Composable
internal fun DashboardSecondaryPaneSlot(
    props: DashboardSecondaryPaneProps,
    modifier: Modifier,
) {
    DashboardSecondaryPane(
        activePanel = props.activePanel,
        mapEngine = props.mapEngine,
        mapDataViewModel = props.mapDataViewModel,
        onDismissPanel = props.onDismissPanel,
        onOpenMapData = props.onOpenMapData,
        onPreviewSearchPlace = props.onPreviewSearchPlace,
        onOpenAddFromLink = props.onOpenAddFromLink,
        onDismissAddPlacePanel = props.onDismissAddPlacePanel,
        onConfirmAddPlaceFromLink = props.onConfirmAddPlaceFromLink,
        recentDestinations = props.recentDestinations,
        savedPlaces = props.savedPlaces,
        onDestinationSelected = props.onDestinationSelected,
        onToggleSavedPlace = props.onToggleSavedPlace,
        onUpdateSavedPlace = props.onUpdateSavedPlace,
        onDismissSelectedPoi = { props.mapEngine.dismissSelectedPoi() },
        onClearPoiReturnToSearch = props.onClearPoiReturnToSearch,
        mediaState = props.mediaState,
        defaultAudioPackage = props.defaultAudioPackage,
        onSetDefaultAudioPackage = props.onSetDefaultAudioPackage,
        onMediaPlayPause = props.onMediaPlayPause,
        onMediaSkipPrevious = props.onMediaSkipPrevious,
        onMediaSkipNext = props.onMediaSkipNext,
        onMediaToggleLike = props.onMediaToggleLike,
        albumArtMode = props.albumArtMode,
        onAlbumArtModeChange = props.onAlbumArtModeChange,
        isLeftHandDrive = props.isLeftHandDrive,
        isShortcutsHorizontal = props.isShortcutsHorizontal,
        limitSearchDistance = props.limitSearchDistance,
        useVectorTiles = props.useVectorTiles,
        show3dBuildings = props.show3dBuildings,
        showTraffic = props.showTraffic,
        navigationVoiceEnabled = props.navigationVoiceEnabled,
        navigationVoiceVolume = props.navigationVoiceVolume,
        tomTomApiKey = props.tomTomApiKey,
        isLauncherMode = props.isLauncherMode,
        shortcutIconSize = props.shortcutIconSize,
        drivingZoom = props.drivingZoom,
        drivingTilt = props.drivingTilt,
        puckHorizontalOffset = props.puckHorizontalOffset,
        puckVerticalOffset = props.puckVerticalOffset,
        onToggleLhd = props.onToggleLhd,
        onToggleShortcutsHorizontal = props.onToggleShortcutsHorizontal,
        onToggleLimitSearchDistance = props.onToggleLimitSearchDistance,
        onToggleVectorTiles = props.onToggleVectorTiles,
        onToggleShow3dBuildings = props.onToggleShow3dBuildings,
        onToggleTraffic = props.onToggleTraffic,
        onToggleNavigationVoice = props.onToggleNavigationVoice,
        onNavigationVoiceVolumeChange = props.onNavigationVoiceVolumeChange,
        onTestNavigationVoice = props.onTestNavigationVoice,
        onTomTomApiKeyChange = props.onTomTomApiKeyChange,
        onToggleLauncherMode = props.onToggleLauncherMode,
        onShortcutIconSizeChange = props.onShortcutIconSizeChange,
        onDrivingZoomChange = props.onDrivingZoomChange,
        onDrivingTiltChange = props.onDrivingTiltChange,
        onPuckHorizontalOffsetChange = props.onPuckHorizontalOffsetChange,
        onPuckVerticalOffsetChange = props.onPuckVerticalOffsetChange,
        puckScale = props.puckScale,
        onPuckScaleChange = props.onPuckScaleChange,
        showStatusStrip = props.showStatusStrip,
        showSystemStatusBar = props.showSystemStatusBar,
        onToggleShowStatusStrip = props.onToggleShowStatusStrip,
        onToggleShowSystemStatusBar = props.onToggleShowSystemStatusBar,
        reduceTopInset = props.reduceMediaTopInsetBelowStatusStrip,
        appUpdateState = props.appUpdateState,
        onCheckForUpdate = props.onCheckForUpdate,
        onDownloadUpdate = props.onDownloadUpdate,
        onInstallApk = props.onInstallApk,
        modifier = modifier,
    )
}

@Composable
private fun BoxScope.DashboardAppDrawerSlot(props: DashboardAppDrawerProps) {
    if (props.activePanel != ActivePanel.APP_DRAWER) return
    AppDrawerOverlay(
        launchableApps = props.launchableApps,
        audioPlayerPackages = props.audioPlayerPackages,
        isLoading = props.isAppDrawerLoading,
        dockPinnedPackages = props.dockPinnedPackages,
        maxDockPinnedApps = LauncherPreferences.MAX_DOCK_PINNED_APPS,
        onToggleDockPin = props.onToggleDockPin,
        onSelectAudioSource = props.onSelectAudioSource,
        onOpenLauncherSettings = props.onOpenLauncherSettings,
        onDismiss = props.onDismiss,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f),
    )
}

@Composable
private fun DashboardHorizontalDock(
    dock: DashboardDockProps,
    modifier: Modifier,
) {
    ShortcutDock(
        isHorizontal = true,
        shortcutIconSize = dock.shortcutIconSize,
        isLeftHandDrive = dock.isLeftHandDrive,
        activePanel = dock.activePanel,
        mediaState = dock.mediaState,
        voiceSearchAvailable = dock.voiceSearchAvailable,
        defaultAudioPackage = dock.defaultAudioPackage,
        dockPinnedPackages = dock.dockPinnedPackages,
        onToggleDockPin = dock.onToggleDockPin,
        onSelectAudioSource = dock.onSelectAudioSource,
        onTogglePanel = dock.onTogglePanel,
        onVoiceSearch = dock.onVoiceSearch,
        modifier = modifier,
    )
}

@Composable
private fun DashboardVerticalDock(
    dock: DashboardDockProps,
    onWidthChanged: (Float) -> Unit,
    modifier: Modifier,
) {
    ShortcutDock(
        isHorizontal = false,
        shortcutIconSize = dock.shortcutIconSize,
        isLeftHandDrive = dock.isLeftHandDrive,
        activePanel = dock.activePanel,
        mediaState = dock.mediaState,
        voiceSearchAvailable = dock.voiceSearchAvailable,
        defaultAudioPackage = dock.defaultAudioPackage,
        dockPinnedPackages = dock.dockPinnedPackages,
        onToggleDockPin = dock.onToggleDockPin,
        onSelectAudioSource = dock.onSelectAudioSource,
        onTogglePanel = dock.onTogglePanel,
        onVoiceSearch = dock.onVoiceSearch,
        modifier = modifier.onSizeChanged { onWidthChanged(it.width.toFloat()) },
    )
}

@Composable
private fun DashboardMapView(
    map: DashboardMapPaneProps,
    modifier: Modifier,
) {
    CarMapViewContainer(
        engine = map.mapEngine,
        onToggleSearch = map.onToggleSearch,
        isDestinationPanelOpen = map.isDestinationPanelOpen,
        onToggleMapSettings = map.onToggleMapSettings,
        isMapSettingsPanelOpen = map.isMapSettingsPanelOpen,
        reduceTopInset = map.reduceTopInsetBelowStatusStrip,
        modifier = modifier,
    )
}

@Composable
internal fun PortraitDashboardLayout(
    layout: DashboardLayoutProps,
    mapMediaContainerPx: Float,
    onMapMediaContainerSizeChanged: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { onMapMediaContainerSizeChanged(it.height.toFloat()) },
            ) {
                DashboardMapView(
                    map = layout.map,
                    modifier = Modifier
                        .weight(if (layout.showSecondaryPane) layout.effectiveMapMediaRatio else 1f)
                        .fillMaxWidth(),
                )
                if (layout.showSecondaryPane) {
                    if (layout.showMapMediaDivider) {
                        MapMediaDividerHandle(
                            isVertical = false,
                            containerSizePx = mapMediaContainerPx,
                            mapMediaRatio = layout.mapMediaRatio,
                            onMapMediaRatioChange = layout.onMapMediaRatioChange,
                        )
                    }
                    DashboardSecondaryPaneSlot(
                        props = layout.secondaryPane,
                        modifier = Modifier
                            .weight(layout.effectiveMediaWeight)
                            .fillMaxWidth(),
                    )
                }
            }
            DashboardAppDrawerSlot(layout.appDrawer)
        }
        DashboardHorizontalDock(
            dock = layout.dock,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = CarDimensions.PaneGap)
                .wrapContentHeight(),
        )
    }
}

@Composable
internal fun LandscapeHorizontalDockLayout(
    layout: DashboardLayoutProps,
    mapMediaContainerPx: Float,
    onMapMediaContainerSizeChanged: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { onMapMediaContainerSizeChanged(it.width.toFloat()) },
            ) {
                if (layout.isLeftHandDrive) {
                    DashboardMapView(
                        map = layout.map,
                        modifier = Modifier
                            .weight(if (layout.showSecondaryPane) layout.effectiveMapMediaRatio else 1f)
                            .fillMaxSize(),
                    )
                    if (layout.showSecondaryPane) {
                        if (layout.showMapMediaDivider) {
                            MapMediaDividerHandle(
                                isVertical = true,
                                containerSizePx = mapMediaContainerPx,
                                mapMediaRatio = layout.mapMediaRatio,
                                onMapMediaRatioChange = layout.onMapMediaRatioChange,
                            )
                        }
                        DashboardSecondaryPaneSlot(
                            props = layout.secondaryPane,
                            modifier = Modifier
                                .weight(layout.effectiveMediaWeight)
                                .fillMaxSize(),
                        )
                    }
                } else {
                    if (layout.showSecondaryPane) {
                        DashboardSecondaryPaneSlot(
                            props = layout.secondaryPane,
                            modifier = Modifier
                                .weight(layout.effectiveMediaWeight)
                                .fillMaxSize(),
                        )
                        if (layout.showMapMediaDivider) {
                            MapMediaDividerHandle(
                                isVertical = true,
                                containerSizePx = mapMediaContainerPx,
                                mapMediaRatio = layout.mapMediaRatio,
                                onMapMediaRatioChange = layout.onMapMediaRatioChange,
                                invertDrag = true,
                            )
                        }
                    }
                    DashboardMapView(
                        map = layout.map,
                        modifier = Modifier
                            .weight(if (layout.showSecondaryPane) layout.effectiveMapMediaRatio else 1f)
                            .fillMaxSize(),
                    )
                }
            }
            DashboardAppDrawerSlot(layout.appDrawer)
        }
        DashboardHorizontalDock(
            dock = layout.dock,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = CarDimensions.PaneGap)
                .wrapContentHeight(),
        )
    }
}

@Composable
internal fun LandscapeVerticalDockLayout(
    layout: DashboardLayoutProps,
    verticalDockMapMediaContainerPx: Float,
    onRowWidthChanged: (Float) -> Unit,
    onVerticalDockWidthChanged: (Float) -> Unit,
) {
    val mapWeight = if (layout.showSecondaryPane) {
        layout.effectiveMapMediaRatio
    } else {
        layout.effectiveMapMediaRatio + layout.effectiveMediaWeight
    }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { onRowWidthChanged(it.width.toFloat()) },
    ) {
        if (layout.isLeftHandDrive) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    DashboardMapView(
                        map = layout.map,
                        modifier = Modifier
                            .weight(mapWeight)
                            .fillMaxSize(),
                    )
                    if (layout.showSecondaryPane) {
                        if (layout.showMapMediaDivider) {
                            MapMediaDividerHandle(
                                isVertical = true,
                                containerSizePx = verticalDockMapMediaContainerPx,
                                mapMediaRatio = layout.mapMediaRatio,
                                onMapMediaRatioChange = layout.onMapMediaRatioChange,
                            )
                        }
                        DashboardSecondaryPaneSlot(
                            props = layout.secondaryPane,
                            modifier = Modifier
                                .weight(layout.effectiveMediaWeight)
                                .fillMaxSize(),
                        )
                    }
                }
                DashboardAppDrawerSlot(layout.appDrawer)
            }
            DashboardVerticalDock(
                dock = layout.dock,
                onWidthChanged = onVerticalDockWidthChanged,
                modifier = Modifier
                    .wrapContentWidth()
                    .fillMaxHeight(),
            )
        } else {
            DashboardVerticalDock(
                dock = layout.dock,
                onWidthChanged = onVerticalDockWidthChanged,
                modifier = Modifier
                    .wrapContentWidth()
                    .fillMaxHeight(),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    DashboardMapView(
                        map = layout.map,
                        modifier = Modifier
                            .weight(mapWeight)
                            .fillMaxSize(),
                    )
                    if (layout.showSecondaryPane) {
                        if (layout.showMapMediaDivider) {
                            MapMediaDividerHandle(
                                isVertical = true,
                                containerSizePx = verticalDockMapMediaContainerPx,
                                mapMediaRatio = layout.mapMediaRatio,
                                onMapMediaRatioChange = layout.onMapMediaRatioChange,
                            )
                        }
                        DashboardSecondaryPaneSlot(
                            props = layout.secondaryPane,
                            modifier = Modifier
                                .weight(layout.effectiveMediaWeight)
                                .fillMaxSize(),
                        )
                    }
                }
                DashboardAppDrawerSlot(layout.appDrawer)
            }
        }
    }
}
