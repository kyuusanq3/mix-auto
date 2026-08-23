package com.kyuusanq3.mixauto.data.map

import android.content.Context
import com.kyuusanq3.mixauto.domain.map.MapUiState
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap

/** Driving viewport padding compute/apply + dedup caches (kept with apply path). */
internal class DrivingViewportPaddingController(
    private val appContext: () -> Context?,
    private val uiState: () -> MapUiState,
    private val puckHorizontalOffset: () -> Float,
    private val puckVerticalOffset: () -> Float,
    private val lastDrivingSpeedMps: () -> Float,
    private val isRouteOverviewActive: () -> Boolean,
    private val navigationCameraTransitionActive: () -> Boolean,
    private val shouldSmoothPuckMotion: () -> Boolean,
    private val forceLocationUpdateForImmediateRender: (
        map: MapLibreMap,
        bypassThrottle: Boolean,
        allowDuringSmoothing: Boolean,
    ) -> Unit,
) {
    private var lastAppliedTrackingPadding: IntArray? = null
    private var lastEngagedTrackingPadding: IntArray? = null
    private var lookaheadPaddingActive: Boolean = false

    fun resetLookaheadPaddingActive() {
        lookaheadPaddingActive = false
    }

    fun updateLookaheadPaddingState(speedMps: Float) {
        lookaheadPaddingActive = nextLookaheadPaddingActive(lookaheadPaddingActive, speedMps)
    }

    fun invalidateDrivingPaddingCache() {
        lastAppliedTrackingPadding = null
        lastEngagedTrackingPadding = null
    }

    fun hasEngagedTrackingPadding(): Boolean = lastEngagedTrackingPadding != null

    fun computeDrivingViewportPadding(map: MapLibreMap): ViewportPadding {
        val density = appContext()?.resources?.displayMetrics?.density ?: 1f
        val lookaheadFrac = bucketedLookaheadTopFraction(lastDrivingSpeedMps(), lookaheadPaddingActive)
        val dm = appContext()?.resources?.displayMetrics
        val fallbackW = dm?.widthPixels?.takeIf { it > 0 } ?: 1080
        val fallbackH = dm?.heightPixels?.takeIf { it > 0 } ?: 1920
        return computeDrivingViewportPadding(
            density = density,
            mapWidth = map.width,
            mapHeight = map.height,
            fallbackWidth = fallbackW,
            fallbackHeight = fallbackH,
            puckHorizontalOffset = puckHorizontalOffset(),
            puckVerticalOffset = puckVerticalOffset(),
            lookaheadFraction = lookaheadFrac,
        )
    }

    fun drivingPaddingNeedsUpdate(
        paddingKey: IntArray,
        trackingGps: Boolean,
    ): Boolean {
        // Key-only: 1px Compose/layout jitter used to force paddingWhileTracking even when the
        // quantized puck offset was unchanged, which restarts MapLibre's tracking camera (flicker).
        // Real resizes that change placement still produce a new key (4dp quantize).
        val cached = if (trackingGps) lastEngagedTrackingPadding else lastAppliedTrackingPadding
        return cached?.contentEquals(paddingKey) != true
    }

    fun applyDrivingViewportPadding(map: MapLibreMap) {
        if (uiState().isInTopDownView) return
        applyMapPaddingImmediate(map, computeDrivingViewportPadding(map))
    }

    fun applyDrivingTrackingPadding(map: MapLibreMap) {
        if (isRouteOverviewActive() || navigationCameraTransitionActive()) return
        if (uiState().isInTopDownView || uiState().isCameraDetached) return
        val padding = computeDrivingViewportPadding(map)
        val paddingKey = intArrayOf(padding.left, padding.top, padding.right, padding.bottom)
        val component = map.locationComponent
        val componentReady = component.isLocationComponentActivated && component.isLocationComponentEnabled
        val alreadyTrackingGps = componentReady && component.cameraMode == CameraMode.TRACKING_GPS
        if (!alreadyTrackingGps) {
            if (!drivingPaddingNeedsUpdate(paddingKey, trackingGps = false)) return
            lastAppliedTrackingPadding = paddingKey
            applyMapPaddingImmediate(map, padding)
            return
        }
        if (!drivingPaddingNeedsUpdate(paddingKey, trackingGps = true)) return
        lastAppliedTrackingPadding = paddingKey
        lastEngagedTrackingPadding = paddingKey
        applyPaddingWhileTrackingIfEngaged(component, padding)
        if (!shouldSmoothPuckMotion()) {
            forceLocationUpdateForImmediateRender(map, false, false)
        }
    }

    fun maybeApplyDrivingPaddingForSpeedChange(map: MapLibreMap) {
        if (isRouteOverviewActive() || navigationCameraTransitionActive()) return
        if (uiState().isInTopDownView || uiState().isCameraDetached) return
        val padding = computeDrivingViewportPadding(map)
        val paddingKey = intArrayOf(padding.left, padding.top, padding.right, padding.bottom)
        val component = map.locationComponent
        val trackingGps = component.isLocationComponentActivated &&
            component.isLocationComponentEnabled &&
            component.cameraMode == CameraMode.TRACKING_GPS
        if (!drivingPaddingNeedsUpdate(paddingKey, trackingGps)) return
        applyDrivingTrackingPadding(map)
    }

    fun clearViewportPaddingForPreview(map: MapLibreMap) {
        invalidateDrivingPaddingCache()
        applyMapPaddingImmediate(map, ViewportPadding(0, 0, 0, 0))
        applyPaddingWhileTrackingIfEngaged(map.locationComponent, ViewportPadding(0, 0, 0, 0))
    }

    fun applyPuckPaddingUpdate(map: MapLibreMap, bypassRenderThrottle: Boolean = false) {
        invalidateDrivingPaddingCache()
        val component = map.locationComponent
        val componentReady = component.isLocationComponentActivated &&
            component.isLocationComponentEnabled
        val trackingGps = componentReady && component.cameraMode == CameraMode.TRACKING_GPS
        if (trackingGps) {
            applyDrivingTrackingPadding(map)
            forceLocationUpdateForImmediateRender(map, bypassRenderThrottle, true)
        } else {
            applyDrivingViewportPadding(map)
            applyDrivingTrackingPadding(map)
            forceLocationUpdateForImmediateRender(map, bypassRenderThrottle, true)
        }
    }

    private fun applyMapPaddingImmediate(map: MapLibreMap, padding: ViewportPadding) {
        map.moveCamera(
            CameraUpdateFactory.paddingTo(
                padding.left.toDouble(),
                padding.top.toDouble(),
                padding.right.toDouble(),
                padding.bottom.toDouble(),
            ),
        )
    }

    private fun applyPaddingWhileTrackingIfEngaged(
        component: LocationComponent,
        padding: ViewportPadding,
    ) {
        if (!component.isLocationComponentActivated || !component.isLocationComponentEnabled) return
        if (component.cameraMode == CameraMode.NONE) return
        component.paddingWhileTracking(
            doubleArrayOf(
                padding.left.toDouble(),
                padding.top.toDouble(),
                padding.right.toDouble(),
                padding.bottom.toDouble(),
            ),
        )
    }
}
