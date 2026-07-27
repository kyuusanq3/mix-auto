package com.kyuusanq3.mixauto.data.map

import com.kyuusanq3.mixauto.domain.map.MapUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/**
 * Route overview session: bounds fit, hold timer, then nav camera dive.
 * Extracted from [MapLibreEngineImpl] via callback injection.
 */
internal class RouteOverviewController(
    private val engineScope: CoroutineScope,
    private val mapView: () -> MapView?,
    private val mapLibreMap: () -> MapLibreMap?,
    private val appContext: () -> android.content.Context?,
    private val uiState: () -> MapUiState,
    private val updateUiState: ((MapUiState) -> MapUiState) -> Unit,
    private val routeGeometryPoints: () -> List<LatLng>,
    private val lastKnownLocation: () -> LatLng?,
    private val invalidateDrivingPaddingCache: () -> Unit,
    private val resetSmoothingMotion: () -> Unit,
    private val clearNavigationCameraTransitionActive: () -> Unit,
    private val setNavigationCameraTransitionActive: (Boolean) -> Unit,
    private val enterNavigationCamera: () -> Unit,
    private val clearRoutePreviewState: () -> Unit,
    private val cancelDrivingTilePrefetch: () -> Unit,
    private val routeOverviewAnimationMs: Int,
    private val routeOverviewHoldMs: Long,
) {
    private var routeOverviewJob: Job? = null
    private var routeOverviewOrigin: LatLng? = null
    private var routeOverviewDestination: LatLng? = null
    private var lastRouteOverviewLayoutWidth = 0
    private var lastRouteOverviewLayoutHeight = 0

    fun routeOverviewOrigin(): LatLng? = routeOverviewOrigin

    fun routeOverviewDestination(): LatLng? = routeOverviewDestination

    fun lastRouteOverviewLayoutWidth(): Int = lastRouteOverviewLayoutWidth

    fun lastRouteOverviewLayoutHeight(): Int = lastRouteOverviewLayoutHeight

    fun setLastRouteOverviewLayoutSize(width: Int, height: Int) {
        lastRouteOverviewLayoutWidth = width
        lastRouteOverviewLayoutHeight = height
    }

    fun isRouteOverviewActive(): Boolean = routeOverviewJob?.isActive == true

    fun clearRouteOverviewState() {
        routeOverviewJob?.cancel()
        routeOverviewJob = null
        routeOverviewOrigin = null
        routeOverviewDestination = null
        lastRouteOverviewLayoutWidth = 0
        lastRouteOverviewLayoutHeight = 0
        clearNavigationCameraTransitionActive()
        cancelDrivingTilePrefetch()
        resetSmoothingMotion()
        updateUiState { it.copy(routeOverviewProgress = 0f) }
    }

    fun showRouteThenDive(origin: LatLng, destination: LatLng) {
        if (mapLibreMap() == null) return
        clearRouteOverviewState()
        clearRoutePreviewState()
        routeOverviewOrigin = origin
        routeOverviewDestination = destination
        lastRouteOverviewLayoutWidth = 0
        lastRouteOverviewLayoutHeight = 0
        updateUiState { it.copy(isCameraDetached = false, isInTopDownView = false) }

        val animateToBounds = {
            fitRouteOverviewCamera(origin, destination, animate = true)
        }
        mapView()?.post { animateToBounds() } ?: animateToBounds()

        routeOverviewJob = engineScope.launch {
            val startMs = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - startMs
                val progress = (elapsed / routeOverviewHoldMs.toFloat()).coerceIn(0f, 1f)
                updateUiState { it.copy(routeOverviewProgress = progress) }
                if (elapsed >= routeOverviewHoldMs) break
                delay(50)
            }
            routeOverviewOrigin = null
            routeOverviewDestination = null
            updateUiState { it.copy(routeOverviewProgress = 0f) }
            if (uiState().isNavigating) {
                setNavigationCameraTransitionActive(true)
                val dive = { enterNavigationCamera() }
                mapView()?.post(dive) ?: dive()
            }
        }
    }

    fun fitRouteOverviewCamera(origin: LatLng, destination: LatLng, animate: Boolean) {
        val map = mapLibreMap() ?: return
        val view = mapView() ?: return
        if (view.width <= 0 || view.height <= 0) return

        val component = map.locationComponent
        if (component.isLocationComponentActivated && component.isLocationComponentEnabled) {
            component.cameraMode = CameraMode.NONE
        }
        invalidateDrivingPaddingCache()
        map.cancelTransitions()
        map.moveCamera(
            CameraUpdateFactory.paddingTo(0.0, 0.0, 0.0, 0.0),
        )

        val bounds = buildRouteOverviewBounds(
            origin,
            destination,
            emptyList(),
            routeGeometryPoints(),
            lastKnownLocation(),
        )
        val density = appContext()?.resources?.displayMetrics?.density ?: 2f
        val padding = computeRouteOverviewPadding(density, map.width.toFloat(), map.height.toFloat())
        val boundsUpdate = CameraUpdateFactory.newLatLngBounds(
            bounds,
            padding.left,
            padding.top,
            padding.right,
            padding.bottom,
        )
        if (animate) {
            map.moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(map.cameraPosition.target)
                        .zoom(map.cameraPosition.zoom)
                        .tilt(0.0)
                        .bearing(0.0)
                        .build(),
                ),
            )
            map.animateCamera(boundsUpdate, routeOverviewAnimationMs)
        } else {
            map.moveCamera(boundsUpdate)
        }
    }
}
