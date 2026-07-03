package com.kyuusanq3.mixauto.ui.dashboard

import android.graphics.Rect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.ui.theme.CarDimensions

@Composable
internal fun DashboardOrientationLayout(
    isPortrait: Boolean,
    isShortcutsHorizontal: Boolean,
    isLeftHandDrive: Boolean,
    showSecondaryPane: Boolean,
    effectiveMapMediaRatio: Float,
    effectiveMediaWeight: Float,
    showMapMediaDivider: Boolean,
    mapMediaRatio: Float,
    onMapMediaRatioChange: (Float) -> Unit,
    googleMapsMode: Boolean,
    onMapBoundsChanged: (Rect) -> Unit,
    mapEngine: CarMapEngine,
    onToggleSearch: () -> Unit,
    isDestinationPanelOpen: Boolean,
    onToggleMapSettings: () -> Unit,
    isMapSettingsPanelOpen: Boolean,
    reduceTopInsetBelowStatusStrip: Boolean,
    reduceMediaTopInsetBelowStatusStrip: Boolean,
    secondaryPane: @Composable (Modifier, Boolean) -> Unit,
    appDrawerOverlay: @Composable BoxScope.() -> Unit,
    horizontalShortcutDock: @Composable (Modifier) -> Unit,
    verticalShortcutDock: @Composable (Modifier) -> Unit,
) {
    when {
        isPortrait -> {
            var portraitMapMediaContainerPx by remember { mutableStateOf(0f) }
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { portraitMapMediaContainerPx = it.height.toFloat() },
                    ) {
                        DashboardMapEnginePane(
                            googleMapsMode = googleMapsMode,
                            onMapBoundsChanged = onMapBoundsChanged,
                            mapEngine = mapEngine,
                            onToggleSearch = onToggleSearch,
                            isDestinationPanelOpen = isDestinationPanelOpen,
                            onToggleMapSettings = onToggleMapSettings,
                            isMapSettingsPanelOpen = isMapSettingsPanelOpen,
                            reduceTopInset = reduceTopInsetBelowStatusStrip,
                            modifier = Modifier
                                .weight(if (showSecondaryPane) effectiveMapMediaRatio else 1f)
                                .fillMaxWidth(),
                        )
                        if (showSecondaryPane) {
                            if (showMapMediaDivider) {
                                MapMediaDividerHandle(
                                    isVertical = false,
                                    containerSizePx = portraitMapMediaContainerPx,
                                    mapMediaRatio = mapMediaRatio,
                                    onMapMediaRatioChange = onMapMediaRatioChange,
                                )
                            }
                            secondaryPane(
                                Modifier
                                    .weight(effectiveMediaWeight)
                                    .fillMaxWidth(),
                                reduceMediaTopInsetBelowStatusStrip,
                            )
                        }
                    }
                    appDrawerOverlay()
                }
                horizontalShortcutDock(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = CarDimensions.PaneGap)
                        .wrapContentHeight(),
                )
            }
        }
        isShortcutsHorizontal -> {
            var landscapeMapMediaContainerPx by remember { mutableStateOf(0f) }
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { landscapeMapMediaContainerPx = it.width.toFloat() },
                    ) {
                        if (isLeftHandDrive) {
                            DashboardMapEnginePane(
                                googleMapsMode = googleMapsMode,
                                onMapBoundsChanged = onMapBoundsChanged,
                                mapEngine = mapEngine,
                                onToggleSearch = onToggleSearch,
                                isDestinationPanelOpen = isDestinationPanelOpen,
                                onToggleMapSettings = onToggleMapSettings,
                                isMapSettingsPanelOpen = isMapSettingsPanelOpen,
                                reduceTopInset = reduceTopInsetBelowStatusStrip,
                                modifier = Modifier
                                    .weight(if (showSecondaryPane) effectiveMapMediaRatio else 1f)
                                    .fillMaxSize(),
                            )
                            if (showSecondaryPane) {
                                if (showMapMediaDivider) {
                                    MapMediaDividerHandle(
                                        isVertical = true,
                                        containerSizePx = landscapeMapMediaContainerPx,
                                        mapMediaRatio = mapMediaRatio,
                                        onMapMediaRatioChange = onMapMediaRatioChange,
                                    )
                                }
                                secondaryPane(
                                    Modifier
                                        .weight(effectiveMediaWeight)
                                        .fillMaxSize(),
                                    reduceMediaTopInsetBelowStatusStrip,
                                )
                            }
                        } else {
                            if (showSecondaryPane) {
                                secondaryPane(
                                    Modifier
                                        .weight(effectiveMediaWeight)
                                        .fillMaxSize(),
                                    reduceMediaTopInsetBelowStatusStrip,
                                )
                                if (showMapMediaDivider) {
                                    MapMediaDividerHandle(
                                        isVertical = true,
                                        containerSizePx = landscapeMapMediaContainerPx,
                                        mapMediaRatio = mapMediaRatio,
                                        onMapMediaRatioChange = onMapMediaRatioChange,
                                        invertDrag = true,
                                    )
                                }
                            }
                            DashboardMapEnginePane(
                                googleMapsMode = googleMapsMode,
                                onMapBoundsChanged = onMapBoundsChanged,
                                mapEngine = mapEngine,
                                onToggleSearch = onToggleSearch,
                                isDestinationPanelOpen = isDestinationPanelOpen,
                                onToggleMapSettings = onToggleMapSettings,
                                isMapSettingsPanelOpen = isMapSettingsPanelOpen,
                                reduceTopInset = reduceTopInsetBelowStatusStrip,
                                modifier = Modifier
                                    .weight(if (showSecondaryPane) effectiveMapMediaRatio else 1f)
                                    .fillMaxSize(),
                            )
                        }
                    }
                    appDrawerOverlay()
                }
                horizontalShortcutDock(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = CarDimensions.PaneGap)
                        .wrapContentHeight(),
                )
            }
        }
        else -> {
            var verticalDockRowWidthPx by remember { mutableStateOf(0f) }
            var verticalDockWidthPx by remember { mutableStateOf(0f) }
            val verticalDockMapMediaContainerPx = (verticalDockRowWidthPx - verticalDockWidthPx)
                .coerceAtLeast(0f)
            val mapPaneWeight = if (showSecondaryPane) {
                effectiveMapMediaRatio
            } else {
                effectiveMapMediaRatio + effectiveMediaWeight
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { verticalDockRowWidthPx = it.width.toFloat() },
            ) {
                if (isLeftHandDrive) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            DashboardMapEnginePane(
                                googleMapsMode = googleMapsMode,
                                onMapBoundsChanged = onMapBoundsChanged,
                                mapEngine = mapEngine,
                                onToggleSearch = onToggleSearch,
                                isDestinationPanelOpen = isDestinationPanelOpen,
                                onToggleMapSettings = onToggleMapSettings,
                                isMapSettingsPanelOpen = isMapSettingsPanelOpen,
                                reduceTopInset = reduceTopInsetBelowStatusStrip,
                                modifier = Modifier
                                    .weight(mapPaneWeight)
                                    .fillMaxSize(),
                            )
                            if (showSecondaryPane) {
                                if (showMapMediaDivider) {
                                    MapMediaDividerHandle(
                                        isVertical = true,
                                        containerSizePx = verticalDockMapMediaContainerPx,
                                        mapMediaRatio = mapMediaRatio,
                                        onMapMediaRatioChange = onMapMediaRatioChange,
                                    )
                                }
                                secondaryPane(
                                    Modifier
                                        .weight(effectiveMediaWeight)
                                        .fillMaxSize(),
                                    reduceMediaTopInsetBelowStatusStrip,
                                )
                            }
                        }
                        appDrawerOverlay()
                    }
                    verticalShortcutDock(
                        Modifier
                            .wrapContentWidth()
                            .fillMaxHeight()
                            .onSizeChanged { verticalDockWidthPx = it.width.toFloat() },
                    )
                } else {
                    verticalShortcutDock(
                        Modifier
                            .wrapContentWidth()
                            .fillMaxHeight()
                            .onSizeChanged { verticalDockWidthPx = it.width.toFloat() },
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            DashboardMapEnginePane(
                                googleMapsMode = googleMapsMode,
                                onMapBoundsChanged = onMapBoundsChanged,
                                mapEngine = mapEngine,
                                onToggleSearch = onToggleSearch,
                                isDestinationPanelOpen = isDestinationPanelOpen,
                                onToggleMapSettings = onToggleMapSettings,
                                isMapSettingsPanelOpen = isMapSettingsPanelOpen,
                                reduceTopInset = reduceTopInsetBelowStatusStrip,
                                modifier = Modifier
                                    .weight(mapPaneWeight)
                                    .fillMaxSize(),
                            )
                            if (showSecondaryPane) {
                                if (showMapMediaDivider) {
                                    MapMediaDividerHandle(
                                        isVertical = true,
                                        containerSizePx = verticalDockMapMediaContainerPx,
                                        mapMediaRatio = mapMediaRatio,
                                        onMapMediaRatioChange = onMapMediaRatioChange,
                                    )
                                }
                                secondaryPane(
                                    Modifier
                                        .weight(effectiveMediaWeight)
                                        .fillMaxSize(),
                                    reduceMediaTopInsetBelowStatusStrip,
                                )
                            }
                        }
                        appDrawerOverlay()
                    }
                }
            }
        }
    }
}
