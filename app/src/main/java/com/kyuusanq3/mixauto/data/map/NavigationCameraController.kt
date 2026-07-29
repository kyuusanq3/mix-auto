package com.kyuusanq3.mixauto.data.map

import android.content.Context
import android.util.Log
import com.kyuusanq3.mixauto.domain.map.MapUiState
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import kotlin.math.abs
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private const val TAG = "NavigationCameraController"
private const val NAV_CAMERA_DURATION_MS = 2500
private const val POI_PREVIEW_ZOOM = 15.5
private const val TOP_DOWN_EXPLORE_ZOOM = 15.0
private const val POI_PREVIEW_MAX_RETRIES = 8
private const val POI_PREVIEW_RETRY_DELAY_MS = 50L
private const val DRIVING_ANIMATION_FPS = 60

/**
 * Camera mode transitions, viewport padding, and top-down / POI preview orchestration,
 * extracted from [MapLibreEngineImpl]. Owns padding dedup caches, POI preview retry state,
 * top-down viewport sync runnables, and [navigationCameraTransitionActive].
 *
 * Cross-cutting navigation/route/overview flags and [lastKnownLocation] stay on the engine
 * and are read via injected getters; location puck refresh uses injected callbacks into
 * [LocationTrackingController].
 */
internal class NavigationCameraController(
    private val mapView: () -> MapView?,
    private val mapLibreMap: () -> MapLibreMap?,
    private val appContext: () -> Context?,
    private val uiState: () -> MapUiState,
    private val updateUiState: ((MapUiState) -> MapUiState) -> Unit,
    private val freeDriveZoom: () -> Double,
    private val navZoom: () -> Double,
    private val freeDriveTilt: () -> Double,
    private val navTilt: () -> Double,
    private val puckHorizontalOffset: () -> Float,
    private val puckVerticalOffset: () -> Float,
    private val useVectorTiles: () -> Boolean,
    private val lastKnownLocation: () -> LatLng?,
    private val setLastKnownLocation: (LatLng?) -> Unit,
    private val hasSnappedCameraToGps: () -> Boolean,
    private val setHasSnappedCameraToGps: (Boolean) -> Unit,
    private val isRouteOverviewActive: () -> Boolean,
    private val lastDrivingSpeedMps: () -> Float,
    private val readLastKnownLocation: (Context) -> LatLng?,
    private val shouldSmoothPuckMotion: () -> Boolean,
    private val forceLocationUpdateForImmediateRender: (
        map: MapLibreMap,
        bypassThrottle: Boolean,
        allowDuringSmoothing: Boolean,
    ) -> Unit,
    private val resetSmoothingMotion: () -> Unit,
    private val stopDeadReckoning: () -> Unit,
    private val showNativeVectorPoiLayers: (Style) -> Unit,
    private val clearPoiOverlay: () -> Unit,
    private val fitRouteOverviewCamera: (origin: LatLng, destination: LatLng, animate: Boolean) -> Unit,
    private val routeOverviewOrigin: () -> LatLng?,
    private val routeOverviewDestination: () -> LatLng?,
    private val lastRouteOverviewLayoutWidth: () -> Int,
    private val lastRouteOverviewLayoutHeight: () -> Int,
    private val setLastRouteOverviewLayoutSize: (width: Int, height: Int) -> Unit,
    private val onNavigationTrackingEngaged: () -> Unit,
) {
    var navigationCameraTransitionActive: Boolean = false
        private set

    /** Incremented to invalidate in-flight [enterNavigationCamera] callbacks after gesture / End nav. */
    private var cameraSessionId: Int = 0

    private var lastAppliedDynamicNavZoom: Double? = null
    private var lastDistToManeuverM: Float? = null

    private lateinit var viewportPadding: DrivingViewportPaddingController
    private lateinit var topDownPoi: TopDownPoiCameraController

    init {
        viewportPadding = DrivingViewportPaddingController(
            appContext = appContext,
            uiState = uiState,
            puckHorizontalOffset = puckHorizontalOffset,
            puckVerticalOffset = puckVerticalOffset,
            lastDrivingSpeedMps = lastDrivingSpeedMps,
            isRouteOverviewActive = isRouteOverviewActive,
            navigationCameraTransitionActive = { navigationCameraTransitionActive },
            shouldSmoothPuckMotion = shouldSmoothPuckMotion,
            forceLocationUpdateForImmediateRender = forceLocationUpdateForImmediateRender,
        )
        topDownPoi = TopDownPoiCameraController(
            mapView = mapView,
            mapLibreMap = mapLibreMap,
            uiState = uiState,
            updateUiState = updateUiState,
            useVectorTiles = useVectorTiles,
            lastKnownLocation = lastKnownLocation,
            resolveFreeDriveTarget = ::resolveFreeDriveTarget,
            stopDeadReckoning = stopDeadReckoning,
            resetSmoothingMotion = resetSmoothingMotion,
            showNativeVectorPoiLayers = showNativeVectorPoiLayers,
            clearPoiOverlay = clearPoiOverlay,
            clearViewportPaddingForPreview = viewportPadding::clearViewportPaddingForPreview,
            ensureTopDownCameraDetached = { map ->
                val component = map.locationComponent
                if (component.isLocationComponentActivated && component.isLocationComponentEnabled) {
                    component.cameraMode = CameraMode.NONE
                }
            },
        )
    }

    fun resetLookaheadPaddingActive() = viewportPadding.resetLookaheadPaddingActive()

    fun resetDynamicNavigationZoom() {
        lastAppliedDynamicNavZoom = null
        lastDistToManeuverM = null
    }

    fun onNavZoomCeilingChanged() {
        val distance = lastDistToManeuverM
        if (distance != null) {
            lastAppliedDynamicNavZoom = null
            updateNavigationZoomForDistance(distance)
        } else {
            applyNavigationZoomCeiling()
        }
    }

    fun onDrivingTiltChanged() {
        val map = mapLibreMap() ?: return
        val state = uiState()
        if (!state.isNavigating || state.isCameraDetached || navigationCameraTransitionActive) return

        val component = map.locationComponent
        if (!component.isLocationComponentActivated || !component.isLocationComponentEnabled) return

        val target = lastKnownLocation() ?: map.cameraPosition.target ?: return
        val bearing = component.lastKnownLocation?.bearing?.toDouble() ?: map.cameraPosition.bearing
        val zoomTarget = resolveNavigationZoomTarget()

        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(target)
                    .zoom(zoomTarget)
                    .tilt(navTilt())
                    .bearing(bearing)
                    .build(),
            ),
        )
    }

    fun updateNavigationZoomForDistance(distanceM: Float) {
        lastDistToManeuverM = distanceM
        if (!canApplyDynamicNavigationZoom()) return

        val target = NavigationZoom.targetZoomForManeuverDistance(distanceM, navZoom())
        if (!NavigationZoom.shouldApplyZoomChange(lastAppliedDynamicNavZoom, target)) return

        val map = mapLibreMap() ?: return
        val component = map.locationComponent
        if (!component.isLocationComponentActivated || !component.isLocationComponentEnabled) return
        if (component.cameraMode != CameraMode.TRACKING_GPS) return

        component.zoomWhileTracking(target)
        lastAppliedDynamicNavZoom = target
    }

    private fun canApplyDynamicNavigationZoom(): Boolean {
        val state = uiState()
        return state.isNavigating &&
            !state.isCameraDetached &&
            !isRouteOverviewActive() &&
            !navigationCameraTransitionActive
    }

    private fun resolveNavigationZoomTarget(): Double =
        lastAppliedDynamicNavZoom ?: navZoom()

    private fun applyNavigationZoomCeiling() {
        if (!canApplyDynamicNavigationZoom()) return
        val map = mapLibreMap() ?: return
        val component = map.locationComponent
        if (!component.isLocationComponentActivated || !component.isLocationComponentEnabled) return
        if (component.cameraMode != CameraMode.TRACKING_GPS) return
        val target = navZoom()
        if (!NavigationZoom.shouldApplyZoomChange(lastAppliedDynamicNavZoom, target)) return
        component.zoomWhileTracking(target)
        lastAppliedDynamicNavZoom = target
    }

    fun resetTopDownExploreUserAdjusted() = topDownPoi.resetTopDownExploreUserAdjusted()

    fun clearNavigationCameraTransitionActive() {
        navigationCameraTransitionActive = false
    }

    fun setNavigationCameraTransitionActive(active: Boolean) {
        navigationCameraTransitionActive = active
    }

    /** Stale dive/overview callbacks must no-op after user pan, zoom, or End nav. */
    fun invalidateCameraSession() {
        cameraSessionId++
        navigationCameraTransitionActive = false
    }

    fun prepareForFreeDriveCamera(map: MapLibreMap) {
        invalidateCameraSession()
        map.cancelTransitions()
        ensureTopDownCameraDetached(map)
    }

    fun hasPendingPoiPreviewTarget(): Boolean = topDownPoi.hasPendingPoiPreviewTarget()

    fun clearPoiPreviewState() = topDownPoi.clearPoiPreviewState()

    fun animateTopDownCamera(
        lat: Double,
        lng: Double,
        zoom: Double,
        exploreMode: Boolean = false,
    ) = topDownPoi.animateTopDownCamera(lat, lng, zoom, exploreMode)

    fun cancelPoiPreviewRetries() = topDownPoi.cancelPoiPreviewRetries()

    fun schedulePoiPreviewCameraRetry(immediate: Boolean = false) =
        topDownPoi.schedulePoiPreviewCameraRetry(immediate)

    fun ensureTopDownCameraDetached(map: MapLibreMap) {
        val component = map.locationComponent
        if (component.isLocationComponentActivated && component.isLocationComponentEnabled) {
            component.cameraMode = CameraMode.NONE
        }
    }

    fun cancelTopDownViewportSync() = topDownPoi.cancelTopDownViewportSync()

    fun scheduleTopDownViewportSync(map: MapLibreMap) = topDownPoi.scheduleTopDownViewportSync(map)

    fun handleMapLayoutChange(map: MapLibreMap) {
        val view = mapView() ?: return
        if (view.width <= 0 || view.height <= 0) return

        if (isRouteOverviewActive()) {
            val origin = routeOverviewOrigin()
            val destination = routeOverviewDestination()
            if (origin != null && destination != null) {
                val w = view.width
                val h = view.height
                if (w != lastRouteOverviewLayoutWidth() || h != lastRouteOverviewLayoutHeight()) {
                    setLastRouteOverviewLayoutSize(w, h)
                    fitRouteOverviewCamera(origin, destination, false)
                }
            }
            return
        }
        if (navigationCameraTransitionActive) {
            return
        }

        val state = uiState()
        if (state.isInTopDownView) {
            when {
                topDownPoi.hasPendingPoiPreviewTarget() -> topDownPoi.schedulePoiPreviewCameraRetry(immediate = true)
                state.selectedPoi != null -> topDownPoi.recenterOnSelectedPoi(map, state.selectedPoi)
                else -> topDownPoi.handleTopDownLayoutChange(map)
            }
            return
        }
        if (!state.isCameraDetached) {
            val component = map.locationComponent
            val componentReady = component.isLocationComponentActivated &&
                component.isLocationComponentEnabled
            val trackingGps = componentReady && component.cameraMode == CameraMode.TRACKING_GPS
            val padding = viewportPadding.computeDrivingViewportPadding(map)
            val paddingKey = intArrayOf(padding.left, padding.top, padding.right, padding.bottom)
            val paddingChanged = viewportPadding.drivingPaddingNeedsUpdate(map, paddingKey, trackingGps)
            if (paddingChanged) {
                if (trackingGps) {
                    viewportPadding.applyDrivingTrackingPadding(map)
                } else {
                    viewportPadding.applyDrivingViewportPadding(map)
                    viewportPadding.applyDrivingTrackingPadding(map)
                    if (state.isNavigating) {
                        forceLocationUpdateForImmediateRender(map, true, false)
                    }
                }
            }
            if (state.isNavigating && componentReady && component.cameraMode != CameraMode.TRACKING_GPS) {
                activateNavigationTracking(componentReady)
            }
        }
    }

    fun applyPuckPaddingUpdate(map: MapLibreMap, bypassRenderThrottle: Boolean = false) {
        viewportPadding.applyPuckPaddingUpdate(map, bypassRenderThrottle)
        val component = map.locationComponent
        val componentReady = component.isLocationComponentActivated &&
            component.isLocationComponentEnabled
        if (uiState().isNavigating && componentReady && component.cameraMode != CameraMode.TRACKING_GPS) {
            activateNavigationTracking(componentReady)
        }
    }

    fun clearViewportPaddingForPreview(map: MapLibreMap) =
        viewportPadding.clearViewportPaddingForPreview(map)

    fun scheduleFreeDrivePaddingRestore(map: MapLibreMap) {
        if (uiState().isNavigating) return
        val view = mapView() ?: return
        view.post {
            if (uiState().isInTopDownView ||
                uiState().isCameraDetached ||
                uiState().isNavigating
            ) {
                return@post
            }
            applyPuckPaddingUpdate(map, bypassRenderThrottle = true)
        }
    }

    fun resolveFreeDriveTarget(map: MapLibreMap): LatLng? {
        lastKnownLocation()?.let { return it }
        val component = map.locationComponent
        if (component.isLocationComponentActivated) {
            component.lastKnownLocation?.let { return LatLng(it.latitude, it.longitude) }
        }
        appContext()?.let { readLastKnownLocation(it) }?.let { return it }
        return null
    }

    fun activateFreeDriveTrackingMode(map: MapLibreMap) {
        if (uiState().isNavigating ||
            uiState().isInTopDownView ||
            uiState().isCameraDetached
        ) {
            return
        }

        val component = map.locationComponent
        if (!component.isLocationComponentActivated || !component.isLocationComponentEnabled) return

        val alreadyTracking = component.cameraMode == CameraMode.TRACKING_GPS &&
            component.renderMode == RenderMode.GPS
        if (alreadyTracking) {
            if (!viewportPadding.hasEngagedTrackingPadding()) {
                viewportPadding.applyDrivingTrackingPadding(map)
                forceLocationUpdateForImmediateRender(
                    map,
                    true,
                    true,
                )
            }
            return
        }

        component.renderMode = RenderMode.GPS
        component.cameraMode = CameraMode.TRACKING_GPS
        component.setMaxAnimationFps(DRIVING_ANIMATION_FPS)
        viewportPadding.applyDrivingTrackingPadding(map)
    }

    fun snapCameraToGpsIfNeeded(latLng: LatLng) {
        if (uiState().isInTopDownView) return
        setLastKnownLocation(latLng)
        val map = mapLibreMap() ?: return
        if (!needsFreeDriveCameraSnap(map)) {
            activateFreeDriveTrackingMode(map)
            return
        }

        val component = map.locationComponent
        val componentReady = component.isLocationComponentActivated && component.isLocationComponentEnabled
        val bearing = if (componentReady) {
            component.lastKnownLocation?.bearing?.toDouble() ?: map.cameraPosition.bearing
        } else {
            map.cameraPosition.bearing
        }

        Log.i(
            TAG,
            "Snapping free-drive camera to ${latLng.latitude}, ${latLng.longitude} " +
                "zoom=${freeDriveZoom()} tilt=${freeDriveTilt()} (current=${map.cameraPosition.zoom}, " +
                "target=${formatCameraTarget(map)})",
        )

        if (componentReady) {
            component.renderMode = RenderMode.GPS
            component.cameraMode = CameraMode.NONE
        }

        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(latLng)
                    .tilt(freeDriveTilt())
                    .zoom(freeDriveZoom())
                    .bearing(bearing)
                    .build(),
            ),
        )

        if (componentReady) {
            component.cameraMode = CameraMode.TRACKING_GPS
            viewportPadding.applyDrivingTrackingPadding(map)
            forceLocationUpdateForImmediateRender(
                map,
                true,
                true,
            )
        }

        setHasSnappedCameraToGps(true)
        updateUiState {
            it.copy(streetName = "Free Drive", isCameraDetached = false, isInTopDownView = false)
        }
    }

    fun enterNavigationCamera() {
        val map = mapLibreMap() ?: run {
            navigationCameraTransitionActive = false
            return
        }
        val component = map.locationComponent
        val componentReady = component.isLocationComponentActivated &&
            component.isLocationComponentEnabled
        val target = lastKnownLocation() ?: map.cameraPosition.target ?: run {
            navigationCameraTransitionActive = false
            return
        }

        invalidateCameraSession()
        val sessionId = cameraSessionId
        navigationCameraTransitionActive = true

        val bearing = if (componentReady) {
            component.lastKnownLocation?.bearing?.toDouble() ?: map.cameraPosition.bearing
        } else {
            map.cameraPosition.bearing
        }

        Log.i(
            TAG,
            "Entering navigation camera zoom=${navZoom()} tilt=${navTilt()} " +
                "(current=${map.cameraPosition.zoom}, target=${target.latitude},${target.longitude})",
        )

        viewportPadding.applyDrivingViewportPadding(map)
        map.cancelTransitions()
        if (componentReady) {
            runCatching {
                component.renderMode = RenderMode.GPS
                component.cameraMode = CameraMode.NONE
            }.onFailure { error ->
                Log.w(TAG, "Failed to detach location component before nav dive: ${error.message}")
            }
        }

        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(target)
                    .zoom(navZoom())
                    .tilt(navTilt())
                    .bearing(bearing)
                    .build(),
            ),
            NAV_CAMERA_DURATION_MS,
            object : MapLibreMap.CancelableCallback {
                override fun onFinish() {
                    finishNavigationCameraTransition(sessionId, componentReady)
                }

                override fun onCancel() {
                    finishNavigationCameraTransition(sessionId, componentReady)
                }
            },
        )
        updateUiState { it.copy(isCameraDetached = false, isInTopDownView = false) }
    }

    private fun finishNavigationCameraTransition(sessionId: Int, componentReady: Boolean) {
        if (sessionId != cameraSessionId ||
            !uiState().isNavigating ||
            uiState().isCameraDetached
        ) {
            navigationCameraTransitionActive = false
            return
        }
        activateNavigationTracking(componentReady)
    }

    fun activateNavigationTracking(componentReady: Boolean) {
        navigationCameraTransitionActive = false
        if (!uiState().isNavigating || uiState().isCameraDetached) return
        val map = mapLibreMap() ?: return
        val component = map.locationComponent
        if (componentReady && component.isLocationComponentActivated && component.isLocationComponentEnabled) {
            runCatching {
                val target = lastKnownLocation() ?: map.cameraPosition.target
                if (target != null) {
                    val bearing = component.lastKnownLocation?.bearing?.toDouble()
                        ?: map.cameraPosition.bearing
                    val current = map.cameraPosition
                    val zoomTarget = resolveNavigationZoomTarget()
                    if (current.tilt < navTilt() - 5.0 || abs(current.zoom - zoomTarget) > 0.5) {
                        map.moveCamera(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder()
                                    .target(target)
                                    .zoom(zoomTarget)
                                    .tilt(navTilt())
                                    .bearing(bearing)
                                    .build(),
                            ),
                        )
                        lastAppliedDynamicNavZoom = zoomTarget
                    }
                }
                component.renderMode = RenderMode.GPS
                component.cameraMode = CameraMode.TRACKING_GPS
                component.setMaxAnimationFps(DRIVING_ANIMATION_FPS)
                viewportPadding.applyDrivingTrackingPadding(map)
                lastDistToManeuverM?.let { updateNavigationZoomForDistance(it) }
            }.onFailure { error ->
                Log.w(TAG, "Failed to activate navigation tracking: ${error.message}")
            }
        }
        onNavigationTrackingEngaged()
    }

    fun computeDrivingViewportPadding(map: MapLibreMap): ViewportPadding =
        viewportPadding.computeDrivingViewportPadding(map)

    fun updateLookaheadPaddingState(speedMps: Float) =
        viewportPadding.updateLookaheadPaddingState(speedMps)

    fun maybeApplyDrivingPaddingForSpeedChange(map: MapLibreMap) =
        viewportPadding.maybeApplyDrivingPaddingForSpeedChange(map)

    fun applyDrivingViewportPadding(map: MapLibreMap) =
        viewportPadding.applyDrivingViewportPadding(map)

    fun invalidateDrivingPaddingCache() = viewportPadding.invalidateDrivingPaddingCache()

    fun applyDrivingTrackingPadding(map: MapLibreMap) =
        viewportPadding.applyDrivingTrackingPadding(map)

    fun onCameraGestureStarted(map: MapLibreMap) {
        invalidateCameraSession()
        map.cancelTransitions()
        topDownPoi.onCameraGestureStarted(map)
    }

    fun enterTopDownExploreView() = topDownPoi.enterTopDownExploreView()

    private fun isFreeDriveZoomTooWide(map: MapLibreMap): Boolean =
        map.cameraPosition.zoom < freeDriveZoom() - 0.5

    private fun needsFreeDriveCameraSnap(map: MapLibreMap): Boolean =
        !hasSnappedCameraToGps() || isFreeDriveZoomTooWide(map)

    private fun formatCameraTarget(map: MapLibreMap): String {
        val target = map.cameraPosition.target
        return if (target != null) "${target.latitude},${target.longitude}" else "null"
    }
}
