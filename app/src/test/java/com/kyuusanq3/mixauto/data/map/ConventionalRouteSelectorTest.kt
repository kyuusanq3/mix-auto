package com.kyuusanq3.mixauto.data.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConventionalRouteSelectorTest {

    @Test
    fun arterialRouteScoresLowerThanAlleyRoute() {
        val alley = alleyRoute()
        val arterial = arterialRoute()
        assertTrue(
            ConventionalRouteSelector.scoreRoute(alley) >
                ConventionalRouteSelector.scoreRoute(arterial),
        )
    }

    @Test
    fun selectPicksArterialWhenWithinDurationCap() {
        val alley = alleyRoute(durationSeconds = 600.0)
        val arterial = arterialRoute(durationSeconds = 650.0) // +8.3% vs alley baseline
        val index = ConventionalRouteSelector.selectConventionalRoute(listOf(alley, arterial))
        assertEquals(1, index)
    }

    @Test
    fun selectFallsBackToFastestWhenOnlyOneCandidate() {
        val alley = alleyRoute()
        val index = ConventionalRouteSelector.selectConventionalRoute(listOf(alley))
        assertEquals(0, index)
    }

    @Test
    fun selectFallsBackToFastestWhenArterialExceedsDurationCap() {
        val alley = alleyRoute(durationSeconds = 600.0)
        val arterial = arterialRoute(durationSeconds = 700.0) // +16.7% — over 12% cap
        val index = ConventionalRouteSelector.selectConventionalRoute(listOf(alley, arterial))
        assertEquals(0, index)
    }

    @Test
    fun selectFallsBackToFastestWhenNoEligibleRoutes() {
        val slow = arterialRoute(durationSeconds = 1000.0)
        val slower = arterialRoute(durationSeconds = 1200.0)
        val index = ConventionalRouteSelector.selectConventionalRoute(listOf(slow, slower))
        assertEquals(0, index)
    }

    private fun alleyRoute(durationSeconds: Double = 600.0): ConventionalRouteCandidate {
        val steps = buildList {
            repeat(20) { i ->
                add(
                    ConventionalRouteStep(
                        streetName = if (i % 3 == 0) "Alley $i" else "",
                        distanceMeters = 25.0,
                        maneuverType = if (i == 0) "depart" else "turn",
                        maneuverModifier = if (i % 2 == 0) "left" else "right",
                    ),
                )
            }
        }
        return ConventionalRouteCandidate(
            durationSeconds = durationSeconds,
            distanceMeters = 500.0,
            steps = steps,
        )
    }

    private fun arterialRoute(durationSeconds: Double = 650.0): ConventionalRouteCandidate {
        val steps = listOf(
            ConventionalRouteStep("Lacson Street", 1200.0, "depart", ""),
            ConventionalRouteStep("Burgos Avenue", 800.0, "turn", "right"),
            ConventionalRouteStep("Araneta Avenue", 1500.0, "turn", "left"),
            ConventionalRouteStep("Destination Road", 400.0, "arrive", ""),
        )
        return ConventionalRouteCandidate(
            durationSeconds = durationSeconds,
            distanceMeters = 3900.0,
            steps = steps,
        )
    }
}
