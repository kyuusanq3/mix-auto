package com.kyuusanq3.mixauto.data.map

import org.json.JSONObject
import java.util.Locale
import kotlin.math.atan2
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

internal const val TOMTOM_USER_AGENT = "MixAutoCarLauncher/1.0"

internal fun trafficLevel(magnitude: String, iconCategory: String): TrafficFlowLevel = when {
    iconCategory == "accident" -> TrafficFlowLevel.HEAVY
    magnitude == "major" -> TrafficFlowLevel.HEAVY
    magnitude == "moderate" -> TrafficFlowLevel.MODERATE
    magnitude == "minor" -> TrafficFlowLevel.LIGHT
    iconCategory == "jam" -> TrafficFlowLevel.MODERATE
    else -> TrafficFlowLevel.CLEAR
}

internal fun levelRank(level: TrafficFlowLevel): Int = when (level) {
    TrafficFlowLevel.HEAVY -> 3
    TrafficFlowLevel.MODERATE -> 2
    TrafficFlowLevel.LIGHT -> 1
    TrafficFlowLevel.CLEAR -> 0
}

/** Prefer a single road name; drop redundant from→to when both sides match. */
internal fun formatStreetName(from: String?, to: String?): String? {
    if (!from.isNullOrBlank() && !to.isNullOrBlank()) {
        if (from.equals(to, ignoreCase = true)) return from
        if (to.contains(from, ignoreCase = true)) return to
        if (from.contains(to, ignoreCase = true)) return from
        return from
    }
    return from ?: to
}

internal fun incidentCenterLatLng(geometry: JSONObject?): Pair<Double, Double>? {
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

internal fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val earthRadiusM = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLng / 2) * sin(dLng / 2)
    return earthRadiusM * 2 * atan2(sqrt(a), sqrt(1 - a))
}
