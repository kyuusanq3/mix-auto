package com.kyuusanq3.mixauto.data.map

import android.graphics.RectF
import android.location.Location
import android.os.Looper
import android.util.Log
import com.kyuusanq3.mixauto.data.places.EncounteredPlacesRepository
import com.kyuusanq3.mixauto.data.places.LocalPlacesRepository
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.maplibre.geojson.Geometry
import org.maplibre.geojson.MultiPoint
import org.maplibre.geojson.Point
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

private const val TAG = "MapLibreEngineImpl"

/**
 * POI cache search, viewport tile queries, and nearby suggestion assembly.
 */
internal class PoiQueryCoordinator(
    private val poiCache: () -> Map<String, SearchResultPlace>,
    private val savedPlacesKeys: () -> Set<String>,
    private val mapLibreMap: () -> MapLibreMap?,
    private val mapView: () -> MapView?,
    private val useVectorTiles: () -> Boolean,
    private val lastKnownLocation: () -> LatLng?,
    private val localPlaces: () -> LocalPlacesRepository?,
    private val encounteredPlaces: () -> EncounteredPlacesRepository?,
    private val rememberEncounteredPlaces: () -> Boolean,
    private val mergeIntoPoiCache: (List<SearchResultPlace>) -> Unit,
    private val trimPoiCacheToMax: (LatLng) -> Unit,
    private val resolveSearchOrigin: (Double, Double) -> Pair<Double, Double>,
    private val isValidSearchOrigin: (Double, Double) -> Boolean,
) {
    fun getNearbyPois(lat: Double, lng: Double, limit: Int): List<SearchResultPlace> {
        if (limit <= 0) return emptyList()

        val (searchLat, searchLng) = if (isValidSearchOrigin(lat, lng)) {
            lat to lng
        } else {
            resolveSearchOrigin(lat, lng)
        }

        val cacheResults = poiCache().values
            .map { place ->
                val distanceResults = FloatArray(1)
                Location.distanceBetween(
                    searchLat,
                    searchLng,
                    place.latitude,
                    place.longitude,
                    distanceResults,
                )
                place.copy(distanceInMeters = distanceResults[0])
            }

        val viewportCacheResults = poiCacheInViewport().map { place ->
            val distanceResults = FloatArray(1)
            Location.distanceBetween(
                searchLat,
                searchLng,
                place.latitude,
                place.longitude,
                distanceResults,
            )
            place.copy(distanceInMeters = distanceResults[0])
        }

        val repo = localPlaces()
        val offlineResults = if (repo != null && repo.hasInstalledDatabase) {
            repo.getPlacesInBounds(
                minLat = searchLat - NEARBY_SEARCH_BBOX_DELTA,
                maxLat = searchLat + NEARBY_SEARCH_BBOX_DELTA,
                minLng = searchLng - NEARBY_SEARCH_BBOX_DELTA,
                maxLng = searchLng + NEARBY_SEARCH_BBOX_DELTA,
                limit = limit * 2,
            ).map { place ->
                val distanceResults = FloatArray(1)
                Location.distanceBetween(
                    searchLat,
                    searchLng,
                    place.latitude,
                    place.longitude,
                    distanceResults,
                )
                place.copy(
                    distanceInMeters = distanceResults[0],
                    category = normalizeOvertureCategory(place.category),
                    poiSource = POI_SOURCE_OVERTURE,
                )
            }
        } else {
            emptyList()
        }

        val encounteredResults = if (rememberEncounteredPlaces()) {
            encounteredPlaces()?.getPlacesNear(
                lat = searchLat,
                lng = searchLng,
                maxRadiusM = ENCOUNTER_NEARBY_RADIUS_M,
                limit = limit,
            ).orEmpty()
        } else {
            emptyList()
        }

        return mergeAndDeduplicate(offlineResults, cacheResults + viewportCacheResults)
            .let { mergeAndDeduplicate(it, encounteredResults) }
            .sortedBy { it.distanceInMeters }
            .take(limit)
    }

    fun searchPoiCache(
        query: String,
        currentLat: Double,
        currentLng: Double,
    ): List<SearchResultPlace> {
        val tokens = tokenizeSearchQuery(query)
        if (tokens.isEmpty()) return emptyList()

        return poiCache().values
            .filter { place -> placeMatchesQueryTokens(place, tokens) }
            .map { place ->
                val distanceResults = FloatArray(1)
                Location.distanceBetween(
                    currentLat,
                    currentLng,
                    place.latitude,
                    place.longitude,
                    distanceResults,
                )
                place.copy(distanceInMeters = distanceResults[0])
            }
            .sortedBy { it.distanceInMeters }
            .take(POI_CACHE_SEARCH_LIMIT)
    }

    fun searchViewportPoiCache(
        query: String,
        currentLat: Double,
        currentLng: Double,
    ): List<SearchResultPlace> {
        val tokens = tokenizeSearchQuery(query)
        if (tokens.isEmpty()) return emptyList()
        val bounds = currentViewportBounds() ?: return emptyList()

        return poiCache().values
            .filter { place ->
                placeInBounds(place, bounds) && placeMatchesQueryTokens(place, tokens)
            }
            .map { place ->
                val distanceResults = FloatArray(1)
                Location.distanceBetween(
                    currentLat,
                    currentLng,
                    place.latitude,
                    place.longitude,
                    distanceResults,
                )
                place.copy(distanceInMeters = distanceResults[0])
            }
            .sortedBy { it.distanceInMeters }
            .take(POI_CACHE_SEARCH_LIMIT)
    }

    fun mergePoiPins(
        local: List<SearchResultPlace>,
        photon: List<SearchResultPlace>,
    ): List<SearchResultPlace> {
        val merged = mutableListOf<SearchResultPlace>()
        val keys = savedPlacesKeys()

        fun findDuplicateIndex(place: SearchResultPlace): Int? {
            for (i in merged.indices) {
                val existing = merged[i]
                val distanceResults = FloatArray(1)
                Location.distanceBetween(
                    existing.latitude,
                    existing.longitude,
                    place.latitude,
                    place.longitude,
                    distanceResults,
                )
                if (distanceResults[0] < DEDUP_THRESHOLD_M) return i
            }
            return null
        }

        for (place in local + photon) {
            if (keys.contains(savedPlaceKey(place))) continue
            val duplicateIndex = findDuplicateIndex(place)
            if (duplicateIndex == null) {
                merged.add(place)
            } else {
                merged[duplicateIndex] = preferPoiEntry(merged[duplicateIndex], place)
            }
        }
        return merged.take(MAX_POI_PINS)
    }

    fun queryTilePois(map: MapLibreMap, queryBounds: GeoBounds): List<SearchResultPlace> {
        if (!useVectorTiles()) return emptyList()
        val view = mapView() ?: return emptyList()
        val reference = lastKnownLocation()
        return runCatching {
            val w = view.width.toFloat()
            val h = view.height.toFloat()
            if (w == 0f || h == 0f) return emptyList()
            val screenBounds = RectF(0f, 0f, w, h)
            map.queryRenderedFeatures(screenBounds, *VECTOR_POI_LAYER_IDS)
                .mapNotNull { feature -> tileFeatureToPlace(feature, reference, queryBounds) }
                .distinctBy { "${it.latitude},${it.longitude}" }
                .take(MAX_POI_PINS)
        }.getOrElse { error ->
            Log.w(TAG, "Tile POI query failed: ${error.message}")
            emptyList()
        }
    }

    fun tileFeatureToPlace(
        feature: org.maplibre.geojson.Feature,
        reference: LatLng?,
        queryBounds: GeoBounds,
    ): SearchResultPlace? {
        val name = resolveTileFeatureName(feature) ?: return null
        val (lat, lng) = extractPointCoordinates(feature.geometry()) ?: return null
        if (lat !in queryBounds.minLat..queryBounds.maxLat ||
            lng !in queryBounds.minLng..queryBounds.maxLng
        ) {
            return null
        }

        val cls = feature.getStringProperty("class").orEmpty()
        val sub = feature.getStringProperty("subclass").orEmpty()
        val distanceInMeters = if (reference != null) {
            val distanceResults = FloatArray(1)
            Location.distanceBetween(
                reference.latitude,
                reference.longitude,
                lat,
                lng,
                distanceResults,
            )
            distanceResults[0]
        } else {
            0f
        }
        return SearchResultPlace(
            name = name,
            subTitle = cls.ifBlank { sub },
            latitude = lat,
            longitude = lng,
            distanceInMeters = distanceInMeters,
            category = maplibreClassToCategory(cls, sub),
            poiSource = POI_SOURCE_VECTOR,
        )
    }

    fun geoBoundsAround(lat: Double, lng: Double, deltaDegrees: Double = 0.001): GeoBounds {
        return GeoBounds(
            minLat = lat - deltaDegrees,
            maxLat = lat + deltaDegrees,
            minLng = lng - deltaDegrees,
            maxLng = lng + deltaDegrees,
        )
    }

    fun expandGeoBounds(
        bounds: LatLngBounds,
        padLat: Double,
        padLng: Double,
    ): GeoBounds {
        return GeoBounds(
            minLat = bounds.southWest.latitude - padLat,
            maxLat = bounds.northEast.latitude + padLat,
            minLng = bounds.southWest.longitude - padLng,
            maxLng = bounds.northEast.longitude + padLng,
        )
    }

    suspend fun seedViewportPoisIntoCache(map: MapLibreMap) {
        val zoom = map.cameraPosition.zoom
        if (zoom < MIN_POI_ZOOM) return
        val bounds = map.projection.visibleRegion.latLngBounds
        val center = map.cameraPosition.target ?: return
        val latSpan = bounds.northEast.latitude - bounds.southWest.latitude
        val lngSpan = bounds.northEast.longitude - bounds.southWest.longitude
        val padLat = latSpan * BBOX_PADDING_FACTOR / 2
        val padLng = lngSpan * BBOX_PADDING_FACTOR / 2
        val queryBounds = expandGeoBounds(bounds, padLat, padLng)

        val tileResults = queryTilePois(map, queryBounds)
        val localResults = withContext(Dispatchers.IO) {
            (localPlaces()?.getPlacesInBounds(
                minLat = queryBounds.minLat,
                maxLat = queryBounds.maxLat,
                minLng = queryBounds.minLng,
                maxLng = queryBounds.maxLng,
                limit = MAX_POI_PINS,
            ) ?: emptyList()).map { place ->
                place.copy(
                    category = normalizeOvertureCategory(place.category),
                    poiSource = POI_SOURCE_OVERTURE,
                )
            }
        }
        val deduped = mergePoiPins(localResults + tileResults, emptyList())
        mergeIntoPoiCache(deduped)
        trimPoiCacheToMax(center)
    }

    fun currentViewportBounds(): GeoBounds? = readOnMainThread {
        val map = mapLibreMap() ?: return@readOnMainThread null
        if (map.cameraPosition.zoom < MIN_POI_ZOOM) return@readOnMainThread null
        val bounds = map.projection.visibleRegion.latLngBounds
        val latSpan = bounds.northEast.latitude - bounds.southWest.latitude
        val lngSpan = bounds.northEast.longitude - bounds.southWest.longitude
        val padLat = latSpan * BBOX_PADDING_FACTOR / 2
        val padLng = lngSpan * BBOX_PADDING_FACTOR / 2
        expandGeoBounds(bounds, padLat, padLng)
    }

    private fun poiCacheInViewport(): List<SearchResultPlace> {
        val bounds = currentViewportBounds() ?: return emptyList()
        return poiCache().values.filter { place -> placeInBounds(place, bounds) }
    }

    private fun <T> readOnMainThread(block: () -> T): T {
        if (Looper.getMainLooper().isCurrentThread) return block()
        return runBlocking(Dispatchers.Main.immediate) { block() }
    }

    private companion object {
        private val VECTOR_POI_LAYER_IDS = arrayOf("poi_r1", "poi_r7", "poi_r20", "poi_transit")
        private const val MIN_POI_ZOOM = 13.0
        private const val MAX_POI_PINS = 100
        private const val POI_CACHE_SEARCH_LIMIT = 15
        private const val NEARBY_SEARCH_BBOX_DELTA = 0.5
        private const val BBOX_PADDING_FACTOR = 1.5
        private const val ENCOUNTER_NEARBY_RADIUS_M = 10_000f
        private const val DEDUP_THRESHOLD_M = 50f
    }
}

internal fun filterPlacesToBounds(
    places: List<SearchResultPlace>,
    bounds: GeoBounds,
): List<SearchResultPlace> {
    return places.filter { place -> placeInBounds(place, bounds) }
}

internal fun placeInBounds(place: SearchResultPlace, bounds: GeoBounds): Boolean {
    return place.latitude in bounds.minLat..bounds.maxLat &&
        place.longitude in bounds.minLng..bounds.maxLng
}

private fun tokenizeSearchQuery(query: String): List<String> {
    val trimmed = query.trim().lowercase()
    if (trimmed.length < 2) return emptyList()
    return trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
}

private fun placeMatchesQueryTokens(place: SearchResultPlace, tokens: List<String>): Boolean {
    val haystack = "${place.name} ${place.subTitle} ${place.category}".lowercase()
    return tokens.all { token -> haystack.contains(token) }
}

private fun resolveTileFeatureName(feature: org.maplibre.geojson.Feature): String? {
    return listOf("name", "name_en", "name:latin", "name:nonlatin")
        .asSequence()
        .mapNotNull { key -> feature.getStringProperty(key)?.takeIf { it.isNotBlank() } }
        .firstOrNull()
}

internal fun extractPointCoordinates(geometry: Geometry?): Pair<Double, Double>? {
    return when (geometry) {
        is Point -> geometry.latitude() to geometry.longitude()
        is MultiPoint -> {
            val first = geometry.coordinates().firstOrNull() ?: return null
            first.latitude() to first.longitude()
        }
        else -> null
    }
}
