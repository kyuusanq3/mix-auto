package com.kyuusanq3.mixauto.data.map

import kotlin.math.abs
import kotlin.math.ln

/**
 * Pure follow-zoom math for driving camera.
 *
 * **Extend here (Turn A):** append new formulas (e.g. `targetZoomForSpeed`) **after**
 * [shouldApplyZoomChange] — never nest inside [targetZoomForManeuverDistance].
 * Unit-test first; apply via [NavigationCameraController.updateDrivingZoomForSpeed] /
 * [NavigationCameraController.updateNavigationZoomForDistance] (GPS tick already wired).
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

    fun targetZoomForSpeed(speedMps: Double, maxZoom: Double): Double {
        val ceiling = maxZoom.coerceAtLeast(DYNAMIC_ZOOM_MIN)
        val mid = (ceiling + DYNAMIC_ZOOM_MIN) / 2.0
        val speed = speedMps.coerceAtLeast(0.0)

        return when {
            speed <= 2.0 -> ceiling
            speed >= 20.0 -> DYNAMIC_ZOOM_MIN
            speed <= 8.0 -> {
                val progress = (speed - 2.0) / (8.0 - 2.0)
                ceiling - progress * (ceiling - mid)
            }
            speed <= 12.0 -> mid
            else -> {
                val progress = (speed - 12.0) / (20.0 - 12.0)
                mid - progress * (mid - DYNAMIC_ZOOM_MIN)
            }
        }
    }
}
