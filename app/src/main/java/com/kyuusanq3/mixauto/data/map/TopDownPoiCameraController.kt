package com.kyuusanq3.mixauto.data.map

import com.kyuusanq3.mixauto.domain.map.MapUiState
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private const val TOP_DOWN_EXPLORE_ZOOM = 15.0
private const val POI_PREVIEW_MAX_RETRIES = 8
private const val POI_PREVIEW_RETRY_DELAY_MS = 50L

/** POI preview retries, top-down explore, and viewport sync runnables. */
internal class TopDownPoiCameraController(
    private val mapView: () -> MapView?,
    private val mapLibreMap: () -> MapLibreMap?,
    private val uiState: () -> MapUiState,
    private val updateUiState: ((MapUiState) -> MapUiState) -> Unit,
    private val useVectorTiles: () -> Boolean,
    private val lastKnownLocation: () -> LatLng?,
    private val resolveFreeDriveTarget: (MapLibreMap) -> LatLng?,
    private val stopDeadReckoning: () -> Unit,
    private val resetSmoothingMotion: () -> Unit,
    private val showNativeVectorPoiLayers: (Style) -> Unit,
    private val clearPoiOverlay: () -> Unit,
    private val clearViewportPaddingForPreview: (MapLibreMap) -> Unit,
    private val ensureTopDownCameraDetached: (MapLibreMap) -> Unit,
) {
    private var pendingPoiPreviewTarget: LatLng? = null
    private var pendingPoiPreviewZoom: Double = POI_PREVIEW_ZOOM
    private var poiPreviewRetryCount = 0
    private var poiPreviewRetryRunnable: Runnable? = null
    private var topDownViewportSyncRunnable: Runnable? = null
    var topDownExploreUserAdjusted: Boolean = false
        private set

    fun hasPendingPoiPreviewTarget(): Boolean = pendingPoiPreviewTarget != null

    fun resetTopDownExploreUserAdjusted() {
        topDownExploreUserAdjusted = false
    }

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

    fun recenterOnSelectedPoi(map: MapLibreMap, place: SearchResultPlace) {
        refreshTopDownCamera(
            map,
            LatLng(place.latitude, place.longitude),
            map.cameraPosition.zoom.coerceAtLeast(POI_PREVIEW_ZOOM),
        )
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

    fun handleTopDownLayoutChange(map: MapLibreMap) {
        val state = uiState()
        if (!state.isInTopDownView) return
        val selected = state.selectedPoi
        if (selected != null) {
            recenterOnSelectedPoi(map, selected)
        } else if (!topDownExploreUserAdjusted) {
            syncTopDownViewportPaddingOnly(map)
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

    private fun syncTopDownViewportPaddingOnly(map: MapLibreMap) {
        ensureTopDownCameraDetached(map)
        clearViewportPaddingForPreview(map)
        map.triggerRepaint()
        mapView()?.invalidate()
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
}
