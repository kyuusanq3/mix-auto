package com.kyuusanq3.mixauto.data.map

import android.location.Location
import android.os.SystemClock
import com.kyuusanq3.mixauto.data.places.EncounteredPlacesRepository
import com.kyuusanq3.mixauto.data.places.LocalPlacesRepository
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng

private const val ENCOUNTER_SAMPLE_MIN_MOVE_M = 200f
private const val ENCOUNTER_SAMPLE_MIN_INTERVAL_MS = 30_000L
private const val ENCOUNTER_CORRIDOR_DELTA = 0.02
private const val ENCOUNTER_ROUTE_LOOKAHEAD_M = 5_000f
private const val ENCOUNTER_FREE_DRIVE_VECTOR_MIN_ZOOM = 15.0

private const val SOURCE_OVERTURE = "overture"
private const val SOURCE_VECTOR = "vector"

/**
 * Passive "drive-by" POI sampler extracted from [MapLibreEngineImpl]. On each GPS fix it decides
 * (throttled by distance/time) whether to pull nearby Overture/vector POIs into the encountered-places
 * database and the in-memory POI cache, so places driven past show up in later searches offline.
 *
 * Depends on the engine only via callbacks/lambdas for map/route/cache state it does not own —
 * everything it *does* own (sample throttle state, the collection job) lives here.
 */
internal class EncounteredPlacesSampler(
    private val engineScope: CoroutineScope,
    private val localPlaces: LocalPlacesRepository?,
    private val encounteredPlaces: EncounteredPlacesRepository?,
    private val maxResults: Int,
    private val isRememberEnabled: () -> Boolean,
    private val isNavigating: () -> Boolean,
    private val useVectorTiles: () -> Boolean,
    private val currentZoom: () -> Double?,
    private val routeGeometryPoints: () -> List<LatLng>,
    private val projectOntoRoute: (Location) -> RouteProjection?,
    private val queryVectorPois: (GeoBounds) -> List<SearchResultPlace>,
    private val onPlacesCollected: (List<SearchResultPlace>) -> Unit,
) {
    private var job: Job? = null
    private var lastSampleMs = 0L
    private var lastSampleLatLng: LatLng? = null

    fun cancel() {
        job?.cancel()
        job = null
    }

    fun maybeSample(location: Location) {
        if (!isRememberEnabled() || encounteredPlaces == null) return
        if (!shouldSample(location)) return

        job?.cancel()
        job = engineScope.launch {
            val navigating = isNavigating()
            val overturePlaces = withContext(Dispatchers.IO) {
                collectOverturePlaces(location, navigating)
            }
            val vectorPlaces = if (!navigating) {
                withContext(Dispatchers.Main) {
                    collectVectorPlaces(location)
                }
            } else {
                emptyList()
            }
            if (overturePlaces.isEmpty() && vectorPlaces.isEmpty()) return@launch
            withContext(Dispatchers.IO) {
                persist(overturePlaces, SOURCE_OVERTURE)
                persist(vectorPlaces, SOURCE_VECTOR)
            }
            withContext(Dispatchers.Main) {
                onPlacesCollected(overturePlaces + vectorPlaces)
            }
        }
    }

    fun persist(places: List<SearchResultPlace>, source: String) {
        if (!isRememberEnabled() || places.isEmpty()) return
        encounteredPlaces?.upsertAll(places, source)
        encounteredPlaces?.pruneToMaxRecords()
    }

    private fun shouldSample(location: Location): Boolean {
        val now = SystemClock.elapsedRealtime()
        val last = lastSampleLatLng
        if (last != null) {
            val distanceResults = FloatArray(1)
            Location.distanceBetween(
                last.latitude,
                last.longitude,
                location.latitude,
                location.longitude,
                distanceResults,
            )
            val movedEnough = distanceResults[0] >= ENCOUNTER_SAMPLE_MIN_MOVE_M
            val intervalPassed = now - lastSampleMs >= ENCOUNTER_SAMPLE_MIN_INTERVAL_MS
            if (!movedEnough && !intervalPassed) return false
        }
        lastSampleMs = now
        lastSampleLatLng = LatLng(location.latitude, location.longitude)
        return true
    }

    private fun collectOverturePlaces(location: Location, navigating: Boolean): List<SearchResultPlace> {
        val repo = localPlaces ?: return emptyList()
        if (!repo.hasInstalledDatabase) return emptyList()
        val bounds = if (navigating) {
            buildRouteEncounterBounds(location) ?: return emptyList()
        } else {
            boundsAroundLatLng(location.latitude, location.longitude, ENCOUNTER_CORRIDOR_DELTA)
        }
        return repo.getPlacesInBounds(
            minLat = bounds.minLat,
            maxLat = bounds.maxLat,
            minLng = bounds.minLng,
            maxLng = bounds.maxLng,
            limit = maxResults,
        ).map { place ->
            place.copy(
                category = normalizeOvertureCategory(place.category),
                poiSource = POI_SOURCE_OVERTURE,
            )
        }
    }

    private fun collectVectorPlaces(location: Location): List<SearchResultPlace> {
        if (!useVectorTiles()) return emptyList()
        val zoom = currentZoom() ?: return emptyList()
        if (zoom < ENCOUNTER_FREE_DRIVE_VECTOR_MIN_ZOOM) return emptyList()
        val bounds = boundsAroundLatLng(location.latitude, location.longitude, ENCOUNTER_CORRIDOR_DELTA)
        return queryVectorPois(bounds)
    }

    private fun buildRouteEncounterBounds(location: Location): GeoBounds? {
        val points = routeGeometryPoints()
        if (points.size < 2) return null
        val projection = projectOntoRoute(location) ?: return null
        val corridorPoints = buildRemainingRoutePoints(
            points,
            projection.segmentIndex,
            projection.splitLat,
            projection.splitLng,
        )
        if (corridorPoints.isEmpty()) return null
        val trimmed = trimPolylineToMaxDistance(corridorPoints, ENCOUNTER_ROUTE_LOOKAHEAD_M)
        if (trimmed.isEmpty()) return null
        return boundsFromPoints(trimmed, ENCOUNTER_CORRIDOR_DELTA)
    }

    private fun boundsAroundLatLng(lat: Double, lng: Double, delta: Double): GeoBounds {
        return GeoBounds(
            minLat = lat - delta,
            maxLat = lat + delta,
            minLng = lng - delta,
            maxLng = lng + delta,
        )
    }

    private fun boundsFromPoints(points: List<LatLng>, padDelta: Double): GeoBounds {
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLng = points.minOf { it.longitude }
        val maxLng = points.maxOf { it.longitude }
        return GeoBounds(
            minLat = minLat - padDelta,
            maxLat = maxLat + padDelta,
            minLng = minLng - padDelta,
            maxLng = maxLng + padDelta,
        )
    }

    private fun trimPolylineToMaxDistance(points: List<LatLng>, maxM: Float): List<LatLng> {
        if (points.isEmpty()) return emptyList()
        val result = mutableListOf(points.first())
        var total = 0f
        for (index in 0 until points.lastIndex) {
            val start = points[index]
            val end = points[index + 1]
            val segmentResults = FloatArray(1)
            Location.distanceBetween(
                start.latitude,
                start.longitude,
                end.latitude,
                end.longitude,
                segmentResults,
            )
            val segmentLength = segmentResults[0]
            if (total + segmentLength > maxM) {
                val remaining = maxM - total
                if (segmentLength > 0f) {
                    val fraction = remaining / segmentLength
                    val lat = start.latitude + (end.latitude - start.latitude) * fraction
                    val lng = start.longitude + (end.longitude - start.longitude) * fraction
                    result.add(LatLng(lat, lng))
                }
                break
            }
            total += segmentLength
            result.add(end)
        }
        return result
    }
}
