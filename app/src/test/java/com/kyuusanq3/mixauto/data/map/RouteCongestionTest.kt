package com.kyuusanq3.mixauto.data.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.geometry.LatLng

class RouteCongestionTest {

    @Test
    fun congestionColorForMagnitude_mapsLevels() {
        assertEquals(ROUTE_COLOR, congestionColorForMagnitude(0))
        assertEquals("#FBBF24", congestionColorForMagnitude(1))
        assertEquals("#F97316", congestionColorForMagnitude(2))
        assertEquals("#EF4444", congestionColorForMagnitude(3))
        assertEquals("#7F1D1D", congestionColorForMagnitude(4))
    }

    @Test
    fun edgeMagnitudes_takesMaxOverlap() {
        val sections = listOf(
            TomTomTrafficSection(0, 2, 1),
            TomTomTrafficSection(1, 3, 3),
        )
        val mag = edgeMagnitudesForTrafficSections(4, sections)
        assertEquals(3, mag.size)
        assertEquals(1, mag[0])
        assertEquals(3, mag[1])
        assertEquals(3, mag[2])
    }

    @Test
    fun buildCongestionFeatureCollection_mergesRunsAndColors() {
        val points = listOf(
            LatLng(10.0, 123.0),
            LatLng(10.1, 123.1),
            LatLng(10.2, 123.2),
            LatLng(10.3, 123.3),
        )
        val sections = listOf(TomTomTrafficSection(1, 3, 2))
        val json = buildCongestionFeatureCollectionJson(points, sections)
        assertTrue(json.contains("FeatureCollection"))
        assertTrue(json.contains("#F97316"))
        assertTrue(json.contains(ROUTE_COLOR))
        assertTrue(json.contains("congestionColor"))
    }

    @Test
    fun buildRemainingCongestion_usesFullRouteEdgeMagnitudes() {
        val full = listOf(
            LatLng(10.0, 123.0),
            LatLng(10.1, 123.1),
            LatLng(10.2, 123.2),
            LatLng(10.3, 123.3),
        )
        val sections = listOf(TomTomTrafficSection(0, 3, 3))
        val remaining = listOf(LatLng(10.15, 123.15), LatLng(10.2, 123.2), LatLng(10.3, 123.3))
        val json = buildRemainingCongestionFeatureCollectionJson(
            fullPoints = full,
            sections = sections,
            remainingPoints = remaining,
            remainingStartSegmentIndex = 1,
        )
        assertTrue(json.contains("#EF4444"))
        assertFalse(json.contains("\"features\":[]"))
    }

    @Test
    fun emptySections_singleCyanFeature() {
        val points = listOf(LatLng(10.0, 123.0), LatLng(10.1, 123.1))
        val json = buildCongestionFeatureCollectionJson(points, emptyList())
        assertTrue(json.contains(ROUTE_COLOR))
        assertEquals(1, Regex(""""type":"Feature"""").findAll(json).count())
    }
}