package com.kyuusanq3.mixauto.data.map

import android.content.Context
import android.util.Log
import org.maplibre.android.geometry.LatLng

internal data class ResolvedLocation(
    val latLng: LatLng,
    val zoom: Double,
    val fromGps: Boolean,
)

/** Cold-start location bootstrap before MapView style load. */
internal object InitialLocationResolver {
    private const val TAG = "MapLibreEngineImpl"
    private const val DEFAULT_ZOOM_FALLBACK = 6.0

    fun applyBootstrapLocation(
        context: Context,
        freeDriveZoom: Double,
        hasLocationPermission: (Context) -> Boolean,
        readLastKnownLocation: (Context) -> LatLng?,
        onGpsFix: (LatLng) -> Unit,
    ): ResolvedLocation {
        if (!hasLocationPermission(context)) {
            Log.d(TAG, "Location permission not granted; using Philippines fallback")
            return ResolvedLocation(
                latLng = SEARCH_DEFAULT_LOCATION,
                zoom = DEFAULT_ZOOM_FALLBACK,
                fromGps = false,
            )
        }

        val location = readLastKnownLocation(context)
        return if (location != null) {
            onGpsFix(location)
            ResolvedLocation(
                latLng = location,
                zoom = freeDriveZoom,
                fromGps = true,
            )
        } else {
            Log.d(TAG, "No last known location; using Philippines fallback until GPS fix")
            ResolvedLocation(
                latLng = SEARCH_DEFAULT_LOCATION,
                zoom = DEFAULT_ZOOM_FALLBACK,
                fromGps = false,
            )
        }
    }
}
