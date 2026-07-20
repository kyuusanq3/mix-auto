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
private const val NAV_TILT = 63.0
private const val NAV_CAMERA_DURATION_MS = 2500
private const val FREE_DRIVE_TILT = 50.0
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

    private var pendingPoiPreviewTarget: LatLng? = null
    private var pendingPoiPreviewZoom: Double = POI_PREVIEW_ZOOM
    private var poiPreviewRetryCount = 0
    private var poiPreviewRetryRunnable: Runnable? = null
    private var topDownViewportSyncRunnable: Runnable? = null
    var topDownExploreUserAdjusted: Boolean = false
        private set

    private var lastAppliedTrackingPadding: IntArray? = null
    private var lastEngagedTrackingPadding: IntArray? = null
    private var lastPaddingMapWidth: Int = 0
    private var lastPaddingMapHeight: Int = 0
    private var lookaheadPaddingActive: Boolean = false

    fun resetLookaheadPaddingActive() {
        lookaheadPaddingActive = false
    }

    fun resetTopDownExploreUserAdjusted() {
        topDownExploreUserAdjusted = false
    }

    fun clearNavigationCameraTransitionActive() {
        navigationCameraTransitionActive = false
    }

    fun setNavigationCameraTransitionActive(active: Boolean) {
        navigationCameraTransitionActive = active
    }

    fun hasPendingPoiPreviewTarget(): Boolean = pendingPoiPreviewTarget != null

    fun clearPoiPreviewState() {
        cancelPoiPreviewRetries()
        pendingPoiPreviewTarget = null
        topDownExploreUserAdjusted = false
    }

    fun animateTopDownCamera(
        lat: Double,
        lng: Double,
        zoom: Double,
        exploreMode: Boolean = false,
    ) {
        val map = mapLibreMap() ?: return
        val targetZoom = if (exploreMode) {
            zoom
        } else {
            map.cameraPosition.zoom.coerceAtLeast(zoom)
        }
        stopDeadReckoning()
        resetSmoothingMotion()
        topDownExploreUserAdjusted = false
        val component = map.locationComponent
        if (component.isLocationComponentActivated && component.isLocationComponentEnabled) {
            component.cameraMode = CameraMode.NONE
        }
        updateUiState { it.copy(isCameraDetached = true, isInTopDownView = true) }
        clearViewportPaddingForPreview(map)
        if (useVectorTiles()) {
            map.getStyle { showNativeVectorPoiLayers(it) }
        }
        clearPoiOverlay()
        pendingPoiPreviewTarget = LatLng(lat, lng)
        pendingPoiPreviewZoom = targetZoom
        poiPreviewRetryCount = 0
        mapView()?.requestLayout()
        schedulePoiPreviewCameraRetry(immediate = true)
    }

    fun cancelPoiPreviewRetries() {
        poiPreviewRetryRunnable?.let { runnable ->
            mapView()?.removeCallbacks(runnable)
        }
        poiPreviewRetryRunnable = null
        poiPreviewRetryCount = 0
    }

    fun schedulePoiPreviewCameraRetry(immediate: Boolean = false) {
        val map = mapLibreMap() ?: return
        val view = mapView() ?: return
        poiPreviewRetryRunnable?.let { view.removeCallbacks(it) }
        poiPreviewRetryRunnable = null
        val runnable = Runnable {
            poiPreviewRetryRunnable = null
            if (applyPoiPreviewCamera(map)) return@Runnable
            if (poiPreviewRetryCount < POI_PREVIEW_MAX_RETRIES) {
                poiPreviewRetryCount++
                schedulePoiPreviewCameraRetry(immediate = false)
            } else {
                poiPreviewRetryCount = 0
            }
        }
        poiPreviewRetryRunnable = runnable
        if (immediate) {
            view.post { view.post(runnable) }
        } else {
            view.postDelayed(runnable, POI_PREVIEW_RETRY_DELAY_MS * poiPreviewRetryCount.coerceAtLeast(1))
        }
    }

    private fun applyPoiPreviewCamera(map: MapLibreMap): Boolean {
        val view = mapView() ?: return false
        if (view.width <= 0 || view.height <= 0) return false
        val target = pendingPoiPreviewTarget ?: return true

        val component = map.locationComponent
        if (component.isLocationComponentActivated && component.isLocationComponentEnabled) {
            component.cameraMode = CameraMode.NONE
        }
        clearViewportPaddingForPreview(map)
        if (useVectorTiles()) {
            map.getStyle { style -> showNativeVectorPoiLayers(style) }
        }

        val zoom = pendingPoiPreviewZoom
        map.cancelTransitions()
        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(target)
                    .zoom(zoom)
                    .tilt(0.0)
                    .bearing(0.0)
                    .build(),
            ),
        )
        pendingPoiPreviewTarget = null
        map.triggerRepaint()
        view.invalidate()
        scheduleTopDownViewportSync(map)
        return true
    }

    fun ensureTopDownCameraDetached(map: MapLibreMap) {
        val component = map.locationComponent
        if (component.isLocationComponentActivated && component.isLocationComponentEnabled) {
            component.cameraMode = CameraMode.NONE
        }
    }

    private fun syncTopDownViewportPaddingOnly(map: MapLibreMap) {
        ensureTopDownCameraDetached(map)
        clearViewportPaddingForPreview(map)
        map.triggerRepaint()
        mapView()?.invalidate()
    }

    fun cancelTopDownViewportSync() {
        topDownViewportSyncRunnable?.let { runnable ->
            mapView()?.removeCallbacks(runnable)
        }
        topDownViewportSyncRunnable = null
    }

    fun scheduleTopDownViewportSync(map: MapLibreMap) {
        val view = mapView() ?: return
        cancelTopDownViewportSync()
        val runnable = Runnable {
            topDownViewportSyncRunnable = null
            if (!uiState().isInTopDownView) return@Runnable
            if (topDownExploreUserAdjusted && uiState().selectedPoi == null) return@Runnable
            syncTopDownViewportPaddingOnly(map)
        }
        topDownViewportSyncRunnable = runnable
        view.post(runnable)
    }

    private fun recenterOnSelectedPoi(map: MapLibreMap, place: SearchResultPlace) {
        refreshTopDownCamera(
            map,
            LatLng(place.latitude, place.longitude),
            map.cameraPosition.zoom.coerceAtLeast(POI_PREVIEW_ZOOM),
        )
    }

    private fun refreshTopDownCamera(map: MapLibreMap, target: LatLng, zoom: Double) {
        val view = mapView() ?: return
        val component = map.locationComponent
        if (component.isLocationComponentActivated && component.isLocationComponentEnabled) {
            component.cameraMode = CameraMode.NONE
        }
        clearViewportPaddingForPreview(map)
        map.cancelTransitions()
        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(target)
                    .zoom(zoom)
                    .tilt(0.0)
                    .bearing(0.0)
                    .build(),
            ),
        )
        map.triggerRepaint()
        view.invalidate()
        scheduleTopDownViewportSync(map)
    }

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
                pendingPoiPreviewTarget != null -> schedulePoiPreviewCameraRetry(immediate = true)
                state.selectedPoi != null -> recenterOnSelectedPoi(map, state.selectedPoi)
                topDownExploreUserAdjusted -> Unit
                else -> syncTopDownViewportPaddingOnly(map)
            }
            return
        }
        if (!state.isCameraDetached) {
            val component = map.locationComponent
            val componentReady = component.isLocationComponentActivated &&
                component.isLocationComponentEnabled
            val trackingGps = componentReady && component.cameraMode == CameraMode.TRACKING_GPS
            val padding = computeDrivingViewportPadding(map)
            val paddingKey = intArrayOf(padding.left, padding.top, padding.right, padding.bottom)
            val paddingChanged = drivingPaddingNeedsUpdate(map, paddingKey, trackingGps)
            if (paddingChanged) {
                if (trackingGps) {
                    applyDrivingTrackingPadding(map)
                } else {
                    applyDrivingViewportPadding(map)
                    applyDrivingTrackingPadding(map)
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
        invalidateDrivingPaddingCache()
        val component = map.locationComponent
        val componentReady = component.isLocationComponentActivated &&
            component.isLocationComponentEnabled
        val trackingGps = componentReady && component.cameraMode == CameraMode.TRACKING_GPS
        if (trackingGps) {
            applyDrivingTrackingPadding(map)
            forceLocationUpdateForImmediateRender(
                map,
                bypassRenderThrottle,
                true,
            )
        } else {
            applyDrivingViewportPadding(map)
            applyDrivingTrackingPadding(map)
            forceLocationUpdateForImmediateRender(
                map,
                bypassRenderThrottle,
                true,
            )
        }
        if (uiState().isNavigating && componentReady && component.cameraMode != CameraMode.TRACKING_GPS) {
            activateNavigationTracking(componentReady)
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

    fun clearViewportPaddingForPreview(map: MapLibreMap) {
        invalidateDrivingPaddingCache()
        applyMapPaddingImmediate(map, ViewportPadding(0, 0, 0, 0))
        applyPaddingWhileTrackingIfEngaged(map.locationComponent, ViewportPadding(0, 0, 0, 0))
    }

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
            if (lastEngagedTrackingPadding == null) {
                applyDrivingTrackingPadding(map)
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
        applyDrivingTrackingPadding(map)
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
                "zoom=${freeDriveZoom()} tilt=$FREE_DRIVE_TILT (current=${map.cameraPosition.zoom}, " +
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
                    .tilt(FREE_DRIVE_TILT)
                    .zoom(freeDriveZoom())
                    .bearing(bearing)
                    .build(),
            ),
        )

        if (componentReady) {
            component.cameraMode = CameraMode.TRACKING_GPS
            applyDrivingTrackingPadding(map)
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

        navigationCameraTransitionActive = true

        val bearing = if (componentReady) {
            component.lastKnownLocation?.bearing?.toDouble() ?: map.cameraPosition.bearing
        } else {
            map.cameraPosition.bearing
        }

        Log.i(
            TAG,
            "Entering navigation camera zoom=${navZoom()} tilt=$NAV_TILT " +
                "(current=${map.cameraPosition.zoom}, target=${target.latitude},${target.longitude})",
        )

        applyDrivingViewportPadding(map)
        map.cancelTransitions()
        if (componentReady) {
            component.renderMode = RenderMode.GPS
            component.cameraMode = CameraMode.NONE
        }

        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(target)
                    .zoom(navZoom())
                    .tilt(NAV_TILT)
                    .bearing(bearing)
                    .build(),
            ),
            NAV_CAMERA_DURATION_MS,
            object : MapLibreMap.CancelableCallback {
                override fun onFinish() {
                    activateNavigationTracking(componentReady)
                }

                override fun onCancel() {
                    activateNavigationTracking(componentReady)
                }
            },
        )
        updateUiState { it.copy(isCameraDetached = false, isInTopDownView = false) }
    }

    fun activateNavigationTracking(componentReady: Boolean) {
        navigationCameraTransitionActive = false
        if (!uiState().isNavigating) return
        val map = mapLibreMap() ?: return
        val component = map.locationComponent
        if (componentReady && component.isLocationComponentActivated && component.isLocationComponentEnabled) {
            val target = lastKnownLocation() ?: map.cameraPosition.target
            if (target != null) {
                val bearing = component.lastKnownLocation?.bearing?.toDouble()
                    ?: map.cameraPosition.bearing
                val current = map.cameraPosition
                if (current.tilt < NAV_TILT - 5.0 || abs(current.zoom - navZoom()) > 0.5) {
                    map.moveCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(target)
                                .zoom(navZoom())
                                .tilt(NAV_TILT)
                                .bearing(bearing)
                                .build(),
                        ),
                    )
                }
            }
            component.renderMode = RenderMode.GPS
            component.cameraMode = CameraMode.TRACKING_GPS
            component.setMaxAnimationFps(DRIVING_ANIMATION_FPS)
            applyDrivingTrackingPadding(map)
        }
        onNavigationTrackingEngaged()
    }

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

    private fun drivingPaddingNeedsUpdate(
        map: MapLibreMap,
        paddingKey: IntArray,
        trackingGps: Boolean,
    ): Boolean {
        val w = map.width.toInt()
        val h = map.height.toInt()
        if (w > 0 && h > 0 && (w != lastPaddingMapWidth || h != lastPaddingMapHeight)) {
            return true
        }
        val cached = if (trackingGps) lastEngagedTrackingPadding else lastAppliedTrackingPadding
        return cached?.contentEquals(paddingKey) != true
    }

    private fun markDrivingPaddingMapSize(map: MapLibreMap) {
        val w = map.width.toInt()
        val h = map.height.toInt()
        if (w > 0) lastPaddingMapWidth = w
        if (h > 0) lastPaddingMapHeight = h
    }

    fun updateLookaheadPaddingState(speedMps: Float) {
        lookaheadPaddingActive = nextLookaheadPaddingActive(lookaheadPaddingActive, speedMps)
    }

    fun maybeApplyDrivingPaddingForSpeedChange(map: MapLibreMap) {
        if (isRouteOverviewActive() || navigationCameraTransitionActive) return
        if (uiState().isInTopDownView || uiState().isCameraDetached) return
        val padding = computeDrivingViewportPadding(map)
        val paddingKey = intArrayOf(padding.left, padding.top, padding.right, padding.bottom)
        val component = map.locationComponent
        val trackingGps = component.isLocationComponentActivated &&
            component.isLocationComponentEnabled &&
            component.cameraMode == CameraMode.TRACKING_GPS
        if (!drivingPaddingNeedsUpdate(map, paddingKey, trackingGps)) return
        applyDrivingTrackingPadding(map)
    }

    fun applyDrivingViewportPadding(map: MapLibreMap) {
        if (uiState().isInTopDownView) return
        applyMapPaddingImmediate(map, computeDrivingViewportPadding(map))
    }

    fun invalidateDrivingPaddingCache() {
        lastAppliedTrackingPadding = null
        lastEngagedTrackingPadding = null
        lastPaddingMapWidth = 0
        lastPaddingMapHeight = 0
    }

    fun applyDrivingTrackingPadding(map: MapLibreMap) {
        if (isRouteOverviewActive() || navigationCameraTransitionActive) return
        if (uiState().isInTopDownView || uiState().isCameraDetached) return
        val padding = computeDrivingViewportPadding(map)
        val paddingKey = intArrayOf(padding.left, padding.top, padding.right, padding.bottom)
        val component = map.locationComponent
        val componentReady = component.isLocationComponentActivated && component.isLocationComponentEnabled
        val alreadyTrackingGps = componentReady && component.cameraMode == CameraMode.TRACKING_GPS
        if (!alreadyTrackingGps) {
            if (!drivingPaddingNeedsUpdate(map, paddingKey, trackingGps = false)) return
            lastAppliedTrackingPadding = paddingKey
            markDrivingPaddingMapSize(map)
            applyMapPaddingImmediate(map, padding)
            return
        }
        if (!drivingPaddingNeedsUpdate(map, paddingKey, trackingGps = true)) return
        lastAppliedTrackingPadding = paddingKey
        lastEngagedTrackingPadding = paddingKey
        markDrivingPaddingMapSize(map)
        applyPaddingWhileTrackingIfEngaged(component, padding)
        if (!shouldSmoothPuckMotion()) {
            forceLocationUpdateForImmediateRender(map, false, false)
        }
    }

    fun onCameraGestureStarted(map: MapLibreMap) {
        ensureTopDownCameraDetached(map)
        if (uiState().isInTopDownView) {
            cancelTopDownViewportSync()
            cancelPoiPreviewRetries()
            pendingPoiPreviewTarget = null
            if (uiState().selectedPoi == null) {
                topDownExploreUserAdjusted = true
            }
        }
    }

    fun enterTopDownExploreView() {
        val map = mapLibreMap() ?: return
        val target = lastKnownLocation()
            ?: resolveFreeDriveTarget(map)
            ?: map.cameraPosition.target
            ?: return
        animateTopDownCamera(
            target.latitude,
            target.longitude,
            TOP_DOWN_EXPLORE_ZOOM,
            exploreMode = true,
        )
    }

    private fun isFreeDriveZoomTooWide(map: MapLibreMap): Boolean =
        map.cameraPosition.zoom < freeDriveZoom() - 0.5

    private fun needsFreeDriveCameraSnap(map: MapLibreMap): Boolean =
        !hasSnappedCameraToGps() || isFreeDriveZoomTooWide(map)

    private fun formatCameraTarget(map: MapLibreMap): String {
        val target = map.cameraPosition.target
        return if (target != null) "${target.latitude},${target.longitude}" else "null"
    }
}
