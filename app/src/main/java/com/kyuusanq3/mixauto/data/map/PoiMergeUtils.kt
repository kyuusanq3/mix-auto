package com.kyuusanq3.mixauto.data.map

import android.location.Location
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import kotlin.math.roundToInt

const val POI_SOURCE_OVERTURE = "overture"
const val POI_SOURCE_VECTOR = "vector"
const val POI_SOURCE_PHOTON = "photon"
const val POI_SOURCE_SEARCH = "search"

private val SOURCE_PRIORITY = mapOf(
    POI_SOURCE_OVERTURE to 4,
    POI_SOURCE_SEARCH to 3,
    POI_SOURCE_PHOTON to 2,
    POI_SOURCE_VECTOR to 1,
)

internal fun poiSourcePriority(source: String): Int = SOURCE_PRIORITY[source] ?: 0

internal fun roundedPoiKey(lat: Double, lng: Double): String {
    val roundedLat = (lat * 100_000.0).roundToInt() / 100_000.0
    val roundedLng = (lng * 100_000.0).roundToInt() / 100_000.0
    return "$roundedLat,$roundedLng"
}

internal fun poiCacheKey(place: SearchResultPlace): String =
    roundedPoiKey(place.latitude, place.longitude)

internal fun placesWithinMeters(
    aLat: Double,
    aLng: Double,
    bLat: Double,
    bLng: Double,
    maxM: Float,
): Boolean {
    val distanceResults = FloatArray(1)
    Location.distanceBetween(aLat, aLng, bLat, bLng, distanceResults)
    return distanceResults[0] < maxM
}

internal fun preferPoiEntry(existing: SearchResultPlace, incoming: SearchResultPlace): SearchResultPlace {
    val existingPriority = poiSourcePriority(existing.poiSource)
    val incomingPriority = poiSourcePriority(incoming.poiSource)
    val winner = when {
        incomingPriority > existingPriority -> incoming
        existingPriority > incomingPriority -> existing
        incoming.category.isNotBlank() && existing.category.isBlank() -> incoming
        existing.category.isNotBlank() && incoming.category.isBlank() -> existing
        incoming.subTitle.length > existing.subTitle.length -> incoming
        else -> existing
    }
    val loser = if (winner === incoming) existing else incoming
    return winner.copy(
        name = winner.name.ifBlank { loser.name },
        subTitle = winner.subTitle.ifBlank { loser.subTitle },
        category = winner.category.ifBlank { loser.category },
        poiSource = if (poiSourcePriority(winner.poiSource) >= poiSourcePriority(loser.poiSource)) {
            winner.poiSource
        } else {
            loser.poiSource
        },
        latitude = winner.latitude,
        longitude = winner.longitude,
    )
}

internal fun sortPoiPinsForMerge(places: List<SearchResultPlace>): List<SearchResultPlace> =
    places.sortedWith(
        compareBy(
            { if (it.category.isBlank()) 1 else 0 },
            { -poiSourcePriority(it.poiSource) },
        ),
    )
