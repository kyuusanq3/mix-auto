package com.kyuusanq3.mixauto.data.map

import android.location.Location
import kotlin.math.abs

private const val STOPPED_SPEED_MPS = 1.4f

/** GPS jitter radius — fixes closer than this with no speed are treated as stopped. */
private const val STOPPED_MOVE_M = 8f
private const val BEARING_EMA_ALPHA = 0.35f

/** Hold bearing steady on straight roads when GPS course noise is below this threshold. */
private const val STRAIGHT_ROAD_LOCK_DEG = 4f

/**
 * Synthesizes and smooths a stable heading for the driving puck, extracted from
 * [MapLibreEngineImpl]. Owns the only mutable state this logic touches: the previous raw fix
 * (for movement-based bearing), the current smoothed/frozen bearing, and a short-lived cache so
 * [BearingEnrichedLocationEngine] and `applyAndroidLocation` share one enrichment per raw GPS
 * tick (computing it twice on the same tick sees zero movement and flickers the bearing).
 *
 * [cameraBearingFallback] lets [resolveFrozenBearing] fall back to the current map camera
 * bearing when stopped with no prior stable bearing — this is the one genuinely cross-cutting
 * dependency, injected rather than duplicated.
 */
internal class BearingEnricher(
    private val cameraBearingFallback: () -> Float?,
) {
    private var previousLocationFix: Location? = null
    private var lastStableBearing: Float? = null
    private var lastEnrichedFix: Location? = null
    private var lastEnrichedFixTimeMs: Long = Long.MIN_VALUE

    fun lastBearing(): Float? = lastStableBearing

    fun getOrEnrichLocation(location: Location): Location {
        lastEnrichedFix?.let { cached ->
            if (location.time == lastEnrichedFixTimeMs) return cached
            if (cached.distanceTo(location) < LOCATION_FIX_DEDUP_DIST_M &&
                abs(location.time - lastEnrichedFixTimeMs) < 500L
            ) {
                return cached
            }
        }
        val enriched = computeEnrichedLocation(location)
        lastEnrichedFixTimeMs = location.time
        lastEnrichedFix = enriched
        return enriched
    }

    private fun computeEnrichedLocation(location: Location): Location {
        val prev = previousLocationFix
        val movedM = prev?.distanceTo(location) ?: Float.MAX_VALUE
        previousLocationFix = location

        val isStopped = when {
            location.hasSpeed() -> location.speed < STOPPED_SPEED_MPS
            movedM < STOPPED_MOVE_M -> true
            else -> false
        }
        if (isStopped) {
            val frozen = Location(location)
            frozen.bearing = resolveFrozenBearing(location)
            return frozen
        }
        if (location.hasBearing() && location.bearing != 0f) {
            val rawBearing = location.bearing
            applyStraightRoadBearingLock(rawBearing)?.let { locked ->
                val enriched = Location(location)
                enriched.bearing = locked
                return enriched
            }
            val smoothed = smoothBearing(rawBearing)
            lastStableBearing = smoothed
            val enriched = Location(location)
            enriched.bearing = smoothed
            return enriched
        }
        // Distance gate: need >= 10 m of movement for a stable heading (above GPS jitter floor)
        if (prev != null && movedM > 10f) {
            val enriched = Location(location)
            val computed = prev.bearingTo(location)
            applyStraightRoadBearingLock(computed)?.let { locked ->
                enriched.bearing = locked
                return enriched
            }
            enriched.bearing = smoothBearing(computed)
            lastStableBearing = enriched.bearing
            return enriched
        }
        lastStableBearing?.let { stable ->
            val held = Location(location)
            held.bearing = stable
            return held
        }
        return Location(location)
    }

    private fun smoothBearing(target: Float): Float {
        val previous = lastStableBearing ?: return target
        val delta = signedBearingDelta(target - previous)
        return normalizeBearing(previous + delta * BEARING_EMA_ALPHA)
    }

    private fun applyStraightRoadBearingLock(rawBearing: Float): Float? {
        val stable = lastStableBearing ?: return null
        if (abs(signedBearingDelta(rawBearing - stable)) < STRAIGHT_ROAD_LOCK_DEG) {
            return stable
        }
        return null
    }

    private fun normalizeBearing(bearing: Float): Float {
        var b = bearing % 360f
        if (b < 0f) b += 360f
        return b
    }

    private fun signedBearingDelta(delta: Float): Float {
        var d = delta % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    fun resolveFrozenBearing(location: Location): Float {
        lastStableBearing?.let { return it }
        cameraBearingFallback()?.let { cameraBearing ->
            lastStableBearing = cameraBearing
            return cameraBearing
        }
        if (location.hasBearing()) return location.bearing
        return 0f
    }
}
