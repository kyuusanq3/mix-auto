package com.kyuusanq3.mixauto.data.map

import org.maplibre.android.geometry.LatLng

// Constants definitions

private const val TAG = "MapLibreEngine"

// Default location for fallbacks
private val DEFAULT_LOCATION = LatLng(12.8797, 121.7740) // Philippines

// Map zoom levels and camera settings
private const val FREE_DRIVE_TILT = 50.0
private const val NAV_TILT = 63.0
private const val TOP_DOWN_EXPLORE_ZOOM = 15.0
private const val DEFAULT_ZOOM_FALLBACK = 7.0

// Navigation and routing configuration
private const val LOCATION_ACQUIRE_TIMEOUT_MS = 20_000L
private const val LOCATION_POLL_INTERVAL_MS = 100L
private const val LOCATION_ENGINE_INTERVAL_MS = 1000L
private const val LOCATION_ENGINE_FASTEST_INTERVAL_MS = 500L
private const val NAV_CAMERA_DURATION_MS = 700

// Route configuration
private const val ROUTE_OVERVIEW_ANIMATION_MS = 800
private const val ROUTE_OVERVIEW_HOLD_MS = 10_000L
private const val REROUTE_THRESHOLD_M = 75f
private const val ROUTE_PROJECTION_SEARCH_RADIUS = 5
private const val ROUTE_PROGRESS_BACKTRACK_TOLERANCE_M = 20f
private const val ROUTE_PROGRESS_MAP_MIN_ADVANCE_M = 10f
private const val ROUTE_PROGRESS_HIGHWAY_SPEED_MPS = 20f
private const val ROUTE_PROGRESS_MAP_MIN_ADVANCE_HIGHWAY_M = 30f
private const val ROUTE_SIMPLIFY_TOLERANCE_M = 4f
private const val ROUTE_SIMPLIFY_MAX_POINTS = 256
private const val ROUTE_OVERVIEW_BOUNDS_EXPAND_FRACTION = 0.3
private const val ROUTE_OVERVIEW_MIN_BOUNDS_PAD_DEGREES = 0.01

// Route line styles and properties
private const val ROUTE_WIDTH = 14f
private const val ROUTE_COLOR = "#00E5FF"
private const val ROUTE_CASING_COLOR = "#000000"
private const val ROUTE_CASING_WIDTH = 20f
private const val ROUTE_TRAVELED_OPACITY = 0.6f

// Route layers and IDs
private const val ROUTE_TRAVELED_SOURCE_ID = "mix-route-traveled-source"
private const val ROUTE_REMAINING_SOURCE_ID = "mix-route-remaining-source"
private const val ROUTE_TRAVELED_CASING_LAYER_ID = "mix-route-traveled-casing-layer"
private const val ROUTE_TRAVELED_LAYER_ID = "mix-route-traveled-layer"
private const val ROUTE_REMAINING_CASING_LAYER_ID = "mix-route-remaining-casing-layer"
private const val ROUTE_REMAINING_LAYER_ID = "mix-route-remaining-layer"

// Route ID constants
private const val ROUTE_ID_OSRM_FASTEST = "osrm_fastest"
private const val ROUTE_ID_TOMTOM = "tomtom"
private const val ROUTE_ID_OSRM_ALT = "osrm_alt"

// Traffic-related constants
private const val TRAFFIC_LAYER_ID = "mix-traffic-layer"
private const val ROUTE_TOMTOM_COLOR = "#00E5FF"
private const val ROUTE_OSRM_ALT_COLOR = "#00E5FF"
private const val ROUTE_TOMTOM_WIDTH = 12f
private const val ROUTE_OSRM_ALT_WIDTH = 12f
private const val ROUTE_TOMTOM_OPACITY = 0.9f
private const val ROUTE_OSRM_ALT_OPACITY = 0.3f

// Navigation animation and performance
private const val DRIVING_ANIMATION_FPS = 30
private const val PREPARE_TIMEOUT_MS = 120_000L

// Traffic-related configuration
private const val NAVIGATION_VOICE_TTS_VOLUME_FACTOR = 1f

// Style and UI constants
private const val ATTRIBUTION_LEFT_MARGIN_DP = 98f
private const val MAP_LIBRE_LOGO_SIZE_DP = 54f