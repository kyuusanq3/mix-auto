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
}