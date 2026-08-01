package com.kyuusanq3.mixauto.data.map

import org.maplibre.android.maps.Style

/** Layer/source IDs and POI overlay constants shared across map collaborators. */
internal const val RASTER_BASE_LAYER_ID = "osm"
internal const val TRAFFIC_SOURCE_ID = "mix-traffic-source"
internal const val TRAFFIC_LAYER_ID = "mix-traffic-layer"

internal val VECTOR_POI_LAYER_IDS = arrayOf("poi_r1", "poi_r7", "poi_r20", "poi_transit")

internal const val POI_SOURCE_ID = "mix-poi-source"
internal const val POI_LAYER_ID = "mix-poi-layer"
internal const val POI_LABEL_LAYER_ID = "mix-poi-label-layer"

internal const val PREVIEW_POI_SOURCE_ID = "mix-preview-poi-source"
internal const val PREVIEW_POI_LAYER_ID = "mix-preview-poi-layer"
internal const val PREVIEW_POI_LABEL_LAYER_ID = "mix-preview-poi-label-layer"

internal const val CUSTOM_PIN_SOURCE_ID = "mix-custom-pin-source"
internal const val CUSTOM_PIN_LAYER_ID = "mix-custom-pin-layer"

internal const val SAVED_PLACES_SOURCE_ID = "mix-saved-source"
internal const val SAVED_PLACES_LAYER_ID = "mix-saved-layer"

internal const val EMPTY_POI_GEOJSON = """{"type":"FeatureCollection","features":[]}"""
internal const val EMPTY_CUSTOM_PIN_GEOJSON = """{"type":"FeatureCollection","features":[]}"""

internal const val MIN_POI_ZOOM = 13.0
internal const val MAX_POI_PINS = 100
internal const val POI_DEBOUNCE_MS = 400L
internal const val BBOX_PADDING_FACTOR = 1.5
internal const val PHOTON_MOVE_THRESHOLD_M = 300f
internal const val MAP_TAP_NEAREST_POI_MAX_M = 500f
internal const val MAP_PIN_ICON_HIT_RADIUS_DP = 20f
internal const val DEDUP_THRESHOLD_M = 50f
internal const val NEARBY_PIN_DEDUP_THRESHOLD_M = 50f
internal const val POI_PREVIEW_ZOOM = 15.5
internal const val MAX_SEARCH_RADIUS_M = 500_000f

internal fun resolveMapOverlayAnchorLayerId(style: Style): String? {
    return when {
        style.getLayer(TRAFFIC_LAYER_ID) != null -> TRAFFIC_LAYER_ID
        style.getLayer(RASTER_BASE_LAYER_ID) != null -> RASTER_BASE_LAYER_ID
        style.getLayer("poi_transit") != null -> "poi_transit"
        style.getLayer("road_motorway") != null -> "road_motorway"
        else -> null
    }
}
