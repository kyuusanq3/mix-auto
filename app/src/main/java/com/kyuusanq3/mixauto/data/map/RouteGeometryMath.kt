package com.kyuusanq3.mixauto.data.map

import android.location.Location
import org.maplibre.android.geometry.LatLng

/**
 * Stateless geometry helpers for route-line rendering and GPS-to-route projection.
 * These operate purely on the [LatLng] points passed in — no engine instance state.
 */

internal data class ClosestSegmentResult(val distM: Float, val lat: Double, val lng: Double)

internal data class RouteProjection(
    val segmentIndex: Int,
    val splitLat: Double,
    val splitLng: Double,
    val distanceFromStartM: Float,
    val distToRouteM: Float,
)

internal fun coordsNear(a: LatLng, b: LatLng, thresholdM: Float = 1f): Boolean {
    val results = FloatArray(1)
    Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, results)
    return results[0] < thresholdM
}

/** Route geometry from the start up to the traveled split point, or empty if nothing traveled yet. */
internal fun buildTraveledRoutePoints(
    routeProgressDistanceM: Float,
    points: List<LatLng>,
    segmentIndex: Int,
    splitLat: Double,
    splitLng: Double,
): List<LatLng> {
    if (routeProgressDistanceM <= 0f) return emptyList()
    val split = LatLng(splitLat, splitLng)
    if (segmentIndex == 0 && coordsNear(points[0], split)) return emptyList()
    val result = mutableListOf<LatLng>()
    for (i in 0..segmentIndex.coerceAtMost(points.lastIndex)) {
        result.add(points[i])
    }
    if (!coordsNear(result.last(), split)) {
        result.add(split)
    }
    return if (result.size >= 2) result else emptyList()
}

/** Route geometry from the traveled split point to the end. */
internal fun buildRemainingRoutePoints(
    points: List<LatLng>,
    segmentIndex: Int,
    splitLat: Double,
    splitLng: Double,
): List<LatLng> {
    val split = LatLng(splitLat, splitLng)
    val result = mutableListOf<LatLng>()
    result.add(split)
    for (i in (segmentIndex + 1).coerceAtMost(points.lastIndex) until points.size) {
        result.add(points[i])
    }
    if (result.size >= 2 && coordsNear(result[0], result[1])) {
        result.removeAt(0)
    }
    return if (result.size >= 2) result else emptyList()
}

internal fun distanceAlongRoute(
    points: List<LatLng>,
    segmentIndex: Int,
    splitLat: Double,
    splitLng: Double,
): Float {
    if (points.size < 2) return 0f
    var total = 0f
    val cappedIndex = segmentIndex.coerceIn(0, points.lastIndex - 1)
    for (i in 0 until cappedIndex) {
        val results = FloatArray(1)
        Location.distanceBetween(
            points[i].latitude,
            points[i].longitude,
            points[i + 1].latitude,
            points[i + 1].longitude,
            results,
        )
        total += results[0]
    }
    val results = FloatArray(1)
    Location.distanceBetween(
        points[cappedIndex].latitude,
        points[cappedIndex].longitude,
        splitLat,
        splitLng,
        results,
    )
    return total + results[0]
}

internal fun scanRouteSegments(
    location: Location,
    points: List<LatLng>,
    segmentStart: Int,
    segmentEnd: Int,
): RouteProjection {
    var minDist = Float.MAX_VALUE
    var bestSegment = segmentStart.coerceIn(0, (points.size - 2).coerceAtLeast(0))
    var bestLat = location.latitude
    var bestLng = location.longitude

    val end = segmentEnd.coerceIn(0, points.size - 2)
    val start = segmentStart.coerceIn(0, end)
    for (i in start..end) {
        val result = closestPointOnSegment(location, points[i], points[i + 1])
        if (result.distM < minDist) {
            minDist = result.distM
            bestSegment = i
            bestLat = result.lat
            bestLng = result.lng
        }
    }

    return RouteProjection(
        segmentIndex = bestSegment,
        splitLat = bestLat,
        splitLng = bestLng,
        distanceFromStartM = distanceAlongRoute(points, bestSegment, bestLat, bestLng),
        distToRouteM = minDist,
    )
}

internal fun distanceToSegmentMeters(
    point: Location,
    segStart: LatLng,
    segEnd: LatLng,
): Float {
    val px = point.longitude
    val py = point.latitude
    val ax = segStart.longitude
    val ay = segStart.latitude
    val bx = segEnd.longitude
    val by = segEnd.latitude

    val dx = bx - ax
    val dy = by - ay
    if (dx == 0.0 && dy == 0.0) {
        val results = FloatArray(1)
        Location.distanceBetween(py, px, ay, ax, results)
        return results[0]
    }

    var t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)
    t = t.coerceIn(0.0, 1.0)
    val closestLat = ay + t * dy
    val closestLng = ax + t * dx

    val results = FloatArray(1)
    Location.distanceBetween(py, px, closestLat, closestLng, results)
    return results[0]
}

/**
 * Returns the closest point on the given segment together with its distance from [point].
 * Mirrors the math in [distanceToSegmentMeters] but also returns the projected coordinates.
 */
internal fun closestPointOnSegment(
    point: Location,
    segStart: LatLng,
    segEnd: LatLng,
): ClosestSegmentResult {
    val px = point.longitude
    val py = point.latitude
    val ax = segStart.longitude
    val ay = segStart.latitude
    val bx = segEnd.longitude
    val by = segEnd.latitude
    val dx = bx - ax
    val dy = by - ay
    if (dx == 0.0 && dy == 0.0) {
        val results = FloatArray(1)
        Location.distanceBetween(py, px, ay, ax, results)
        return ClosestSegmentResult(results[0], ay, ax)
    }
    var t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)
    t = t.coerceIn(0.0, 1.0)
    val closestLat = ay + t * dy
    val closestLng = ax + t * dx
    val results = FloatArray(1)
    Location.distanceBetween(py, px, closestLat, closestLng, results)
    return ClosestSegmentResult(results[0], closestLat, closestLng)
}
