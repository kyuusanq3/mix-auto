package com.kyuusanq3.mixauto.ui.dashboard

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.ui.components.CarMapViewContainer
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import kotlin.math.roundToInt

@Composable
internal fun MapPaneSlot(
    googleMapsMode: Boolean,
    onMapBoundsChanged: (Rect) -> Unit,
    onMapEnginePause: () -> Unit,
    onMapEngineResume: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    LaunchedEffect(googleMapsMode) {
        if (googleMapsMode) {
            onMapEnginePause()
        } else {
            onMapEngineResume()
        }
    }
    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            onMapBoundsChanged(
                Rect(
                    bounds.left.roundToInt(),
                    bounds.top.roundToInt(),
                    bounds.right.roundToInt(),
                    bounds.bottom.roundToInt(),
                ),
            )
        },
    ) {
        if (googleMapsMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OledBlack),
            )
        } else {
            content()
        }
    }
}

@Composable
internal fun DashboardMapEnginePane(
    googleMapsMode: Boolean,
    onMapBoundsChanged: (Rect) -> Unit,
    mapEngine: CarMapEngine,
    onToggleSearch: () -> Unit,
    isDestinationPanelOpen: Boolean,
    onToggleMapSettings: () -> Unit,
    isMapSettingsPanelOpen: Boolean,
    reduceTopInset: Boolean,
    modifier: Modifier,
) {
    MapPaneSlot(
        googleMapsMode = googleMapsMode,
        onMapBoundsChanged = onMapBoundsChanged,
        onMapEnginePause = { mapEngine.onPause() },
        onMapEngineResume = { mapEngine.onResume() },
        modifier = modifier,
    ) {
        CarMapViewContainer(
            engine = mapEngine,
            onToggleSearch = onToggleSearch,
            isDestinationPanelOpen = isDestinationPanelOpen,
            onToggleMapSettings = onToggleMapSettings,
            isMapSettingsPanelOpen = isMapSettingsPanelOpen,
            reduceTopInset = reduceTopInset,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
