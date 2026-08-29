package com.kyuusanq3.mixauto.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kyuusanq3.mixauto.data.map.MixAutoPuckLog
import com.kyuusanq3.mixauto.ui.components.AppUpdateSection
import com.kyuusanq3.mixauto.ui.components.PanelHeaderRow
import com.kyuusanq3.mixauto.ui.components.SettingsSwitchRow
import com.kyuusanq3.mixauto.ui.components.carScrollbar
import com.kyuusanq3.mixauto.ui.dashboard.DockShortcutIconSize
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import java.io.File
import kotlin.math.roundToInt

@Composable
internal fun SettingsContent(
    isLeftHandDrive: Boolean,
    isShortcutsHorizontal: Boolean,
    isLauncherMode: Boolean,
    shortcutIconSize: DockShortcutIconSize,
    showStatusStrip: Boolean,
    showSystemStatusBar: Boolean,
    onToggleShowStatusStrip: () -> Unit,
    onToggleShowSystemStatusBar: () -> Unit,
    onToggleLhd: () -> Unit,
    onToggleShortcutsHorizontal: () -> Unit,
    onToggleLauncherMode: () -> Unit,
    onShortcutIconSizeChange: (DockShortcutIconSize) -> Unit,
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
                .padding(
                    horizontal = CarDimensions.PaneGap * 2,
                    vertical = CarDimensions.PaneGap,
                ),
            verticalArrangement = Arrangement.spacedBy(CarDimensions.DockItemSpacing),
        ) {
            PanelHeaderRow(
                title = "Launcher Settings",
                onClose = onDismiss,
                closeContentDescription = "Close settings",
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

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CarBodyText(
                    text = "Shortcut Icon Size",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Slider(
                    value = shortcutIconSize.ordinal.toFloat(),
                    onValueChange = { raw ->
                        onShortcutIconSizeChange(DockShortcutIconSize.fromOrdinal(raw.roundToInt()))
                    },
                    valueRange = 0f..2f,
                    steps = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CarDimensions.MinTapTarget),
                )
                CarLabelText(
                    text = shortcutIconSize.label,
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            if (DeveloperSettings.SHOW_STATUS_STRIP) {
                SettingsSwitchRow(
                    label = "Status strip (time, date, weather)",
                    checked = showStatusStrip,
                    onCheckedChange = { checked ->
                        if (checked != showStatusStrip) {
                            onToggleShowStatusStrip()
                        }
                    },
                )
            }

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

            val context = LocalContext.current
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CarBodyText(
                    text = "Debug Logs",
                    style = MaterialTheme.typography.bodyLarge,
                )
                CarLabelText(
                    text = "Puck and camera probe from this drive. Share after you see the bug.",
                    style = MaterialTheme.typography.labelMedium,
                )
                Button(
                    onClick = { MixAutoPuckLog.share(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CarDimensions.MinTapTarget),
                ) {
                    CarBodyText(text = "Share Debug Logs")
                }
            }
        }
    }
}
