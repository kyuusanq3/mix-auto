package com.kyuusanq3.mixauto.data.map

import android.location.Location
import com.kyuusanq3.mixauto.data.navigation.NavTickContext
import com.kyuusanq3.mixauto.data.navigation.NavigationVoiceController
import com.kyuusanq3.mixauto.domain.map.MapUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

/** Per-fix nav step advance, arrival, and off-route evaluation (injects OffRouteDetector). */
internal class NavigationProgressEvaluator(
    private val engineScope: CoroutineScope,
    private val uiState: () -> MapUiState,
    private val updateUiState: ((MapUiState) -> MapUiState) -> Unit,
    private val fullRouteSteps: () -> List<LegStep>,
    private val currentStepIndex: () -> Int,
    private val setCurrentStepIndex: (Int) -> Unit,
    private val destinationLatLng: () -> LatLng?,
    private val navigationArrivalTriggered: () -> Boolean,
    private val setNavigationArrivalTriggered: (Boolean) -> Unit,
    private val routeGeometryPoints: () -> List<LatLng>,
    private val offRouteDetector: OffRouteDetector,
    private val navigationVoice: () -> NavigationVoiceController?,
    private val isRouteOverviewActive: () -> Boolean,
    private val navigationCameraTransitionActive: () -> Boolean,
    private val updateNavigationZoomForDistance: (Float) -> Unit,
    private val startFreeDrive: () -> Unit,
) {
    fun evaluateStepAdvancement(currentLocation: Location) {
        if (navigationArrivalTriggered()) return

        val steps = fullRouteSteps()
        if (steps.isEmpty()) return

        val dest = destinationLatLng()
        if (dest != null) {
            val destLoc = Location("dest").apply {
                latitude = dest.latitude
                longitude = dest.longitude
            }
            if (currentLocation.distanceTo(destLoc) < ARRIVAL_THRESHOLD_M) {
                triggerArrival()
                return
            }
        }

        val nextIdx = currentStepIndex() + 1
        if (nextIdx >= steps.size) {
            triggerArrival()
            return
        }

        val nextStep = steps[nextIdx]
        val maneuverLoc = Location("maneuver").apply {
            latitude = nextStep.maneuverLat
            longitude = nextStep.maneuverLng
        }
        val distToManeuver = currentLocation.distanceTo(maneuverLoc)

        updateUiState {
            it.copy(distanceToNextTurn = NavigationRouteFetcher.formatDistance(distToManeuver.toDouble()))
        }
        updateNavigationZoomForDistance(distToManeuver)

        val speedMps = if (currentLocation.hasSpeed()) currentLocation.speed else 0f
        navigationVoice()?.onNavTick(
            NavTickContext(
                currentStepIndex = currentStepIndex(),
                steps = steps.map { it.toNavStepPhrase() },
                distToNextManeuverM = distToManeuver,
                speedMps = speedMps,
                isRouteOverviewActive = isRouteOverviewActive() ||
                    navigationCameraTransitionActive(),
                isRerouteInProgress = offRouteDetector.isRerouteInProgress,
            ),
        )

        if (distToManeuver < STEP_ADVANCE_THRESHOLD_M) {
            setCurrentStepIndex(nextIdx)
            offRouteDetector.offRouteGraceUntilMs = System.currentTimeMillis() + OFF_ROUTE_GRACE_AFTER_MANEUVER_MS
            val advanced = steps[currentStepIndex()]
            navigationVoice()?.onStepAdvanced(currentStepIndex(), advanced.toNavStepPhrase())
            updateUiState {
                it.copy(
                    turnInstruction = advanced.instruction,
                    distanceToNextTurn = advanced.distanceLabel,
                    streetName = advanced.streetName.ifBlank { "On route" },
                )
            }
        }

        checkOffRoute(currentLocation)
    }

    private fun checkOffRoute(currentLocation: Location) {
        offRouteDetector.checkOffRoute(
            currentLocation,
            routeGeometryPoints(),
            destinationLatLng(),
            navigationArrivalTriggered(),
        )
    }

    private fun triggerArrival() {
        if (navigationArrivalTriggered()) return
        setNavigationArrivalTriggered(true)
        updateUiState { it.copy(streetName = "Arrived at destination") }
        navigationVoice()?.onArrival {
            engineScope.launch {
                if (navigationArrivalTriggered()) {
                    startFreeDrive()
                }
            }
        } ?: engineScope.launch {
            delay(ARRIVAL_FREE_DRIVE_DELAY_MS)
            startFreeDrive()
        }
    }

    companion object {
        private const val STEP_ADVANCE_THRESHOLD_M = 25f
        private const val ARRIVAL_THRESHOLD_M = 15f
        private const val OFF_ROUTE_GRACE_AFTER_MANEUVER_MS = 8_000L
        private const val ARRIVAL_FREE_DRIVE_DELAY_MS = 5_000L
    }
}
