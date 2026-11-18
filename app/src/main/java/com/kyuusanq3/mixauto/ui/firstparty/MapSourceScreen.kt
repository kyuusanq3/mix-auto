package com.kyuusanq3.mixauto.ui.firstparty

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kyuusanq3.mixauto.R
import com.kyuusanq3.mixauto.ui.components.PanelHeaderRow
import com.kyuusanq3.mixauto.ui.components.SettingsSwitchRow
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.OledBlack

/**
 * Headunit flavor first-party app: a single switch between Mix Auto's own map (MapLibre) and
 * an embedded Google Maps on the same map pane. No-op on the standard flavor (not shown in the
 * app drawer — see [FIRST_PARTY_APPS] / AppDrawerOverlay's BuildConfig.HEADUNIT_SYSTEM filter).
 */
@Composable
fun MapSourceScreen(
    useEmbeddedGoogleMaps: Boolean,
    onSetUseEmbeddedGoogleMaps: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                title = "Map Source",
                onClose = onDismiss,
                closeContentDescription = "Close Map Source",
            )

            val appName = stringResource(R.string.app_name)
            SettingsSwitchRow(
                label = "Use Google Maps instead of $appName Maps",
                checked = useEmbeddedGoogleMaps,
                onCheckedChange = onSetUseEmbeddedGoogleMaps,
            )

            CarBodyText(
                text = "Shows Google Maps embedded in the map pane instead of $appName's " +
                    "built-in map. Requires Google Maps to be installed. $appName search, " +
                    "routing, and map settings are unavailable while this is on.",
                modifier = Modifier.padding(top = CarDimensions.PaneGap),
                maxLines = 6,
            )
        }
    }
}
