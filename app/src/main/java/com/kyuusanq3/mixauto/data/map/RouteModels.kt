package com.kyuusanq3.mixauto.data.map

import com.kyuusanq3.mixauto.data.navigation.NavStepPhrase
import com.kyuusanq3.mixauto.data.navigation.NavTtsPhrases
import org.maplibre.android.geometry.LatLng

internal data class LegStep(
    val maneuverLat: Double,
    val maneuverLng: Double,
    val instruction: String,
    val distanceLabel: String,
    val streetName: String,
    val distanceMeters: Double,
    val maneuverType: String,
    val maneuverModifier: String,
)

internal data class GeoBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
)

internal fun LegStep.toNavStepPhrase(): NavStepPhrase = NavStepPhrase(
    instruction = instruction,
    shortInstruction = NavTtsPhrases.shortManeuver(maneuverType, maneuverModifier),
    streetName = streetName,
    distanceMeters = distanceMeters,
    maneuverType = maneuverType,
)

/** Route rendering/progress constants shared with [RouteRenderer] and [MapLibreEngineImpl]. */
/** Urban parallel streets are often 20–40 m apart — 75 m never fired for those detours. */
internal const val REROUTE_THRESHOLD_M = 35f
internal const val ROUTE_PROJECTION_SEARCH_RADIUS = 20
internal const val ROUTE_PROGRESS_HIGHWAY_SPEED_MPS = 15f
internal const val ROUTE_PROGRESS_MAP_MIN_ADVANCE_HIGHWAY_M = 25f
internal const val ROUTE_PROGRESS_MAP_MIN_ADVANCE_M = 10f
internal const val ROUTE_PROGRESS_BACKTRACK_TOLERANCE_M = 5f
/** Only grey/advance the route line when GPS is this close; freeze while on a parallel street. */
internal const val ON_ROUTE_PROGRESS_MAX_M = 25f
/** When within this distance, force-sync progress (unstick after a detour / false snap). */
internal const val ON_ROUTE_RESYNC_M = 18f
/** OSRM must route from near the real GPS on reroute — stops snapping back onto the old road. */
internal const val REROUTE_ORIGIN_RADIUS_M = 25.0
internal const val REROUTE_ORIGIN_BEARING_RANGE_DEG = 45
internal const val ROUTE_TRAVELED_SOURCE_ID = "mix-route-traveled-source"
internal const val ROUTE_REMAINING_SOURCE_ID = "mix-route-remaining-source"
internal const val ROUTE_TRAVELED_CASING_LAYER_ID = "mix-route-traveled-casing-layer"
internal const val ROUTE_TRAVELED_LAYER_ID = "mix-route-traveled-layer"
internal const val ROUTE_REMAINING_CASING_LAYER_ID = "mix-route-remaining-casing-layer"
internal const val ROUTE_REMAINING_LAYER_ID = "mix-route-remaining-layer"
/** Legacy single-source IDs — removed on teardown for in-flight upgrades. */
internal const val ROUTE_SOURCE_ID = "mix-route-source"
internal const val ROUTE_CASING_LAYER_ID = "mix-route-casing-layer"
internal const val ROUTE_LAYER_ID = "mix-route-layer"
internal const val ROUTE_TOMTOM_SOURCE_ID = "mix-route-tomtom-source"
internal const val ROUTE_TOMTOM_LAYER_ID = "mix-route-tomtom-layer"
internal const val ROUTE_OSRM_ALT_SOURCE_ID = "mix-route-osrm-alt-source"
internal const val ROUTE_OSRM_ALT_LAYER_ID = "mix-route-osrm-alt-layer"
internal const val ROUTE_OSRM_ALT_CASING_LAYER_ID = "mix-route-osrm-alt-casing-layer"
internal const val ROUTE_OSRM_ALT_CALLOUT_SOURCE_ID = "mix-route-osrm-alt-callout-source"
internal const val ROUTE_OSRM_ALT_CALLOUT_LAYER_ID = "mix-route-osrm-alt-callout-layer"
internal const val ROUTE_OSRM_ALT_CALLOUT_ICON_ID = "mix-route-osrm-alt-callout-icon"
internal const val ROUTE_OSRM_PRIMARY_PREVIEW_SOURCE_ID = "mix-route-osrm-primary-preview-source"
internal const val ROUTE_OSRM_PRIMARY_PREVIEW_LAYER_ID = "mix-route-osrm-primary-preview-layer"
internal const val ROUTE_TOMTOM_COLOR = "#FFB300"
internal const val ROUTE_TOMTOM_WIDTH = 10f
internal const val ROUTE_TOMTOM_OPACITY = 0.7f
internal const val ROUTE_OSRM_ALT_COLOR = "#6B7280"
internal const val ROUTE_OSRM_ALT_WIDTH = 8f
internal const val ROUTE_OSRM_ALT_OPACITY = 0.75f
internal const val ROUTE_OSRM_ALT_CASING_WIDTH = 12f
internal const val ROUTE_CASING_COLOR = "#CC000000"
internal const val ROUTE_CASING_WIDTH = 18f
internal const val ROUTE_COLOR = "#00CBD6"
internal const val ROUTE_TRAVELED_COLOR = "#6B7280"
internal const val ROUTE_TRAVELED_OPACITY = 0.85f
internal const val ROUTE_WIDTH = 14f

/** Shared by [BearingEnricher]'s enrichment cache and [LocationTrackingController.isDuplicateLocationFix]. */
internal const val LOCATION_FIX_DEDUP_DIST_M = 2f

internal data class RouteResult(
    val geometryJson: String,
    val geometryPoints: List<LatLng>,
    val streetName: String,
    val instruction: String,
    val distance: String,
    val steps: List<LegStep> = emptyList(),
    val durationSeconds: Double = 0.0,
    val distanceMeters: Double = 0.0,
    val trafficDelaySeconds: Int = 0,
    val trafficSections: List<TomTomTrafficSection> = emptyList(),
)
