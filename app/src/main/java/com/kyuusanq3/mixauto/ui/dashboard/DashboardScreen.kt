package com.kyuusanq3.mixauto.ui.dashboard

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.domain.media.MediaPlaybackState
import com.kyuusanq3.mixauto.ui.components.CarMapViewContainer
import com.kyuusanq3.mixauto.ui.components.MapDataOverlay
import com.kyuusanq3.mixauto.ui.components.OverlayCloseButton
import com.kyuusanq3.mixauto.ui.settings.MapDataViewModel
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarHeadlineText
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.OledBlack
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
    useVectorTiles: Boolean,
    isLauncherMode: Boolean,
    isLargeShortcutIcons: Boolean,
    drivingZoom: Float,
    onToggleLhd: () -> Unit,
    onToggleShortcutsHorizontal: () -> Unit,
    onMapMediaRatioChange: (Float) -> Unit,
    onToggleLimitSearchDistance: () -> Unit,
    onToggleVectorTiles: () -> Unit,
    onToggleLauncherMode: () -> Unit,
    onToggleLargeShortcutIcons: () -> Unit,
    onDrivingZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var settingsOpen by remember { mutableStateOf(false) }
    var mapDataOpen by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val mediaWeight = 1f - mapMediaRatio
    val dockContentWeight = 1f - CarDimensions.DockVerticalWeight
    val landscapeMapWeight = mapMediaRatio * dockContentWeight
    val landscapeMediaWeight = mediaWeight * dockContentWeight

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack)
            .systemBarsPadding(),
    ) {
        when {
            isPortrait -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    CarMapViewContainer(
                        engine = mapEngine,
                        limitSearchDistance = limitSearchDistance,
                        modifier = Modifier
                            .weight(mapMediaRatio)
                            .fillMaxWidth(),
                    )
                    MediaPlayerPane(
                        mediaState = mediaState,
                        onPlayPause = onMediaPlayPause,
                        onSkipPrevious = onMediaSkipPrevious,
                        onSkipNext = onMediaSkipNext,
                        modifier = Modifier
                            .weight(mediaWeight)
                            .fillMaxWidth(),
                    )
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
                            .fillMaxWidth(),
                    ) {
                        if (isLeftHandDrive) {
                            CarMapViewContainer(
                                engine = mapEngine,
                                limitSearchDistance = limitSearchDistance,
                                modifier = Modifier
                                    .weight(mapMediaRatio)
                                    .fillMaxSize(),
                            )
                            MediaPlayerPane(
                                mediaState = mediaState,
                                onPlayPause = onMediaPlayPause,
                                onSkipPrevious = onMediaSkipPrevious,
                                onSkipNext = onMediaSkipNext,
                                modifier = Modifier
                                    .weight(mediaWeight)
                                    .fillMaxSize(),
                            )
                        } else {
                            MediaPlayerPane(
                                mediaState = mediaState,
                                onPlayPause = onMediaPlayPause,
                                onSkipPrevious = onMediaSkipPrevious,
                                onSkipNext = onMediaSkipNext,
                                modifier = Modifier
                                    .weight(mediaWeight)
                                    .fillMaxSize(),
                            )
                            CarMapViewContainer(
                                engine = mapEngine,
                                limitSearchDistance = limitSearchDistance,
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
                Row(modifier = Modifier.fillMaxSize()) {
                    if (isLeftHandDrive) {
                        CarMapViewContainer(
                            engine = mapEngine,
                            limitSearchDistance = limitSearchDistance,
                            modifier = Modifier
                                .weight(landscapeMapWeight)
                                .fillMaxSize(),
                        )
                        MediaPlayerPane(
                            mediaState = mediaState,
                            onPlayPause = onMediaPlayPause,
                            onSkipPrevious = onMediaSkipPrevious,
                            onSkipNext = onMediaSkipNext,
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
                            modifier = Modifier
                                .weight(landscapeMapWeight)
                                .fillMaxSize(),
                        )
                        MediaPlayerPane(
                            mediaState = mediaState,
                            onPlayPause = onMediaPlayPause,
                            onSkipPrevious = onMediaSkipPrevious,
                            onSkipNext = onMediaSkipNext,
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

        if (settingsOpen) {
            SettingsOverlay(
                isLeftHandDrive = isLeftHandDrive,
                isShortcutsHorizontal = isShortcutsHorizontal,
                mapMediaRatio = mapMediaRatio,
                limitSearchDistance = limitSearchDistance,
                useVectorTiles = useVectorTiles,
                isLauncherMode = isLauncherMode,
                isLargeShortcutIcons = isLargeShortcutIcons,
                drivingZoom = drivingZoom,
                onToggleLhd = onToggleLhd,
                onToggleShortcutsHorizontal = onToggleShortcutsHorizontal,
                onMapMediaRatioChange = onMapMediaRatioChange,
                onToggleLimitSearchDistance = onToggleLimitSearchDistance,
                onToggleVectorTiles = onToggleVectorTiles,
                onToggleLauncherMode = onToggleLauncherMode,
                onToggleLargeShortcutIcons = onToggleLargeShortcutIcons,
                onDrivingZoomChange = onDrivingZoomChange,
                onDismiss = { settingsOpen = false },
            )
        }
    }
}

@Composable
private fun SettingsOverlay(
    isLeftHandDrive: Boolean,
    isShortcutsHorizontal: Boolean,
    mapMediaRatio: Float,
    limitSearchDistance: Boolean,
    useVectorTiles: Boolean,
    isLauncherMode: Boolean,
    isLargeShortcutIcons: Boolean,
    drivingZoom: Float,
    onToggleLhd: () -> Unit,
    onToggleShortcutsHorizontal: () -> Unit,
    onMapMediaRatioChange: (Float) -> Unit,
    onToggleLimitSearchDistance: () -> Unit,
    onToggleVectorTiles: () -> Unit,
    onToggleLauncherMode: () -> Unit,
    onToggleLargeShortcutIcons: () -> Unit,
    onDrivingZoomChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = OledBlack,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CarBodyText(
                        text = "Map / Media Split",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Slider(
                        value = mapMediaRatio,
                        onValueChange = onMapMediaRatioChange,
                        valueRange = 0.3f..0.8f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(CarDimensions.MinTapTarget),
                    )
                    CarLabelText(
                        text = "Map ${(mapMediaRatio * 100).roundToInt()}% / Media ${((1f - mapMediaRatio) * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CarBodyText(
                        text = "Driving Zoom",
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
            modifier = Modifier.weight(1f),
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
