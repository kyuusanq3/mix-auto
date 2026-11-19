package com.kyuusanq3.mixauto.ui.firstparty

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kyuusanq3.mixauto.R
import com.kyuusanq3.mixauto.service.FloatingWindowService
import com.kyuusanq3.mixauto.ui.components.PanelHeaderRow
import com.kyuusanq3.mixauto.ui.components.SettingsSwitchRow
import com.kyuusanq3.mixauto.ui.settings.LauncherPreferences
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import kotlin.math.roundToInt

/**
 * Glance-card settings: enable, width, and position. The card shows whenever Mix Auto
 * leaves the foreground while it is the default launcher.
 */
@Composable
fun FloatingWindowScreen(
    enabled: Boolean,
    isLauncherMode: Boolean,
    widthPercent: Int,
    horizontalPercent: Int,
    verticalPercent: Int,
    onSetEnabled: (Boolean) -> Unit,
    onWidthPercentChange: (Int) -> Unit,
    onHorizontalPercentChange: (Int) -> Unit,
    onVerticalPercentChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
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
                label = "Show glance card when " + appName + " is in the background",
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
                text = if (isLauncherMode) {
                    "While " + appName + " is the default launcher, leaving the app shows time, weather, traffic, and now-playing. Tap the card to come back."
                } else {
                    "Turn on Launcher Mode and set " + appName + " as the default home app. The card only appears then."
                },
                modifier = Modifier.padding(bottom = CarDimensions.PaneGap),
            )

            CarLabelText(text = "Width")
            Slider(
                value = widthPercent.toFloat(),
                onValueChange = { raw -> onWidthPercentChange(raw.roundToInt()) },
                valueRange = LauncherPreferences.MIN_FLOATING_WINDOW_WIDTH_PERCENT.toFloat()..
                    LauncherPreferences.MAX_FLOATING_WINDOW_WIDTH_PERCENT.toFloat(),
                steps = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CarDimensions.MinTapTarget),
            )
            CarLabelText(
                text = widthPercent.toString() + "% of the screen width",
                style = MaterialTheme.typography.labelMedium,
            )

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
