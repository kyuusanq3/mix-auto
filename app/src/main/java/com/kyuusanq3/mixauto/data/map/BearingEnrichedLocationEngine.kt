package com.kyuusanq3.mixauto.data.map

import android.app.PendingIntent
import android.location.Location
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap
import org.maplibre.android.location.engine.LocationEngine
import org.maplibre.android.location.engine.LocationEngineCallback
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.engine.LocationEngineResult

/**
 * Feeds bearing-smoothed fixes into MapLibre's LocationComponent so the puck and
 * TRACKING_GPS camera don't spin on noisy compass/GPS headings while parked.
 * App HUD/nav logic listens via [onRawFix] on the same delegate subscription.
 */
internal class BearingEnrichedLocationEngine(
    private val delegate: LocationEngine,
    private val enrich: (Location) -> Location,
    private val onRawFix: (Location) -> Unit = {},
) : LocationEngine {

    private val callbackMap =
        ConcurrentHashMap<LocationEngineCallback<LocationEngineResult>, LocationEngineCallback<LocationEngineResult>>()

    private fun wrapCallback(
        callback: LocationEngineCallback<LocationEngineResult>,
    ): LocationEngineCallback<LocationEngineResult> {
        val wrapped = object : LocationEngineCallback<LocationEngineResult> {
            override fun onSuccess(result: LocationEngineResult) {
                val raw = result.lastLocation
                if (raw == null) {
                    callback.onSuccess(result)
                    return
                }
                onRawFix(raw)
                val enriched = enrich(raw)
                callback.onSuccess(LocationEngineResult.create(enriched))
            }

            override fun onFailure(exception: Exception) {
                callback.onFailure(exception)
            }
        }
        callbackMap[callback] = wrapped
        return wrapped
    }

    override fun getLastLocation(callback: LocationEngineCallback<LocationEngineResult>) {
        delegate.getLastLocation(wrapCallback(callback))
    }

    override fun requestLocationUpdates(
        request: LocationEngineRequest,
        callback: LocationEngineCallback<LocationEngineResult>,
        looper: Looper?,
    ) {
        delegate.requestLocationUpdates(request, wrapCallback(callback), looper)
    }

    override fun requestLocationUpdates(
        request: LocationEngineRequest,
        pendingIntent: PendingIntent,
    ) {
        delegate.requestLocationUpdates(request, pendingIntent)
    }

    override fun removeLocationUpdates(callback: LocationEngineCallback<LocationEngineResult>) {
        val wrapped = callbackMap.remove(callback)
        if (wrapped != null) {
            delegate.removeLocationUpdates(wrapped)
        }
    }

    override fun removeLocationUpdates(pendingIntent: PendingIntent) {
        delegate.removeLocationUpdates(pendingIntent)
    }
}
