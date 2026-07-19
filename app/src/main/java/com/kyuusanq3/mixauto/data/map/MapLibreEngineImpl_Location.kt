package com.kyuusanq3.mixauto.data.map

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

private fun hasLocationPermission(context: Context): Boolean {
    val fineGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    return fineGranted || coarseGranted
}

private fun readLastKnownLocation(context: Context): LatLng? {
    if (!hasLocationPermission(context)) {
        return null
    }

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return null

    val bestLocation = locationManager.allProviders
        .mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
        .maxByOrNull(Location::getTime)

    if (bestLocation != null) {
        Log.i(
            TAG,
            "readLastKnownLocation: ${bestLocation.latitude}, ${bestLocation.longitude} " +
                "from ${bestLocation.provider}",
        )
    }
    return bestLocation?.let { LatLng(it.latitude, it.longitude) }
}

fun resolveSearchOrigin(): Pair<Double, Double> {
    // This function is part of the interface
    val latLng = lastKnownLocation ?: run {
        appContext?.let { readLastKnownLocation(it) } ?: DEFAULT_LOCATION
    }
    return Pair(latLng.latitude, latLng.longitude)
}

fun hasReliableSearchOrigin(): Boolean {
    // This function is part of the interface
    return lastKnownLocation != null || 
           (appContext?.let { readLastKnownLocation(it) } != null)
}

fun refreshSearchOrigin() {
    // This function is part of the interface
    appContext?.let { ctx ->
        val location = readLastKnownLocation(ctx)
        if (location != null) {
            lastKnownLocation = location
        }
        // Update the UI state with new origin
    }
}

fun resolveInitialLocation(context: Context): ResolvedLocation {
    if (!hasLocationPermission(context)) {
        Log.d(TAG, "Location permission not granted; using Philippines fallback")
        return ResolvedLocation(
            latLng = DEFAULT_LOCATION,
            zoom = DEFAULT_ZOOM_FALLBACK,
            fromGps = false,
        )
    }

    val location = readLastKnownLocation(context)
    return if (location != null) {
        lastKnownLocation = location
        _uiState.update {
            it.copy(
                streetName = "Locating...",
                currentLat = location.latitude,
                currentLng = location.longitude,
            )
        }
        ResolvedLocation(
            latLng = location,
            zoom = freeDriveZoom,
            fromGps = true,
        )
    } else {
        Log.d(TAG, "No last known location; using Philippines fallback until GPS fix")
        ResolvedLocation(
            latLng = DEFAULT_LOCATION,
            zoom = DEFAULT_ZOOM_FALLBACK,
            fromGps = false,
        )
    }
}