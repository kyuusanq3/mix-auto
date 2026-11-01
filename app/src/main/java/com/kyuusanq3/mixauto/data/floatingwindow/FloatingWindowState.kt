package com.kyuusanq3.mixauto.data.floatingwindow

/**
 * Tiny volatile holder bridging the live map engine state to the overlay service.
 *
 * The service runs in the same process as [com.kyuusanq3.mixauto.MainActivity], but it cannot
 * directly observe the Compose [androidx.lifecycle.ViewModel] layer. [com.kyuusanq3.mixauto.ui.map.MapHostViewModel]
 * publishes the most recent GPS fix here on every tick; [FloatingWindowService] reads it when
 * refreshing weather / traffic.
 */
object FloatingWindowState {
    @Volatile
    var lastKnownLat: Double? = null

    @Volatile
    var lastKnownLng: Double? = null

    fun update(lat: Double?, lng: Double?) {
        if (lat != null && lng != null) {
            lastKnownLat = lat
            lastKnownLng = lng
        }
    }
}
