package com.kyuusanq3.mixauto.ui.components

import java.time.LocalTime

/**
 * Pure helper for Map Settings "Night Mode" — dims the map pane either on a manual toggle
 * or automatically between [NIGHT_MODE_AUTO_START_HOUR] (6 PM) and [NIGHT_MODE_AUTO_END_HOUR]
 * (6 AM). Kept separate from [CarMapViewContainer] so the schedule math is unit-testable
 * without Compose.
 */
internal const val NIGHT_MODE_AUTO_START_HOUR: Int = 18
internal const val NIGHT_MODE_AUTO_END_HOUR: Int = 6
internal const val NIGHT_MODE_DIM_ALPHA: Float = 0.38f

internal fun isNightModeActive(
    manualEnabled: Boolean,
    autoEnabled: Boolean,
    now: LocalTime = LocalTime.now(),
): Boolean {
    if (!autoEnabled) return manualEnabled
    val startHour = NIGHT_MODE_AUTO_START_HOUR
    val endHour = NIGHT_MODE_AUTO_END_HOUR
    return if (startHour > endHour) {
        now.hour >= startHour || now.hour < endHour
    } else {
        now.hour in startHour until endHour
    }
}
