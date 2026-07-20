package com.kyuusanq3.mixauto.data.map

import org.maplibre.android.style.expressions.Expression

/**
 * Pure helpers for boosting road line width on the bundled vector (Liberty-fork) style so
 * automotive driving zooms read clearly. See `tools/gen_mix_auto_driving_style.py` for the
 * companion JSON-time width boost baked into `mix-auto-driving.json`.
 */

private const val AUTOMOTIVE_MAIN_ROAD_EXTRA = 1.55f
private const val AUTOMOTIVE_MINOR_ROAD_EXTRA = 1.65f
private const val AUTOMOTIVE_MAIN_BOOST_ZOOM_START = 13.0
private const val AUTOMOTIVE_MINOR_BOOST_ZOOM_START = 15.0
private const val AUTOMOTIVE_BOOST_ZOOM_FULL = 17.0

/** 1.0Ã— below per-class zoom start; full [baseFactor] at nav zoom and above. */
internal fun automotiveRoadWidthZoomFactor(layerId: String, baseFactor: Float): Expression {
    val zoomStart = if (isAutomotiveMinorRoadLineLayer(layerId)) {
        AUTOMOTIVE_MINOR_BOOST_ZOOM_START
    } else {
        AUTOMOTIVE_MAIN_BOOST_ZOOM_START
    }
    return Expression.interpolate(
        Expression.linear(),
        Expression.zoom(),
        Expression.literal(zoomStart),
        Expression.literal(1.0f),
        Expression.literal(AUTOMOTIVE_BOOST_ZOOM_FULL),
        Expression.literal(baseFactor),
    )
}

internal fun isAutomotiveRoadLineLayer(layerId: String): Boolean {
    if (
        !layerId.startsWith("road_") &&
        !layerId.startsWith("tunnel_") &&
        !layerId.startsWith("bridge_")
    ) {
        return false
    }
    if ("one_way" in layerId || layerId.startsWith("road_area")) return false
    if ("path" in layerId || "pedestrian" in layerId || "rail" in layerId) return false
    return true
}

internal fun isAutomotiveMinorRoadLineLayer(layerId: String): Boolean {
    val minorTokens = listOf(
        "minor",
        "service",
        "track",
        "tertiary",
        "living",
        "street",
        "link",
    )
    return minorTokens.any { layerId.contains(it) }
}

internal fun automotiveRoadWidthFactor(layerId: String): Float {
    return if (isAutomotiveMinorRoadLineLayer(layerId)) {
        AUTOMOTIVE_MINOR_ROAD_EXTRA
    } else {
        AUTOMOTIVE_MAIN_ROAD_EXTRA
    }
}
