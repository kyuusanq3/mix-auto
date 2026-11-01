package com.kyuusanq3.mixauto.ui.firstparty

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kyuusanq3.mixauto.data.apps.LaunchableAppEntry
import com.kyuusanq3.mixauto.service.FloatingWindowService
import com.kyuusanq3.mixauto.ui.components.PanelHeaderRow
import com.kyuusanq3.mixauto.ui.components.SettingsSwitchRow
import com.kyuusanq3.mixauto.ui.components.carLazyScrollbar
import com.kyuusanq3.mixauto.ui.components.rememberAppIcon
import com.kyuusanq3.mixauto.ui.settings.LauncherPreferences
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import kotlin.math.roundToInt

/**
 * First-party settings for the Mix Auto glance overlay: enable, which docked navigation
 * app raises it, and the horizontal position (0% left to 100% right, in 10% steps).
 */
@Composable
fun FloatingWindowScreen(
    enabled: Boolean,
    navPackage: String,
    horizontalPercent: Int,
    verticalPercent: Int,
    launchableApps: List<LaunchableAppEntry>,
    onSetEnabled: (Boolean) -> Unit,
    onNavPackageChange: (String) -> Unit,
    onHorizontalPercentChange: (Int) -> Unit,
    onVerticalPercentChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (FloatingWindowService.canShowOverlay(context)) {
            onSetEnabled(true)
        } else {
            Toast.makeText(
                context,
                "Allow Display over other apps to use the floating window",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    val selectedLabel = remember(navPackage, launchableApps) {
        launchableApps.firstOrNull { it.packageName == navPackage }?.label
            ?: if (navPackage == LauncherPreferences.DEFAULT_FLOATING_WINDOW_NAV_PACKAGE) {
                "Google Maps"
            } else {
                navPackage
            }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding(),
        color = OledBlack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = CarDimensions.PaneGap * 2, vertical = CarDimensions.PaneGap / 2),
        ) {
            PanelHeaderRow(
                title = "Floating Window",
                onClose = onDismiss,
                closeContentDescription = "Close Floating Window",
            )

            SettingsSwitchRow(
                label = "Show glance card over the navigation app",
                checked = enabled,
                onCheckedChange = { checked ->
                    if (checked == enabled) return@SettingsSwitchRow
                    if (!checked) {
                        onSetEnabled(false)
                        return@SettingsSwitchRow
                    }
                    if (FloatingWindowService.canShowOverlay(context)) {
                        onSetEnabled(true)
                    } else {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + context.packageName),
                        )
                        runCatching { overlayPermissionLauncher.launch(intent) }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    "Could not open the Display over other apps settings",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                    }
                },
            )

            CarLabelText(
                text = "When you open the selected app from the shortcut bar, Mix Auto shows time, weather, traffic, and now-playing. Tap the card to come back.",
                modifier = Modifier.padding(bottom = CarDimensions.PaneGap),
            )

            CarLabelText(
                text = "Navigation app",
                modifier = Modifier.padding(bottom = CarDimensions.PaneGap / 2),
            )
            CarBodyText(
                text = selectedLabel,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = CarDimensions.PaneGap / 2),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (launchableApps.isEmpty()) {
                    CarBodyText(
                        text = "No installed apps found.",
                        modifier = Modifier.padding(vertical = CarDimensions.PaneGap),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .carLazyScrollbar(listState),
                        verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap / 2),
                    ) {
                        items(launchableApps, key = { it.packageName }) { app ->
                            NavigationAppRow(
                                app = app,
                                selected = app.packageName == navPackage,
                                onClick = { onNavPackageChange(app.packageName) },
                            )
                        }
                    }
                }
            }

            CarLabelText(
                text = "Horizontal position",
                modifier = Modifier.padding(top = CarDimensions.PaneGap),
            )
            Slider(
                value = horizontalPercent.toFloat(),
                onValueChange = { raw -> onHorizontalPercentChange(raw.roundToInt()) },
                valueRange = 0f..100f,
                steps = 9,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CarDimensions.MinTapTarget),
            )
            CarLabelText(
                text = horizontalPercent.toString() + "% from the left  (0% left, 100% right)",
                style = MaterialTheme.typography.labelMedium,
            )
            CarLabelText(
                text = "Vertical position",
                modifier = Modifier.padding(top = CarDimensions.PaneGap),
            )
            Slider(
                value = verticalPercent.toFloat(),
                onValueChange = { raw -> onVerticalPercentChange(raw.roundToInt()) },
                valueRange = 0f..100f,
                steps = 9,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CarDimensions.MinTapTarget),
            )
            CarLabelText(
                text = verticalPercent.toString() + "% from the top  (0% top, 100% bottom)",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = CarDimensions.PaneGap),
            )
        }
    }
}

@Composable
private fun NavigationAppRow(
    app: LaunchableAppEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val icon = rememberAppIcon(app.packageName)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CarDimensions.MinTapTarget)
            .then(
                if (selected) {
                    Modifier.background(ElectricCyan.copy(alpha = 0.18f))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = CarDimensions.PaneGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = app.label,
                modifier = Modifier
                    .size(CarDimensions.AppIconSize)
                    .clip(CircleShape),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Android,
                contentDescription = app.label,
                modifier = Modifier.size(CarDimensions.AppIconSize),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        CarBodyText(
            text = app.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
