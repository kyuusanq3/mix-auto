package com.kyuusanq3.mixauto.data.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationZoomTest {

    @Test
    fun closeManeuverUsesMaxZoom() {
        assertEquals(18.5, NavigationZoom.targetZoomForManeuverDistance(50f, 18.5), 0.001)
    }

    @Test
    fun farManeuverUsesMinZoom() {
        assertEquals(NavigationZoom.DYNAMIC_ZOOM_MIN, NavigationZoom.targetZoomForManeuverDistance(2000f, 18.5), 0.001)
    }

    @Test
    fun midDistanceZoomsOutBetweenMinAndMax() {
        val zoom = NavigationZoom.targetZoomForManeuverDistance(400f, 18.5)
        assertTrue(zoom > NavigationZoom.DYNAMIC_ZOOM_MIN)
        assertTrue(zoom < 18.5)
    }

    @Test
    fun hysteresisBlocksSmallChanges() {
        assertFalse(NavigationZoom.shouldApplyZoomChange(17.0, 17.1))
        assertTrue(NavigationZoom.shouldApplyZoomChange(17.0, 17.3))
    }

    @Test
    fun speedBelowThresholdReturnsCeiling() {
        val ceiling = 18.5
        assertEquals(ceiling, NavigationZoom.targetZoomForSpeed(1.5, ceiling), 0.001)
    }

    @Test
    fun speedBetween2And8InterpolatesToMid() {
        val ceiling = 18.5
        val mid = (ceiling + NavigationZoom.DYNAMIC_ZOOM_MIN) / 2.0
        val speed = 5.0
        val result = NavigationZoom.targetZoomForSpeed(speed, ceiling)
        assertTrue(result > mid)
        assertTrue(result < ceiling)
    }

    @Test
    fun speedBetween8And12ReturnsMid() {
        val ceiling = 18.5
        val mid = (ceiling + NavigationZoom.DYNAMIC_ZOOM_MIN) / 2.0
        val speed = 10.0
        assertEquals(mid, NavigationZoom.targetZoomForSpeed(speed, ceiling), 0.001)
    }

    @Test
    fun speedBetween12And20InterpolatesToMin() {
        val ceiling = 18.5
        val mid = (ceiling + NavigationZoom.DYNAMIC_ZOOM_MIN) / 2.0
        val speed = 15.0
        val result = NavigationZoom.targetZoomForSpeed(speed, ceiling)
        assertTrue(result > NavigationZoom.DYNAMIC_ZOOM_MIN)
        assertTrue(result < mid)
    }

    @Test
    fun speedAboveThresholdReturnsMin() {
        assertEquals(NavigationZoom.DYNAMIC_ZOOM_MIN, NavigationZoom.targetZoomForSpeed(25.0, 18.5), 0.001)
    }

    @Test
    fun zoomAlwaysWithinBounds() {
        val ceiling = 18.5
        val speed = 10.0
        val result = NavigationZoom.targetZoomForSpeed(speed, ceiling)
        assertTrue(result >= NavigationZoom.DYNAMIC_ZOOM_MIN)
        assertTrue(result <= ceiling)
}
    }
