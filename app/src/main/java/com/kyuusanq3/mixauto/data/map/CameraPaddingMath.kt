package com.kyuusanq3.mixauto.data.map

import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import kotlin.math.max

private const val ROUTE_OVERVIEW_BOUNDS_EXPAND_FRACTION = 0.12
private const val ROUTE_OVERVIEW_MIN_BOUNDS_PAD_DEGREES = 0.0008
private const val PADDING_QUANTIZE_DP = 4
private const val LOOKAHEAD_MIN_SPEED_MPS = 2f
internal const val LOOKAHEAD_ENTER_SPEED_MPS = 2.5f
internal const val LOOKAHEAD_EXIT_SPEED_MPS = 1.5f
private const val LOOKAHEAD_MAX_TOP_FRACTION = 0.12f

/**
 * Pure viewport-padding and route-overview-bounds math extracted from [MapLibreEngineImpl].
 * These functions have no side effects and no dependency on engine instance state — every input
 * (map size, density, offsets, current speed) is passed explicitly. The stateful camera
 * orchestration that calls these (mode transitions, exact ordering around `TRACKING_GPS`,
 * top-down/nav-dive sequencing) stays on the engine — see the extensive "Lessons learned" in
 * `mix-auto-map-engine.mdc` documenting why that ordering is fragile.
 */
internal data class ViewportPadding(
    val left: Int,
    val top: Int,
    val right: Int = 0,
    val bottom: Int = 0,
)

internal fun expandLatLngBounds(bounds: LatLngBounds, fraction: Double): LatLngBounds {
    val latSpan = bounds.northEast.latitude - bounds.southWest.latitude
    val lngSpan = bounds.northEast.longitude - bounds.southWest.longitude
    val latPad = max(latSpan * fraction, ROUTE_OVERVIEW_MIN_BOUNDS_PAD_DEGREES)
    val lngPad = max(lngSpan * fraction, ROUTE_OVERVIEW_MIN_BOUNDS_PAD_DEGREES)
    return LatLngBounds.from(
        bounds.northEast.latitude + latPad,
        bounds.northEast.longitude + lngPad,
        bounds.southWest.latitude - latPad,
        bounds.southWest.longitude - lngPad,
    )
}

internal fun buildRouteOverviewBounds(
    origin: LatLng,
    destination: LatLng,
    selectionBoundsPoints: List<LatLng>,
    routeGeometryPoints: List<LatLng>,
    lastKnownLocation: LatLng?,
): LatLngBounds {
    val builder = LatLngBounds.Builder()
    if (selectionBoundsPoints.isNotEmpty()) {
        selectionBoundsPoints.forEach { builder.include(it) }
    } else if (routeGeometryPoints.isNotEmpty()) {
        routeGeometryPoints.forEach { builder.include(it) }
    } else {
        builder.include(origin)
    }
    builder.include(destination)
    lastKnownLocation?.let { builder.include(it) }
    return expandLatLngBounds(builder.build(), ROUTE_OVERVIEW_BOUNDS_EXPAND_FRACTION)
}

internal fun computeRouteOverviewPadding(density: Float, mapWidth: Float, mapHeight: Float): ViewportPadding {
    if (mapWidth > 0 && mapHeight > 0) {
        return ViewportPadding(
            left = max((mapWidth * 0.14f).toInt(), (72 * density).toInt()),
            top = max((mapHeight * 0.24f).toInt(), (140 * density).toInt()),
            right = max((mapWidth * 0.10f).toInt(), (56 * density).toInt()),
            bottom = max((mapHeight * 0.08f).toInt(), (40 * density).toInt()),
        )
    }
    return ViewportPadding(
        left = (160 * density).toInt(),
        top = (220 * density).toInt(),
        right = (112 * density).toInt(),
        bottom = (64 * density).toInt(),
    )
}

internal fun quantizePaddingPx(px: Int, density: Float): Int {
    val stepPx = (PADDING_QUANTIZE_DP * density).toInt().coerceAtLeast(1)
    return (px / stepPx) * stepPx
}

/** Speed-bucketed (not continuous) so padding doesn't recompute on every tiny speed change. */
internal fun bucketedLookaheadTopFraction(speedMps: Float, lookaheadActive: Boolean): Float {
    if (!lookaheadActive) return 0f
    val bucketedSpeed = when {
        speedMps < 5f -> 3.5f
        speedMps < 12f -> 8f
        else -> 20f
    }
    return lookaheadTopFraction(bucketedSpeed)
}

internal fun lookaheadTopFraction(speedMps: Float): Float {
    if (speedMps < LOOKAHEAD_MIN_SPEED_MPS) return 0f
    return when {
        speedMps < 8f -> 0.05f + (speedMps - LOOKAHEAD_MIN_SPEED_MPS) / 6f * 0.04f
        else -> (0.09f + (speedMps - 8f) / 12f * 0.03f).coerceAtMost(LOOKAHEAD_MAX_TOP_FRACTION)
    }
}

/** Hysteresis gate for speed-based lookahead padding (enter 2.5 m/s, exit 1.5 m/s). */
internal fun nextLookaheadPaddingActive(current: Boolean, speedMps: Float): Boolean = when {
    !current && speedMps >= LOOKAHEAD_ENTER_SPEED_MPS -> true
    current && speedMps < LOOKAHEAD_EXIT_SPEED_MPS -> false
    else -> current
}

internal fun computeDrivingViewportPadding(
    density: Float,
    mapWidth: Float,
    mapHeight: Float,
    fallbackWidth: Int,
    fallbackHeight: Int,
    puckHorizontalOffset: Float,
    puckVerticalOffset: Float,
    lookaheadFraction: Float,
): ViewportPadding {
    val topFraction = (puckVerticalOffset + lookaheadFraction).coerceAtMost(0.55f)
    if (mapWidth > 0 && mapHeight > 0) {
        return ViewportPadding(
            left = quantizePaddingPx((mapWidth * puckHorizontalOffset).toInt(), density),
            top = quantizePaddingPx((mapHeight * topFraction).toInt(), density),
        )
    }
    return ViewportPadding(
        left = quantizePaddingPx((fallbackWidth * puckHorizontalOffset).toInt(), density),
        top = quantizePaddingPx((fallbackHeight * topFraction).toInt(), density),
    )
}
