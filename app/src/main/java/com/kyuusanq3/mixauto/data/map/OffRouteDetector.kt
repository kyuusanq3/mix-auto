package com.kyuusanq3.mixauto.data.map

import android.location.Location
import android.util.Log
import org.maplibre.android.geometry.LatLng

private const val TAG = "OffRouteDetector"
private const val REROUTE_CONFIRM_COUNT = 4
private const val REROUTE_COOLDOWN_MS = 20_000L
/** Snap only when clearly on the painted line — 40 m glued the puck to parallel streets. */
private const val SNAP_TO_ROUTE_MAX_M = 18f
private const val SNAP_TO_ROUTE_RELEASE_M = 22f
private const val HIGH_SPEED_SNAP_BLEND_MPS = 15f

/**
 * Off-route detection/reroute-trigger and road-snap blending, extracted from
 * [MapLibreEngineImpl]. Owns the off-route confirmation counter, reroute cooldown, and the
 * hysteresis flag for road-snapping — all fields only ever touched by the methods below (plus a
 * handful of reset call sites on the engine, exposed via [reset] and the public [offRouteCount] /
 * [isRerouteInProgress] vars they also mutate directly on navigation start/end).
 *
 * [projectionForLocation] and [onReroute] are injected since route-projection lookup
 * ([RouteRenderer]) and starting the actual reroute (camera/UI-state orchestration) are owned
 * elsewhere on the engine.
 */
internal class OffRouteDetector(
    private val projectionForLocation: (Location) -> RouteProjection?,
    private val onReroute: (origin: Location, destLat: Double, destLng: Double) -> Unit,
    private val visibleManeuverAlternateGeometry: () -> List<LatLng> = { emptyList() },
    private val onAdoptManeuverAlternate: () -> Unit = {},
) {
    var offRouteCount: Int = 0
    var isRerouteInProgress: Boolean = false
    var offRouteGraceUntilMs: Long = 0L
    private var rerouteCooldownUntilMs: Long = 0L
    private var routeSnapEngaged: Boolean = false

    fun reset() {
        offRouteCount = 0
        isRerouteInProgress = false
        routeSnapEngaged = false
    }

    fun checkOffRoute(
        currentLocation: Location,
        routeGeometryPoints: List<LatLng>,
        destinationLatLng: LatLng?,
        navigationArrivalTriggered: Boolean,
    ) {
        if (navigationArrivalTriggered) return
        if (routeGeometryPoints.size < 2) return
        if (System.currentTimeMillis() < rerouteCooldownUntilMs) return
        if (System.currentTimeMillis() < offRouteGraceUntilMs) return
        if (isRerouteInProgress) return

        val distToRoute = projectionForLocation(currentLocation)?.distToRouteM
            ?: distanceToRouteMeters(currentLocation, routeGeometryPoints)
        val altPts = visibleManeuverAlternateGeometry()
        if (altPts.size >= 2) {
            val distAlt = distanceToRouteMeters(currentLocation, altPts)
            if (ManeuverAlternatePlanner.shouldAdoptAlternate(distToRoute, distAlt)) {
                offRouteCount = 0
                Log.i(TAG, "Adopting maneuver alternate")
                onAdoptManeuverAlternate()
                return
            }
            if (distAlt <= ON_ROUTE_PROGRESS_MAX_M) {
                offRouteCount = 0
                return
            }
        }
        if (distToRoute > REROUTE_THRESHOLD_M) {
            offRouteCount++
            Log.i(TAG, "Off route: ${distToRoute.toInt()}m from path (count=$offRouteCount)")
        } else {
            offRouteCount = 0
            return
        }

        if (offRouteCount < REROUTE_CONFIRM_COUNT) return

        val dest = destinationLatLng ?: return
        offRouteCount = 0
        isRerouteInProgress = true
        rerouteCooldownUntilMs = System.currentTimeMillis() + REROUTE_COOLDOWN_MS
        Log.i(TAG, "Re-routing from alternate path (${distToRoute.toInt()}m off)")
        onReroute(currentLocation, dest.latitude, dest.longitude)
    }

    private fun distanceToRouteMeters(location: Location, routePoints: List<LatLng>): Float {
        if (routePoints.isEmpty()) return Float.MAX_VALUE
        if (routePoints.size == 1) {
            val results = FloatArray(1)
            Location.distanceBetween(
                location.latitude,
                location.longitude,
                routePoints[0].latitude,
                routePoints[0].longitude,
                results,
            )
            return results[0]
        }

        var minDist = Float.MAX_VALUE
        for (i in 0 until routePoints.size - 1) {
            val segmentDist = distanceToSegmentMeters(
                location,
                routePoints[i],
                routePoints[i + 1],
            )
            if (segmentDist < minDist) {
                minDist = segmentDist
            }
        }
        return minDist
    }

    /**
     * When navigating, snaps [location] onto the nearest route segment if the GPS fix is within
     * [SNAP_TO_ROUTE_MAX_M] metres of the route. Preserves all other location fields (bearing,
     * speed, accuracy) so the HUD and dead-reckoning remain unaffected.
     *
     * Returns null when there is no active route or the fix is too far away (off-route territory —
     * let [checkOffRoute] handle that case with the original coordinates).
     */
    fun snapLocationToRoute(location: Location): Location? {
        val projection = projectionForLocation(location) ?: run {
            routeSnapEngaged = false
            return null
        }
        val releaseThreshold = if (routeSnapEngaged) {
            SNAP_TO_ROUTE_RELEASE_M
        } else {
            SNAP_TO_ROUTE_MAX_M
        }
        if (projection.distToRouteM > releaseThreshold) {
            routeSnapEngaged = false
            return null
        }
        routeSnapEngaged = true
        val speed = if (location.hasSpeed()) location.speed else 0f
        if (speed < HIGH_SPEED_SNAP_BLEND_MPS) {
            return Location(location).apply {
                latitude = projection.splitLat
                longitude = projection.splitLng
            }
        }
        val blend = 0.7f
        return Location(location).apply {
            latitude = location.latitude * (1 - blend) + projection.splitLat * blend
            longitude = location.longitude * (1 - blend) + projection.splitLng * blend
        }
    }
}
