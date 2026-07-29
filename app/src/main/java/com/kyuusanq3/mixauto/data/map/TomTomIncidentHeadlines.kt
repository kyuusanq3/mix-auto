package com.kyuusanq3.mixauto.data.map

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos

internal object TomTomIncidentHeadlines {
    private const val TAG = "TomTomIncidentHeadlines"
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
        connection.setRequestProperty("User-Agent", TOMTOM_USER_AGENT)
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
}
