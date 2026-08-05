package com.kyuusanq3.mixauto.ui.settings

/**
 * HARDCODED developer overrides — compile-time only (not SharedPreferences / Map Settings UI).
 *
 * Agents: add new developer toggles HERE. Grep `DeveloperSettings`. Do not invent parallel flag files.
 */
object DeveloperSettings {
    /**
     * true  = manual driving zoom (show Map Settings Zoom slider; no free-drive speed curve)
     * false = dynamic free-drive zoom (hide slider; NavigationZoom.targetZoomForSpeed)
     */
    const val MANUAL_DRIVING_ZOOM: Boolean = false

    /**
     * true  = append poiSource next to distance in destination-search rows (debug)
     * false = distance only (default)
     */
    const val SHOW_POI_SOURCE: Boolean = false

    /**
     * true  = drop Overture rows with confidence below [MIN_POI_CONFIDENCE] (default)
     * false = return all Overture rows regardless of confidence
     */
    const val FILTER_LOW_CONFIDENCE_POIS: Boolean = true

    /** Minimum Overture places.confidence (0..1) when [FILTER_LOW_CONFIDENCE_POIS] is true. */
    const val MIN_POI_CONFIDENCE: Float = 0.5f

    /**
     * Overture rows with confidence below this ceiling (or without a street address)
     * show the approximate-location icon in search/detail UI.
     */
    const val APPROXIMATE_POI_CONFIDENCE_CEILING: Float = 0.7f
}