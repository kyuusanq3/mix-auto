package com.kyuusanq3.mixauto.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyuusanq3.mixauto.ui.settings.MapDataViewModel
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import kotlin.math.roundToInt

@Composable
fun MapSettingsPanelContent(
    mapDataViewModel: MapDataViewModel,
    limitSearchDistance: Boolean,
    useVectorTiles: Boolean,
    show3dBuildings: Boolean,
    showTraffic: Boolean,
    navigationVoiceEnabled: Boolean,
    drivingZoom: Float,
    puckHorizontalOffset: Float,
    puckVerticalOffset: Float,
    puckScale: Float,
    tomTomApiKey: String,
    onToggleLimitSearchDistance: () -> Unit,
    onToggleVectorTiles: () -> Unit,
    onToggleShow3dBuildings: () -> Unit,
    onToggleTraffic: () -> Unit,
    onToggleNavigationVoice: () -> Unit,
    onDrivingZoomChange: (Float) -> Unit,
    onPuckHorizontalOffsetChange: (Float) -> Unit,
    onPuckVerticalOffsetChange: (Float) -> Unit,
    onPuckScaleChange: (Float) -> Unit,
    onTomTomApiKeyChange: (String) -> Unit,
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
                    vertical = CarDimensions.PaneGap / 2,
                ),
            verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
        ) {
            PanelHeaderRow(
                title = "Map Settings",
                onClose = onDismiss,
                closeContentDescription = "Close map settings",
                compact = true,
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
                label = "3D Buildings (vector tiles only)",
                checked = show3dBuildings,
                enabled = useVectorTiles,
                onCheckedChange = { checked ->
                    if (checked != show3dBuildings) {
                        onToggleShow3dBuildings()
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

            SettingsSwitchRow(
                label = "Voice navigation",
                checked = navigationVoiceEnabled,
                onCheckedChange = { checked ->
                    if (checked != navigationVoiceEnabled) {
                        onToggleNavigationVoice()
                    }
                },
            )

            DrivingViewSettingsSection(
                puckHorizontalOffset = puckHorizontalOffset,
                puckVerticalOffset = puckVerticalOffset,
                puckScale = puckScale,
                drivingZoom = drivingZoom,
                onPuckHorizontalOffsetChange = onPuckHorizontalOffsetChange,
                onPuckVerticalOffsetChange = onPuckVerticalOffsetChange,
                onPuckScaleChange = onPuckScaleChange,
                onDrivingZoomChange = onDrivingZoomChange,
            )

            MapDataSectionContent(
                viewModel = mapDataViewModel,
                tomTomApiKey = tomTomApiKey,
                onTomTomApiKeyChange = onTomTomApiKeyChange,
            )
        }
    }
}

@Composable
private fun DrivingViewSettingsSection(
    puckHorizontalOffset: Float,
    puckVerticalOffset: Float,
    puckScale: Float,
    drivingZoom: Float,
    onPuckHorizontalOffsetChange: (Float) -> Unit,
    onPuckVerticalOffsetChange: (Float) -> Unit,
    onPuckScaleChange: (Float) -> Unit,
    onDrivingZoomChange: (Float) -> Unit,
) {
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
                value = drivingZoom.coerceIn(MIN_DRIVING_ZOOM_SLIDER, MAX_DRIVING_ZOOM_SLIDER),
                onValueChange = onDrivingZoomChange,
                valueRange = MIN_DRIVING_ZOOM_SLIDER..MAX_DRIVING_ZOOM_SLIDER,
                steps = DRIVING_ZOOM_SLIDER_STEPS,
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

private const val MIN_DRIVING_ZOOM_SLIDER = 12f
private const val MAX_DRIVING_ZOOM_SLIDER = 22f
private const val DRIVING_ZOOM_SLIDER_STEPS = 19
