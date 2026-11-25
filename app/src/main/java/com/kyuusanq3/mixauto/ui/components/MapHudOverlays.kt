package com.kyuusanq3.mixauto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.ForkLeft
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.RampLeft
import androidx.compose.material.icons.filled.RampRight
import androidx.compose.material.icons.filled.RoundaboutLeft
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.UTurnRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarHeadlineText
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.DarkSurface
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** End-nav, top-down, and recenter controls are 1.5x [CarDimensions.MinTapTarget] (64 dp -> 96 dp). */
private val MapCameraActionSize = CarDimensions.MinTapTarget * 1.5f
private val MapCameraActionIconSize = 36.dp

internal val MapLibreAttributionReserveDp = 40.dp
internal val SpeedCircleSize = 54.dp
internal val SpeedCircleBottomInset = 4.dp
private val NavHudManeuverIconSize = 48.dp
private val NavHudCornerRadius = CarDimensions.CardCornerRadius * 1.5f
private val NavHudTextWhite = Color.White

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

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(NavHudCornerRadius),
        color = DarkSurface.copy(alpha = 0.92f),
        contentColor = NavHudTextWhite,
        shadowElevation = CarDimensions.CardElevation,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CarDimensions.PaneGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
        ) {
            Icon(
                imageVector = maneuverIcon(turnInstruction),
                contentDescription = turnInstruction ?: "On route",
                modifier = Modifier.size(NavHudManeuverIconSize),
                tint = ElectricCyan,
            )
            Column(modifier = Modifier.weight(1f)) {
                CarHeadlineText(
                    text = streetName,
                    style = MaterialTheme.typography.headlineLarge.copy(color = NavHudTextWhite),
                )
                turnInstruction?.let { instruction ->
                    CarBodyText(
                        text = instruction,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.titleLarge.copy(color = NavHudTextWhite),
                        maxLines = 1,
                    )
                }
                distanceToNextTurn?.let { distance ->
                    CarLabelText(
                        text = distance,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.titleMedium.copy(color = NavHudTextWhite),
                    )
                }
            }
        }
    }
}

private fun maneuverIcon(instruction: String?): ImageVector {
    val lower = instruction.orEmpty().trim().lowercase()
    return when {
        lower.contains("u-turn") || lower.contains("uturn") -> {
            if (lower.contains("right")) Icons.Filled.UTurnRight else Icons.Filled.UTurnLeft
        }
        lower.startsWith("depart") -> Icons.Filled.Navigation
        lower.contains("arrive") -> Icons.Filled.Flag
        lower.contains("roundabout") -> {
            if (lower.contains("left")) Icons.Filled.RoundaboutLeft else Icons.Filled.RoundaboutRight
        }
        lower.contains("ramp") || lower.contains("exit") -> {
            if (lower.contains("left")) Icons.Filled.RampLeft else Icons.Filled.RampRight
        }
        lower.contains("merge") -> Icons.Filled.Merge
        lower.contains("keep left") || lower.contains("fork left") -> Icons.Filled.ForkLeft
        lower.contains("keep right") || lower.contains("fork right") -> Icons.Filled.ForkRight
        lower.contains("sharp left") -> Icons.Filled.TurnSharpLeft
        lower.contains("sharp right") -> Icons.Filled.TurnSharpRight
        lower.contains("slight left") -> Icons.Filled.TurnSlightLeft
        lower.contains("slight right") -> Icons.Filled.TurnSlightRight
        lower.contains("left") -> Icons.Filled.TurnLeft
        lower.contains("right") -> Icons.Filled.TurnRight
        else -> Icons.Filled.Straight
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
                    .size(MapCameraActionSize)
                    .background(Color(0xBBCC2200), MaterialTheme.shapes.small),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "End navigation",
                    modifier = Modifier.size(MapCameraActionIconSize),
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
                        .size(MapCameraActionSize)
                        .background(OledBlack.copy(alpha = 0.72f), MaterialTheme.shapes.small),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CropFree,
                        contentDescription = "Top-down view",
                        modifier = Modifier.size(MapCameraActionIconSize),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(CarDimensions.PaneGap))
            IconButton(
                onClick = { engine.recenterCamera() },
                modifier = Modifier
                    .size(MapCameraActionSize)
                    .background(OledBlack.copy(alpha = 0.72f), MaterialTheme.shapes.small),
            ) {
                Icon(
                    imageVector = Icons.Filled.GpsFixed,
                    contentDescription = if (isNavigating) {
                        "Resume navigation view"
                    } else {
                        "Recenter map"
                    },
                    modifier = Modifier.size(MapCameraActionIconSize),
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
