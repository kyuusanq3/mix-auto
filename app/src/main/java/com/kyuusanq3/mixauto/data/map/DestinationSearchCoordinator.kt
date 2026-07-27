package com.kyuusanq3.mixauto.data.map

import android.util.Log
import com.kyuusanq3.mixauto.data.places.EncounteredPlacesRepository
import com.kyuusanq3.mixauto.data.places.LocalPlacesRepository
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "MapLibreEngineImpl"

/**
 * Destination search orchestration: local SQLite, encountered places, POI cache, Photon merge.
 * Extracted from [MapLibreEngineImpl] via callback injection.
 */
internal class DestinationSearchCoordinator(
    private val localPlaces: () -> LocalPlacesRepository?,
    private val encounteredPlaces: () -> EncounteredPlacesRepository?,
    private val rememberEncounteredPlaces: () -> Boolean,
    private val isValidSearchOrigin: (Double, Double) -> Boolean,
    private val resolveSearchOrigin: (Double, Double) -> Pair<Double, Double>,
    private val hasReliableSearchOrigin: () -> Boolean,
    private val poiQueryCoordinator: PoiQueryCoordinator,
    private val mergeIntoPoiCache: (List<SearchResultPlace>) -> Unit,
    private val updatePoiLayerFromCache: () -> Unit,
    private val maxSearchRadiusM: Float,
) {
    suspend fun searchDestination(
        query: String,
        currentLat: Double,
        currentLng: Double,
        limitDistance: Boolean,
        onLocalResults: suspend (List<SearchResultPlace>) -> Unit,
    ): List<SearchResultPlace> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val (searchLat, searchLng) = if (isValidSearchOrigin(currentLat, currentLng)) {
            currentLat to currentLng
        } else {
            resolveSearchOrigin(currentLat, currentLng)
        }

        fun applyDistanceLimit(places: List<SearchResultPlace>): List<SearchResultPlace> =
            if (limitDistance) {
                places.filter { it.distanceInMeters <= maxSearchRadiusM }
            } else {
                places
            }

        val hasOfflineData = localPlaces()?.hasInstalledDatabase == true
        val local = if (hasOfflineData) {
            localPlaces()?.searchPlaces(query, searchLat, searchLng).orEmpty().map { place ->
                place.copy(
                    category = normalizeOvertureCategory(place.category),
                    poiSource = POI_SOURCE_OVERTURE,
                )
            }
        } else {
            emptyList()
        }
        val encountered = if (rememberEncounteredPlaces()) {
            encounteredPlaces()?.searchPlaces(query, searchLat, searchLng).orEmpty()
        } else {
            emptyList()
        }
        val cacheResults = withContext(Dispatchers.Main) {
            mergeAndDeduplicate(
                poiQueryCoordinator.searchPoiCache(query, searchLat, searchLng),
                poiQueryCoordinator.searchViewportPoiCache(query, searchLat, searchLng),
            )
        }
        val localAndEncountered = mergeAndDeduplicate(local, encountered)
        val localAndCache = mergeAndDeduplicate(localAndEncountered, cacheResults)
        val localAndCacheFiltered = applyDistanceLimit(localAndCache)
        if (localAndCacheFiltered.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                onLocalResults(localAndCacheFiltered)
            }
        }

        val photon = PhotonSearchClient.fetchPhoton(query, searchLat, searchLng)
        val finalResults = applyDistanceLimit(mergeAndDeduplicate(localAndCache, photon))
        Log.i(
            TAG,
            "searchDestination query=\"$query\" origin=$searchLat,$searchLng " +
                "reliable=${hasReliableSearchOrigin()} local=${local.size} encountered=${encountered.size} " +
                "cache=${cacheResults.size} photon=${photon.size} final=${finalResults.size}",
        )
        if (finalResults.isNotEmpty()) {
            if (rememberEncounteredPlaces()) {
                encounteredPlaces()?.upsertAll(finalResults, POI_SOURCE_SEARCH)
                encounteredPlaces()?.pruneToMaxRecords()
            }
            withContext(Dispatchers.Main) {
                mergeIntoPoiCache(finalResults)
                updatePoiLayerFromCache()
            }
        }
        finalResults
    }
}
