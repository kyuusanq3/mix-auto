package com.kyuusanq3.mixauto.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
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
                largeActions = true,
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

            AppDrawerSettingsSection()

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
            val saveLogsLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("text/plain"),
            ) { uri ->
                if (uri != null) {
                    MixAutoPuckLog.saveToUri(context, uri)
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CarBodyText(
                    text = "Debug Logs",
                    style = MaterialTheme.typography.bodyLarge,
                )
                CarLabelText(
                    text = "Puck/camera probe plus crash and Android Auto process-death lines. Share or save after the bug.",
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
                Button(
                    onClick = {
                        MixAutoPuckLog.save(context) { name ->
                            val launched = runCatching { saveLogsLauncher.launch(name) }.isSuccess
                            if (!launched) {
                                Toast.makeText(
                                    context,
                                    "Could not save debug logs",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CarDimensions.MinTapTarget),
                ) {
                    CarBodyText(text = "Save Debug Logs")
                }
            }
        }
    }
}

@Composable
private fun AppDrawerSettingsSection(
    launcherViewModel: LauncherViewModel = viewModel(),
) {
    val columnCount = launcherViewModel.appDrawerColumnCount
    val pagingMode = launcherViewModel.appDrawerPagingMode
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CarBodyText(
            text = "App drawer columns",
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = columnCount.toFloat(),
            onValueChange = { raw ->
                launcherViewModel.updateAppDrawerColumnCount(raw.roundToInt())
            },
            valueRange = LauncherPreferences.MIN_APP_DRAWER_COLUMNS.toFloat()..
                LauncherPreferences.MAX_APP_DRAWER_COLUMNS.toFloat(),
            steps = 2,
            modifier = Modifier
                .fillMaxWidth()
                .height(CarDimensions.MinTapTarget),
        )
        CarLabelText(
            text = "$columnCount columns",
            style = MaterialTheme.typography.labelMedium,
        )
        CarBodyText(
            text = "App drawer movement",
            style = MaterialTheme.typography.bodyLarge,
        )
        AppDrawerPagingMode.entries.forEach { mode ->
            AppDrawerPagingOption(
                label = mode.label,
                selected = pagingMode == mode,
                onSelect = { launcherViewModel.updateAppDrawerPagingMode(mode) },
            )
        }
        CarLabelText(
            text = "Scroll is continuous. Swipe horizontal and swipe vertical move one page at a time.",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun AppDrawerPagingOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CarDimensions.MinTapTarget)
            .clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
        )
        CarBodyText(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
