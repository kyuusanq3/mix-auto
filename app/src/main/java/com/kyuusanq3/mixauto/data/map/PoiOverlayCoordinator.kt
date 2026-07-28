package com.kyuusanq3.mixauto.data.map

import android.location.Location
import com.kyuusanq3.mixauto.domain.map.MapUiState
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * In-memory POI cache, mix overlay refresh, preview layers, and native vector POI visibility.
 * Extracted from [MapLibreEngineImpl] via callback injection.
 */
internal class PoiOverlayCoordinator(
    private val mapLibreMap: () -> MapLibreMap?,
    private val mapReleased: () -> Boolean,
    private val uiState: () -> MapUiState,
    private val useVectorTiles: () -> Boolean,
    private val savedPlacesKeys: () -> Set<String>,
    private val poiOverlayRenderer: PoiOverlayRenderer,
    private val poiQueryCoordinator: () -> PoiQueryCoordinator,
    private val resolveMapOverlayAnchorLayerId: (Style) -> String?,
    private val poiLayerId: String,
    private val poiLabelLayerId: String,
    private val savedPlacesLayerId: String,
    private val previewPoiSourceId: String,
    private val previewPoiLayerId: String,
    private val previewPoiLabelLayerId: String,
    private val vectorPoiLayerIds: Array<String>,
    private val emptyPoiGeoJson: String,
    private val maxPoiPins: Int,
    private val bboxPaddingFactor: Double,
    private val photonMoveThresholdM: Float,
    private val dedupThresholdM: Float,
    private val mapTapNearestPoiMaxM: Float,
) {
    private val poiCache = mutableMapOf<String, SearchResultPlace>()
    private var mixPoiOverlayActive = false
    private var lastPhotonQueryCenter: LatLng? = null

    fun poiCacheMap(): MutableMap<String, SearchResultPlace> = poiCache

    fun setLastPhotonQueryCenter(center: LatLng) {
        lastPhotonQueryCenter = center
    }

    fun resetLastPhotonQueryCenter() {
        lastPhotonQueryCenter = null
    }

    fun mergeIntoPoiCache(places: List<SearchResultPlace>) {
        for (place in places) {
            val near = findPoiCacheEntryNear(place)
            if (near == null) {
                poiCache[poiCacheKey(place)] = place
            } else {
                val (existingKey, existing) = near
                val merged = preferPoiEntry(existing, place)
                if (existingKey != poiCacheKey(merged)) {
                    poiCache.remove(existingKey)
                }
                poiCache[poiCacheKey(merged)] = merged
            }
        }
    }

    fun trimPoiCacheToMax(center: LatLng) {
        if (poiCache.size <= maxPoiPins) return
        val keepKeys = poiCache.values
            .sortedBy { place ->
                val distanceResults = FloatArray(1)
                Location.distanceBetween(
                    center.latitude,
                    center.longitude,
                    place.latitude,
                    place.longitude,
                    distanceResults,
                )
                distanceResults[0]
            }
            .take(maxPoiPins)
            .map { poiCacheKey(it) }
            .toSet()
        poiCache.entries.removeIf { it.key !in keepKeys }
    }

    fun clearPoiCache() {
        poiCache.clear()
    }

    fun shouldQueryPhoton(center: LatLng): Boolean {
        val lastCenter = lastPhotonQueryCenter ?: return true
        val distanceResults = FloatArray(1)
        Location.distanceBetween(
            lastCenter.latitude,
            lastCenter.longitude,
            center.latitude,
            center.longitude,
            distanceResults,
        )
        return distanceResults[0] > photonMoveThresholdM
    }

    fun findNearestPoiInCache(lat: Double, lng: Double): SearchResultPlace? {
        var nearest: SearchResultPlace? = null
        var nearestDistance = mapTapNearestPoiMaxM
        for (place in poiCache.values) {
            val distanceResults = FloatArray(1)
            Location.distanceBetween(lat, lng, place.latitude, place.longitude, distanceResults)
            val distance = distanceResults[0]
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearest = place
            }
        }
        return nearest
    }

    fun refreshPoiOverlay() {
        if (mapReleased()) return
        if (uiState().selectedPoi != null || uiState().isInTopDownView) return

        val pins = poiQueryCoordinator().mergePoiPins(
            sortPoiPinsForMerge(poiCache.values.toList()),
            emptyList(),
        )
        if (pins.isNotEmpty()) {
            updatePoiLayer(pins)
        } else {
            clearPoiOverlay()
        }
    }

    fun updatePoiLayerFromCache() {
        if (mapReleased()) return
        if (uiState().selectedPoi != null || uiState().isInTopDownView) return

        val map = mapLibreMap()
        if (useVectorTiles() && map != null) {
            val bounds = map.projection.visibleRegion.latLngBounds
            val latSpan = bounds.northEast.latitude - bounds.southWest.latitude
            val lngSpan = bounds.northEast.longitude - bounds.southWest.longitude
            val padLat = latSpan * bboxPaddingFactor / 2
            val padLng = lngSpan * bboxPaddingFactor / 2
            val queryBounds = poiQueryCoordinator().expandGeoBounds(bounds, padLat, padLng)
            mergeIntoPoiCache(poiQueryCoordinator().queryTilePois(map, queryBounds))
            map.cameraPosition.target?.let { trimPoiCacheToMax(it) }
        }
        refreshPoiOverlay()
    }

    fun clearPoiLayer() {
        clearPoiCache()
        clearPoiOverlay()
    }

    fun clearPoiOverlay() {
        if (mapLibreMap() == null || mapReleased()) return
        mixPoiOverlayActive = false
        withMapStyle { style ->
            poiOverlayRenderer.clearMixPoiSource(style, emptyPoiGeoJson)
            syncPoiOverlayVisibility(style)
        }
    }

    fun showNativeVectorPoiLayers(style: Style) {
        vectorPoiLayerIds.forEach { id ->
            style.getLayer(id)?.setProperties(PropertyFactory.visibility(Property.VISIBLE))
        }
    }

    fun hideNativeVectorPoiLayers(style: Style) {
        vectorPoiLayerIds.forEach { id ->
            style.getLayer(id)?.setProperties(PropertyFactory.visibility(Property.NONE))
        }
    }

    fun syncPoiOverlayVisibility(style: Style) {
        poiOverlayRenderer.syncPoiLabelVisibility(
            style,
            shouldShowMixPoiLabels() && mixPoiOverlayActive,
        )
        syncNativePoiLayerVisibility(style)
    }

    fun isPlaceRenderedOnMap(place: SearchResultPlace): Boolean {
        if (poiCache.values.any { cached ->
                placesWithinMeters(
                    cached.latitude,
                    cached.longitude,
                    place.latitude,
                    place.longitude,
                    dedupThresholdM,
                )
            }
        ) {
            return true
        }
        val map = mapLibreMap() ?: return false
        val screenPoint = map.projection.toScreenLocation(LatLng(place.latitude, place.longitude))
        if (map.queryRenderedFeatures(screenPoint, poiLayerId).isNotEmpty()) return true
        if (useVectorTiles() &&
            map.queryRenderedFeatures(screenPoint, *vectorPoiLayerIds).isNotEmpty()
        ) {
            return true
        }
        return false
    }

    fun showForcedPreviewPoi(place: SearchResultPlace) {
        val map = mapLibreMap() ?: return
        val geoJson = buildPoiGeoJson(listOf(place), savedPlacesKeys())
        map.getStyle { style ->
            ensurePreviewPoiLayers(style, geoJson)
            style.getLayer(previewPoiLayerId)?.setProperties(PropertyFactory.visibility(Property.VISIBLE))
            style.getLayer(previewPoiLabelLayerId)?.setProperties(PropertyFactory.visibility(Property.VISIBLE))
            syncNativePoiLayerVisibility(style)
        }
    }

    fun clearForcedPreviewPoi() {
        val map = mapLibreMap() ?: return
        map.getStyle { style ->
            (style.getSource(previewPoiSourceId) as? GeoJsonSource)?.setGeoJson(emptyPoiGeoJson)
            style.getLayer(previewPoiLayerId)?.setProperties(PropertyFactory.visibility(Property.NONE))
            style.getLayer(previewPoiLabelLayerId)?.setProperties(PropertyFactory.visibility(Property.NONE))
            syncNativePoiLayerVisibility(style)
        }
    }

    fun updateSavedPlacesLayer(places: List<SearchResultPlace>) {
        if (mapReleased() || mapLibreMap() == null) return
        val geoJson = if (places.isEmpty()) {
            emptyPoiGeoJson
        } else {
            buildPoiGeoJson(places, savedPlacesKeys(), forceStarred = true)
        }
        withMapStyle { style ->
            poiOverlayRenderer.updateSavedPlacesLayer(style, geoJson, places.isNotEmpty())
        }
    }

    private fun updatePoiLayer(places: List<SearchResultPlace>) {
        if (mapLibreMap() == null || mapReleased()) return
        val geoJson = buildPoiGeoJson(places, savedPlacesKeys())
        mixPoiOverlayActive = places.isNotEmpty()
        withMapStyle { style ->
            poiOverlayRenderer.ensureMixPoiOverlayLayers(style, geoJson)
            syncPoiOverlayVisibility(style)
        }
    }

    private fun findPoiCacheEntryNear(place: SearchResultPlace): Pair<String, SearchResultPlace>? {
        for ((key, cached) in poiCache) {
            if (placesWithinMeters(
                    cached.latitude,
                    cached.longitude,
                    place.latitude,
                    place.longitude,
                    dedupThresholdM,
                )
            ) {
                return key to cached
            }
        }
        return null
    }

    /**
     * Mix POI name labels are shown only when the camera is manually detached in free drive.
     * Nav-mode label stretch/streak artifacts are **not** fixed here — while [isNavigating],
     * use style symbol `text-pitch-alignment` in [MapStyleController] / mix-auto-driving.json.
     */
    private fun shouldShowMixPoiLabels(): Boolean {
        val state = uiState()
        return state.isCameraDetached &&
            !state.isInTopDownView &&
            !state.isNavigating &&
            state.selectedPoi == null
    }

    private fun syncNativePoiLayerVisibility(style: Style) {
        val state = uiState()
        poiOverlayRenderer.syncNativePoiLayerVisibility(
            style = style,
            useVectorTiles = useVectorTiles(),
            isNavigating = state.isNavigating,
            zoom = mapLibreMap()?.cameraPosition?.zoom ?: 0.0,
            mixPoiOverlayActive = mixPoiOverlayActive,
            isInTopDownView = state.isInTopDownView,
            hasSelectedPoi = state.selectedPoi != null,
        )
    }

    private fun ensurePreviewPoiLayers(style: Style, geoJson: String) {
        val existing = style.getSource(previewPoiSourceId)
        if (existing is GeoJsonSource) {
            existing.setGeoJson(geoJson)
            return
        }
        style.addSource(GeoJsonSource(previewPoiSourceId, geoJson))
        val iconLayer = SymbolLayer(previewPoiLayerId, previewPoiSourceId).withProperties(
            *poiIconOnlyLayerProperties(mixPoiIconExpression()),
        )
        val labelLayer = SymbolLayer(previewPoiLabelLayerId, previewPoiSourceId).withProperties(
            *poiTextOnlyLayerProperties(),
        )
        when {
            style.getLayer(savedPlacesLayerId) != null -> {
                style.addLayerBelow(iconLayer, savedPlacesLayerId)
            }
            style.getLayer(poiLabelLayerId) != null -> {
                style.addLayerAbove(iconLayer, poiLabelLayerId)
            }
            style.getLayer(poiLayerId) != null -> {
                style.addLayerAbove(iconLayer, poiLayerId)
            }
            else -> {
                val anchor = resolveMapOverlayAnchorLayerId(style)
                if (anchor != null) {
                    style.addLayerAbove(iconLayer, anchor)
                } else {
                    style.addLayer(iconLayer)
                }
            }
        }
        style.addLayerAbove(labelLayer, previewPoiLayerId)
    }

    private inline fun withMapStyle(crossinline block: (Style) -> Unit) {
        val map = mapLibreMap() ?: return
        if (mapReleased()) return
        map.getStyle { style ->
            if (mapReleased() || !style.isFullyLoaded) return@getStyle
            block(style)
        }
    }
}
