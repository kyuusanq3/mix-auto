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
}
