package com.kyuusanq3.mixauto.ui.dashboard

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.ui.components.CarMapViewContainer
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarHeadlineText
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.DarkSurface
import com.kyuusanq3.mixauto.ui.theme.OledBlack

@Composable
fun DashboardScreen(
    mapEngine: CarMapEngine,
    isLeftHandDrive: Boolean,
    isShortcutsHorizontal: Boolean,
    onToggleLhd: () -> Unit,
    onToggleShortcutsHorizontal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var settingsOpen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack)
            .systemBarsPadding(),
    ) {
        if (isShortcutsHorizontal) {
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
                            modifier = Modifier
                                .weight(0.6f)
                                .fillMaxSize(),
                        )
                        MediaPlayerPane(
                            modifier = Modifier
                                .weight(0.4f)
                                .fillMaxSize(),
                        )
                    } else {
                        MediaPlayerPane(
                            modifier = Modifier
                                .weight(0.4f)
                                .fillMaxSize(),
                        )
                        CarMapViewContainer(
                            engine = mapEngine,
                            modifier = Modifier
                                .weight(0.6f)
                                .fillMaxSize(),
                        )
                    }
                }
                ShortcutDock(
                    isHorizontal = true,
                    onOpenSettings = { settingsOpen = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CarDimensions.DockHorizontalHeightDp + CarDimensions.PaneGap),
                )
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                if (isLeftHandDrive) {
                    CarMapViewContainer(
                        engine = mapEngine,
                        modifier = Modifier
                            .weight(CarDimensions.MapWeight)
                            .fillMaxSize(),
                    )
                    MediaPlayerPane(
                        modifier = Modifier
                            .weight(CarDimensions.MediaWeight)
                            .fillMaxSize(),
                    )
                    ShortcutDock(
                        isHorizontal = false,
                        onOpenSettings = { settingsOpen = true },
                        modifier = Modifier
                            .weight(CarDimensions.DockVerticalWeight)
                            .fillMaxSize(),
                    )
                } else {
                    ShortcutDock(
                        isHorizontal = false,
                        onOpenSettings = { settingsOpen = true },
                        modifier = Modifier
                            .weight(CarDimensions.DockVerticalWeight)
                            .fillMaxSize(),
                    )
                    CarMapViewContainer(
                        engine = mapEngine,
                        modifier = Modifier
                            .weight(CarDimensions.MapWeight)
                            .fillMaxSize(),
                    )
                    MediaPlayerPane(
                        modifier = Modifier
                            .weight(CarDimensions.MediaWeight)
                            .fillMaxSize(),
                    )
                }
            }
        }

        if (settingsOpen) {
            SettingsOverlay(
                isLeftHandDrive = isLeftHandDrive,
                isShortcutsHorizontal = isShortcutsHorizontal,
                onToggleLhd = onToggleLhd,
                onToggleShortcutsHorizontal = onToggleShortcutsHorizontal,
                onDismiss = { settingsOpen = false },
            )
        }
    }
}

@Composable
private fun SettingsOverlay(
    isLeftHandDrive: Boolean,
    isShortcutsHorizontal: Boolean,
    onToggleLhd: () -> Unit,
    onToggleShortcutsHorizontal: () -> Unit,
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
                CarHeadlineText(
                    text = "Launcher Settings",
                    style = MaterialTheme.typography.headlineMedium,
                )

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

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .padding(top = CarDimensions.PaneGap)
                        .size(
                            width = CarDimensions.PrimaryTapTarget * 2,
                            height = CarDimensions.PrimaryTapTarget,
                        ),
                ) {
                    CarLabelText(
                        text = "Close",
                        style = MaterialTheme.typography.labelLarge,
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

@Composable
private fun MediaPlayerPane(modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier.padding(CarDimensions.PaneGap),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = CarDimensions.CardElevation),
        colors = CardDefaults.elevatedCardColors(
            containerColor = DarkSurface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(CarDimensions.PaneGap),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CarHeadlineText(
                text = "Media Player",
                style = MaterialTheme.typography.headlineMedium,
            )

            Box(
                modifier = Modifier
                    .padding(top = CarDimensions.PaneGap)
                    .size(CarDimensions.PrimaryTapTarget)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                CarLabelText(
                    text = "Album Art",
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            CarBodyText(
                text = "No track playing",
                modifier = Modifier.padding(top = CarDimensions.PaneGap),
                style = MaterialTheme.typography.bodyLarge,
            )

            Row(
                modifier = Modifier
                    .padding(top = CarDimensions.PaneGap)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {},
                    modifier = Modifier.size(CarDimensions.MinTapTarget),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(CarDimensions.AppIconSize),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(
                    onClick = {},
                    modifier = Modifier.size(CarDimensions.PrimaryTapTarget),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(CarDimensions.PrimaryTapTarget - 8.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(
                    onClick = {},
                    modifier = Modifier.size(CarDimensions.MinTapTarget),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(CarDimensions.AppIconSize),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
