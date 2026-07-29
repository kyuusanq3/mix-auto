package com.kyuusanq3.mixauto.data.map

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

internal object TomTomRouteJamFinder {
    private const val TAG = "TomTomRouteJamFinder"

    /**
     * Finds the worst moderate-or-heavier jam/accident within [corridorM] of [routePoints]
     * in the first [maxLookAheadM] along the route. Route points are (latitude, longitude).
     */
    fun findJamOnRoute(
        routePoints: List<Pair<Double, Double>>,
        apiKey: String,
        maxLookAheadM: Double = 10_000.0,
        corridorM: Double = 120.0,
    ): RouteTrafficJam? {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank() || routePoints.size < 2) return null

        val lookAheadM = minOf(maxLookAheadM, totalRouteLengthM(routePoints))
        val bbox = routeBoundingBox(routePoints) ?: return null
        val body = fetchIncidentsForBbox(bbox, trimmedKey) ?: return null
        val incidents = JSONObject(body).optJSONArray("incidents") ?: return null

        var best: RouteTrafficJam? = null
        for (index in 0 until incidents.length()) {
            val candidate = parseRouteIncidentFeature(incidents.optJSONObject(index)) ?: continue
            if (levelRank(candidate.level) < levelRank(TrafficFlowLevel.MODERATE)) continue

            val onRoute = closestPointOnRoute(
                candidate.lat,
                candidate.lng,
                routePoints,
                lookAheadM,
            ) ?: continue
            if (onRoute.distanceToRouteM > corridorM || onRoute.distanceAlongRouteM > lookAheadM) continue

            val jam = RouteTrafficJam(
                level = candidate.level,
                streetName = candidate.streetName,
                distanceAlongRouteM = onRoute.distanceAlongRouteM,
            )
            best = selectBetterRouteJam(best, jam)
        }
        if (best != null) {
            Log.d(TAG, "findJamOnRoute: ${best.level} on ${best.streetName} at ${best.distanceAlongRouteM.toInt()} m")
        }
        return best
    }

    private data class RouteIncidentCandidate(
        val lat: Double,
        val lng: Double,
        val level: TrafficFlowLevel,
        val streetName: String,
    )

    private data class ClosestOnRoute(
        val distanceToRouteM: Double,
        val distanceAlongRouteM: Double,
    )

    private data class RouteBbox(
        val minLon: Double,
        val minLat: Double,
        val maxLon: Double,
        val maxLat: Double,
    )

    private fun selectBetterRouteJam(current: RouteTrafficJam?, candidate: RouteTrafficJam): RouteTrafficJam {
        if (current == null) return candidate
        val currentRank = levelRank(current.level)
        val candidateRank = levelRank(candidate.level)
        return when {
            candidateRank > currentRank -> candidate
            candidateRank < currentRank -> current
            candidate.distanceAlongRouteM < current.distanceAlongRouteM -> candidate
            else -> current
        }
    }

    private fun routeBoundingBox(routePoints: List<Pair<Double, Double>>): RouteBbox? {
        if (routePoints.isEmpty()) return null
        var minLat = routePoints[0].first
        var maxLat = minLat
        var minLon = routePoints[0].second
        var maxLon = minLon
        var accumulatedM = 0.0
        for (i in 1 until routePoints.size) {
            val prev = routePoints[i - 1]
            val point = routePoints[i]
            minLat = minOf(minLat, point.first)
            maxLat = maxOf(maxLat, point.first)
            minLon = minOf(minLon, point.second)
            maxLon = maxOf(maxLon, point.second)
            accumulatedM += haversineMeters(prev.first, prev.second, point.first, point.second)
            if (accumulatedM > 12_000.0) break
        }
        val pad = 0.015
        return RouteBbox(
            minLon = minLon - pad,
            minLat = minLat - pad,
            maxLon = maxLon + pad,
            maxLat = maxLat + pad,
        )
    }

    private fun totalRouteLengthM(routePoints: List<Pair<Double, Double>>): Double {
        var total = 0.0
        for (i in 1 until routePoints.size) {
            val prev = routePoints[i - 1]
            val point = routePoints[i]
            total += haversineMeters(prev.first, prev.second, point.first, point.second)
        }
        return total
    }

    private fun fetchIncidentsForBbox(bbox: RouteBbox, apiKey: String): String? {
        val bboxParam = listOf(bbox.minLon, bbox.minLat, bbox.maxLon, bbox.maxLat)
            .joinToString(",") { String.format(Locale.US, "%.5f", it) }
        val url = URL(
            "https://api.tomtom.com/maps/orbis/traffic/incidents/details" +
                "?apiVersion=2&bbox=$bboxParam&timeValidity=present" +
                "&iconCategories=jam,accident",
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", TOMTOM_USER_AGENT)
        connection.setRequestProperty("TomTom-Api-Key", apiKey)
        connection.setRequestProperty("TomTom-Api-Version", "2")
        connection.setRequestProperty(
            "Attributes",
            "incidents(type,geometry(type,coordinates),properties(" +
                "iconCategory,magnitudeOfDelay,from,to,delayInSeconds))",
        )
        connection.setRequestProperty("Accept", "application/json")
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000

        return try {
            when (connection.responseCode) {
                204 -> null
                in 200..299 -> connection.inputStream.bufferedReader().readText()
                else -> {
                    Log.w(TAG, "fetchIncidentsForBbox HTTP ${connection.responseCode}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchIncidentsForBbox failed: ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRouteIncidentFeature(feature: JSONObject?): RouteIncidentCandidate? {
        if (feature == null) return null
        val props = feature.optJSONObject("properties") ?: return null
        val iconCategory = props.optString("iconCategory")
        if (iconCategory != "jam" && iconCategory != "accident") return null

        val magnitude = props.optString("magnitudeOfDelay")
        val from = props.optString("from").takeIf { it.isNotBlank() }
        val to = props.optString("to").takeIf { it.isNotBlank() }
        val street = formatStreetName(from, to) ?: return null
        val level = trafficLevel(magnitude, iconCategory)
        if (level == TrafficFlowLevel.CLEAR || level == TrafficFlowLevel.LIGHT) return null

        val incidentLatLng = incidentCenterLatLng(feature.optJSONObject("geometry")) ?: return null
        return RouteIncidentCandidate(
            lat = incidentLatLng.first,
            lng = incidentLatLng.second,
            level = level,
            streetName = street,
        )
    }

    private fun closestPointOnRoute(
        lat: Double,
        lng: Double,
        routePoints: List<Pair<Double, Double>>,
        maxLookAheadM: Double,
    ): ClosestOnRoute? {
        var alongM = 0.0
        var bestDistM = Double.MAX_VALUE
        var bestAlongM = Double.MAX_VALUE

        for (i in 0 until routePoints.size - 1) {
            val a = routePoints[i]
            val b = routePoints[i + 1]
            val segLen = haversineMeters(a.first, a.second, b.first, b.second)
            if (segLen <= 0.0) continue

            val (t, distToSeg) = projectOntoSegment(lat, lng, a.first, a.second, b.first, b.second)
            val alongAt = alongM + t * segLen
            if (distToSeg < bestDistM) {
                bestDistM = distToSeg
                bestAlongM = alongAt
            }
            alongM += segLen
            if (alongM > maxLookAheadM + 500.0) break
        }

        if (bestDistM == Double.MAX_VALUE) return null
        return ClosestOnRoute(
            distanceToRouteM = bestDistM,
            distanceAlongRouteM = bestAlongM,
        )
    }

    /** @return segment parameter t in [0, 1] and distance from point to projected point in meters. */
    private fun projectOntoSegment(
        pLat: Double,
        pLng: Double,
        aLat: Double,
        aLng: Double,
        bLat: Double,
        bLng: Double,
    ): Pair<Double, Double> {
        val cosLat = cos(Math.toRadians((aLat + bLat) / 2.0))
        val bx = (bLng - aLng) * cosLat
        val by = bLat - aLat
        val px = (pLng - aLng) * cosLat
        val py = pLat - aLat
        val len2 = bx * bx + by * by
        val t = if (len2 <= 1e-12) {
            0.0
        } else {
            ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
        }
        val projLat = aLat + t * (bLat - aLat)
        val projLng = aLng + t * (bLng - aLng)
        return t to haversineMeters(pLat, pLng, projLat, projLng)
    }
}
