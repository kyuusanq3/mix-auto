package com.kyuusanq3.mixauto.data.map

import android.content.Context
import android.util.Log
import com.kyuusanq3.mixauto.data.places.LocalPlacesRepository
import com.kyuusanq3.mixauto.domain.map.MapUiState
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.geojson.Feature

/**
 * Map tap/long-press POI interactions and camera-idle POI refresh.
 * Extracted from [MapLibreEngineImpl] via callback injection.
 */
internal class MapInteractionController(
    private val uiState: () -> MapUiState,
    private val updateUiState: ((MapUiState) -> MapUiState) -> Unit,
    private val mapLibreMap: () -> MapLibreMap?,
    private val mapView: () -> MapView?,
    private val mapReleased: () -> Boolean,
    private val engineScope: CoroutineScope,
    private val getPoiRefreshJob: () -> Job?,
    private val setPoiRefreshJob: (Job?) -> Unit,
    private val focusOnPoi: (SearchResultPlace, Boolean) -> Unit,
    private val switchToLighterTrafficAlternate: () -> Unit,
    private val placeCustomPin: (Double, Double, Boolean) -> Unit,
    private val animateTopDownCamera: (Double, Double, Double) -> Unit,
    private val clearPoiOverlay: () -> Unit,
    private val mergeIntoPoiCache: (List<SearchResultPlace>) -> Unit,
    private val trimPoiCacheToMax: (LatLng) -> Unit,
    private val refreshPoiOverlay: () -> Unit,
    private val poiQueryCoordinator: PoiQueryCoordinator,
    private val encounteredPlacesSampler: EncounteredPlacesSampler,
    private val shouldQueryPhoton: (LatLng) -> Boolean,
    private val setLastPhotonQueryCenter: (LatLng) -> Unit,
    private val findSavedPlaceAt: (Double, Double) -> SearchResultPlace?,
    private val coordinatesNear: (Double, Double, Double, Double) -> Boolean,
    private val formatLatLng: (Double, Double) -> String,
    private val customPinController: CustomPinController,
    private val localPlaces: () -> LocalPlacesRepository?,
    private val useVectorTiles: () -> Boolean,
    private val lastKnownLocation: () -> LatLng?,
    private val poiCache: () -> MutableMap<String, SearchResultPlace>,
    private val mapTapDismissHandler: () -> (() -> Unit)?,
    private val routeTomtomLayerId: String,
    private val savedPlacesLayerId: String,
    private val customPinLayerId: String,
    private val poiLayerId: String,
    private val vectorPoiLayerIds: Array<String>,
    private val minPoiZoom: Double,
    private val maxPoiPins: Int,
    private val poiDebounceMs: Long,
    private val bboxPaddingFactor: Double,
    private val poiPreviewZoom: Double,
) {

    fun registerPoiInteractions(map: MapLibreMap) {
        map.addOnCameraIdleListener {
            if (uiState().isNavigating ||
                uiState().selectedPoi != null ||
                uiState().isInTopDownView
            ) {
                return@addOnCameraIdleListener
            }

            val component = map.locationComponent
            if (component.isLocationComponentActivated &&
                component.cameraMode == CameraMode.TRACKING_GPS &&
                !uiState().isCameraDetached
            ) {
                return@addOnCameraIdleListener
            }

            val zoom = map.cameraPosition.zoom
            if (zoom < minPoiZoom) {
                clearPoiOverlay()
                return@addOnCameraIdleListener
            }

            getPoiRefreshJob()?.cancel()
            setPoiRefreshJob(
                engineScope.launch {
                    delay(poiDebounceMs)
                    if (!isActive || mapReleased() || mapLibreMap() !== map) return@launch
                    if (uiState().isNavigating ||
                        uiState().selectedPoi != null ||
                        uiState().isInTopDownView
                    ) {
                        return@launch
                    }
                    val bounds = map.projection.visibleRegion.latLngBounds
                    val center = map.cameraPosition.target ?: return@launch

                    val latSpan = bounds.northEast.latitude - bounds.southWest.latitude
                    val lngSpan = bounds.northEast.longitude - bounds.southWest.longitude
                    val padLat = latSpan * bboxPaddingFactor / 2
                    val padLng = lngSpan * bboxPaddingFactor / 2
                    val queryBounds = poiQueryCoordinator.expandGeoBounds(bounds, padLat, padLng)

                    val tileResults = poiQueryCoordinator.queryTilePois(map, queryBounds)
                    val localResults = withContext(Dispatchers.IO) {
                        (localPlaces()?.getPlacesInBounds(
                            minLat = queryBounds.minLat,
                            maxLat = queryBounds.maxLat,
                            minLng = queryBounds.minLng,
                            maxLng = queryBounds.maxLng,
                            limit = maxPoiPins,
                        ) ?: emptyList()).map { place ->
                            place.copy(
                                category = normalizeOvertureCategory(place.category),
                                poiSource = POI_SOURCE_OVERTURE,
                            )
                        }
                    }
                    if (!isActive) return@launch

                    val dedupedFirstPass = poiQueryCoordinator.mergePoiPins(localResults + tileResults, emptyList())
                    mergeIntoPoiCache(dedupedFirstPass)
                    encounteredPlacesSampler.persist(localResults, POI_SOURCE_OVERTURE)
                    encounteredPlacesSampler.persist(tileResults, POI_SOURCE_VECTOR)
                    trimPoiCacheToMax(center)
                    refreshPoiOverlay()

                    val photonResults = withContext(Dispatchers.IO) {
                        if (shouldQueryPhoton(center)) {
                            PhotonSearchClient.fetchPhotonNearby(center, queryBounds) { c ->
                                setLastPhotonQueryCenter(c)
                            }
                        } else {
                            emptyList()
                        }
                    }
                    if (!isActive) return@launch

                    if (photonResults.isNotEmpty()) {
                        mergeIntoPoiCache(poiQueryCoordinator.mergePoiPins(localResults, photonResults))
                        trimPoiCacheToMax(center)
                        refreshPoiOverlay()
                    }
                },
            )
        }

        map.addOnMapClickListener {
            mapTapDismissHandler()?.let { handler ->
                handler()
                return@addOnMapClickListener true
            }
            val loadedMap = mapLibreMap() ?: return@addOnMapClickListener false
            handleMapPointSelection(loadedMap, it)
        }

        map.addOnMapLongClickListener { latLng ->
            val loadedMap = mapLibreMap() ?: return@addOnMapLongClickListener false
            if (uiState().isNavigating) return@addOnMapLongClickListener false
            if (handleMapPointSelection(loadedMap, latLng)) return@addOnMapLongClickListener true
            startCustomPinDraft(latLng.latitude, latLng.longitude)
            true
        }
    }

    fun handleMapPointSelection(map: MapLibreMap, latLng: LatLng): Boolean {
        val screenPoint = map.projection.toScreenLocation(latLng)

        if (uiState().isNavigating && uiState().lighterTrafficAlternateActive) {
            if (map.queryRenderedFeatures(screenPoint, routeTomtomLayerId).isNotEmpty()) {
                switchToLighterTrafficAlternate()
                return true
            }
        }

        run {
            val feature = map.queryRenderedFeatures(screenPoint, savedPlacesLayerId).firstOrNull()
                ?: return@run
            val lat = feature.getNumberProperty("lat")?.toDouble() ?: return@run
            val lng = feature.getNumberProperty("lng")?.toDouble() ?: return@run
            if (!isTapNearPinIcon(map, screenPoint, lat, lng)) return@run
            val place = findSavedPlaceAt(lat, lng) ?: placeFromSymbolFeature(feature) ?: return@run
            focusOnPoi(place, true)
            return true
        }

        run {
            val feature = map.queryRenderedFeatures(screenPoint, customPinLayerId).firstOrNull()
                ?: return@run
            val coords = extractPointCoordinates(feature.geometry()) ?: return@run
            val (pinLat, pinLng) = coords
            if (!isTapNearPinIcon(map, screenPoint, pinLat, pinLng)) return@run
            val current = uiState().selectedPoi
            val place = when {
                current != null &&
                    coordinatesNear(current.latitude, current.longitude, pinLat, pinLng) ->
                    current
                else -> findSavedPlaceAt(pinLat, pinLng)
                    ?: SearchResultPlace(
                        name = current?.name ?: "Dropped Pin",
                        subTitle = formatLatLng(pinLat, pinLng),
                        latitude = pinLat,
                        longitude = pinLng,
                        isDroppedPin = true,
                    )
            }
            focusOnPoi(place, true)
            return true
        }

        map.queryRenderedFeatures(screenPoint, poiLayerId).firstOrNull()?.let { feature ->
            val place = placeFromSymbolFeature(feature) ?: return false
            focusOnPoi(place, true)
            return true
        }

        if (useVectorTiles() && !uiState().isNavigating) {
            val tapBounds = poiQueryCoordinator.geoBoundsAround(latLng.latitude, latLng.longitude)
            val place = map.queryRenderedFeatures(screenPoint, *vectorPoiLayerIds)
                .firstNotNullOfOrNull { feature ->
                    poiQueryCoordinator.tileFeatureToPlace(feature, lastKnownLocation(), tapBounds)
                }
            if (place != null) {
                val enriched = poiCache().values.find { cached ->
                    coordinatesNear(cached.latitude, cached.longitude, place.latitude, place.longitude)
                }?.let { cached ->
                    place.copy(
                        subTitle = cached.subTitle.ifBlank { place.subTitle },
                        category = cached.category.ifBlank { place.category },
                    )
                } ?: place
                focusOnPoi(enriched, true)
                mergeIntoPoiCache(listOf(enriched))
                return true
            }
        }

        return false
    }

    fun exitFreeDriveToTopViewIfNeeded(lat: Double, lng: Double) {
        if (uiState().isNavigating || uiState().isCameraDetached) return
        animateTopDownCamera(lat, lng, poiPreviewZoom)
    }

    fun startCustomPinDraft(lat: Double, lng: Double) {
        exitFreeDriveToTopViewIfNeeded(lat, lng)
        val selectedPlace = SearchResultPlace(
            name = "Dropped Pin",
            subTitle = formatLatLng(lat, lng),
            latitude = lat,
            longitude = lng,
            isDroppedPin = true,
        )
        focusOnPoi(selectedPlace, false)
        placeCustomPin(lat, lng, true)
        engineScope.launch {
            val streetName = reverseGeocode(lat, lng)
            updateUiState { state ->
                val current = state.selectedPoi
                if (current?.isDroppedPin == true &&
                    current.latitude == lat &&
                    current.longitude == lng
                ) {
                    state.copy(selectedPoi = current.copy(name = streetName))
                } else {
                    state
                }
            }
        }
    }

    fun isTapNearPinIcon(
        map: MapLibreMap,
        screenPoint: android.graphics.PointF,
        pinLat: Double,
        pinLng: Double,
    ): Boolean {
        val density = mapView()?.context?.resources?.displayMetrics?.density ?: 2.5f
        return customPinController.isTapNearPinIcon(map, screenPoint, pinLat, pinLng, density)
    }

    private suspend fun reverseGeocode(lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        val url = URL(
            "https://nominatim.openstreetmap.org/reverse" +
                "?lat=$lat&lon=$lng&format=json",
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "MixAutoCarLauncher/1.0")
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        try {
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "Reverse geocode HTTP error: ${connection.responseCode}")
                return@withContext formatLatLng(lat, lng)
            }
            val body = connection.inputStream.bufferedReader().readText()
            val root = JSONObject(body)
            val address = root.optJSONObject("address")
            if (address != null) {
                listOf("road", "suburb", "city_district", "neighbourhood", "town", "city")
                    .forEach { key ->
                        val value = address.optString(key).trim()
                        if (value.isNotBlank()) return@withContext value
                    }
            }
            formatLatLng(lat, lng)
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocode failed: ${e.message}", e)
            formatLatLng(lat, lng)
        } finally {
            connection.disconnect()
        }
    }

    private fun placeFromSymbolFeature(feature: Feature): SearchResultPlace? {
        val name = feature.getStringProperty("name") ?: return null
        val lat = feature.getNumberProperty("lat")?.toDouble() ?: return null
        val lng = feature.getNumberProperty("lng")?.toDouble() ?: return null
        return SearchResultPlace(
            name = name,
            subTitle = feature.getStringProperty("subtitle").orEmpty(),
            latitude = lat,
            longitude = lng,
            category = feature.getStringProperty("category").orEmpty(),
        )
    }

    companion object {
        private const val TAG = "MapInteractionController"
    }
}
