package com.kyuusanq3.mixauto.data.map

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.ceil

/** Parsed turn step from TomTom Routing API — converted to engine LegStep in MapLibreEngineImpl. */
data class TomTomRouteStep(
    val maneuverLat: Double,
    val maneuverLng: Double,
    val instruction: String,
    val distanceLabel: String,
    val streetName: String,
    val distanceMeters: Double = 0.0,
)

data class TomTomRouteResult(
    val geometryPoints: List<Pair<Double, Double>>,
    val travelTimeSeconds: Int,
    val distanceMeters: Double,
    val trafficDelaySeconds: Int,
    val steps: List<TomTomRouteStep>,
    val primaryStreet: String,
)

object TomTomRoutingClient {
    private const val TAG = "TomTomRoutingClient"
    private const val USER_AGENT = "MixAutoCarLauncher/1.0"

    fun fetchRoute(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        apiKey: String,
    ): TomTomRouteResult? {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) return null

        val locations = String.format(
            Locale.US,
            "%.6f,%.6f:%.6f,%.6f",
            originLat,
            originLng,
            destLat,
            destLng,
        )
        val query =
            "traffic=true&routeType=fastest&travelMode=car" +
                "&instructionsType=text&language=en-US"
        val url = URL(
            "https://api.tomtom.com/routing/1/calculateRoute/$locations/json" +
                "?key=$trimmedKey&$query",
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000

        return try {
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "fetchRoute HTTP ${connection.responseCode}")
                return null
            }
            val body = connection.inputStream.bufferedReader().readText()
            parseResponse(body)
        } catch (e: Exception) {
            Log.w(TAG, "fetchRoute failed: ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResponse(body: String): TomTomRouteResult? {
        val root = JSONObject(body)
        if (root.has("detailedError")) {
            Log.w(TAG, "TomTom route error: ${root.optJSONObject("detailedError")?.optString("message")}")
            return null
        }
        val routes = root.optJSONArray("routes") ?: return null
        if (routes.length() == 0) return null
        val route = routes.getJSONObject(0)
        val summary = route.getJSONObject("summary")
        val travelTimeSeconds = summary.optInt("travelTimeInSeconds", 0)
        val distanceMeters = summary.optDouble("lengthInMeters", 0.0)
        val trafficDelaySeconds = summary.optInt("trafficDelayInSeconds", 0)

        val geometryPoints = parseGeometryPoints(route)
        if (geometryPoints.size < 2) return null

        val steps = parseGuidanceSteps(route, geometryPoints)
        val primaryStreet = steps.firstOrNull()?.streetName?.takeIf { it.isNotBlank() }
            ?: "On route"

        return TomTomRouteResult(
            geometryPoints = geometryPoints,
            travelTimeSeconds = travelTimeSeconds,
            distanceMeters = distanceMeters,
            trafficDelaySeconds = trafficDelaySeconds,
            steps = steps,
            primaryStreet = primaryStreet,
        )
    }

    private fun parseGeometryPoints(route: JSONObject): List<Pair<Double, Double>> {
        val legs = route.optJSONArray("legs") ?: return emptyList()
        return buildList {
            for (legIndex in 0 until legs.length()) {
                val leg = legs.getJSONObject(legIndex)
                val points = leg.optJSONArray("points") ?: continue
                for (i in 0 until points.length()) {
                    val point = points.optJSONObject(i) ?: continue
                    add(Pair(point.optDouble("latitude"), point.optDouble("longitude")))
                }
            }
        }
    }

    private fun parseGuidanceSteps(
        route: JSONObject,
        geometryPoints: List<Pair<Double, Double>>,
    ): List<TomTomRouteStep> {
        val guidance = route.optJSONObject("guidance") ?: return fallbackSteps(geometryPoints)
        val instructions = guidance.optJSONArray("instructions") ?: return fallbackSteps(geometryPoints)
        if (instructions.length() == 0) return fallbackSteps(geometryPoints)

        return buildList {
            for (i in 0 until instructions.length()) {
                val inst = instructions.optJSONObject(i) ?: continue
                val point = inst.optJSONObject("point")
                val lat = point?.optDouble("latitude") ?: geometryPoints.first().first
                val lng = point?.optDouble("longitude") ?: geometryPoints.first().second
                val message = inst.optString("message", "").ifBlank { "Continue" }
                val street = inst.optString("street", "").ifBlank {
                    extractStreetFromMessage(message)
                }
                val distanceM = inst.optInt("distanceInMeters", 0)
                add(
                    TomTomRouteStep(
                        maneuverLat = lat,
                        maneuverLng = lng,
                        instruction = message,
                        distanceLabel = formatDistance(distanceM.toDouble()),
                        streetName = street,
                        distanceMeters = distanceM.toDouble(),
                    ),
                )
            }
        }
    }

    private fun fallbackSteps(geometryPoints: List<Pair<Double, Double>>): List<TomTomRouteStep> {
        if (geometryPoints.isEmpty()) return emptyList()
        val start = geometryPoints.first()
        val end = geometryPoints.last()
        return listOf(
            TomTomRouteStep(
                maneuverLat = start.first,
                maneuverLng = start.second,
                instruction = "Depart",
                distanceLabel = formatDistance(totalPathMeters(geometryPoints)),
                streetName = "On route",
                distanceMeters = totalPathMeters(geometryPoints),
            ),
            TomTomRouteStep(
                maneuverLat = end.first,
                maneuverLng = end.second,
                instruction = "Arrive at destination",
                distanceLabel = "0 m",
                streetName = "",
            ),
        )
    }

    private fun extractStreetFromMessage(message: String): String {
        val onto = " onto "
        val idx = message.indexOf(onto, ignoreCase = true)
        if (idx >= 0) return message.substring(idx + onto.length).trim()
        return ""
    }

    private fun totalPathMeters(points: List<Pair<Double, Double>>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.size - 1) {
            total += haversineMeters(
                points[i].first,
                points[i].second,
                points[i + 1].first,
                points[i + 1].second,
            )
        }
        return total
    }

    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadiusM = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
        return earthRadiusM * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }

    fun formatDistance(meters: Double): String = when {
        meters >= 1000 -> "%.1f km".format(meters / 1000.0)
        else -> "${meters.toInt()} m"
    }

    fun etaMinutes(travelTimeSeconds: Int): Int =
        ceil(travelTimeSeconds / 60.0).toInt().coerceAtLeast(1)

    fun formatEtaDeltaMinutes(deltaSeconds: Int): String {
        val deltaMin = ceil(kotlin.math.abs(deltaSeconds) / 60.0).toInt()
        return when {
            deltaSeconds > 0 -> "+$deltaMin min vs fastest"
            deltaSeconds < 0 -> "−$deltaMin min vs fastest"
            else -> "Same time as fastest"
        }
    }
}
