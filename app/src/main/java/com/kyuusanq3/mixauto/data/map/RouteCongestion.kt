package com.kyuusanq3.mixauto.data.map

import org.maplibre.android.geometry.LatLng
import kotlin.math.max

/** MapLibre hex for TomTom magnitudeOfDelay on the remaining route line. */
internal fun congestionColorForMagnitude(magnitude: Int): String = when {
    magnitude >= 4 -> "#7F1D1D"
    magnitude == 3 -> "#EF4444"
    magnitude == 2 -> "#F97316"
    magnitude == 1 -> "#FBBF24"
    else -> ROUTE_COLOR
}

/**
 * Per-edge max delay magnitude for geometry with [pointCount] points
 * (edges = pointCount - 1). Section indices are inclusive point indices from TomTom.
 */
internal fun edgeMagnitudesForTrafficSections(
    pointCount: Int,
    sections: List<TomTomTrafficSection>,
): IntArray {
    val edgeCount = (pointCount - 1).coerceAtLeast(0)
    val mag = IntArray(edgeCount)
    if (edgeCount == 0 || sections.isEmpty()) return mag
    for (section in sections) {
        val start = section.startPointIndex.coerceAtLeast(0)
        val end = section.endPointIndex.coerceAtMost(pointCount - 1)
        if (end <= start) continue
        for (i in start until end) {
            if (i in 0 until edgeCount) {
                mag[i] = max(mag[i], section.magnitudeOfDelay)
            }
        }
    }
    return mag
}

/**
 * FeatureCollection of LineStrings with property congestionColor.
 * Empty [sections] yields one cyan feature (same look as the legacy single LineString).
 */
internal fun buildCongestionFeatureCollectionJson(
    points: List<LatLng>,
    sections: List<TomTomTrafficSection>,
): String {
    if (points.size < 2) {
        return """{"type":"FeatureCollection","features":[]}"""
    }
    val magnitudes = edgeMagnitudesForTrafficSections(points.size, sections)
    return buildFeatureCollectionFromEdgeMagnitudes(points, magnitudes)
}

/**
 * Remaining-route FeatureCollection after progress split.
 * [remainingPoints] is [split] + fullPoints[segmentIndex+1..]; first edge uses mag[segmentIndex].
 */
internal fun buildRemainingCongestionFeatureCollectionJson(
    fullPoints: List<LatLng>,
    sections: List<TomTomTrafficSection>,
    remainingPoints: List<LatLng>,
    remainingStartSegmentIndex: Int,
): String {
    if (remainingPoints.size < 2) {
        return """{"type":"FeatureCollection","features":[]}"""
    }
    if (sections.isEmpty()) {
        return buildCongestionFeatureCollectionJson(remainingPoints, emptyList())
    }
    val fullMag = edgeMagnitudesForTrafficSections(fullPoints.size, sections)
    val remEdges = remainingPoints.size - 1
    val remMag = IntArray(remEdges)
    for (k in 0 until remEdges) {
        val fullEdge = remainingStartSegmentIndex + k
        remMag[k] = if (fullEdge in fullMag.indices) fullMag[fullEdge] else 0
    }
    return buildFeatureCollectionFromEdgeMagnitudes(remainingPoints, remMag)
}

private fun buildFeatureCollectionFromEdgeMagnitudes(
    points: List<LatLng>,
    magnitudes: IntArray,
): String {
    if (points.size < 2 || magnitudes.isEmpty()) {
        return """{"type":"FeatureCollection","features":[]}"""
    }
    val features = StringBuilder()
    var featureCount = 0
    var runStart = 0
    while (runStart < magnitudes.size) {
        val runMag = magnitudes[runStart]
        var runEnd = runStart + 1
        while (runEnd < magnitudes.size && magnitudes[runEnd] == runMag) {
            runEnd++
        }
        // points[runStart] .. points[runEnd] inclusive
        val slice = points.subList(runStart, runEnd + 1)
        val coords = slice.joinToString(",") { p ->
            "[${p.longitude},${p.latitude}]"
        }
        val color = congestionColorForMagnitude(runMag)
        if (featureCount > 0) features.append(',')
        features.append(
            """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]},"properties":{"congestionColor":"$color"}}""",
        )
        featureCount++
        runStart = runEnd
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}