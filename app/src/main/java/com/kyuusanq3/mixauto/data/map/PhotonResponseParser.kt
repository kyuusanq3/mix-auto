package com.kyuusanq3.mixauto.data.map

import android.location.Location
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import org.json.JSONObject

private const val DEFAULT_DEDUP_THRESHOLD_M = 50f

/** Parses a Photon geocoder JSON response into ranked [SearchResultPlace] results. */
internal fun parsePhotonResponse(
    json: String,
    currentLat: Double,
    currentLng: Double,
): List<SearchResultPlace> {
    val root = JSONObject(json)
    val features = root.optJSONArray("features") ?: return emptyList()
    return (0 until features.length()).mapNotNull { index ->
        val feature = features.getJSONObject(index)
        val geometry = feature.optJSONObject("geometry") ?: return@mapNotNull null
        val coordinates = geometry.optJSONArray("coordinates") ?: return@mapNotNull null
        if (coordinates.length() < 2) return@mapNotNull null

        val placeLng = coordinates.getDouble(0)
        val placeLat = coordinates.getDouble(1)
        val properties = feature.optJSONObject("properties") ?: return@mapNotNull null

        val name = properties.optString("name").takeIf { it.isNotBlank() }
            ?: properties.optString("street").takeIf { it.isNotBlank() }
            ?: properties.optString("city").takeIf { it.isNotBlank() }
            ?: return@mapNotNull null

        val subTitle = listOfNotNull(
            properties.optString("street").takeIf { it.isNotBlank() && it != name },
            properties.optString("city").takeIf { it.isNotBlank() },
            properties.optString("country").takeIf { it.isNotBlank() },
        ).joinToString(", ")

        val distanceResults = FloatArray(1)
        Location.distanceBetween(currentLat, currentLng, placeLat, placeLng, distanceResults)

        val osmKey = properties.optString("osm_key")
        val osmValue = properties.optString("osm_value")
        val category = photonToCategory(osmKey, osmValue)

        SearchResultPlace(
            name = name,
            subTitle = subTitle,
            latitude = placeLat,
            longitude = placeLng,
            distanceInMeters = distanceResults[0],
            category = category,
            poiSource = POI_SOURCE_PHOTON,
        )
    }.sortedBy { it.distanceInMeters }
}

/**
 * Merges two ranked result lists, dropping later duplicates within [dedupThresholdMeters] of an
 * already-kept place, then re-sorts by distance. Used for destination search result lists —
 * for map pin overlays (which must preserve local categorization) use `mergePoiPins` instead.
 */
internal fun mergeAndDeduplicate(
    local: List<SearchResultPlace>,
    photon: List<SearchResultPlace>,
    dedupThresholdMeters: Float = DEFAULT_DEDUP_THRESHOLD_M,
): List<SearchResultPlace> {
    val merged = mutableListOf<SearchResultPlace>()
    val seen = mutableListOf<Pair<Double, Double>>()

    fun isDuplicate(place: SearchResultPlace): Boolean {
        for ((lat, lng) in seen) {
            if (placesWithinMeters(lat, lng, place.latitude, place.longitude, dedupThresholdMeters)) {
                return true
            }
        }
        return false
    }

    for (place in local + photon) {
        if (!isDuplicate(place)) {
            merged.add(place)
            seen.add(place.latitude to place.longitude)
        }
    }
    return rankSearchResults(merged)
}

/**
 * Search list ranking: street address first, then higher confidence, then nearer.
 */
internal fun rankSearchResults(places: List<SearchResultPlace>): List<SearchResultPlace> =
    places.sortedWith(
        compareByDescending<SearchResultPlace> { it.hasStreetAddress }
            .thenByDescending { it.confidence ?: 0f }
            .thenBy { it.distanceInMeters },
    )
