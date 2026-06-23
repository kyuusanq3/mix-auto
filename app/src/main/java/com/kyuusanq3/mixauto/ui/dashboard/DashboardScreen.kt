package com.kyuusanq3.mixauto.ui.dashboard

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import com.kyuusanq3.mixauto.ui.components.MapDataOverlay
import com.kyuusanq3.mixauto.ui.components.OverlayCloseButton
import com.kyuusanq3.mixauto.ui.settings.AppUpdateState
import com.kyuusanq3.mixauto.ui.settings.AppUpdateViewModel
import com.kyuusanq3.mixauto.ui.settings.LauncherViewModel
import com.kyuusanq3.mixauto.ui.settings.MapDataViewModel
import com.kyuusanq3.mixauto.ui.settings.TomTomKeyCheckState
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarHeadlineText
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import java.io.File
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    mapEngine: CarMapEngine,
    mapDataViewModel: MapDataViewModel,
    mediaState: MediaPlaybackState,
    onMediaPlayPause: () -> Unit,
    onMediaSkipPrevious: () -> Unit,
    onMediaSkipNext: () -> Unit,
    isLeftHandDrive: Boolean,
    isShortcutsHorizontal: Boolean,
    mapMediaRatio: Float,
    limitSearchDistance: Boolean,
    recentDestinations: List<SearchResultPlace>,
    onDestinationSelected: (SearchResultPlace) -> Unit,
    useVectorTiles: Boolean,
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
    var settingsOpen by remember { mutableStateOf(false) }
    var mapDataOpen by remember { mutableStateOf(false) }
    val appUpdateViewModel: AppUpdateViewModel = viewModel()
    val appUpdateState by appUpdateViewModel.uiState.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val mediaWeight = 1f - mapMediaRatio
    val dockContentWeight = 1f - CarDimensions.DockVerticalWeight
    val landscapeMapWeight = mapMediaRatio * dockContentWeight
    val landscapeMediaWeight = mediaWeight * dockContentWeight
    var portraitMapMediaContainerPx by remember { mutableStateOf(0f) }
    var landscapeMapMediaContainerPx by remember { mutableStateOf(0f) }
    var verticalDockRowWidthPx by remember { mutableStateOf(0f) }
    val verticalDockMapMediaContainerPx = verticalDockRowWidthPx * dockContentWeight

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack)
            .systemBarsPadding(),
    ) {
        when {
            isPortrait -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onSizeChanged { portraitMapMediaContainerPx = it.height.toFloat() },
                    ) {
                        CarMapViewContainer(
                            engine = mapEngine,
                            limitSearchDistance = limitSearchDistance,
                            recentDestinations = recentDestinations,
                            onDestinationSelected = onDestinationSelected,
                            modifier = Modifier
                                .weight(mapMediaRatio)
                                .fillMaxWidth(),
                        )
                        MapMediaDividerHandle(
                            isVertical = false,
                            containerSizePx = portraitMapMediaContainerPx,
                            mapMediaRatio = mapMediaRatio,
                            onMapMediaRatioChange = onMapMediaRatioChange,
                        )
                        MediaOrSettingsPane(
                            settingsOpen = settingsOpen,
                            onDismissSettings = { settingsOpen = false },
                            mediaState = mediaState,
                            onMediaPlayPause = onMediaPlayPause,
                            onMediaSkipPrevious = onMediaSkipPrevious,
                            onMediaSkipNext = onMediaSkipNext,
                            isLeftHandDrive = isLeftHandDrive,
                            isShortcutsHorizontal = isShortcutsHorizontal,
                            limitSearchDistance = limitSearchDistance,
                            useVectorTiles = useVectorTiles,
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
                                .weight(mediaWeight)
                                .fillMaxWidth(),
                        )
                    }
                    ShortcutDock(
                        isHorizontal = true,
                        isLargeIcons = isLargeShortcutIcons,
                        onOpenSettings = { settingsOpen = true },
                        onOpenMapData = { mapDataOpen = true },
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
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onSizeChanged { landscapeMapMediaContainerPx = it.width.toFloat() },
                    ) {
                        if (isLeftHandDrive) {
                            CarMapViewContainer(
                                engine = mapEngine,
                                limitSearchDistance = limitSearchDistance,
                                recentDestinations = recentDestinations,
                                onDestinationSelected = onDestinationSelected,
                                modifier = Modifier
                                    .weight(mapMediaRatio)
                                    .fillMaxSize(),
                            )
                            MapMediaDividerHandle(
                                isVertical = true,
                                containerSizePx = landscapeMapMediaContainerPx,
                                mapMediaRatio = mapMediaRatio,
                                onMapMediaRatioChange = onMapMediaRatioChange,
                            )
                            MediaOrSettingsPane(
                                settingsOpen = settingsOpen,
                                onDismissSettings = { settingsOpen = false },
                                mediaState = mediaState,
                                onMediaPlayPause = onMediaPlayPause,
                                onMediaSkipPrevious = onMediaSkipPrevious,
                                onMediaSkipNext = onMediaSkipNext,
                                isLeftHandDrive = isLeftHandDrive,
                                isShortcutsHorizontal = isShortcutsHorizontal,
                                limitSearchDistance = limitSearchDistance,
                                useVectorTiles = useVectorTiles,
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
                                    .weight(mediaWeight)
                                    .fillMaxSize(),
                            )
                        } else {
                            MediaOrSettingsPane(
                                settingsOpen = settingsOpen,
                                onDismissSettings = { settingsOpen = false },
                                mediaState = mediaState,
                                onMediaPlayPause = onMediaPlayPause,
                                onMediaSkipPrevious = onMediaSkipPrevious,
                                onMediaSkipNext = onMediaSkipNext,
                                isLeftHandDrive = isLeftHandDrive,
                                isShortcutsHorizontal = isShortcutsHorizontal,
                                limitSearchDistance = limitSearchDistance,
                                useVectorTiles = useVectorTiles,
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
                                    .weight(mediaWeight)
                                    .fillMaxSize(),
                            )
                            MapMediaDividerHandle(
                                isVertical = true,
                                containerSizePx = landscapeMapMediaContainerPx,
                                mapMediaRatio = mapMediaRatio,
                                onMapMediaRatioChange = onMapMediaRatioChange,
                                invertDrag = true,
                            )
                            CarMapViewContainer(
                                engine = mapEngine,
                                limitSearchDistance = limitSearchDistance,
                                recentDestinations = recentDestinations,
                                onDestinationSelected = onDestinationSelected,
                                modifier = Modifier
                                    .weight(mapMediaRatio)
                                    .fillMaxSize(),
                            )
                        }
                    }
                    ShortcutDock(
                        isHorizontal = true,
                        isLargeIcons = isLargeShortcutIcons,
                        onOpenSettings = { settingsOpen = true },
                        onOpenMapData = { mapDataOpen = true },
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
                        CarMapViewContainer(
                            engine = mapEngine,
                            limitSearchDistance = limitSearchDistance,
                            recentDestinations = recentDestinations,
                            onDestinationSelected = onDestinationSelected,
                            modifier = Modifier
                                .weight(landscapeMapWeight)
                                .fillMaxSize(),
                        )
                        MapMediaDividerHandle(
                            isVertical = true,
                            containerSizePx = verticalDockMapMediaContainerPx,
                            mapMediaRatio = mapMediaRatio,
                            onMapMediaRatioChange = onMapMediaRatioChange,
                        )
                        MediaOrSettingsPane(
                            settingsOpen = settingsOpen,
                            onDismissSettings = { settingsOpen = false },
                            mediaState = mediaState,
                            onMediaPlayPause = onMediaPlayPause,
                            onMediaSkipPrevious = onMediaSkipPrevious,
                            onMediaSkipNext = onMediaSkipNext,
                            isLeftHandDrive = isLeftHandDrive,
                            isShortcutsHorizontal = isShortcutsHorizontal,
                            limitSearchDistance = limitSearchDistance,
                            useVectorTiles = useVectorTiles,
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
                                .weight(landscapeMediaWeight)
                                .fillMaxSize(),
                        )
                        ShortcutDock(
                            isHorizontal = false,
                            isLargeIcons = isLargeShortcutIcons,
                            onOpenSettings = { settingsOpen = true },
                            onOpenMapData = { mapDataOpen = true },
                            modifier = Modifier
                                .weight(CarDimensions.DockVerticalWeight)
                                .fillMaxSize(),
                        )
                    } else {
                        ShortcutDock(
                            isHorizontal = false,
                            isLargeIcons = isLargeShortcutIcons,
                            onOpenSettings = { settingsOpen = true },
                            onOpenMapData = { mapDataOpen = true },
                            modifier = Modifier
                                .weight(CarDimensions.DockVerticalWeight)
                                .fillMaxSize(),
                        )
                        CarMapViewContainer(
                            engine = mapEngine,
                            limitSearchDistance = limitSearchDistance,
                            recentDestinations = recentDestinations,
                            onDestinationSelected = onDestinationSelected,
                            modifier = Modifier
                                .weight(landscapeMapWeight)
                                .fillMaxSize(),
                        )
                        MapMediaDividerHandle(
                            isVertical = true,
                            containerSizePx = verticalDockMapMediaContainerPx,
                            mapMediaRatio = mapMediaRatio,
                            onMapMediaRatioChange = onMapMediaRatioChange,
                        )
                        MediaOrSettingsPane(
                            settingsOpen = settingsOpen,
                            onDismissSettings = { settingsOpen = false },
                            mediaState = mediaState,
                            onMediaPlayPause = onMediaPlayPause,
                            onMediaSkipPrevious = onMediaSkipPrevious,
                            onMediaSkipNext = onMediaSkipNext,
                            isLeftHandDrive = isLeftHandDrive,
                            isShortcutsHorizontal = isShortcutsHorizontal,
                            limitSearchDistance = limitSearchDistance,
                            useVectorTiles = useVectorTiles,
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
                                .weight(landscapeMediaWeight)
                                .fillMaxSize(),
                        )
                    }
                }
            }
        }

        if (mapDataOpen) {
            MapDataOverlay(
                viewModel = mapDataViewModel,
                onDismiss = { mapDataOpen = false },
            )
        }
    }
}

@Composable
private fun MediaOrSettingsPane(
    settingsOpen: Boolean,
    onDismissSettings: () -> Unit,
    mediaState: MediaPlaybackState,
    onMediaPlayPause: () -> Unit,
    onMediaSkipPrevious: () -> Unit,
    onMediaSkipNext: () -> Unit,
    isLeftHandDrive: Boolean,
    isShortcutsHorizontal: Boolean,
    limitSearchDistance: Boolean,
    useVectorTiles: Boolean,
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
    Box(modifier = modifier) {
        if (settingsOpen) {
            SettingsContent(
                isLeftHandDrive = isLeftHandDrive,
                isShortcutsHorizontal = isShortcutsHorizontal,
                limitSearchDistance = limitSearchDistance,
                useVectorTiles = useVectorTiles,
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
                onDismiss = onDismissSettings,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            MediaPlayerPane(
                mediaState = mediaState,
                onPlayPause = onMediaPlayPause,
                onSkipPrevious = onMediaSkipPrevious,
                onSkipNext = onMediaSkipNext,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SettingsContent(
    isLeftHandDrive: Boolean,
    isShortcutsHorizontal: Boolean,
    limitSearchDistance: Boolean,
    useVectorTiles: Boolean,
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
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val launcherViewModel: LauncherViewModel = viewModel()
    val tomTomKeyCheckState = launcherViewModel.tomTomKeyCheckState

    Surface(
        modifier = modifier,
        color = OledBlack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
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
                    label = "Traffic Overlay",
                    checked = showTraffic,
                    onCheckedChange = { checked ->
                        if (checked != showTraffic) {
                            onToggleTraffic()
                        }
                    },
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CarBodyText(
                        text = "TomTom API Key (traffic)",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    OutlinedTextField(
                        value = tomTomApiKey,
                        onValueChange = onTomTomApiKeyChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(CarDimensions.PrimaryTapTarget + CarDimensions.PaneGap),
                        placeholder = {
                            CarBodyText(
                                text = "Paste key from developer.tomtom.com",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = OledBlack,
                            unfocusedContainerColor = OledBlack,
                            focusedTextColor = ElectricCyan,
                            unfocusedTextColor = ElectricCyan,
                        ),
                    )
                    CarLabelText(
                        text = "Free tier covers Philippines. Required for traffic overlay.",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    when (tomTomKeyCheckState) {
                        TomTomKeyCheckState.Idle -> Unit
                        TomTomKeyCheckState.Checking -> {
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
                                CarBodyText(text = "Testing API key...")
                            }
                        }
                        is TomTomKeyCheckState.Success -> {
                            CarLabelText(
                                text = tomTomKeyCheckState.message,
                                style = MaterialTheme.typography.labelMedium.copy(color = ElectricCyan),
                            )
                        }
                        is TomTomKeyCheckState.Error -> {
                            CarLabelText(
                                text = tomTomKeyCheckState.message,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = MaterialTheme.colorScheme.error,
                                ),
                            )
                        }
                    }
                    Button(
                        onClick = launcherViewModel::checkTomTomApiKey,
                        enabled = tomTomKeyCheckState !is TomTomKeyCheckState.Checking,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(CarDimensions.MinTapTarget),
                    ) {
                        CarBodyText(text = "Test TomTom API Key")
                    }
                }

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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CarDimensions.MinTapTarget),
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
            modifier = Modifier.size(CarDimensions.MinTapTarget),
        )
    }
}
