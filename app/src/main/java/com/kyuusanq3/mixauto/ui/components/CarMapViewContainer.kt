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
    val mapUiState by engine.uiState.collectAsState()

    DisposableEffect(lifecycleOwner, engine) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> engine.onStart()
                Lifecycle.Event.ON_RESUME -> engine.onResume()
                Lifecycle.Event.ON_PAUSE -> engine.onPause()
                Lifecycle.Event.ON_STOP -> engine.onStop()
                Lifecycle.Event.ON_DESTROY -> engine.onDestroy()
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

        if (mapUiState.isNavigating) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(CarDimensions.PaneGap)
                    .background(OledBlack.copy(alpha = 0.72f))
                    .padding(CarDimensions.PaneGap),
            ) {
                if (mapUiState.isRouteSelecting) {
                    CarBodyText(
                        text = "Choose a route",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    val remainingSec = ((1f - mapUiState.routeOverviewProgress) * 10f).toInt().coerceAtLeast(0)
                    CarLabelText(
                        text = "Tap again on selected route to start · auto-start in ${remainingSec}s",
                        modifier = Modifier.padding(top = CarDimensions.PaneGap / 2),
                        style = MaterialTheme.typography.labelMedium,
                    )
                } else {
                    CarBodyText(
                        text = mapUiState.streetName,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    mapUiState.turnInstruction?.let { instruction ->
                        CarLabelText(
                            text = instruction,
                            modifier = Modifier.padding(top = CarDimensions.PaneGap / 2),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    mapUiState.distanceToNextTurn?.let { distance ->
                        CarLabelText(
                            text = distance,
                            modifier = Modifier.padding(top = CarDimensions.PaneGap / 4),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(CarDimensions.PaneGap),
            horizontalAlignment = Alignment.End,
        ) {
            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier
                    .size(CarDimensions.MinTapTarget)
                    .background(
                        if (isDestinationPanelOpen) {
                            ElectricCyan
                        } else {
                            OledBlack.copy(alpha = 0.72f)
                        },
                        MaterialTheme.shapes.small,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = if (isDestinationPanelOpen) {
                        "Hide destination search"
                    } else {
                        "Show destination search"
                    },
                    tint = if (isDestinationPanelOpen) {
                        OledBlack
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }

            if (mapUiState.isNavigating) {
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

            if (mapUiState.isCameraDetached && !mapUiState.isNavigating) {
                if (!mapUiState.isInTopDownView) {
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
                        contentDescription = "Recenter map",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

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

        if (mapUiState.isRouteSelecting) {
            RouteSelectionLegend(
                routeProviders = mapUiState.routeOptions.map { it.provider }.distinct(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = CarDimensions.PaneGap,
                        bottom = MapLibreAttributionReserveDp + CarDimensions.PanelHeaderTapTarget + CarDimensions.PaneGap,
                    ),
            )
        }

        if (mapUiState.isRouteSelecting || mapUiState.routeOverviewProgress > 0f) {
            LinearProgressIndicator(
                progress = { mapUiState.routeOverviewProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(6.dp),
                color = ElectricCyan,
                trackColor = OledBlack.copy(alpha = 0.5f),
            )
        }

        SpeedCircle(
            speedKmh = mapUiState.currentSpeed,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = SpeedCircleBottomInset),
        )
    }
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
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            Text(
                text = "km/h",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = ElectricCyan.copy(alpha = 0.75f),
                    fontSize = 7.sp,
                    lineHeight = 7.sp,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

private val SpeedCircleSize = 30.dp
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
