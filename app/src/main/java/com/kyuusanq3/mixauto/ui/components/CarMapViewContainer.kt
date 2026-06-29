package com.kyuusanq3.mixauto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.domain.map.RouteProvider
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack

@Composable
fun CarMapViewContainer(
    engine: CarMapEngine,
    onToggleSearch: () -> Unit,
    isDestinationPanelOpen: Boolean,
    onToggleMapSettings: () -> Unit,
    isMapSettingsPanelOpen: Boolean,
    reduceTopInset: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, engine) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> engine.onStart()
                Lifecycle.Event.ON_RESUME -> engine.onResume()
                Lifecycle.Event.ON_PAUSE -> engine.onPause()
                Lifecycle.Event.ON_STOP -> engine.onStop()
                // Teardown is owned by [MapHostViewModel.onCleared] so the engine survives rotation.
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = modifier
            .padding(
                start = CarDimensions.PaneGap,
                end = CarDimensions.PaneGap,
                bottom = CarDimensions.PaneGap,
                top = if (reduceTopInset) {
                    CarDimensions.StatusStripAdjacentGap
                } else {
                    CarDimensions.PaneGap
                },
            )
            .clip(MaterialTheme.shapes.medium)
            .onSizeChanged { engine.onMapHostLayoutChanged() },
    ) {
        AndroidView(
            factory = { context -> engine.createMapView(context) },
            modifier = Modifier.fillMaxSize(),
        )

        NavHudOverlay(
            engine = engine,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(CarDimensions.PaneGap),
        )

        DestinationSearchFab(
            isOpen = isDestinationPanelOpen,
            onClick = onToggleSearch,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = CarDimensions.PaneGap),
        )

        MapToolbarOverlay(
            engine = engine,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(CarDimensions.PaneGap),
        )

        MapSettingsFab(
            isOpen = isMapSettingsPanelOpen,
            onClick = onToggleMapSettings,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = CarDimensions.PaneGap,
                    bottom = MapLibreAttributionReserveDp,
                ),
        )

        RouteSelectionLegendOverlay(
            engine = engine,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = CarDimensions.PaneGap,
                    bottom = MapLibreAttributionReserveDp + CarDimensions.PanelHeaderTapTarget + CarDimensions.PaneGap,
                ),
        )

        RouteOverviewProgressOverlay(
            engine = engine,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        SpeedCircleOverlay(
            engine = engine,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = SpeedCircleBottomInset),
        )
    }
}

@Composable
private fun NavHudOverlay(
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
    val isRouteSelecting by engine.uiState
        .map { it.isRouteSelecting }
        .distinctUntilChanged()
        .collectAsState(initial = false)
    val routeOverviewProgress by engine.uiState
        .map { it.routeOverviewProgress }
        .distinctUntilChanged()
        .collectAsState(initial = 0f)
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
        if (isRouteSelecting) {
            CarBodyText(
                text = "Choose a route",
                style = MaterialTheme.typography.bodyLarge,
            )
            val remainingSec = ((1f - routeOverviewProgress) * 10f).toInt().coerceAtLeast(0)
            CarLabelText(
                text = "Tap again on selected route to start · auto-start in ${remainingSec}s",
                modifier = Modifier.padding(top = CarDimensions.PaneGap / 2),
                style = MaterialTheme.typography.labelMedium,
            )
        } else {
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
}

@Composable
private fun DestinationSearchFab(
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
private fun MapToolbarOverlay(
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
private fun RouteSelectionLegendOverlay(
    engine: CarMapEngine,
    modifier: Modifier = Modifier,
) {
    val isRouteSelecting by engine.uiState
        .map { it.isRouteSelecting }
        .distinctUntilChanged()
        .collectAsState(initial = false)
    if (!isRouteSelecting) return

    val routeProviders by engine.uiState
        .map { state -> state.routeOptions.map { it.provider }.distinct() }
        .distinctUntilChanged()
        .collectAsState(initial = emptyList())

    RouteSelectionLegend(
        routeProviders = routeProviders,
        modifier = modifier,
    )
}

@Composable
private fun RouteOverviewProgressOverlay(
    engine: CarMapEngine,
    modifier: Modifier = Modifier,
) {
    val isRouteSelecting by engine.uiState
        .map { it.isRouteSelecting }
        .distinctUntilChanged()
        .collectAsState(initial = false)
    val routeOverviewProgress by engine.uiState
        .map { it.routeOverviewProgress }
        .distinctUntilChanged()
        .collectAsState(initial = 0f)
    if (!isRouteSelecting && routeOverviewProgress <= 0f) return

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
private fun SpeedCircleOverlay(
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

private val MapLibreAttributionReserveDp = 40.dp

@Composable
private fun MapSettingsFab(
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

private val SpeedCircleSize = 54.dp
private val SpeedCircleBottomInset = 4.dp

private val RouteLegendFastestColor = Color(0xFF00CBD6)
private val RouteLegendTomTomColor = Color(0xFFFFB300)
private val RouteLegendAltColor = Color(0xFF6B7280)

@Composable
private fun RouteSelectionLegend(
    routeProviders: List<RouteProvider>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(OledBlack.copy(alpha = 0.72f), MaterialTheme.shapes.small)
            .padding(horizontal = CarDimensions.PaneGap / 2, vertical = CarDimensions.PaneGap / 4),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (routeProviders.contains(RouteProvider.OSRM_FASTEST)) {
            RouteLegendRow(color = RouteLegendFastestColor, label = "Fastest")
        }
        if (routeProviders.contains(RouteProvider.TOMTOM_TRAFFIC)) {
            RouteLegendRow(color = RouteLegendTomTomColor, label = "Traffic")
        }
        if (routeProviders.contains(RouteProvider.OSRM_ALTERNATE)) {
            RouteLegendRow(color = RouteLegendAltColor, label = "Alternate")
        }
    }
}

@Composable
private fun RouteLegendRow(
    color: Color,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        CarLabelText(
            text = label,
            modifier = Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
