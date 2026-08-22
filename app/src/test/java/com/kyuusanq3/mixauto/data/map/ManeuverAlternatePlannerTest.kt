package com.kyuusanq3.mixauto.data.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.geometry.LatLng

class ManeuverAlternatePlannerTest {

    @Test
    fun mainTurnIndicesSkipsDepartArriveAndNonTurns() {
        val steps = listOf(
            leg("depart", 200.0),
            leg("turn", 500.0),
            leg("new name", 300.0),
            leg("merge", 1500.0),
            leg("arrive", 0.0),
        )
        val indices = ManeuverAlternatePlanner.mainTurnManeuverIndices(steps, currentStepIndex = 0)
        assertEquals(listOf(1, 3), indices)
    }

    @Test
    fun mainTurnIndicesRespectsLookaheadFromCurrentStep() {
        val steps = listOf(
            leg("depart", 100.0),
            leg("turn", 2000.0),
            leg("turn", 2000.0),
            leg("turn", 2000.0),
            leg("turn", 2000.0),
            leg("turn", 2000.0),
            leg("arrive", 0.0),
        )
        val indices = ManeuverAlternatePlanner.mainTurnManeuverIndices(
            steps, currentStepIndex = 1, lookaheadStepCount = 2,
        )
        assertEquals(listOf(2, 3), indices)
    }

    @Test
    fun mainTurnIndicesDropsTurnsNearArrivalByMinRemainingDistance() {
        val steps = listOf(
            leg("depart", 100.0),
            leg("turn", 1000.0),
            leg("turn", 200.0),
            leg("arrive", 0.0),
        )
        val indices = ManeuverAlternatePlanner.mainTurnManeuverIndices(steps, currentStepIndex = 0)
        assertEquals(emptyList<Int>(), indices)
    }

    @Test
    fun estimateRemainingDurationScalesByDistanceRatio() {
        val route = RouteResult(
            geometryJson = "",
            geometryPoints = emptyList(),
            streetName = "",
            instruction = "",
            distance = "",
            steps = listOf(
                leg("depart", 1000.0),
                leg("turn", 2000.0),
                leg("arrive", 1000.0),
            ),
            durationSeconds = 600.0,
            distanceMeters = 4000.0,
        )
        val rem = ManeuverAlternatePlanner.estimateRemainingDurationSeconds(route, fromStepIndex = 1)
        assertEquals(450.0, rem, 0.001)
    }

    @Test
    fun filterKeepsAlternatesWithinPlusMinusFiveMinutes() {
        val baseline = 600.0
        val kept = ManeuverAlternatePlanner.filterAlternatesByDurationDelta(
            alternates = listOf(
                alt(590.0),
                alt(895.0),
                alt(901.0),
                alt(300.0),
                alt(900.0),
                alt(299.0),
            ),
            baselineDurationSeconds = baseline,
        )
        assertEquals(4, kept.size)
    }

    @Test
    fun filterUsesCustomDelta() {
        val kept = ManeuverAlternatePlanner.filterAlternatesByDurationDelta(
            alternates = listOf(alt(605.0), alt(615.0)),
            baselineDurationSeconds = 600.0,
            deltaSeconds = 10.0,
        )
        assertEquals(1, kept.size)
    }

    @Test
    fun remainingGeometryStartsAtNearestVertexToManeuver() {
        val pts = listOf(
            LatLng(0.0, 0.0),
            LatLng(0.0, 0.001),
            LatLng(0.0, 0.002),
            LatLng(0.0, 0.003),
        )
        val rem = ManeuverAlternatePlanner.remainingGeometryFromManeuver(pts, 0.0, 0.002)
        assertEquals(2, rem.size)
        assertEquals(0.002, rem[0].longitude, 0.0000001)
        assertEquals(emptyList<LatLng>(), ManeuverAlternatePlanner.remainingGeometryFromManeuver(emptyList(), 0.0, 0.0))
    }

    @Test
    fun alternateForNextTurnPicksExactUpcomingStep() {
        val alts = listOf(alt(600.0, step = 2), alt(610.0, step = 4))
        val picked = ManeuverAlternatePlanner.alternateForNextTurn(alts, nextStepIndex = 2)
        assertEquals(2, picked?.maneuverStepIndex)
        assertNull(ManeuverAlternatePlanner.alternateForNextTurn(alts, nextStepIndex = 3))
        assertNull(ManeuverAlternatePlanner.alternateForNextTurn(emptyList(), nextStepIndex = 2))
    }

    @Test
    fun shouldAdoptWhenOnAlternateAndOffPrimary() {
        assertTrue(
            ManeuverAlternatePlanner.shouldAdoptAlternate(
                distToPrimaryM = 40f,
                distToAlternateM = 12f,
            ),
        )
        assertFalse(
            ManeuverAlternatePlanner.shouldAdoptAlternate(
                distToPrimaryM = 10f,
                distToAlternateM = 12f,
            ),
        )
        assertFalse(
            ManeuverAlternatePlanner.shouldAdoptAlternate(
                distToPrimaryM = 40f,
                distToAlternateM = 30f,
            ),
        )
    }

    @Test
    fun etaCalloutLabelUsesSimilarOrMinutesFasterSlower() {
        assertEquals("Similar ETA", ManeuverAlternatePlanner.formatEtaCalloutLabel(0.0))
        assertEquals("Similar ETA", ManeuverAlternatePlanner.formatEtaCalloutLabel(59.0))
        assertEquals("Similar ETA", ManeuverAlternatePlanner.formatEtaCalloutLabel(-45.0))
        assertEquals("1 min slower", ManeuverAlternatePlanner.formatEtaCalloutLabel(60.0))
        assertEquals("2 min faster", ManeuverAlternatePlanner.formatEtaCalloutLabel(-90.0))
        assertEquals("3 min slower", ManeuverAlternatePlanner.formatEtaCalloutLabel(200.0))
    }

    @Test
    fun calloutAnchorSkipsSharedStartForLongFork() {
        val pts = listOf(
            LatLng(10.0, 123.0),
            LatLng(10.001, 123.0),
            LatLng(10.002, 123.0),
            LatLng(10.003, 123.0),
        )
        val anchor = ManeuverAlternatePlanner.calloutAnchorPoint(pts, alongRouteM = 50.0)
        assertTrue(anchor != null)
        assertTrue(anchor!!.latitude > pts[0].latitude)
    }

    private fun leg(type: String, dist: Double): LegStep = LegStep(
        maneuverLat = 0.0,
        maneuverLng = 0.0,
        instruction = "",
        distanceLabel = "",
        streetName = "",
        distanceMeters = dist,
        maneuverType = type,
        maneuverModifier = "",
    )

    private fun alt(duration: Double, step: Int = 1): ManeuverAlternateRoute = ManeuverAlternateRoute(
        maneuverStepIndex = step,
        branchLat = 0.0,
        branchLng = 0.0,
        geometryPoints = listOf(LatLng(0.0, 0.0)),
        durationSeconds = duration,
        distanceMeters = 1000.0,
    )
}