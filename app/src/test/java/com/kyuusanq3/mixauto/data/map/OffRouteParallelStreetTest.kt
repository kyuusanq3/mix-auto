package com.kyuusanq3.mixauto.data.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OffRouteParallelStreetTest {

    @Test
    fun closeParallelDistanceCountsAsOffRoute() {
        // Urban parallel streets are often 20–40 m apart; old 75 m threshold never fired.
        assertTrue(44f > REROUTE_THRESHOLD_M)
        assertFalse(20f > REROUTE_THRESHOLD_M)
    }

    @Test
    fun routeProgressFreezesOnParallelStreet() {
        val decision = decideRouteProgressUpdate(
            distToRouteM = 44f,
            projectionDistanceFromStartM = 800f,
            currentProgressDistanceM = 200f,
        )
        assertFalse("Must freeze greying while on a parallel street", decision.shouldUpdate)
    }

    @Test
    fun routeProgressCatchesUpWhenRejoiningAhead() {
        val decision = decideRouteProgressUpdate(
            distToRouteM = 5f,
            projectionDistanceFromStartM = 800f,
            currentProgressDistanceM = 200f,
        )
        assertTrue(decision.shouldUpdate)
        assertTrue("Large catch-up should force a map refresh", decision.forceMapUpdate)
    }

    @Test
    fun routeProgressResyncsBackwardWhenStuckAheadOfPuck() {
        val decision = decideRouteProgressUpdate(
            distToRouteM = 8f,
            projectionDistanceFromStartM = 200f,
            currentProgressDistanceM = 800f,
        )
        assertTrue("On-route backtrack must unstick a falsely advanced split", decision.shouldUpdate)
        assertTrue(decision.forceMapUpdate)
    }

    @Test
    fun smallJitterOnRouteDoesNotBacktrack() {
        val decision = decideRouteProgressUpdate(
            distToRouteM = 5f,
            projectionDistanceFromStartM = 198f,
            currentProgressDistanceM = 200f,
        )
        assertFalse(decision.shouldUpdate)
    }

    @Test
    fun buildOsrmRerouteUrlIncludesRadiusesAndBearings() {
        val url = NavigationRouteFetcher.buildOsrmRouteUrl(
            lngA = 122.95,
            latA = 10.67,
            lngB = 122.96,
            latB = 10.68,
            alternatives = 3,
            originRadiusM = REROUTE_ORIGIN_RADIUS_M,
            originBearingDeg = 90f,
        )
        assertTrue(url.contains("radiuses=25;unlimited"))
        assertTrue(url.contains("bearings=90,$REROUTE_ORIGIN_BEARING_RANGE_DEG;"))
        assertTrue(url.contains("alternatives=3"))
    }

    @Test
    fun initialNavigateUrlOmitsRadiuses() {
        val url = NavigationRouteFetcher.buildOsrmRouteUrl(
            lngA = 122.95,
            latA = 10.67,
            lngB = 122.96,
            latB = 10.68,
            alternatives = 3,
            originRadiusM = null,
            originBearingDeg = null,
        )
        assertFalse(url.contains("radiuses="))
        assertFalse(url.contains("bearings="))
    }
}
