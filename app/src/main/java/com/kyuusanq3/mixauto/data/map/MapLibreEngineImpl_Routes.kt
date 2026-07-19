package com.kyuusanq3.mixauto.data.map

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.maps.MapLibreMap

private fun updateLocationEngineInterval() {
    // Implementation for updating location engine interval
}

private fun clearNavTrafficPrefetchState() {
    navTrafficPrefetchJob?.cancel()
    navTrafficPrefetchJob = null
    pendingNavTrafficPhrase = null
    stashedParallelTomTomDelaySeconds = 0
    navStartTrafficEligible = false
}

fun startNavigation(
    origin: LatLng,
    lat: Double,
    lng: Double,
    isReroute: Boolean = false,
) {
    if (isReroute) {
        navigationVoice?.onRerouteStarted()
    }
    if (!isReroute) {
        poiRefreshJob?.cancel()
        poiRefreshJob = null
        clearForcedPreviewPoi()
        clearPoiLayer()
        clearCustomPin()
        clearRoutePreviewState()
        mapLibreMap?.getStyle { hideNativeVectorPoiLayers(it) }
    }
    updateLocationEngineInterval()
    _uiState.update {
        it.copy(
            isNavigating = true,
            streetName = if (isReroute) "Re-routing..." else "Calculating route...",
            selectedPoi = null,
            nearbyPois = emptyList(),
            isInTopDownView = if (isReroute) it.isInTopDownView else false,
        )
    }

    engineScope.launch {
        try {
            if (isReroute) {
                val route = withContext(Dispatchers.IO) {
                    fetchOsrmRoute(origin.longitude, origin.latitude, lng, lat)
                }
                if (route != null) {
                    applyActiveRoute(route)
                    destinationLatLng = LatLng(lat, lng)
                    navigationArrivalTriggered = false
                    offRouteCount = 0
                    _uiState.update {
                        it.copy(
                            isNavigating = true,
                            isRouteSelecting = false,
                            routeOptions = emptyList(),
                            selectedRouteId = null,
                            streetName = route.streetName,
                            turnInstruction = route.instruction,
                            distanceToNextTurn = route.distance,
                        )
                    }
                    isRerouteInProgress = false
                    enterNavigationCamera()
                } else {
                    isRerouteInProgress = false
                    _uiState.update { it.copy(isNavigating = false, streetName = "Route not found") }
                }
                return@launch
            }

            val osrmRoutesDeferred = async(Dispatchers.IO) {
                fetchOsrmRoutesWithAlternatives(origin.longitude, origin.latitude, lng, lat)
            }
            val tomtomDeferred = async(Dispatchers.IO) {
                if (tomTomApiKey.isBlank()) {
                    null
                } else {
                    TomTomRoutingClient.fetchRoute(
                        origin.latitude,
                        origin.longitude,
                        lat,
                        lng,
                        tomTomApiKey,
                    )
                }
            }
            val osrmRoutes = osrmRoutesDeferred.await()
            val tomtomRoute = tomtomDeferred.await()

            if (osrmRoutes.isEmpty() && tomtomRoute == null) {
                _uiState.update { it.copy(isNavigating = false, streetName = "Route not found") }
                return@launch
            }

            val candidates = buildRouteCandidates(osrmRoutes, tomtomRoute)
            destinationLatLng = LatLng(lat, lng)
            navigationArrivalTriggered = false
            offRouteCount = 0
            stashedParallelTomTomDelaySeconds = tomtomRoute?.trafficDelaySeconds ?: 0
            navStartTrafficEligible = true

            if (candidates.size <= 1) {
                val stored = candidates.firstOrNull()
                    ?: osrmRoutes.firstOrNull()?.let { osrmToStoredRoute(it, RouteProvider.OSRM_FASTEST, "osrm_fastest", "Fastest", "Shortest path") }
                if (stored == null) {
                    _uiState.update { it.copy(isNavigating = false, streetName = "Route not found") }
                    return@launch
                }
                applyActiveRoute(stored.result)
                _uiState.update {
                    it.copy(
                        isNavigating = true,
                        isRouteSelecting = false,
                        routeOptions = emptyList(),
                        selectedRouteId = null,
                        streetName = stored.result.streetName,
                        turnInstruction = stored.result.instruction,
                        distanceToNextTurn = stored.result.distance,
                    )
                }
                showRouteThenDive(origin, LatLng(lat, lng))
            } else {
                enterRouteSelection(origin, LatLng(lat, lng), candidates)
            }
        } catch (e: Exception) {
            isRerouteInProgress = false
            Log.w(TAG, "Route fetch failed: ${e.message}", e)
            _uiState.update { it.copy(isNavigating = false, streetName = "Routing failed") }
        }
    }
}

fun selectRouteOption(routeId: String) {
    if (!_uiState.value.isRouteSelecting) return
    if (routeResultsById[routeId] == null) return
    if (routeId == _uiState.value.selectedRouteId) {
        confirmRouteSelection()
        return
    }
    routeOverviewTimerStartMs = System.currentTimeMillis()
    _uiState.update {
        it.copy(
            selectedRouteId = routeId,
            routeOverviewProgress = 0f,
        )
    }
    updateSelectedRouteHighlight(routeId)
}

fun confirmRouteSelection() {
    if (!_uiState.value.isRouteSelecting) return
    val routeId = _uiState.value.selectedRouteId ?: return
    val stored = routeResultsById[routeId] ?: return

    routeOverviewJob?.cancel()
    routeOverviewJob = null
    routeOverviewOrigin = null
    routeOverviewDestination = null

    applyActiveRoute(stored.result)
    removeAlternateRouteLayers()
    routeResultsById.clear()
    selectionOrigin = null
    selectionDestination = null
    selectionBoundsPoints = emptyList()

    _uiState.update {
        it.copy(
            isRouteSelecting = false,
            routeOptions = emptyList(),
            selectedRouteId = null,
            routeOverviewProgress = 0f,
            streetName = stored.result.streetName,
            turnInstruction = stored.result.instruction,
            distanceToNextTurn = stored.result.distance,
        )
    }
    beginNavigationAfterRouteSelection()
}

private fun applyActiveRoute(route: RouteResult) {
    routeGeometryPoints = route.geometryPoints
    fullRouteSteps = route.steps
    currentStepIndex = 0
    drawRoute()
    prefetchNavTrafficHint(route)
}

private fun prefetchNavTrafficHint(route: RouteResult) {
    navTrafficPrefetchJob?.cancel()
    pendingNavTrafficPhrase = null
    if (!navStartTrafficEligible || tomTomApiKey.isBlank()) return

    val routeLatLng = route.geometryPoints.map { Pair(it.latitude, it.longitude) }
    val routeDelay = route.trafficDelaySeconds
    val parallelDelay = stashedParallelTomTomDelaySeconds
    val apiKey = tomTomApiKey

    navTrafficPrefetchJob = engineScope.launch {
        val phrase = withContext(Dispatchers.IO) {
            resolveNavStartTrafficPhrase(routeLatLng, apiKey, routeDelay, parallelDelay)
        }
        if (!phrase.isNullOrBlank()) {
            pendingNavTrafficPhrase = phrase
        }
    }
}

private fun resolveNavStartTrafficPhrase(
    routeLatLng: List<Pair<Double, Double>>,
    apiKey: String,
    routeDelaySeconds: Int,
    parallelDelaySeconds: Int,
): String? {
    val jam = TomTomTrafficClient.findJamOnRoute(routeLatLng, apiKey)
    jam?.let { found ->
        NavTtsPhrases.buildNavStartTrafficOnRoute(found.level, found.streetName)?.let { return it }
    }
    val delaySec = when {
        routeDelaySeconds >= 120 -> routeDelaySeconds
        parallelDelaySeconds >= 120 -> parallelDelaySeconds
        else -> 0
    }
    return NavTtsPhrases.buildNavStartTrafficDelay(delaySec)
}

private fun enterRouteSelection(origin: LatLng, destination: LatLng, candidates: List<StoredRoute>) {
    resetSmoothingMotion()
    drivingTilePrefetcher?.cancel()
    routeResultsById.clear()
    candidates.forEach { routeResultsById[it.id] = it }

    val defaultId = candidates.first().id
    selectionOrigin = origin
    selectionDestination = destination
    selectionBoundsPoints = candidates.flatMap { it.result.geometryPoints }.distinctBy {
        "${it.latitude},${it.longitude}"
    }

    _uiState.update {
        it.copy(
            isNavigating = true,
            isRouteSelecting = true,
            routeOptions = candidates.map { stored -> stored.toRouteOption() },
            selectedRouteId = defaultId,
            streetName = "Choose a route",
            turnInstruction = null,
            distanceToNextTurn = null,
            selectedPoi = null,
            nearbyPois = emptyList(),
            isInTopDownView = false,
            routeOverviewProgress = 0f,
        )
    }

    updateSelectedRouteHighlight(defaultId)
    startSelectionOverviewTimer(origin, destination)
}

private fun beginNavigationAfterRouteSelection() {
    if (mapLibreMap == null) return
    clearRoutePreviewState()
    _uiState.update { it.copy(isCameraDetached = false, isInTopDownView = false) }
    if (_uiState.value.isNavigating) {
        navigationCameraTransitionActive = true
        val dive = { enterNavigationCamera() }
        mapView?.post(dive) ?: dive()
    }
}

private fun enterNavigationCamera() {
    val map = mapLibreMap ?: run {
        navigationCameraTransitionActive = false
        return
    }
    val component = map.locationComponent
    val componentReady = component.isLocationComponentActivated &&
        component.isLocationComponentEnabled
    val target = lastKnownLocation ?: map.cameraPosition.target ?: run {
        navigationCameraTransitionActive = false
        return
    }

    navigationCameraTransitionActive = true

    val bearing = if (componentReady) {
        component.lastKnownLocation?.bearing?.toDouble() ?: map.cameraPosition.bearing
    } else {
        map.cameraPosition.bearing
    }

    Log.i(
        TAG,
        "Entering navigation camera zoom=$navZoom tilt=$NAV_TILT " +
            "(current=${map.cameraPosition.zoom}, target=${target.latitude},${target.longitude})",
    )

    applyDrivingViewportPadding(map)
    map.cancelTransitions()
    if (componentReady) {
        component.renderMode = RenderMode.GPS
        component.cameraMode = CameraMode.NONE
    }

    map.animateCamera(
        CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder()
                .target(target)
                .zoom(navZoom)
                .tilt(NAV_TILT)
                .bearing(bearing)
                .build(),
        ),
        NAV_CAMERA_DURATION_MS,
        object : MapLibreMap.CancelableCallback {
            override fun onFinish() {
                activateNavigationTracking(componentReady)
            }

            override fun onCancel() {
                activateNavigationTracking(componentReady)
            }
        },
    )
    _uiState.update { it.copy(isCameraDetached = false, isInTopDownView = false) }
}

private fun activateNavigationTracking(componentReady: Boolean) {
    navigationCameraTransitionActive = false
    if (!_uiState.value.isNavigating) return
    val map = mapLibreMap ?: return
    val component = map.locationComponent
    if (componentReady && component.isLocationComponentActivated && component.isLocationComponentEnabled) {
        val target = lastKnownLocation ?: map.cameraPosition.target
        if (target != null) {
            val bearing = component.lastKnownLocation?.bearing?.toDouble()
                ?: map.cameraPosition.bearing
            val current = map.cameraPosition
            // Layout padding updates can cancel the dive animation — snap tilt/zoom before follow.
            if (current.tilt < NAV_TILT - 5.0 || abs(current.zoom - navZoom) > 0.5) {
                map.moveCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(target)
                            .zoom(navZoom)
                            .tilt(NAV_TILT)
                            .bearing(bearing)
                            .build(),
                    ),
                )
            }
        }
        component.renderMode = RenderMode.GPS
        component.cameraMode = CameraMode.TRACKING_GPS
        component.setMaxAnimationFps(DRIVING_ANIMATION_FPS)
        applyDrivingTrackingPadding(map)
    }
    val trafficPhrase = if (navStartTrafficEligible) {
        navStartTrafficEligible = false
        pendingNavTrafficPhrase
    } else {
        null
    }
    pendingNavTrafficPhrase = null
    fullRouteSteps.firstOrNull()?.toNavStepPhrase()?.let { firstStep ->
        navigationVoice?.onNavigationDrivingStarted(firstStep, trafficPhrase)
    }
    updateLocationEngineInterval()
}

private fun showRouteThenDive(origin: LatLng, destination: LatLng) {
    if (mapLibreMap == null) return
    clearRouteOverviewState()
    clearRoutePreviewState()
    routeOverviewOrigin = origin
    routeOverviewDestination = destination
    resetRouteOverviewLayoutCache()
    _uiState.update { it.copy(isCameraDetached = false, isInTopDownView = false) }

    val animateToBounds = {
        fitRouteOverviewCamera(origin, destination, animate = true)
    }
    mapView?.post { animateToBounds() } ?: animateToBounds()

    routeOverviewJob = engineScope.launch {
        val startMs = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - startMs
            val progress = (elapsed / ROUTE_OVERVIEW_HOLD_MS.toFloat()).coerceIn(0f, 1f)
            _uiState.update { it.copy(routeOverviewProgress = progress) }
            if (elapsed >= ROUTE_OVERVIEW_HOLD_MS) break
            delay(50)
        }
        routeOverviewOrigin = null
        routeOverviewDestination = null
        _uiState.update { it.copy(routeOverviewProgress = 0f) }
        if (_uiState.value.isNavigating) {
            navigationCameraTransitionActive = true
            val dive = { enterNavigationCamera() }
            mapView?.post(dive) ?: dive()
        }
    }
}

private fun clearRoutePreviewState() {
    cancelPoiPreviewRetries()
    cancelTopDownViewportSync()
    pendingPoiPreviewTarget = null
    topDownExploreUserAdjusted = false
}

private fun isRouteOverviewActive(): Boolean = routeOverviewJob?.isActive == true

private fun startSelectionOverviewTimer(origin: LatLng, destination: LatLng) {
    clearRoutePreviewState()
    routeOverviewOrigin = origin
    routeOverviewDestination = destination
    resetRouteOverviewLayoutCache()
    _uiState.update { it.copy(isCameraDetached = false, isInTopDownView = false) }

    val animateToBounds = { fitRouteOverviewCamera(origin, destination, animate = true) }
    mapView?.post { animateToBounds() } ?: animateToBounds()

    routeOverviewTimerStartMs = System.currentTimeMillis()
    routeOverviewJob?.cancel()
    routeOverviewJob = engineScope.launch {
        while (isActive) {
            val elapsed = System.currentTimeMillis() - routeOverviewTimerStartMs
            val progress = (elapsed / ROUTE_OVERVIEW_HOLD_MS.toFloat()).coerceIn(0f, 1f)
            _uiState.update { it.copy(routeOverviewProgress = progress) }
            if (elapsed >= ROUTE_OVERVIEW_HOLD_MS) break
            delay(50)
        }
        if (_uiState.value.isRouteSelecting) {
            confirmRouteSelection()
        }
    }
}

private fun clearRouteOverviewState() {
    routeOverviewJob?.cancel()
    routeOverviewJob = null
    routeOverviewOrigin = null
    routeOverviewDestination = null
    lastRouteOverviewLayoutWidth = 0
    lastRouteOverviewLayoutHeight = 0
    navigationCameraTransitionActive = false
    drivingTilePrefetcher?.cancel()
    resetSmoothingMotion()
    _uiState.update { it.copy(routeOverviewProgress = 0f) }
}

private fun resetRouteOverviewLayoutCache() {
    lastRouteOverviewLayoutWidth = 0
    lastRouteOverviewLayoutHeight = 0
}

private fun fitRouteOverviewCamera(origin: LatLng, destination: LatLng, animate: Boolean) {
    val map = mapLibreMap ?: return
    val view = mapView ?: return
    if (view.width <= 0 || view.height <= 0) return

    val component = map.locationComponent
    if (component.isLocationComponentActivated && component.isLocationComponentEnabled) {
        component.cameraMode = CameraMode.NONE
    }
    lastAppliedTrackingPadding = null
    lastEngagedTrackingPadding = null
    map.cancelTransitions()
    applyMapPaddingImmediate(map, ViewportPadding(0, 0, 0, 0))

    val bounds = buildRouteOverviewBounds(origin, destination)
    val padding = computeRouteOverviewPadding(map)
    val boundsUpdate = CameraUpdateFactory.newLatLngBounds(
        bounds,
        padding.left,
        padding.top,
        padding.right,
        padding.bottom,
    )
    if (animate) {
        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(map.cameraPosition.target)
                    .zoom(map.cameraPosition.zoom)
                    .tilt(0.0)
                    .bearing(0.0)
                    .build(),
            ),
        )
        map.animateCamera(boundsUpdate, ROUTE_OVERVIEW_ANIMATION_MS)
    } else {
        map.moveCamera(boundsUpdate)
    }
}

private fun buildRouteOverviewBounds(origin: LatLng, destination: LatLng): LatLngBounds {
    val builder = LatLngBounds.Builder()
    if (selectionBoundsPoints.isNotEmpty()) {
        selectionBoundsPoints.forEach { builder.include(it) }
    } else if (routeGeometryPoints.isNotEmpty()) {
        routeGeometryPoints.forEach { builder.include(it) }
    } else {
        builder.include(origin)
    }
    builder.include(destination)
    lastKnownLocation?.let { builder.include(it) }
    return expandLatLngBounds(builder.build(), ROUTE_OVERVIEW_BOUNDS_EXPAND_FRACTION)
}

private fun expandLatLngBounds(bounds: LatLngBounds, fraction: Double): LatLngBounds {
    val latSpan = bounds.northEast.latitude - bounds.southWest.latitude
    val lngSpan = bounds.northEast.longitude - bounds.southWest.longitude
    val latPad = max(latSpan * fraction, ROUTE_OVERVIEW_MIN_BOUNDS_PAD_DEGREES)
    val lngPad = max(lngSpan * fraction, ROUTE_OVERVIEW_MIN_BOUNDS_PAD_DEGREES)
    return LatLngBounds.from(
        bounds.northEast.latitude + latPad,
        bounds.northEast.longitude + lngPad,
        bounds.southWest.latitude - latPad,
        bounds.southWest.longitude - lngPad,
    )
}

private fun computeRouteOverviewPadding(map: MapLibreMap): ViewportPadding {
    val density = appContext?.resources?.displayMetrics?.density ?: 2f
    val w = map.width
    val h = map.height
    if (w > 0 && h > 0) {
        return ViewportPadding(
            left = max((w * 0.14f).toInt(), (72 * density).toInt()),
            top = max((h * 0.24f).toInt(), (140 * density).toInt()),
            right = max((w * 0.10f).toInt(), (56 * density).toInt()),
            bottom = max((h * 0.08f).toInt(), (40 * density).toInt()),
        )
    }
    return ViewportPadding(
        left = (160 * density).toInt(),
        top = (220 * density).toInt(),
        right = (112 * density).toInt(),
        bottom = (64 * density).toInt(),
    )
}