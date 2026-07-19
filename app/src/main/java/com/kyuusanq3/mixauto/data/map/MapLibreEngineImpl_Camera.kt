package com.kyuusanq3.mixauto.data.map

import android.util.Log
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.modes.CameraMode

fun startFreeDrive() {
    if (_uiState.value.isNavigating || _uiState.value.isInTopDownView) {
        return // Do nothing if already navigating or in top-down view
    }

    appContext?.let { ctx ->
        val location = lastKnownLocation ?: run {
            readLastKnownLocation(ctx) 
        }
        
        if (location != null) {
            // Snap to GPS position when starting free drive
            snapCameraToGpsIfNeeded(location)
        } else {
            // Fallback to default location if no location is available
            val defaultLocation = DEFAULT_LOCATION
            
            _uiState.update {
                it.copy(
                    streetName = "Free Drive",
                    isCameraDetached = false,
                    isInTopDownView = false,
                )
            }
            
            mapLibreMap?.moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(defaultLocation)
                        .tilt(FREE_DRIVE_TILT)
                        .zoom(freeDriveZoom)
                        .bearing(0.0)
                        .build(),
                ),
            )
        }
    }
}

fun recenterCamera() {
    val map = mapLibreMap ?: return
    lastKnownLocation?.let { location ->
        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(location)
                    .tilt(0.0)
                    .zoom(15.0)
                    .bearing(0.0)
                    .build(),
            ),
        )
        _uiState.update {
            it.copy(isCameraDetached = false, isInTopDownView = false)
        }
    }
}

fun enterTopDownView() {
    val map = mapLibreMap ?: return
    lastKnownLocation?.let { location ->
        // Enter CropFree mode - centered on puck with fixed zoom
        _uiState.update {
            it.copy(
                isInTopDownView = true,
                isCameraDetached = true,
            )
        }
        
        val component = map.locationComponent
        if (component.isLocationComponentActivated && component.isLocationComponentEnabled) {
            component.cameraMode = CameraMode.NONE
        }
        
        // Move to top-down view centered on the current GPS position
        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(location)
                    .tilt(TOP_DOWN_EXPLORE_ZOOM)
                    .zoom(TOP_DOWN_EXPLORE_ZOOM)
                    .bearing(0.0)
                    .build(),
            ),
        )
        // Apply map padding appropriate for this view
        applyMapPaddingImmediate(map, ViewportPadding(0, 0, 0, 0))
    }
}

fun focusOnLocation(lat: Double, lng: Double) {
    val map = mapLibreMap ?: return
    
    val target = LatLng(lat, lng)
    
    _uiState.update {
        it.copy(isCameraDetached = true)
    }
    
    // Move camera to the specified location
    map.moveCamera(
        CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder()
                .target(target)
                .tilt(0.0)  // Flat view for zoomed in focus
                .zoom(18.0)  // Adjust zoom level to focus on location
                .bearing(0.0)
                .build(),
        ),
    )
}

fun dismissSelectedPoi() {
    val map = mapLibreMap ?: return
    
    // Cancel preview if currently loading
    cancelPoiPreviewRetries()
    
    // Return to previous state or clear the selection
    _uiState.update {
        it.copy(
            selectedPoi = null,
            nearbyPois = emptyList(),
            isCameraDetached = false,
            isInTopDownView = false,
        )
    }
    
    // Clear preview layer
    clearPreviewLayer()
    
    // Clear the custom pin if there was one
    clearCustomPin()
    
    val component = map.locationComponent
    if (component.isLocationComponentActivated && component.isLocationComponentEnabled) {
        component.cameraMode = CameraMode.TRACKING_GPS
        applyDrivingTrackingPadding(map)
    }
}

private fun activateFreeDriveTrackingMode(map: MapLibreMap) {
    if (_uiState.value.isNavigating ||
        _uiState.value.isInTopDownView ||
        _uiState.value.isCameraDetached
    ) {
        return
    }

    val component = map.locationComponent
    if (!component.isLocationComponentActivated || !component.isLocationComponentEnabled) return

    val alreadyTracking = component.cameraMode == CameraMode.TRACKING_GPS &&
        component.renderMode == RenderMode.GPS
    if (alreadyTracking) {
        // Top-down / preview clears padding caches while camera stays in TRACKING_GPS.
        if (lastEngagedTrackingPadding == null) {
            applyDrivingTrackingPadding(map)
            forceLocationUpdateForImmediateRender(
                map,
                bypassThrottle = true,
                allowDuringSmoothing = true,
            )
        }
        return
    }

    component.renderMode = RenderMode.GPS
    component.cameraMode = CameraMode.TRACKING_GPS
    component.setMaxAnimationFps(DRIVING_ANIMATION_FPS)
    applyDrivingTrackingPadding(map)
}

private fun isFreeDriveZoomTooWide(map: MapLibreMap): Boolean =
    map.cameraPosition.zoom < freeDriveZoom - 0.5

private fun needsFreeDriveCameraSnap(map: MapLibreMap): Boolean =
    !hasSnappedCameraToGps || isFreeDriveZoomTooWide(map)

private fun snapCameraToGpsIfNeeded(latLng: LatLng) {
    if (_uiState.value.isInTopDownView) return
    lastKnownLocation = latLng
    val map = mapLibreMap ?: return
    if (!needsFreeDriveCameraSnap(map)) {
        activateFreeDriveTrackingMode(map)
        return
    }

    val component = map.locationComponent
    val componentReady = component.isLocationComponentActivated && component.isLocationComponentEnabled
    val bearing = if (componentReady) {
        component.lastKnownLocation?.bearing?.toDouble() ?: map.cameraPosition.bearing
    } else {
        map.cameraPosition.bearing
    }

    Log.i(
        TAG,
        "Snapping free-drive camera to ${latLng.latitude}, ${latLng.longitude} " +
            "zoom=$freeDriveZoom tilt=$FREE_DRIVE_TILT (current=${map.cameraPosition.zoom}, " +
            "target=${formatCameraTarget(map)})",
    )

    if (componentReady) {
        component.renderMode = RenderMode.GPS
        component.cameraMode = CameraMode.NONE
    }

    map.moveCamera(
        CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder()
                .target(latLng)
                .tilt(FREE_DRIVE_TILT)
                .zoom(freeDriveZoom)
                .bearing(bearing)
                .build(),
        ),
    )

    if (componentReady) {
        component.cameraMode = CameraMode.TRACKING_GPS
        applyDrivingTrackingPadding(map)
        forceLocationUpdateForImmediateRender(
            map,
            bypassThrottle = true,
            allowDuringSmoothing = true,
        )
    }

    hasSnappedCameraToGps = true
    _uiState.update {
        it.copy(streetName = "Free Drive", isCameraDetached = false, isInTopDownView = false)
    }
}

private fun formatCameraTarget(map: MapLibreMap): String {
    val target = map.cameraPosition.target
    return if (target != null) "${target.latitude},${target.longitude}" else "null"
}

fun navigateToCoordinates(lat: Double, lng: Double) {
    val ctx = appContext
    var origin = lastKnownLocation ?: ctx?.let { readLastKnownLocation(it) }

    if (origin == null && ctx != null && hasLocationPermission(ctx)) {
        resolveMapViewOrigin()?.let { mapOrigin ->
            Log.i(TAG, "Routing from map view at zoom ${mapLibreMap?.cameraPosition?.zoom}")
            lastKnownLocation = mapOrigin
            startNavigation(mapOrigin, lat, lng)
            return
        }

        _uiState.update { it.copy(streetName = "Acquiring location...") }
        beginLocationAcquisition(ctx)
        engineScope.launch {
            val deadline = System.currentTimeMillis() + LOCATION_ACQUIRE_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                val resolvedOrigin = lastKnownLocation ?: readLastKnownLocation(ctx)
                if (resolvedOrigin != null) {
                    lastKnownLocation = resolvedOrigin
                    startNavigation(resolvedOrigin, lat, lng)
                    return@launch
                }
                resolveMapViewOrigin()?.let { mapOrigin ->
                    Log.i(TAG, "GPS unavailable; routing from map view")
                    lastKnownLocation = mapOrigin
                    startNavigation(mapOrigin, lat, lng)
                    return@launch
                }
                delay(LOCATION_POLL_INTERVAL_MS)
            }
            Log.w(TAG, "Navigation aborted: no location after ${LOCATION_ACQUIRE_TIMEOUT_MS}ms")
            _uiState.update {
                it.copy(streetName = "Zoom map to your area, then retry")
            }
        }
        return
    }

    if (origin == null) {
        resolveMapViewOrigin()?.let { mapOrigin ->
            Log.i(TAG, "Routing from map view (no permission path)")
            lastKnownLocation = mapOrigin
            startNavigation(mapOrigin, lat, lng)
            return
        }
        Log.w(TAG, "No known location; cannot route")
        _uiState.update { it.copy(streetName = "Zoom map to your area") }
        return
    }

    val resolvedOrigin = origin
    lastKnownLocation = resolvedOrigin
    startNavigation(resolvedOrigin, lat, lng)
}

private fun resolveMapViewOrigin(): LatLng? {
    // This function should return the camera's current position as a default fallback when no GPS is available
    val map = mapLibreMap ?: return null
    val pos = map.cameraPosition
    
    // If zoom level is above 10 (zoomed in), use the center of the view as our origin 
    if (pos.zoom >= 10.0) {
        return pos.target
    }
    
    // Otherwise return default location or null
    return null
}