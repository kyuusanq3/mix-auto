package com.kyuusanq3.mixauto.data.map

import com.kyuusanq3.mixauto.data.navigation.NavigationVoiceController
import com.kyuusanq3.mixauto.domain.map.MapUiState
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlinx.coroutines.Job

internal class FreeDriveSessionCoordinator(
    private val mapReleased: () -> Boolean,
    private val mapLibreMap: () -> MapLibreMap?,
    private val mapView: () -> MapView?,
    private val updateUiState: ((MapUiState) -> MapUiState) -> Unit,
    private val navigationCamera: () -> NavigationCameraController,
    private val locationTracking: () -> LocationTrackingController,
    private val poiOverlayCoordinator: () -> PoiOverlayCoordinator,
    private val routeOverviewController: () -> RouteOverviewController,
    private val navigationSessionCoordinator: () -> NavigationSessionCoordinator,
    private val routeRenderer: () -> RouteRenderer,
    private val encounteredPlacesSampler: EncounteredPlacesSampler,
    private val navigationVoice: () -> NavigationVoiceController?,
    private val lastKnownLocation: () -> LatLng?,
    private val setHasSnappedCameraToGps: (Boolean) -> Unit,
    private val getPoiRefreshJob: () -> Job?,
    private val setPoiRefreshJob: (Job?) -> Unit,
    private val resetNavSessionFields: () -> Unit,
    private val clearCustomPin: () -> Unit,
    private val clearRoutePreviewState: () -> Unit,
    private val stopDeadReckoning: () -> Unit,
    private val updateLocationEngineInterval: () -> Unit,
    private val withMapStyle: ((Style) -> Unit) -> Unit,
    private val enterNavigationCamera: () -> Unit,
    private val isNavigating: () -> Boolean,
) {
    fun recenterCamera() {
        val map = mapLibreMap() ?: return
        if (isNavigating()) {
            enterNavigationCamera()
        } else {
            navigationCamera().prepareForFreeDriveCamera(map)
            clearRoutePreviewState()
            updateUiState { it.copy(isCameraDetached = false, isInTopDownView = false) }
            setHasSnappedCameraToGps(false)
            val target = lastKnownLocation() ?: navigationCamera().resolveFreeDriveTarget(map)
            if (target != null) {
                navigationCamera().snapCameraToGpsIfNeeded(target)
            } else {
                navigationCamera().activateFreeDriveTrackingMode(map)
            }
            navigationCamera().scheduleFreeDrivePaddingRestore(map)
            withMapStyle { poiOverlayCoordinator().syncPoiOverlayVisibility(it) }
        }
    }

    fun startFreeDrive() {
        navigationVoice()?.onNavigationEnded()
        stopDeadReckoning()
        routeOverviewController().clearRouteOverviewState()
        getPoiRefreshJob()?.cancel()
        setPoiRefreshJob(null)
        encounteredPlacesSampler.cancel()
        clearRoutePreviewState()
        poiOverlayCoordinator().clearForcedPreviewPoi()
        resetNavSessionFields()
        navigationSessionCoordinator().clearNavTrafficPrefetchState()

        val map = mapLibreMap()
        map?.let { navigationCamera().prepareForFreeDriveCamera(it) }
        setHasSnappedCameraToGps(false)

        updateUiState {
            MapUiState(
                isNavigating = false,
                streetName = "Free Drive",
                routeOverviewProgress = 0f,
            )
        }

        if (map != null) {
            applyFreeDriveToMap(map)
            poiOverlayCoordinator().clearPoiLayer()
            clearCustomPin()
            withMapStyle { poiOverlayCoordinator().showNativeVectorPoiLayers(it) }
        } else if (!mapReleased()) {
            mapView()?.getMapAsync { loadedMap ->
                if (mapReleased()) return@getMapAsync
                navigationCamera().prepareForFreeDriveCamera(loadedMap)
                applyFreeDriveToMap(loadedMap)
                poiOverlayCoordinator().clearPoiLayer()
                clearCustomPin()
                withMapStyle { poiOverlayCoordinator().showNativeVectorPoiLayers(it) }
            }
        }
        updateLocationEngineInterval()
        locationTracking().resetSmoothingMotion()
        navigationCamera().resetLookaheadPaddingActive()
        navigationCamera().resetDynamicNavigationZoom()
    }

    private fun applyFreeDriveToMap(map: MapLibreMap) {
        if (mapReleased()) return
        navigationCamera().applyDrivingViewportPadding(map)
        withMapStyle { style ->
            routeRenderer().removeRouteLayers(style)

            val target = navigationCamera().resolveFreeDriveTarget(map)
            if (target != null) {
                navigationCamera().snapCameraToGpsIfNeeded(target)
            } else {
                navigationCamera().activateFreeDriveTrackingMode(map)
            }
            navigationCamera().scheduleFreeDrivePaddingRestore(map) // applyFreeDriveToMap restore
        }
    }
}
