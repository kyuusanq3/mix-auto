package com.kyuusanq3.mixauto.data.map

import android.location.Location
import com.kyuusanq3.mixauto.data.navigation.NavTtsPhrases
import org.maplibre.android.geometry.LatLng
import kotlin.math.min

/** TomTom lighter-traffic alternate qualification and route conversion helpers. */
internal object LighterTrafficHelper {
    fun isLighterTrafficAlternate(
        conventional: RouteResult,
        tomtom: TomTomRouteResult,
    ): Boolean {
        val ttResult = tomTomToRouteResult(tomtom)
        if (routesAreSimilar(conventional.geometryPoints, ttResult.geometryPoints)) return false
        val travelTimeFaster = conventional.durationSeconds - ttResult.durationSeconds >= 60
        val lighterUnderTraffic = tomtom.trafficDelaySeconds <= 60 &&
            ttResult.durationSeconds < conventional.durationSeconds
        return travelTimeFaster || lighterUnderTraffic
    }

    fun tomTomToRouteResult(tt: TomTomRouteResult): RouteResult {
        val geometryPoints = tt.geometryPoints.map { LatLng(it.first, it.second) }
        val steps = tt.steps.map { step ->
            val maneuverType = NavTtsPhrases.inferManeuverType(step.instruction)
            val maneuverModifier = NavTtsPhrases.inferManeuverModifier(step.instruction)
            LegStep(
                maneuverLat = step.maneuverLat,
                maneuverLng = step.maneuverLng,
                instruction = step.instruction,
                distanceLabel = step.distanceLabel,
                streetName = step.streetName,
                distanceMeters = step.distanceMeters,
                maneuverType = maneuverType,
                maneuverModifier = maneuverModifier,
            )
        }
        val firstStep = steps.firstOrNull()
        val geometryJson = buildLineStringFeatureJson(geometryPoints)
        return RouteResult(
            geometryJson = geometryJson,
            geometryPoints = geometryPoints,
            streetName = firstStep?.streetName?.ifBlank { tt.primaryStreet } ?: tt.primaryStreet,
            instruction = firstStep?.instruction ?: "Depart",
            distance = firstStep?.distanceLabel ?: TomTomRoutingClient.formatDistance(tt.distanceMeters),
            steps = steps,
            durationSeconds = tt.travelTimeSeconds.toDouble(),
            distanceMeters = tt.distanceMeters,
            trafficDelaySeconds = tt.trafficDelaySeconds,
        )
    }

    fun routesAreSimilar(a: List<LatLng>, b: List<LatLng>): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        val samplesA = sampleRoutePoints(a, 5)
        val samplesB = sampleRoutePoints(b, 5)
        val thresholdM = 50f
        val nearCount = samplesA.count { pointA ->
            samplesB.any { pointB ->
                val results = FloatArray(1)
                Location.distanceBetween(
                    pointA.latitude,
                    pointA.longitude,
                    pointB.latitude,
                    pointB.longitude,
                    results,
                )
                results[0] < thresholdM
            }
        }
        return nearCount >= min(samplesA.size, samplesB.size) - 1
    }

    fun sampleRoutePoints(points: List<LatLng>, count: Int): List<LatLng> {
        if (points.size <= count) return points
        val step = (points.size - 1) / (count - 1).coerceAtLeast(1)
        return buildList {
            var i = 0
            while (i < points.size) {
                add(points[i])
                i += step
            }
            if (last() != points.last()) add(points.last())
        }
    }
}
