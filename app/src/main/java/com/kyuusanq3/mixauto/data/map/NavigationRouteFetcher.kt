package com.kyuusanq3.mixauto.data.map

import android.location.Location
import android.util.Log
import com.kyuusanq3.mixauto.data.navigation.NavTtsPhrases
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng

private const val TAG = "NavigationRouteFetcher"
private const val ROUTE_SIMPLIFY_MAX_POINTS = 500
private const val ROUTE_SIMPLIFY_TOLERANCE_M = 8f

/**
 * OSRM route fetch + JSON parsing, extracted from [MapLibreEngineImpl]. Stateless — every
 * function depends only on its parameters, network I/O, and the pure route-geometry helpers in
 * `RouteGeometryMath.kt`. Route selection/picker orchestration (camera dive, overview timers,
 * TomTom wiring) stays on the engine since it is tightly coupled to camera/UI-state sequencing.
 */
internal object NavigationRouteFetcher {

    fun fetchOsrmRoutesWithAlternatives(
        lngA: Double,
        latA: Double,
        lngB: Double,
        latB: Double,
        originRadiusM: Double? = null,
        originBearingDeg: Float? = null,
    ): List<RouteResult> {
        val url = URL(buildOsrmRouteUrl(lngA, latA, lngB, latB, alternatives = 3, originRadiusM, originBearingDeg))
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "MixAutoCarLauncher/1.0")
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        return try {
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "OSRM HTTP error: ${connection.responseCode}")
                return emptyList()
            }
            val body = connection.inputStream.bufferedReader().readText()
            parseOsrmRoutesResponse(body)
        } finally {
            connection.disconnect()
        }
    }

    fun fetchOsrmRoute(
        lngA: Double,
        latA: Double,
        lngB: Double,
        latB: Double,
        originRadiusM: Double? = null,
        originBearingDeg: Float? = null,
    ): RouteResult? {
        val url = URL(buildOsrmRouteUrl(lngA, latA, lngB, latB, alternatives = null, originRadiusM, originBearingDeg))
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "MixAutoCarLauncher/1.0")
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        return try {
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "OSRM HTTP error: ${connection.responseCode}")
                return null
            }
            val body = connection.inputStream.bufferedReader().readText()
            parseOsrmResponse(body)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Builds an OSRM driving URL. When [originRadiusM] is set (reroutes), OSRM must snap the
     * start within that radius so a parallel-street detour is not pulled back onto the old road.
     */
    internal fun buildOsrmRouteUrl(
        lngA: Double,
        latA: Double,
        lngB: Double,
        latB: Double,
        alternatives: Int?,
        originRadiusM: Double?,
        originBearingDeg: Float?,
    ): String {
        val altQuery = if (alternatives != null) "alternatives=$alternatives&" else ""
        val base =
            "https://routing.openstreetmap.de/routed-car/route/v1/driving/" +
                "$lngA,$latA;$lngB,$latB?${altQuery}geometries=geojson&steps=true&overview=full"
        if (originRadiusM == null) return base
        val radius = originRadiusM.toInt().coerceAtLeast(1)
        val withRadius = "$base&radiuses=$radius;unlimited"
        if (originBearingDeg == null) return withRadius
        val bearing = ((originBearingDeg % 360f) + 360f) % 360f
        return "$withRadius&bearings=${bearing.toInt()},$REROUTE_ORIGIN_BEARING_RANGE_DEG;"
    }

    private fun parseOsrmRoutesResponse(json: String): List<RouteResult> {
        val root = JSONObject(json)
        if (root.optString("code") != "Ok") return emptyList()
        val routes = root.getJSONArray("routes")
        return buildList {
            for (i in 0 until routes.length()) {
                parseOsrmRouteObject(routes.getJSONObject(i))?.let { add(it) }
            }
        }
    }

    private fun parseOsrmResponse(json: String): RouteResult? {
        val root = JSONObject(json)
        if (root.optString("code") != "Ok") return null
        val routes = root.getJSONArray("routes")
        if (routes.length() == 0) return null
        return parseOsrmRouteObject(routes.getJSONObject(0))
    }

    private fun parseOsrmRouteObject(route: JSONObject): RouteResult? {
        val geometry = route.getJSONObject("geometry")
        val geometryJson = geometry.toString()
        val geometryPoints = parseRouteGeometryPoints(geometry)

        val legs = route.getJSONArray("legs")
        if (legs.length() == 0) return null
        val steps = legs.getJSONObject(0).getJSONArray("steps")
        if (steps.length() == 0) return null
        val allSteps = buildList {
            for (i in 0 until steps.length()) {
                val step = steps.getJSONObject(i)
                val maneuver = step.getJSONObject("maneuver")
                val location = maneuver.getJSONArray("location")
                val type = maneuver.optString("type", "")
                val modifier = maneuver.optString("modifier", "")
                val name = step.optString("name", "")
                val stepDistanceM = step.optDouble("distance", 0.0)
                add(
                    LegStep(
                        maneuverLat = location.getDouble(1),
                        maneuverLng = location.getDouble(0),
                        instruction = buildInstruction(type, modifier, name),
                        distanceLabel = formatDistance(stepDistanceM),
                        streetName = name,
                        distanceMeters = stepDistanceM,
                        maneuverType = type,
                        maneuverModifier = modifier,
                    ),
                )
            }
        }

        val firstStep = allSteps.first()

        return RouteResult(
            geometryJson = geometryJson,
            geometryPoints = geometryPoints,
            streetName = firstStep.streetName.ifBlank { "On route" },
            instruction = firstStep.instruction,
            distance = firstStep.distanceLabel,
            steps = allSteps,
            durationSeconds = route.optDouble("duration", 0.0),
            distanceMeters = route.optDouble("distance", 0.0),
        )
    }

    private fun parseRouteGeometryPoints(geometry: JSONObject): List<LatLng> {
        val coordinates = geometry.optJSONArray("coordinates") ?: return emptyList()
        val raw = buildList {
            for (i in 0 until coordinates.length()) {
                val point = coordinates.optJSONArray(i) ?: continue
                if (point.length() < 2) continue
                add(LatLng(point.getDouble(1), point.getDouble(0)))
            }
        }
        return simplifyRoutePoints(raw)
    }

    private fun simplifyRoutePoints(points: List<LatLng>): List<LatLng> {
        if (points.size <= ROUTE_SIMPLIFY_MAX_POINTS) return points
        val simplified = douglasPeucker(points, ROUTE_SIMPLIFY_TOLERANCE_M)
        if (simplified.size <= ROUTE_SIMPLIFY_MAX_POINTS) return simplified
        val step = simplified.size.toFloat() / ROUTE_SIMPLIFY_MAX_POINTS
        return buildList {
            var index = 0f
            while (size < ROUTE_SIMPLIFY_MAX_POINTS && index < simplified.size) {
                add(simplified[index.toInt().coerceIn(0, simplified.lastIndex)])
                index += step
            }
            if (isEmpty() || last() != simplified.last()) {
                add(simplified.last())
            }
        }
    }

    private fun douglasPeucker(points: List<LatLng>, toleranceM: Float): List<LatLng> {
        if (points.size < 3) return points
        var maxDist = 0f
        var index = 0
        val start = points.first()
        val end = points.last()
        for (i in 1 until points.lastIndex) {
            val dist = perpendicularDistanceM(points[i], start, end)
            if (dist > maxDist) {
                maxDist = dist
                index = i
            }
        }
        if (maxDist > toleranceM) {
            val left = douglasPeucker(points.subList(0, index + 1), toleranceM)
            val right = douglasPeucker(points.subList(index, points.size), toleranceM)
            return left.dropLast(1) + right
        }
        return listOf(start, end)
    }

    private fun perpendicularDistanceM(point: LatLng, lineStart: LatLng, lineEnd: LatLng): Float {
        val loc = Location("pt").apply {
            latitude = point.latitude
            longitude = point.longitude
        }
        val result = closestPointOnSegment(loc, lineStart, lineEnd)
        return result.distM
    }

    private fun buildInstruction(type: String, modifier: String, name: String): String {
        return NavTtsPhrases.buildFullInstruction(type, modifier, name)
    }

    fun formatDistance(meters: Double): String = when {
        meters >= 1000 -> "%.1f km".format(meters / 1000.0)
        else -> "${meters.toInt()} m"
    }
}
