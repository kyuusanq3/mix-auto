package com.kyuusanq3.mixauto.data.map

import android.location.Location
import com.kyuusanq3.mixauto.data.places.EncounteredPlacesRepository
import com.kyuusanq3.mixauto.domain.map.MapUiState
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import java.util.Locale

internal class PoiSelectionController(
    private val uiState: () -> MapUiState,
    private val updateUiState: ((MapUiState) -> MapUiState) -> Unit,
    private val mapLibreMap: () -> MapLibreMap?,
    private val navigationCamera: () -> NavigationCameraController,
    private val poiOverlayCoordinator: () -> PoiOverlayCoordinator,
    private val customPinController: CustomPinController,
    private val encounteredPlaces: () -> EncounteredPlacesRepository?,
    private val getNearbyPois: (Double, Double, Int) -> List<SearchResultPlace>,
    private val getSavedPlacesCache: () -> List<SearchResultPlace>,
    private val getSavedPlacesKeys: () -> Set<String>,
    private val setSavedPlacesCache: (List<SearchResultPlace>) -> Unit,
    private val setSavedPlacesKeys: (Set<String>) -> Unit,
    private val lastKnownLocation: () -> LatLng?,
    private val animateTopDownCamera: (Double, Double, Double) -> Unit,
    private val emptyCustomPinGeoJson: String,
    private val nearbyPinDedupThresholdM: Float,
    private val poiPreviewZoom: Double,
) {
    fun dismissSelectedPoi() {
        navigationCamera().cancelPoiPreviewRetries()
        navigationCamera().resetTopDownExploreUserAdjusted()
        poiOverlayCoordinator().clearForcedPreviewPoi()
        updateUiState {
            it.copy(
                selectedPoi = null,
                nearbyPois = emptyList(),
                isInTopDownView = false,
            )
        }
        clearCustomPin()
        poiOverlayCoordinator().updatePoiLayerFromCache()
        mapLibreMap()?.triggerRepaint()
    }

    fun focusOnLocation(lat: Double, lng: Double) {
        animateTopDownCamera(lat, lng, poiPreviewZoom)
    }

    fun focusOnPoi(place: SearchResultPlace, moveCamera: Boolean) {
        poiOverlayCoordinator().clearForcedPreviewPoi()
        if (moveCamera) {
            updateUiState { it.copy(isCameraDetached = true, isInTopDownView = true) }
        }
        val distanceInMeters = computeDistanceFromReference(place.latitude, place.longitude)
        val nearbyPois = if (place.isDroppedPin) {
            getNearbyPois(place.latitude, place.longitude, 10)
                .filter { nearby ->
                    val distanceResults = FloatArray(1)
                    Location.distanceBetween(
                        place.latitude,
                        place.longitude,
                        nearby.latitude,
                        nearby.longitude,
                        distanceResults,
                    )
                    distanceResults[0] >= nearbyPinDedupThresholdM
                }
                .take(2)
        } else {
            emptyList()
        }
        updateUiState {
            it.copy(
                selectedPoi = place.copy(distanceInMeters = distanceInMeters),
                nearbyPois = nearbyPois,
            )
        }
        if (place.isDroppedPin) {
            if (isSavedPlace(place)) {
                clearCustomPin()
            } else {
                placeCustomPin(place.latitude, place.longitude, pending = true)
            }
        } else {
            clearCustomPin()
        }
        if (moveCamera) {
            poiOverlayCoordinator().clearPoiOverlay()
            if (!place.isDroppedPin && !poiOverlayCoordinator().isPlaceRenderedOnMap(place)) {
                val enriched = place.copy(
                    poiSource = place.poiSource.ifBlank { POI_SOURCE_SEARCH },
                    category = place.category.ifBlank { normalizeOvertureCategory(place.category) },
                )
                encounteredPlaces()?.upsertAll(listOf(enriched), POI_SOURCE_SEARCH)
                encounteredPlaces()?.pruneToMaxRecords()
                poiOverlayCoordinator().mergeIntoPoiCache(listOf(enriched))
                poiOverlayCoordinator().showForcedPreviewPoi(enriched)
            }
            focusOnLocation(place.latitude, place.longitude)
        }
    }

    fun setSavedPlaces(places: List<SearchResultPlace>) {
        setSavedPlacesCache(places)
        setSavedPlacesKeys(places.map { savedPlaceKey(it) }.toSet())
        poiOverlayCoordinator().updatePoiLayerFromCache()
        poiOverlayCoordinator().updateSavedPlacesLayer(places)
        val selected = uiState().selectedPoi
        if (selected != null && selected.isDroppedPin) {
            if (isSavedPlace(selected)) {
                clearCustomPin()
            } else {
                placeCustomPin(selected.latitude, selected.longitude, pending = true)
            }
        }
    }

    fun findSavedPlaceAt(lat: Double, lng: Double): SearchResultPlace? =
        customPinController.findSavedPlaceAt(
            getSavedPlacesCache(),
            lat,
            lng,
            nearbyPinDedupThresholdM,
        )

    fun isSavedPlace(place: SearchResultPlace): Boolean =
        getSavedPlacesKeys().contains(savedPlaceKey(place))

    fun coordinatesNear(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double,
        maxM: Float = nearbyPinDedupThresholdM,
    ): Boolean {
        val distanceResults = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, distanceResults)
        return distanceResults[0] < maxM
    }

    fun placeCustomPin(lat: Double, lng: Double, pending: Boolean) {
        val map = mapLibreMap() ?: return
        val geoJson = buildCustomPinGeoJson(lat, lng, pending)
        map.getStyle { style -> customPinController.placeCustomPin(style, geoJson) }
    }

    fun clearCustomPin() {
        val map = mapLibreMap() ?: return
        map.getStyle { style -> customPinController.clearCustomPin(style, emptyCustomPinGeoJson) }
    }

    fun computeDistanceFromReference(lat: Double, lng: Double): Float {
        val reference = lastKnownLocation() ?: return 0f
        val distanceResults = FloatArray(1)
        Location.distanceBetween(
            reference.latitude,
            reference.longitude,
            lat,
            lng,
            distanceResults,
        )
        return distanceResults[0]
    }

    fun formatLatLng(lat: Double, lng: Double): String {
        val latDir = if (lat >= 0) "N" else "S"
        val lngDir = if (lng >= 0) "E" else "W"
        return String.format(
            Locale.US,
            "%.5f° %s, %.5f° %s",
            kotlin.math.abs(lat),
            latDir,
            kotlin.math.abs(lng),
            lngDir,
        )
    }
}
