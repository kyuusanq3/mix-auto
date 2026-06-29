package com.kyuusanq3.mixauto.data.map

import android.content.Context
import org.maplibre.android.MapLibre

/** Ensures MapLibre native SDK is initialized before [OfflineManager] or [MapView]. */
internal object MapLibreAppBootstrap {
    @Volatile
    private var initialized = false

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                MapLibre.getInstance(context.applicationContext)
                initialized = true
            }
        }
    }
}
