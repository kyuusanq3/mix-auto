package com.kyuusanq3.mixauto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.domain.map.RouteOption
import com.kyuusanq3.mixauto.domain.map.RouteProvider
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.CarHeadlineText
import com.kyuusanq3.mixauto.ui.theme.DeepCharcoal
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import com.kyuusanq3.mixauto.ui.theme.OnDark
import kotlin.math.roundToInt

@Composable
fun RoutePickerPane(
    routeOptions: List<RouteOption>,
    selectedRouteId: String?,
    engine: CarMapEngine,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Surface(
        modifier = modifier
            .fillMaxSize()
            .carScrollbar(scrollState),
        color = OledBlack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = CarDimensions.PaneGap * 2,
                    vertical = CarDimensions.PaneGap / 2,
                ),
            verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
        ) {
            PanelHeaderRow(
                title = "Choose route",
                onClose = onDismiss,
                closeContentDescription = "Cancel navigation",
                compact = true,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
            ) {
                routeOptions.forEach { option ->
                    RouteOptionCard(
                        option = option,
                        selected = option.id == selectedRouteId,
                        onClick = { engine.selectRouteOption(option.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteOptionCard(
    option: RouteOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(CarDimensions.PaneGap)
    val backgroundColor = if (selected) ElectricCyan else DeepCharcoal
    val contentColor = if (selected) OledBlack else OnDark
    val borderModifier = if (selected) {
        Modifier.border(2.dp, ElectricCyan, shape)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CarDimensions.MinTapTarget)
            .clip(shape)
            .then(borderModifier)
            .background(backgroundColor, shape)
            .clickable(onClick = onClick)
            .padding(CarDimensions.PaneGap),
        verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap / 4),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CarHeadlineText(
                text = option.label,
                modifier = Modifier.weight(1f),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(color = contentColor),
            )
            CarLabelText(
                text = providerBadge(option.provider),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                    color = contentColor.copy(alpha = 0.75f),
                ),
            )
        }
        CarBodyText(
            text = "${option.etaMinutes} min · ${formatRouteDistance(option.distanceMeters)}",
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(color = contentColor),
        )
        if (option.subtitle.isNotBlank()) {
            CarLabelText(
                text = option.subtitle,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(
                    color = contentColor.copy(alpha = 0.85f),
                ),
            )
        }
    }
}

private fun providerBadge(provider: RouteProvider): String = when (provider) {
    RouteProvider.OSRM_FASTEST -> "OSRM"
    RouteProvider.OSRM_ALTERNATE -> "OSRM"
    RouteProvider.TOMTOM_TRAFFIC -> "TomTom"
}

private fun formatRouteDistance(meters: Double): String = when {
    meters >= 1000 -> {
        val km = (meters / 1000.0 * 10.0).roundToInt() / 10.0
        if (km == km.toLong().toDouble()) "${km.toLong()} km" else "$km km"
    }
    else -> "${meters.roundToInt()} m"
}
