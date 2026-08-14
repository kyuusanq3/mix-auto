package com.kyuusanq3.mixauto.data.map

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.abs

private const val TAG = "ManeuverAlt"

internal data class StashedManeuverAlternate(
    val planned: ManeuverAlternateRoute,
    val route: RouteResult,
)

/**
 * Phase 2: stash per-turn OSRM forks computed at Navigate, draw the next-turn fork in grey,
 * and hand the full [RouteResult] back when the driver takes it.
 */
internal class ManeuverAlternateCoordinator(
    private val engineScope: CoroutineScope,
    private val mapLibreMap: () -> MapLibreMap?,
    private val routeRenderer: () -> RouteRenderer,
    private val getCurrentStepIndex: () -> Int,
    private val getDestinationLatLng: () -> LatLng?,
    private val fetchOsrmFromBranch: (Double, Double, Double, Double) -> List<RouteResult>,
) {
    private var prefetchJob: Job? = null
    private var stashed: List<StashedManeuverAlternate> = emptyList()
    private var visible: StashedManeuverAlternate? = null

    fun visibleGeometry(): List<LatLng> = visible?.planned?.geometryPoints ?: emptyList()

    fun visibleRoute(): RouteResult? = visible?.route

    fun prefetch(route: RouteResult) {
        prefetchJob?.cancel()
        clearVisibleLayer()
        visible = null
        stashed = emptyList()
        val dest = getDestinationLatLng() ?: return
        val indices = ManeuverAlternatePlanner.mainTurnManeuverIndices(
            route.steps,
            getCurrentStepIndex(),
            lookaheadStepCount = route.steps.size,
        )
        if (indices.isEmpty()) return
        prefetchJob = engineScope.launch {
            val found = withContext(Dispatchers.IO) {
                buildList {
                    for (idx in indices) {
                        if (!isActive) return@buildList
                        val step = route.steps.getOrNull(idx) ?: continue
                        val candidates = fetchOsrmFromBranch(
                            step.maneuverLat,
                            step.maneuverLng,
                            dest.latitude,
                            dest.longitude,
                        )
                        val picked = pickBranchRoute(candidates, route, idx) ?: continue
                        add(
                            StashedManeuverAlternate(
                                planned = ManeuverAlternateRoute(
                                    maneuverStepIndex = idx,
                                    branchLat = step.maneuverLat,
                                    branchLng = step.maneuverLng,
                                    geometryPoints = picked.geometryPoints,
                                    durationSeconds = picked.durationSeconds,
                                    distanceMeters = picked.distanceMeters,
                                ),
                                route = picked,
                            ),
                        )
                    }
                }
            }
            if (!isActive) return@launch
            stashed = found
            refreshVisible(getCurrentStepIndex())
        }
    }

    fun refreshVisible(currentStepIndex: Int) {
        val nextIdx = currentStepIndex + 1
        val plannedList = stashed.map { it.planned }
        val match = ManeuverAlternatePlanner.alternateForNextTurn(plannedList, nextIdx)
        val next = stashed.firstOrNull { it.planned.maneuverStepIndex == match?.maneuverStepIndex }
        if (next?.planned?.maneuverStepIndex == visible?.planned?.maneuverStepIndex &&
            next != null
        ) {
            return
        }
        visible = next
        val map = mapLibreMap() ?: return
        map.getStyle { style ->
            if (next == null || next.planned.geometryPoints.size < 2) {
                routeRenderer().clearManeuverAlternate(style)
            } else {
                routeRenderer().showManeuverAlternate(style, next.planned.geometryPoints)
                Log.i(TAG, "Showing maneuver alternate for step " + next.planned.maneuverStepIndex)
            }
        }
    }

    fun clear() {
        prefetchJob?.cancel()
        prefetchJob = null
        stashed = emptyList()
        visible = null
        clearVisibleLayer()
    }

    private fun clearVisibleLayer() {
        mapLibreMap()?.getStyle { style -> routeRenderer().clearManeuverAlternate(style) }
    }

    private fun pickBranchRoute(
        candidates: List<RouteResult>,
        original: RouteResult,
        fromStepIndex: Int,
    ): RouteResult? {
        val step = original.steps.getOrNull(fromStepIndex) ?: return null
        val remaining = ManeuverAlternatePlanner.remainingGeometryFromManeuver(
            original.geometryPoints,
            step.maneuverLat,
            step.maneuverLng,
        )
        val baseline = ManeuverAlternatePlanner.estimateRemainingDurationSeconds(
            original,
            fromStepIndex,
        )
        val delta = ManeuverAlternatePlanner.DURATION_DELTA_SECONDS
        val distinct = candidates.filter { cand ->
            cand.geometryPoints.size >= 2 &&
                remaining.size >= 2 &&
                !LighterTrafficHelper.routesAreSimilar(
                    remaining,
                    cand.geometryPoints,
                )
        }
        val windowed = distinct.filter { cand ->
            abs(cand.durationSeconds - baseline) <= delta
        }
        return windowed.minByOrNull { cand -> abs(cand.durationSeconds - baseline) }
    }
}