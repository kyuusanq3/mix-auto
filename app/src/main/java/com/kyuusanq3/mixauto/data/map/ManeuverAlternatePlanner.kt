package com.kyuusanq3.mixauto.data.map

import org.maplibre.android.geometry.LatLng
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure planner for per-maneuver alternate routes.
 *
 * Phase 1: when navigation starts, MixAuto also computes alternate routes that branch off at
 * each major turn ahead and re-join the destination. Only alternates whose remaining duration is
 * within [DURATION_DELTA_SECONDS] (5 minutes) of the original route's remaining duration from
 * that same maneuver are kept.
 *
 * Phase 2: during navigation, [alternateForNextTurn] picks the greyed fork that starts at the
 * upcoming maneuver. [shouldAdoptAlternate] treats that fork as on-route once the driver has
 * left the primary line and is clearly on the alternate.
 *
 * The actual OSRM fetch is invoked by [NavigationSessionCoordinator] via [NavigationRouteFetcher];
 * everything in this file is pure so it can be unit-tested without network I/O.
 */
internal data class ManeuverAlternateRoute(
    val maneuverStepIndex: Int,
    val branchLat: Double,
    val branchLng: Double,
    val geometryPoints: List<LatLng>,
    val durationSeconds: Double,
    val distanceMeters: Double,
)

internal object ManeuverAlternatePlanner {
    /** Alternates within +/- this many seconds of the original remaining duration are kept. */
    const val DURATION_DELTA_SECONDS: Double = 300.0

    /** Maneuver types that count as a "main turn" worth branching an alternate from. */
    private val MAIN_TURN_MANEUVER_TYPES = setOf(
        "turn",
        "merge",
        "fork",
        "roundabout",
        "end of road",
    )

    /**
     * Only branch alternates at a turn with at least this much remaining route downstream --
     * avoids spawning alternates when the driver is near arrival.
     */
    const val MIN_REMAINING_DISTANCE_M: Double = 1500.0

    /** Only consider turns within this many steps ahead of the current step (lookahead window). */
    const val LOOKAHEAD_STEP_COUNT: Int = 6

    /** |delta| below this is shown as "Similar ETA" on the grey-fork callout. */
    const val SIMILAR_ETA_MAX_SECONDS: Double = 60.0

    /** Place the ETA bubble this far along the fork from the branch. */
    const val CALLOUT_ALONG_ROUTE_M: Double = 150.0

    /**
     * Indices of upcoming "main turn" maneuvers in [steps] starting after [currentStepIndex],
     * bounded by [lookaheadStepCount] steps of look-ahead, and whose remaining route distance is at
     * least [minRemainingDistanceM]. Pure -- allocates no [ManeuverAlternateRoute] objects.
     */
    fun mainTurnManeuverIndices(
        steps: List<LegStep>,
        currentStepIndex: Int,
        lookaheadStepCount: Int = LOOKAHEAD_STEP_COUNT,
        minRemainingDistanceM: Double = MIN_REMAINING_DISTANCE_M,
    ): List<Int> {
        if (steps.isEmpty()) return emptyList()
        val suffixDistance = DoubleArray(steps.size)
        var running = 0.0
        for (i in steps.lastIndex downTo 0) {
            running += steps[i].distanceMeters
            suffixDistance[i] = running
        }
        val start = (currentStepIndex + 1).coerceIn(0, steps.lastIndex)
        val endIndex = (start + lookaheadStepCount).coerceAtMost(steps.size)
        return buildList {
            for (i in start until endIndex) {
                val step = steps[i]
                if (step.maneuverType !in MAIN_TURN_MANEUVER_TYPES) continue
                if (suffixDistance[i] < minRemainingDistanceM) continue
                add(i)
            }
        }
    }

    /**
     * Estimate the original route's remaining duration from [fromStepIndex] onward. [RouteResult]
     * only carries the total duration (per-step durations are not parsed), so the total is split
     * proportionally by distance. Falls back to the full duration when the route has no steps or
     * no usable distance.
     */
    fun estimateRemainingDurationSeconds(
        route: RouteResult,
        fromStepIndex: Int,
    ): Double {
        val totalDistance = route.steps.sumOf { it.distanceMeters }
        if (route.steps.isEmpty() || totalDistance <= 0.0) return route.durationSeconds
        val fromIndex = fromStepIndex.coerceIn(0, route.steps.lastIndex)
        val remainingDistance = route.steps.drop(fromIndex).sumOf { it.distanceMeters }
        return route.durationSeconds * (remainingDistance / totalDistance)
    }

    /**
     * Keep only alternates whose duration is within [deltaSeconds] of [baselineDurationSeconds] --
     * the "5 min slower / faster than the original" gate. Boundaries are inclusive.
     */
    fun filterAlternatesByDurationDelta(
        alternates: List<ManeuverAlternateRoute>,
        baselineDurationSeconds: Double,
        deltaSeconds: Double = DURATION_DELTA_SECONDS,
    ): List<ManeuverAlternateRoute> {
        val lower = baselineDurationSeconds - deltaSeconds
        val upper = baselineDurationSeconds + deltaSeconds
        return alternates.filter { it.durationSeconds in lower..upper }
    }

    /**
     * Remaining original polyline from the vertex nearest [branchLat]/[branchLng] onward.
     * Used so a branch-from-maneuver OSRM result can be compared against the original suffix
     * instead of the full route (which still includes already-traveled geometry).
     */
    fun remainingGeometryFromManeuver(
        geometryPoints: List<LatLng>,
        branchLat: Double,
        branchLng: Double,
    ): List<LatLng> {
        if (geometryPoints.isEmpty()) return emptyList()
        var bestIdx = 0
        var bestD2 = Double.MAX_VALUE
        for (i in geometryPoints.indices) {
            val dLat = geometryPoints[i].latitude - branchLat
            val dLng = geometryPoints[i].longitude - branchLng
            val d2 = dLat * dLat + dLng * dLng
            if (d2 < bestD2) {
                bestD2 = d2
                bestIdx = i
            }
        }
        return geometryPoints.drop(bestIdx)
    }

    /**
     * Phase 2: the greyed fork that starts at the upcoming maneuver ([nextStepIndex] =
     * currentStepIndex + 1). Exact match only -- do not show a later turn's alternate early.
     */
    fun alternateForNextTurn(
        alternates: List<ManeuverAlternateRoute>,
        nextStepIndex: Int,
    ): ManeuverAlternateRoute? {
        if (alternates.isEmpty()) return null
        return alternates.firstOrNull { it.maneuverStepIndex == nextStepIndex }
    }

    /**
     * Phase 2: adopt the greyed fork once the puck is on that line and clearly off the primary.
     * At the shared fork both distances are small, so this stays false until the geometries diverge.
     */
    fun shouldAdoptAlternate(
        distToPrimaryM: Float,
        distToAlternateM: Float,
        onRouteMaxM: Float = ON_ROUTE_PROGRESS_MAX_M,
        rerouteThresholdM: Float = REROUTE_THRESHOLD_M,
    ): Boolean {
        if (distToAlternateM > onRouteMaxM) return false
        return distToPrimaryM > rerouteThresholdM
    }

    /**
     * Driver-facing ETA vs the original remaining duration. [deltaSeconds] is alternate minus
     * original (negative = faster). Under one minute either way is "Similar ETA".
     */
    fun formatEtaCalloutLabel(deltaSeconds: Double): String {
        val absSec = abs(deltaSeconds)
        if (absSec < SIMILAR_ETA_MAX_SECONDS) return "Similar ETA"
        val minutes = round(absSec / 60.0).toInt().coerceAtLeast(1)
        return if (deltaSeconds < 0.0) {
            "$minutes min faster"
        } else {
            "$minutes min slower"
        }
    }

    /**
     * Point on [geometryPoints] about [alongRouteM] from the branch so the callout sits on the
     * grey fork, not on the shared start with the cyan line.
     */
    fun calloutAnchorPoint(
        geometryPoints: List<LatLng>,
        alongRouteM: Double = CALLOUT_ALONG_ROUTE_M,
    ): LatLng? {
        if (geometryPoints.size < 2) return geometryPoints.firstOrNull()
        var traveled = 0.0
        for (i in 1 until geometryPoints.size) {
            traveled += haversineMeters(geometryPoints[i - 1], geometryPoints[i])
            if (traveled >= alongRouteM) return geometryPoints[i]
        }
        return geometryPoints[geometryPoints.size / 2]
    }

    private fun haversineMeters(a: LatLng, b: LatLng): Double {
        val earthM = 6_371_000.0
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
        return 2.0 * earthM * asin(sqrt(h).coerceIn(0.0, 1.0))
    }
}