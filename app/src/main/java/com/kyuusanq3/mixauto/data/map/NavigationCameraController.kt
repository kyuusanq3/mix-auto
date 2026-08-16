package com.kyuusanq3.mixauto.data.map

import android.content.Context
import android.util.Log
import com.kyuusanq3.mixauto.domain.map.MapUiState
import com.kyuusanq3.mixauto.ui.settings.DeveloperSettings
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import kotlin.math.abs
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.OnLocationCameraTransitionListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private const val TAG = "NavigationCameraController"
private const val NAV_CAMERA_DURATION_MS = 2500
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
    /**
     * Last GPS speed for follow-zoom combine (Turn C); updated every tick via
     * [updateDrivingZoomForSpeed]. Read when [NavigationZoom] gains `targetZoomForSpeed`.
     */
    private var lastSpeedMpsForZoom: Float = 0f

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
        lastSpeedMpsForZoom = 0f
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

    /**
     * GPS-tick entry for speed-based follow zoom (wired from [LocationTrackingController]).
     * Stores [speedMps] for nav combine; free-drive applies [NavigationZoom.targetZoomForSpeed].
     */
    fun updateDrivingZoomForSpeed(speedMps: Float) {
        lastSpeedMpsForZoom = speedMps
        if (!canApplyFreeDriveFollowZoom()) return
        applyFollowZoom(NavigationZoom.targetZoomForSpeed(speedMps.toDouble(), freeDriveZoom()))
    }

    fun updateNavigationZoomForDistance(distanceM: Float) {
        lastDistToManeuverM = distanceM
        if (!canApplyDynamicNavigationZoom()) return

        val maneuverZoom = NavigationZoom.targetZoomForManeuverDistance(distanceM, navZoom())
        val speedZoom = NavigationZoom.targetZoomForSpeed(lastSpeedMpsForZoom.toDouble(), navZoom())
        val target = minOf(speedZoom, maneuverZoom)
        applyFollowZoom(target)
    }

    /**
     * Single apply path for follow-mode [LocationComponent.zoomWhileTracking].
     * Callers must already pass mode guards ([canApplyDynamicNavigationZoom] /
     * [canApplyFreeDriveFollowZoom]).
     */
    private fun applyFollowZoom(target: Double): Boolean {
        if (!NavigationZoom.shouldApplyZoomChange(lastAppliedDynamicNavZoom, target)) return false
        val map = mapLibreMap() ?: return false
        val component = map.locationComponent
        if (!component.isLocationComponentActivated || !component.isLocationComponentEnabled) return false
        if (component.cameraMode != CameraMode.TRACKING_GPS) return false
        component.zoomWhileTracking(target)
        lastAppliedDynamicNavZoom = target
        return true
    }

    private fun canApplyDynamicNavigationZoom(): Boolean {
        val state = uiState()
        return state.isNavigating &&
            !state.isCameraDetached &&
            !isRouteOverviewActive() &&
            !navigationCameraTransitionActive
    }

    /** Free-drive follow zoom — never reuse [canApplyDynamicNavigationZoom] (requires navigating). */
    private fun canApplyFreeDriveFollowZoom(): Boolean {
        if (DeveloperSettings.MANUAL_DRIVING_ZOOM) return false
        val state = uiState()
        return !state.isNavigating &&
            !state.isCameraDetached &&
            !state.isInTopDownView &&
            !isRouteOverviewActive() &&
            !navigationCameraTransitionActive
    }

    private fun resolveNavigationZoomTarget(): Double =
        lastAppliedDynamicNavZoom ?: navZoom()

    private fun applyNavigationZoomCeiling() {
        if (!canApplyDynamicNavigationZoom()) return
        applyFollowZoom(navZoom())
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

    /**
     * Engages [CameraMode.TRACKING_GPS] and re-applies the saved puck-placement padding once the
     * mode transition finishes. `paddingWhileTracking()` calls made while the camera mode is still
     * transitioning are silently ignored by MapLibre, which is why the puck would visibly snap back
     * to screen center on reroute / nav turns / exit-nav before this fix — the immediate
     * `applyDrivingTrackingPadding()` call right after flipping `cameraMode` landed mid-transition
     * and was dropped. The [OnLocationCameraTransitionListener] callback fires after the transition
     * completes, guaranteeing the puck offset lands.
     */
    private fun engageTrackingGpsWithPuckPadding(map: MapLibreMap, component: LocationComponent) {
        component.setCameraMode(
            CameraMode.TRACKING_GPS,
            object : OnLocationCameraTransitionListener {
                override fun onLocationCameraTransitionFinished(cameraMode: Int) {
                    viewportPadding.applyDrivingTrackingPadding(map)
                }

                override fun onLocationCameraTransitionCanceled(cameraMode: Int) {
                    viewportPadding.applyDrivingTrackingPadding(map)
                }
            },
        )
        viewportPadding.applyDrivingTrackingPadding(map)
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
        component.setMaxAnimationFps(DRIVING_ANIMATION_FPS)
        engageTrackingGpsWithPuckPadding(map, component)
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

        // Same dedup-cache gap as enterNavigationCamera(): force puck-offset padding to
        // re-apply after this CameraMode.NONE round-trip (exit navigation / free-drive snap).
        viewportPadding.invalidateDrivingPaddingCache()

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
            engageTrackingGpsWithPuckPadding(map, component)
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
        // Force the saved puck-offset padding to re-apply once TRACKING_GPS re-engages below —
        // the dedup cache in DrivingViewportPaddingController would otherwise see an unchanged
        // padding key and skip re-pushing paddingWhileTracking after this dive's CameraMode.NONE
        // round-trip, leaving the puck centered (reroute / turn dive / manual recenter mid-nav).
        viewportPadding.invalidateDrivingPaddingCache()
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
                        viewportPadding.invalidateDrivingPaddingCache()
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
                component.setMaxAnimationFps(DRIVING_ANIMATION_FPS)
                engageTrackingGpsWithPuckPadding(map, component)
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
