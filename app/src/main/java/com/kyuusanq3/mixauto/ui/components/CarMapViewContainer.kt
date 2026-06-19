package com.kyuusanq3.mixauto.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarHeadlineText
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.DeepCharcoal
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import kotlin.math.roundToInt

private fun Float.formatDistance(): String {
    return if (this < 1000f) {
        "${roundToInt()} m"
    } else {
        val km = this / 1000f
        val rounded = (km * 10f).roundToInt() / 10f
        if (rounded == rounded.toLong().toFloat()) {
            "${rounded.toLong()} km"
        } else {
            "$rounded km"
        }
    }
}

private fun categoryDisplayName(category: String): String = when (category) {
    "food" -> "Food & Drink"
    "fuel" -> "Gas Station"
    "health" -> "Health"
    "accommodation" -> "Accommodation"
    "finance" -> "Finance"
    "shopping" -> "Shopping"
    "recreation" -> "Recreation"
    else -> category.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun categoryColor(category: String): Color = when (category) {
    "food" -> Color(0xFFFF8C00)
    "fuel" -> Color(0xFFFFD700)
    "health" -> Color(0xFFFF4444)
    "accommodation" -> Color(0xFF9C27B0)
    "finance" -> Color(0xFF4CAF50)
    "shopping" -> Color(0xFFE91E63)
    "recreation" -> Color(0xFF8BC34A)
    else -> ElectricCyan
}

@Composable
fun CarMapViewContainer(
    engine: CarMapEngine,
    limitSearchDistance: Boolean,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapUiState by engine.uiState.collectAsState()
    var searchOpen by remember { mutableStateOf(false) }

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
            .padding(CarDimensions.PaneGap)
            .clip(MaterialTheme.shapes.medium),
    ) {
        AndroidView(
            factory = { context -> engine.createMapView(context) },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(CarDimensions.PaneGap)
                .background(OledBlack.copy(alpha = 0.72f))
                .padding(CarDimensions.PaneGap),
        ) {
            CarHeadlineText(
                text = "${mapUiState.currentSpeed} km/h",
                style = MaterialTheme.typography.headlineMedium,
            )
            CarBodyText(
                text = mapUiState.streetName,
                modifier = Modifier.padding(top = CarDimensions.PaneGap / 2),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (mapUiState.isNavigating) {
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

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(CarDimensions.PaneGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconButton(
                onClick = { searchOpen = true },
                modifier = Modifier
                    .size(CarDimensions.MinTapTarget)
                    .background(OledBlack.copy(alpha = 0.72f), MaterialTheme.shapes.small),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search destination",
                    tint = MaterialTheme.colorScheme.primary,
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

            if (mapUiState.isCameraDetached) {
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

        if (mapUiState.routeOverviewProgress > 0f) {
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

        AnimatedVisibility(
            visible = mapUiState.selectedPoi != null,
            modifier = Modifier.fillMaxSize(),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            val selectedPoi = mapUiState.selectedPoi ?: return@AnimatedVisibility
            PoiDetailDrawer(
                poi = selectedPoi,
                onNavigate = {
                    engine.navigateToCoordinates(selectedPoi.latitude, selectedPoi.longitude)
                },
                onDismiss = { engine.dismissSelectedPoi() },
            )
        }
    }

    if (searchOpen) {
        NavigationSearchOverlay(
            engine = engine,
            limitSearchDistance = limitSearchDistance,
            onDismiss = { searchOpen = false },
        )
    }
}

@Composable
private fun PoiDetailDrawer(
    poi: SearchResultPlace,
    onNavigate: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OledBlack.copy(alpha = 0.4f))
                .clickable(onClick = onDismiss),
        )

        ElevatedCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(CarDimensions.PaneGap),
            colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
                containerColor = DeepCharcoal,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CarDimensions.PaneGap * 2),
                verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
            ) {
                CarHeadlineText(
                    text = poi.name,
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (poi.category.isNotBlank()) {
                    CarLabelText(
                        text = categoryDisplayName(poi.category),
                        modifier = Modifier
                            .background(
                                categoryColor(poi.category).copy(alpha = 0.25f),
                                MaterialTheme.shapes.small,
                            )
                            .padding(
                                horizontal = CarDimensions.PaneGap,
                                vertical = CarDimensions.PaneGap / 2,
                            ),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = categoryColor(poi.category),
                        ),
                    )
                }
                if (poi.subTitle.isNotBlank()) {
                    CarBodyText(
                        text = poi.subTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                    )
                }
                if (poi.distanceInMeters > 0f) {
                    CarLabelText(
                        text = poi.distanceInMeters.formatDistance(),
                        style = MaterialTheme.typography.labelLarge.copy(color = ElectricCyan),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onNavigate,
                        modifier = Modifier
                            .weight(1f)
                            .height(CarDimensions.MinTapTarget),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = OledBlack,
                        ),
                    ) {
                        CarLabelText(
                            text = "Navigate",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(CarDimensions.MinTapTarget),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close place details",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
