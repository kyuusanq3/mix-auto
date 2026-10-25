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

    /**
     * true  = legacy centered album-art tile + long-press mode picker (PLAIN/VINYL/VISUALIZER)
     * false = immersive layout (opaque album-art background, title/artist top, bar visualizer bottom;
     *         long-press opens Audio Settings; gesture help Info lives in Audio Settings)
     */
    const val USE_LEGACY_MEDIA_PLAYER_LAYOUT: Boolean = false

    /**
     * true  = show dashboard status strip (time/date/weather/traffic) and its Settings toggle
     * false = hide strip and toggle (default)
     */
    const val SHOW_STATUS_STRIP: Boolean = false

    /**
     * true  = show the dock music side control (DockMusicSideControl) in the shortcut bar
     * false = hide dock music side control (default; audio player accessed via other means)
     */
    const val SHOW_DOCK_MUSIC_SIDE_CONTROL: Boolean = false

    /**
     * true  = live-watch logcat for crash / Android Auto lines (DebugLogcatCapture; starts ~20 s
     *         after launch) so they are kept in Share/Save Debug Logs (default)
     * false = no background logcat process; share/save still attaches a one-shot logcat dump
     */
    const val CAPTURE_SYSTEM_LOGCAT: Boolean = true

    /**
     * true  = speak "Continue on {street} for {distance}" after nav start and each step advance
     * false = skip continue lines (default); ahead / turn / reroute / arrival cues unchanged
     */
    const val NAV_TTS_CONTINUE_CUES: Boolean = false

    /**
     * true  = speak the prepare cue (~12 s / 200 m before a turn) between ahead and turn
     * false = skip prepare where the ahead cue applies (default); segments too short for ahead
     *         (< NavTtsPhrases.SEGMENT_SKIP_AHEAD_M) keep prepare as their only early cue
     */
    const val NAV_TTS_PREPARE_CUES: Boolean = false
}