package com.kyuusanq3.mixauto.data.map

import android.location.Location
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

/** Grey dashed alternate shown during navigation when TomTom offers lighter traffic. */
internal const val ROUTE_LIGHTER_TRAFFIC_COLOR = "#6B7280"
internal const val ROUTE_LIGHTER_TRAFFIC_WIDTH = 8f
internal const val ROUTE_LIGHTER_TRAFFIC_OPACITY = 0.55f

internal fun buildLineStringFeatureJson(points: List<LatLng>): String {
    if (points.size < 2) {
        return """{"type":"FeatureCollection","features":[]}"""
    }
    val coords = points.joinToString(",") { point ->
        "[${point.longitude},${point.latitude}]"
    }
    return """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]},"properties":{}}"""
}

/** Pure decision for traveled-line updates — unit-tested without Android Location. */
internal data class RouteProgressDecision(
    val shouldUpdate: Boolean,
    val forceMapUpdate: Boolean,
)

internal fun decideRouteProgressUpdate(
    distToRouteM: Float,
    projectionDistanceFromStartM: Float,
    currentProgressDistanceM: Float,
): RouteProgressDecision {
    if (distToRouteM > ON_ROUTE_PROGRESS_MAX_M) {
        return RouteProgressDecision(shouldUpdate = false, forceMapUpdate = false)
    }
    val delta = projectionDistanceFromStartM - currentProgressDistanceM
    val allowBackwardResync = delta < -ROUTE_PROGRESS_BACKTRACK_TOLERANCE_M &&
        distToRouteM <= ON_ROUTE_RESYNC_M
    val isForward = delta > 0f
    if (!isForward && !allowBackwardResync) {
        return RouteProgressDecision(shouldUpdate = false, forceMapUpdate = false)
    }
    val forceMapUpdate = allowBackwardResync || delta >= ROUTE_PROGRESS_MAP_MIN_ADVANCE_M * 2f
    return RouteProgressDecision(shouldUpdate = true, forceMapUpdate = forceMapUpdate)
}

internal enum class AltRouteStyle {
    LIGHTER_TRAFFIC,
}

/**
 * Renders the active route line (traveled/remaining split), the multi-route-picker alternate
 * lines, and tracks how far along the route the driver has progressed.
 *
 * Extracted from [MapLibreEngineImpl]. Owns the route-progress tracking fields (segment index,
 * split point, distance-along-route, last-map-update distance, and the per-tick projection
 * cache) since they are only ever read/written by the methods below. `routeGeometryPoints`
 * itself stays on the engine (also read by route-overview bounds and off-route detection, not
 * yet extracted) and is passed in on every call. Layer anchoring against traffic/base layers is
 * injected as a constructor callback since it is shared with other overlays.
 */
internal class RouteRenderer(
    private val resolveAnchorLayerId: (Style) -> String?,
    private val ensurePuckAboveOverlays: () -> Unit,
) {
    private var routeProgressSegmentIndex = 0
    private var routeProgressSplitLat = 0.0
    private var routeProgressSplitLng = 0.0
    private var routeProgressDistanceM = 0f
    private var lastRouteProgressMapUpdateM = 0f
    private var cachedTickProjection: RouteProjection? = null
    private var cachedTickProjectionKey: Long = Long.MIN_VALUE
    private var trafficSections: List<TomTomTrafficSection> = emptyList()

    fun removeRouteLayers(style: Style) {
        runCatching { style.removeLayer(ROUTE_TRAVELED_LAYER_ID) }
        runCatching { style.removeLayer(ROUTE_TRAVELED_CASING_LAYER_ID) }
        runCatching { style.removeLayer(ROUTE_REMAINING_LAYER_ID) }
        runCatching { style.removeLayer(ROUTE_REMAINING_CASING_LAYER_ID) }
        runCatching { style.removeLayer(ROUTE_LAYER_ID) }
        runCatching { style.removeLayer(ROUTE_CASING_LAYER_ID) }
        runCatching { style.removeLayer(ROUTE_TOMTOM_LAYER_ID) }
        runCatching { style.removeLayer(ROUTE_OSRM_ALT_LAYER_ID) }
        runCatching { style.removeLayer(ROUTE_OSRM_PRIMARY_PREVIEW_LAYER_ID) }
        runCatching { style.removeSource(ROUTE_TRAVELED_SOURCE_ID) }
        runCatching { style.removeSource(ROUTE_REMAINING_SOURCE_ID) }
        runCatching { style.removeSource(ROUTE_SOURCE_ID) }
        runCatching { style.removeSource(ROUTE_TOMTOM_SOURCE_ID) }
        runCatching { style.removeSource(ROUTE_OSRM_ALT_SOURCE_ID) }
        runCatching { style.removeSource(ROUTE_OSRM_PRIMARY_PREVIEW_SOURCE_ID) }
    }

    fun removeAlternateRouteLayers(style: Style) {
        runCatching { style.removeLayer(ROUTE_TOMTOM_LAYER_ID) }
        runCatching { style.removeSource(ROUTE_TOMTOM_SOURCE_ID) }
        runCatching { style.removeSource(ROUTE_OSRM_ALT_SOURCE_ID) }
        runCatching { style.removeSource(ROUTE_OSRM_PRIMARY_PREVIEW_SOURCE_ID) }
    }

    fun showLighterTrafficAlternate(style: Style, points: List<LatLng>) {
        ensureRouteLayers(style)
        setAltRouteGeoJson(
            style,
            ROUTE_TOMTOM_SOURCE_ID,
            ROUTE_TOMTOM_LAYER_ID,
            points,
            AltRouteStyle.LIGHTER_TRAFFIC,
        )
        ensurePuckAboveOverlays()
    }

    fun clearLighterTrafficAlternate(style: Style) {
        clearAltLayer(style, ROUTE_TOMTOM_SOURCE_ID)
    }

    fun restackRouteLayersAbove(style: Style, anchorLayerId: String) {
        val layerIds = listOf(
            ROUTE_TRAVELED_CASING_LAYER_ID,
            ROUTE_TRAVELED_LAYER_ID,
            ROUTE_REMAINING_CASING_LAYER_ID,
            ROUTE_REMAINING_LAYER_ID,
        )
        if (layerIds.none { style.getLayer(it) != null }) return
        val layers = layerIds.mapNotNull { style.getLayer(it) }
        layers.forEach { style.removeLayer(it) }
        var aboveId = anchorLayerId
        for (layer in layers) {
            style.addLayerAbove(layer, aboveId)
            aboveId = layer.id
        }
    }

    fun ensureRouteLayers(style: Style) {
        val emptyJson = buildLineStringFeatureJson(emptyList())
        if (style.getSource(ROUTE_TRAVELED_SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(ROUTE_TRAVELED_SOURCE_ID, emptyJson))
        }
        if (style.getSource(ROUTE_REMAINING_SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(ROUTE_REMAINING_SOURCE_ID, emptyJson))
        }
        if (style.getLayer(ROUTE_TRAVELED_CASING_LAYER_ID) != null) return

        val traveledCasing = LineLayer(ROUTE_TRAVELED_CASING_LAYER_ID, ROUTE_TRAVELED_SOURCE_ID).withProperties(
            PropertyFactory.lineColor(ROUTE_CASING_COLOR),
            PropertyFactory.lineWidth(ROUTE_CASING_WIDTH),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineOpacity(1f),
        )
        val traveledLine = LineLayer(ROUTE_TRAVELED_LAYER_ID, ROUTE_TRAVELED_SOURCE_ID).withProperties(
            PropertyFactory.lineColor(ROUTE_TRAVELED_COLOR),
            PropertyFactory.lineWidth(ROUTE_WIDTH),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineOpacity(ROUTE_TRAVELED_OPACITY),
        )
        val remainingCasing = LineLayer(ROUTE_REMAINING_CASING_LAYER_ID, ROUTE_REMAINING_SOURCE_ID).withProperties(
            PropertyFactory.lineColor(ROUTE_CASING_COLOR),
            PropertyFactory.lineWidth(ROUTE_CASING_WIDTH),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineOpacity(1f),
        )
        val remainingLine = LineLayer(ROUTE_REMAINING_LAYER_ID, ROUTE_REMAINING_SOURCE_ID).withProperties(
            PropertyFactory.lineColor(
                Expression.coalesce(
                    Expression.toColor(Expression.get("congestionColor")),
                    Expression.toColor(Expression.literal(ROUTE_COLOR)),
                ),
            ),
            PropertyFactory.lineWidth(ROUTE_WIDTH),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineOpacity(0.9f),
        )
        val anchor = resolveAnchorLayerId(style)
        if (anchor != null) {
            style.addLayerAbove(traveledCasing, anchor)
            style.addLayerAbove(traveledLine, ROUTE_TRAVELED_CASING_LAYER_ID)
            style.addLayerAbove(remainingCasing, ROUTE_TRAVELED_LAYER_ID)
            style.addLayerAbove(remainingLine, ROUTE_REMAINING_CASING_LAYER_ID)
        } else {
            style.addLayer(traveledCasing)
            style.addLayer(traveledLine)
            style.addLayer(remainingCasing)
            style.addLayer(remainingLine)
        }
    }

    fun drawRoute(
        map: MapLibreMap,
        routeGeometryPoints: List<LatLng>,
        sections: List<TomTomTrafficSection> = emptyList(),
    ) {
        trafficSections = sections
        resetRouteProgress(routeGeometryPoints)
        map.getStyle { style ->
            ensureRouteLayers(style)
            val remainingJson = buildCongestionFeatureCollectionJson(routeGeometryPoints, trafficSections)
            val emptyJson = buildLineStringFeatureJson(emptyList())
            (style.getSource(ROUTE_TRAVELED_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(emptyJson)
            (style.getSource(ROUTE_REMAINING_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(remainingJson)
            ensurePuckAboveOverlays()
        }
    }

    fun clearTrafficSections() {
        trafficSections = emptyList()
    }

    fun resetRouteProgress(routeGeometryPoints: List<LatLng>) {
        routeProgressSegmentIndex = 0
        routeProgressDistanceM = 0f
        lastRouteProgressMapUpdateM = 0f
        if (routeGeometryPoints.isNotEmpty()) {
            routeProgressSplitLat = routeGeometryPoints[0].latitude
            routeProgressSplitLng = routeGeometryPoints[0].longitude
        } else {
            routeProgressSplitLat = 0.0
            routeProgressSplitLng = 0.0
        }
    }

    fun clearTickProjectionCache() {
        cachedTickProjection = null
        cachedTickProjectionKey = Long.MIN_VALUE
    }

    private fun applyRouteProgressToMap(map: MapLibreMap, routeGeometryPoints: List<LatLng>) {
        val points = routeGeometryPoints
        if (points.size < 2) return
        val traveled = buildTraveledRoutePoints(
            routeProgressDistanceM,
            points,
            routeProgressSegmentIndex,
            routeProgressSplitLat,
            routeProgressSplitLng,
        )
        val remaining = buildRemainingRoutePoints(
            points,
            routeProgressSegmentIndex,
            routeProgressSplitLat,
            routeProgressSplitLng,
        )
        map.getStyle { style ->
            if (!style.isFullyLoaded) return@getStyle
            (style.getSource(ROUTE_TRAVELED_SOURCE_ID) as? GeoJsonSource)
                ?.setGeoJson(buildLineStringFeatureJson(traveled))
            (style.getSource(ROUTE_REMAINING_SOURCE_ID) as? GeoJsonSource)
                ?.setGeoJson(
                    buildRemainingCongestionFeatureCollectionJson(
                        fullPoints = points,
                        sections = trafficSections,
                        remainingPoints = remaining,
                        remainingStartSegmentIndex = routeProgressSegmentIndex,
                    ),
                )
        }
    }

    fun projectOntoRoute(location: Location, routeGeometryPoints: List<LatLng>): RouteProjection? {
        val points = routeGeometryPoints
        if (points.size < 2) return null

        val localStart = (routeProgressSegmentIndex - ROUTE_PROJECTION_SEARCH_RADIUS).coerceAtLeast(0)
        val localEnd = (routeProgressSegmentIndex + ROUTE_PROJECTION_SEARCH_RADIUS)
            .coerceAtMost(points.size - 2)
        var projection = scanRouteSegments(location, points, localStart, localEnd)
        // Expand to full route when the local window misses (rejoin after parallel detour).
        if (projection.distToRouteM > ON_ROUTE_PROGRESS_MAX_M) {
            projection = scanRouteSegments(location, points, 0, points.size - 2)
        }
        return projection
    }

    fun projectionForLocation(location: Location, routeGeometryPoints: List<LatLng>): RouteProjection? {
        if (cachedTickProjectionKey == location.time && cachedTickProjection != null) {
            return cachedTickProjection
        }
        val projection = projectOntoRoute(location, routeGeometryPoints) ?: return null
        cachedTickProjection = projection
        cachedTickProjectionKey = location.time
        return projection
    }

    private fun routeProgressMapMinAdvanceM(speedMps: Float): Float {
        return if (speedMps >= ROUTE_PROGRESS_HIGHWAY_SPEED_MPS) {
            ROUTE_PROGRESS_MAP_MIN_ADVANCE_HIGHWAY_M
        } else {
            ROUTE_PROGRESS_MAP_MIN_ADVANCE_M
        }
    }

    /**
     * Advances the traveled/remaining split from a **raw** GPS fix (not road-snapped).
     *
     * - Far from the line ([ON_ROUTE_PROGRESS_MAX_M]): freeze — avoids ghost-greying while on a
     *   close parallel street.
     * - On the line: advance normally, allow large forward catch-up after a freeze, and allow
     *   on-route resync (including small backtracks) so a stuck split unsticks when the puck
     *   rejoins.
     */
    fun updateRouteProgress(location: Location, routeGeometryPoints: List<LatLng>, map: MapLibreMap?) {
        val projection = projectionForLocation(location, routeGeometryPoints) ?: return
        val decision = decideRouteProgressUpdate(
            distToRouteM = projection.distToRouteM,
            projectionDistanceFromStartM = projection.distanceFromStartM,
            currentProgressDistanceM = routeProgressDistanceM,
        )
        if (!decision.shouldUpdate) return

        routeProgressSegmentIndex = projection.segmentIndex
        routeProgressSplitLat = projection.splitLat
        routeProgressSplitLng = projection.splitLng
        routeProgressDistanceM = projection.distanceFromStartM
        val speedMps = if (location.hasSpeed()) location.speed else 0f
        val minAdvance = routeProgressMapMinAdvanceM(speedMps)
        if (!decision.forceMapUpdate &&
            routeProgressDistanceM - lastRouteProgressMapUpdateM < minAdvance
        ) {
            return
        }
        lastRouteProgressMapUpdateM = routeProgressDistanceM
        val activeMap = map ?: return
        applyRouteProgressToMap(activeMap, routeGeometryPoints)
    }

    /** Test/debug: meters along the active route that have been marked traveled. */
    internal fun debugProgressDistanceM(): Float = routeProgressDistanceM

    /** Test/debug: index of the segment containing the traveled/remaining split. */
    internal fun debugProgressSegmentIndex(): Int = routeProgressSegmentIndex

    private fun clearAltLayer(style: Style, sourceId: String) {
        (style.getSource(sourceId) as? GeoJsonSource)
            ?.setGeoJson(buildLineStringFeatureJson(emptyList()))
    }

    private fun setAltRouteGeoJson(
        style: Style,
        sourceId: String,
        layerId: String,
        points: List<LatLng>,
        altStyle: AltRouteStyle,
    ) {
        if (style.getSource(sourceId) == null) {
            style.addSource(GeoJsonSource(sourceId, buildLineStringFeatureJson(emptyList())))
        }
        if (style.getLayer(layerId) == null) {
            val layer = when (altStyle) {
                AltRouteStyle.LIGHTER_TRAFFIC -> LineLayer(layerId, sourceId).withProperties(
                    PropertyFactory.lineColor(ROUTE_LIGHTER_TRAFFIC_COLOR),
                    PropertyFactory.lineWidth(ROUTE_LIGHTER_TRAFFIC_WIDTH),
                    PropertyFactory.lineOpacity(ROUTE_LIGHTER_TRAFFIC_OPACITY),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                )
            }
            val anchor = resolveAnchorLayerId(style)
            if (anchor != null && style.getLayer(anchor) != null) {
                style.addLayerAbove(layer, anchor)
            } else {
                style.addLayer(layer)
            }
        }
        (style.getSource(sourceId) as? GeoJsonSource)
            ?.setGeoJson(buildLineStringFeatureJson(points))
    }
}
