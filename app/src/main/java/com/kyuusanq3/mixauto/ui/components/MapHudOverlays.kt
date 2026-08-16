package com.kyuusanq3.mixauto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal val MapLibreAttributionReserveDp = 40.dp
internal val SpeedCircleSize = 54.dp
internal val SpeedCircleBottomInset = 4.dp

@Composable
internal fun OfflineMapLabelOverlay(
    engine: CarMapEngine,
    modifier: Modifier = Modifier,
) {
    val isNavigating by engine.uiState
        .map { it.isNavigating }
        .distinctUntilChanged()
        .collectAsState(initial = false)
    if (isNavigating) return

    val label by engine.uiState
        .map { it.mapConnectivityLabel }
        .distinctUntilChanged()
        .collectAsState(initial = null)
    val connectivityLabel = label
    if (connectivityLabel.isNullOrBlank()) return

    CarLabelText(
        text = connectivityLabel,
        modifier = modifier
            .background(OledBlack.copy(alpha = 0.72f))
            .padding(horizontal = CarDimensions.PaneGap / 2, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium.copy(color = ElectricCyan),
    )
}

@Composable
internal fun NavHudOverlay(
    engine: CarMapEngine,
    modifier: Modifier = Modifier,
) {
    val isNavigating by engine.uiState
        .map { it.isNavigating }
        .distinctUntilChanged()
        .collectAsState(initial = false)
    if (!isNavigating) return

    val streetName by engine.uiState
        .map { it.streetName }
        .distinctUntilChanged()
        .collectAsState(initial = "")
    val turnInstruction by engine.uiState
        .map { it.turnInstruction }
        .distinctUntilChanged()
        .collectAsState(initial = null)
    val distanceToNextTurn by engine.uiState
        .map { it.distanceToNextTurn }
        .distinctUntilChanged()
        .collectAsState(initial = null)

    Column(
        modifier = modifier
            .background(OledBlack.copy(alpha = 0.72f))
            .padding(CarDimensions.PaneGap),
    ) {
        CarBodyText(
            text = streetName,
            style = MaterialTheme.typography.bodyLarge,
        )
        turnInstruction?.let { instruction ->
            CarLabelText(
                text = instruction,
                modifier = Modifier.padding(top = CarDimensions.PaneGap / 2),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        distanceToNextTurn?.let { distance ->
            CarLabelText(
                text = distance,
                modifier = Modifier.padding(top = CarDimensions.PaneGap / 4),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
internal fun DestinationSearchFab(
    isOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(CarDimensions.PrimaryTapTarget)
            .background(
                if (isOpen) {
                    ElectricCyan
                } else {
                    OledBlack.copy(alpha = 0.72f)
                },
                CircleShape,
            ),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = if (isOpen) {
                "Hide destination search"
            } else {
                "Show destination search"
            },
            modifier = Modifier.size(40.dp),
            tint = if (isOpen) {
                OledBlack
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}

@Composable
internal fun MapToolbarOverlay(
    engine: CarMapEngine,
    modifier: Modifier = Modifier,
) {
    val isNavigating by engine.uiState
        .map { it.isNavigating }
        .distinctUntilChanged()
        .collectAsState(initial = false)
    val isCameraDetached by engine.uiState
        .map { it.isCameraDetached }
        .distinctUntilChanged()
        .collectAsState(initial = false)
    val isInTopDownView by engine.uiState
        .map { it.isInTopDownView }
        .distinctUntilChanged()
        .collectAsState(initial = false)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
    ) {
        if (isNavigating) {
            Spacer(modifier = Modifier.height(CarDimensions.PaneGap))
            IconButton(
                onClick = { engine.startFreeDrive() },
                modifier = Modifier
                    .size(CarDimensions.MinTapTarget)
                    .background(Color(0xBBCC2200), MaterialTheme.shapes.small),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "End navigation",
                    tint = Color.White,
                )
            }
        }

        if (isCameraDetached) {
            if (!isNavigating && !isInTopDownView) {
                Spacer(modifier = Modifier.height(CarDimensions.PaneGap))
                IconButton(
                    onClick = { engine.enterTopDownView() },
                    modifier = Modifier
                        .size(CarDimensions.MinTapTarget)
                        .background(OledBlack.copy(alpha = 0.72f), MaterialTheme.shapes.small),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CropFree,
                        contentDescription = "Top-down view",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(CarDimensions.PaneGap))
            IconButton(
                onClick = { engine.recenterCamera() },
                modifier = Modifier
                    .size(CarDimensions.MinTapTarget)
                    .background(OledBlack.copy(alpha = 0.72f), MaterialTheme.shapes.small),
            ) {
                Icon(
                    imageVector = Icons.Filled.GpsFixed,
                    contentDescription = if (isNavigating) {
                        "Resume navigation view"
                    } else {
                        "Recenter map"
                    },
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
internal fun LighterTrafficAlternateOverlay(
    engine: CarMapEngine,
    modifier: Modifier = Modifier,
) {
    val active by engine.uiState
        .map { it.lighterTrafficAlternateActive }
        .distinctUntilChanged()
        .collectAsState(initial = false)
    if (!active) return

    CarLabelText(
        text = "Lighter traffic",
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(OledBlack.copy(alpha = 0.85f))
            .clickable(onClick = engine::switchToLighterTrafficAlternate)
            .padding(horizontal = CarDimensions.PaneGap, vertical = CarDimensions.PaneGap / 2),
        style = MaterialTheme.typography.labelLarge.copy(color = ElectricCyan),
    )
}

@Composable
internal fun RouteOverviewProgressOverlay(
    engine: CarMapEngine,
    modifier: Modifier = Modifier,
) {
    val routeOverviewProgress by engine.uiState
        .map { it.routeOverviewProgress }
        .distinctUntilChanged()
        .collectAsState(initial = 0f)
    if (routeOverviewProgress <= 0f) return

    LinearProgressIndicator(
        progress = { routeOverviewProgress },
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp),
        color = ElectricCyan,
        trackColor = OledBlack.copy(alpha = 0.5f),
    )
}

@Composable
internal fun SpeedCircleOverlay(
    engine: CarMapEngine,
    modifier: Modifier = Modifier,
) {
    val speedKmh by engine.uiState
        .map { it.currentSpeed }
        .distinctUntilChanged()
        .collectAsState(initial = 0)
    SpeedCircle(
        speedKmh = speedKmh,
        modifier = modifier,
    )
}

@Composable
internal fun MapSettingsFab(
    isOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tapTarget = CarDimensions.PanelHeaderTapTarget
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(tapTarget)
            .background(
                if (isOpen) {
                    ElectricCyan
                } else {
                    OledBlack.copy(alpha = 0.72f)
                },
                MaterialTheme.shapes.small,
            ),
    ) {
        Icon(
            imageVector = Icons.Filled.Layers,
            contentDescription = if (isOpen) {
                "Hide map settings"
            } else {
                "Show map settings"
            },
            tint = if (isOpen) {
                OledBlack
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}

@Composable
private fun SpeedCircle(
    speedKmh: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(SpeedCircleSize)
            .clip(CircleShape)
            .background(OledBlack.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = speedKmh.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = ElectricCyan,
                    fontSize = 17.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            Text(
                text = "km/h",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = ElectricCyan.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
