package com.kyuusanq3.mixauto.data.map

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

sealed class TomTomKeyCheckResult {
    data class Success(val message: String) : TomTomKeyCheckResult()
    data class Failure(val message: String) : TomTomKeyCheckResult()
}

/** Congestion tier for status-strip color coding (TomTom flow palette). */
enum class TrafficFlowLevel {
    CLEAR,
    LIGHT,
    MODERATE,
    HEAVY,
}

/** One traffic reel line for the status strip. */
data class TrafficHeadline(
    val text: String,
    val level: TrafficFlowLevel,
)

/** Jam or accident aligned with the active route corridor (nav-start TTS). */
data class RouteTrafficJam(
    val level: TrafficFlowLevel,
    val streetName: String,
    val distanceAlongRouteM: Double,
)

object TomTomTrafficClient {
    private const val TAG = "TomTomTrafficClient"
    private const val USER_AGENT = "MixAutoCarLauncher/1.0"
    private const val INCIDENTS_CACHE_TTL_MS = 3 * 60 * 1000L
    private const val INCIDENTS_COORD_CACHE_DELTA = 0.02
    private const val MAX_HEADLINES = 5
    private const val BBOX_RADIUS_DEGREES = 0.12

    @Volatile
    private var cachedIncidentsLat: Double? = null

    @Volatile
    private var cachedIncidentsLng: Double? = null

    @Volatile
    private var cachedHeadlines: List<TrafficHeadline>? = null

    @Volatile
    private var cachedIncidentsAtMs: Long = 0L

    /** Bacolod area tile — same endpoint and params as the traffic overlay layer. */
    private const val TEST_TILE_ZOOM = 12
    private const val TEST_TILE_X = 3444
    private const val TEST_TILE_Y = 1926

    fun verifyApiKey(apiKey: String): TomTomKeyCheckResult {
        val trimmed = apiKey.trim()
        if (trimmed.isBlank()) {
            return TomTomKeyCheckResult.Failure("Enter an API key first")
        }

        val url = URL(
            "https://api.tomtom.com/maps/orbis/traffic/flow/raster/tile/" +
                "$TEST_TILE_ZOOM/$TEST_TILE_X/$TEST_TILE_Y" +
                "?apiVersion=2&key=$trimmed&style=light&tileSize=256",
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000

        return try {
            val code = connection.responseCode
            val contentType = connection.contentType.orEmpty()
            Log.d(TAG, "verifyApiKey HTTP $code contentType=$contentType")

            when (code) {
                in 200..299 -> {
                    val bytes = connection.inputStream.use { it.readBytes() }
                    if (bytes.size < 100) {
                        TomTomKeyCheckResult.Failure(
                            "HTTP $code but tile was empty (${bytes.size} bytes). " +
                                "Key may lack Traffic Flow API access.",
                        )
                    } else {
                        TomTomKeyCheckResult.Success(
                            "Key valid — received traffic tile (${bytes.size} bytes)",
                        )
                    }
                }
                401 -> TomTomKeyCheckResult.Failure("Unauthorized (HTTP 401) — check your API key")
                403 -> TomTomKeyCheckResult.Failure(
                    "Forbidden (HTTP 403) — invalid key or Traffic Flow API not enabled for this key",
                )
                429 -> TomTomKeyCheckResult.Failure("Rate limited (HTTP 429) — try again in a few minutes")
                else -> {
                    val errorBody = runCatching {
                        connection.errorStream?.bufferedReader()?.readText()?.take(200)
                    }.getOrNull()
                    TomTomKeyCheckResult.Failure(
                        "HTTP $code${errorBody?.let { ": $it" } ?: ""}",
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "verifyApiKey failed: ${e.message}")
            TomTomKeyCheckResult.Failure("Network error: ${e.message ?: "unknown"}")
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Fetches nearby congestion for the status strip traffic reel.
     * Returns a single "clear" headline when nothing significant is found;
     * null on network/API failure.
     */
    fun fetchNearbyIncidents(
        latitude: Double,
        longitude: Double,
        apiKey: String,
    ): List<TrafficHeadline>? {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) return emptyList()

        val now = System.currentTimeMillis()
        val cached = cachedHeadlines
        val cacheLat = cachedIncidentsLat
        val cacheLng = cachedIncidentsLng
        if (
            cached != null &&
            cacheLat != null &&
            cacheLng != null &&
            now - cachedIncidentsAtMs < INCIDENTS_CACHE_TTL_MS &&
            abs(cacheLat - latitude) < INCIDENTS_COORD_CACHE_DELTA &&
            abs(cacheLng - longitude) < INCIDENTS_COORD_CACHE_DELTA
        ) {
            return cached
        }

        val latDelta = BBOX_RADIUS_DEGREES
        val lngDelta = BBOX_RADIUS_DEGREES / cos(Math.toRadians(latitude)).coerceAtLeast(0.2)
        val minLon = longitude - lngDelta
        val minLat = latitude - latDelta
        val maxLon = longitude + lngDelta
        val maxLat = latitude + latDelta
        val bbox = listOf(minLon, minLat, maxLon, maxLat)
            .joinToString(",") { String.format(Locale.US, "%.5f", it) }

        val url = URL(
            "https://api.tomtom.com/maps/orbis/traffic/incidents/details" +
                "?apiVersion=2&bbox=$bbox&timeValidity=present" +
                "&iconCategories=jam,accident",
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("TomTom-Api-Key", trimmedKey)
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
                204 -> {
                    val headlines = listOf(clearTrafficHeadline())
                    storeIncidentCache(latitude, longitude, headlines, now)
                    headlines
                }
                in 200..299 -> {
                    val body = connection.inputStream.bufferedReader().readText()
                    val headlines = parseTrafficReel(body, latitude, longitude)
                    storeIncidentCache(latitude, longitude, headlines, now)
                    Log.d(TAG, "fetchNearbyIncidents: ${headlines.size} reel line(s)")
                    headlines
                }
                else -> {
                    Log.w(TAG, "fetchNearbyIncidents HTTP ${connection.responseCode}")
                    cached
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchNearbyIncidents failed: ${e.message}")
            cached
        } finally {
            connection.disconnect()
        }
    }

    private fun clearTrafficHeadline() = TrafficHeadline(
        text = "No major traffic detected",
        level = TrafficFlowLevel.CLEAR,
    )

    private fun storeIncidentCache(
        latitude: Double,
        longitude: Double,
        headlines: List<TrafficHeadline>,
        atMs: Long,
    ) {
        cachedIncidentsLat = latitude
        cachedIncidentsLng = longitude
        cachedHeadlines = headlines
        cachedIncidentsAtMs = atMs
    }

    private fun parseTrafficReel(
        body: String,
        originLat: Double,
        originLng: Double,
    ): List<TrafficHeadline> {
        val incidents = JSONObject(body).optJSONArray("incidents") ?: return listOf(clearTrafficHeadline())
        val parsed = (0 until incidents.length())
            .mapNotNull { index ->
                parseTrafficJamFeature(incidents.optJSONObject(index), originLat, originLng)
            }

        if (parsed.isEmpty()) return listOf(clearTrafficHeadline())

        return parsed
            .groupBy { it.streetKey }
            .values
            .map { entries -> entries.maxBy { levelRank(it.level) } }
            .sortedWith(
                compareByDescending<ParsedJam> { levelRank(it.level) }
                    .thenBy { it.distanceM }
                    .thenBy { it.streetKey },
            )
            .map { it.headline }
            .take(MAX_HEADLINES)
    }

    private data class ParsedJam(
        val streetKey: String,
        val level: TrafficFlowLevel,
        val distanceM: Double,
        val headline: TrafficHeadline,
    )

    private fun parseTrafficJamFeature(
        feature: JSONObject?,
        originLat: Double,
        originLng: Double,
    ): ParsedJam? {
        if (feature == null) return null
        val props = feature.optJSONObject("properties") ?: return null
        val iconCategory = props.optString("iconCategory")
        if (iconCategory != "jam" && iconCategory != "accident") return null

        val magnitude = props.optString("magnitudeOfDelay")
        val from = props.optString("from").takeIf { it.isNotBlank() }
        val to = props.optString("to").takeIf { it.isNotBlank() }
        val street = formatStreetName(from, to) ?: return null
        val level = trafficLevel(magnitude, iconCategory)
        if (level == TrafficFlowLevel.CLEAR) return null

        val levelLabel = when (level) {
            TrafficFlowLevel.HEAVY -> "Heavy traffic"
            TrafficFlowLevel.MODERATE -> "Medium traffic"
            TrafficFlowLevel.LIGHT -> "Light traffic"
            TrafficFlowLevel.CLEAR -> return null
        }
        val text = "$levelLabel › $street"
        val incidentLatLng = incidentCenterLatLng(feature.optJSONObject("geometry"))
        val distanceM = if (incidentLatLng != null) {
            haversineMeters(originLat, originLng, incidentLatLng.first, incidentLatLng.second)
        } else {
            Double.MAX_VALUE
        }
        return ParsedJam(
            streetKey = street.lowercase(Locale.US),
            level = level,
            distanceM = distanceM,
            headline = TrafficHeadline(text = text, level = level),
        )
    }

    private fun trafficLevel(magnitude: String, iconCategory: String): TrafficFlowLevel = when {
        iconCategory == "accident" -> TrafficFlowLevel.HEAVY
        magnitude == "major" -> TrafficFlowLevel.HEAVY
        magnitude == "moderate" -> TrafficFlowLevel.MODERATE
        magnitude == "minor" -> TrafficFlowLevel.LIGHT
        iconCategory == "jam" -> TrafficFlowLevel.MODERATE
        else -> TrafficFlowLevel.CLEAR
    }

    private fun levelRank(level: TrafficFlowLevel): Int = when (level) {
        TrafficFlowLevel.HEAVY -> 3
        TrafficFlowLevel.MODERATE -> 2
        TrafficFlowLevel.LIGHT -> 1
        TrafficFlowLevel.CLEAR -> 0
    }

    /** Prefer a single road name; drop redundant from→to when both sides match. */
    private fun formatStreetName(from: String?, to: String?): String? {
        if (!from.isNullOrBlank() && !to.isNullOrBlank()) {
            if (from.equals(to, ignoreCase = true)) return from
            if (to.contains(from, ignoreCase = true)) return to
            if (from.contains(to, ignoreCase = true)) return from
            return from
        }
        return from ?: to
    }

    private fun incidentCenterLatLng(geometry: JSONObject?): Pair<Double, Double>? {
        if (geometry == null) return null
        val coords = geometry.optJSONArray("coordinates") ?: return null
        return when (geometry.optString("type")) {
            "Point" -> {
                if (coords.length() < 2) return null
                Pair(coords.getDouble(1), coords.getDouble(0))
            }
            "LineString" -> {
                if (coords.length() == 0) return null
                val mid = coords.length() / 2
                val point = coords.optJSONArray(mid) ?: return null
                if (point.length() < 2) return null
                Pair(point.getDouble(1), point.getDouble(0))
            }
            else -> null
        }
    }

    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadiusM = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        return earthRadiusM * 2 * kotlin.math.atan2(sqrt(a), sqrt(1 - a))
    }

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
        connection.setRequestProperty("User-Agent", USER_AGENT)
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
