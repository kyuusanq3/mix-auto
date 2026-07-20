package com.kyuusanq3.mixauto.data.map

import kotlin.math.abs
import kotlin.math.ln

/**
 * Maps distance-to-next-maneuver to navigation follow zoom so the puck and upcoming turn
 * stay visible on the tilted driving viewport.
 */
internal object NavigationZoom {
    const val DYNAMIC_ZOOM_MIN = 15.0
    const val CLOSE_DISTANCE_M = 80.0
    const val FAR_DISTANCE_M = 1500.0
    const val ZOOM_HYSTERESIS = 0.2

    fun targetZoomForManeuverDistance(distanceM: Float, maxZoom: Double): Double {
        val ceiling = maxZoom.coerceAtLeast(DYNAMIC_ZOOM_MIN)
        val distance = distanceM.coerceAtLeast(0f).toDouble()
        if (distance <= CLOSE_DISTANCE_M) return ceiling
        if (distance >= FAR_DISTANCE_M) return DYNAMIC_ZOOM_MIN
        val progress = ln(distance / CLOSE_DISTANCE_M) / ln(FAR_DISTANCE_M / CLOSE_DISTANCE_M)
        val t = progress.coerceIn(0.0, 1.0)
        return ceiling - t * (ceiling - DYNAMIC_ZOOM_MIN)
    }

    fun shouldApplyZoomChange(
        lastApplied: Double?,
        target: Double,
        hysteresis: Double = ZOOM_HYSTERESIS,
    ): Boolean {
        if (lastApplied == null) return true
        return abs(target - lastApplied) >= hysteresis
    }
}
