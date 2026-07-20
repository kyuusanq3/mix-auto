package com.kyuusanq3.mixauto.data.map

/**
 * Heuristic scorer for picking a "conventional" OSRM route among alternatives.
 *
 * Phase 1 only: selects among routes returned by public OSRM — it cannot block
 * mis-tagged alleys when OSRM returns a single geometry.
 */
data class ConventionalRouteStep(
    val streetName: String,
    val distanceMeters: Double,
    val maneuverType: String,
    val maneuverModifier: String,
)

data class ConventionalRouteCandidate(
    val durationSeconds: Double,
    val distanceMeters: Double,
    val steps: List<ConventionalRouteStep>,
)

object ConventionalRouteSelector {
    const val MAX_DURATION_FACTOR = 1.12
    const val SHORT_STEP_THRESHOLD_M = 45.0

    private val TURN_MANEUVER_TYPES = setOf(
        "turn",
        "merge",
        "fork",
        "roundabout",
        "end of road",
    )

    fun scoreRoute(candidate: ConventionalRouteCandidate): Double {
        val steps = candidate.steps
        if (steps.isEmpty()) return 0.0

        val totalKm = (candidate.distanceMeters / 1000.0).coerceAtLeast(0.3)
        val stepCount = steps.size.toDouble()

        val turns = steps.count { it.maneuverType in TURN_MANEUVER_TYPES }
        val shortSteps = steps.count { it.distanceMeters in 1.0..SHORT_STEP_THRESHOLD_M }
        val unnamed = steps.count { it.streetName.isBlank() }
        val leftTurns = steps.count {
            it.maneuverType in TURN_MANEUVER_TYPES && it.maneuverModifier.equals("left", ignoreCase = true)
        }

        val turnsPerKm = turns / totalKm
        val shortStepShare = shortSteps / stepCount
        val unnamedShare = unnamed / stepCount
        val leftTurnsPerKm = leftTurns / totalKm

        return turnsPerKm * 2.0 +
            shortStepShare * 40.0 +
            unnamedShare * 25.0 +
            leftTurnsPerKm * 0.5
    }

    /**
     * @return index of the selected candidate, or 0 if [candidates] is empty.
     */
    fun selectConventionalRoute(
        candidates: List<ConventionalRouteCandidate>,
        maxDurationFactor: Double = MAX_DURATION_FACTOR,
    ): Int {
        if (candidates.isEmpty()) return 0
        if (candidates.size == 1) return 0

        val baselineDuration = candidates.minOf { it.durationSeconds }
        val maxDuration = baselineDuration * maxDurationFactor

        val eligible = candidates.indices.filter { candidates[it].durationSeconds <= maxDuration }
        val pool = if (eligible.isNotEmpty()) eligible else candidates.indices

        return pool.minWith(
            compareBy<Int> { scoreRoute(candidates[it]) }
                .thenBy { candidates[it].durationSeconds },
        )
    }
}

internal fun RouteResult.toConventionalCandidate(): ConventionalRouteCandidate =
    ConventionalRouteCandidate(
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
        steps = steps.map { step ->
            ConventionalRouteStep(
                streetName = step.streetName,
                distanceMeters = step.distanceMeters,
                maneuverType = step.maneuverType,
                maneuverModifier = step.maneuverModifier,
            )
        },
    )
