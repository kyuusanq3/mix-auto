package com.kyuusanq3.mixauto.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.ui.theme.CarDimensions

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

        LighterTrafficAlternateOverlay(
            engine = engine,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = SpeedCircleBottomInset + SpeedCircleSize + CarDimensions.PaneGap),
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

        OfflineMapLabelOverlay(
            engine = engine,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(CarDimensions.PaneGap),
        )
    }
}
