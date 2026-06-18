package com.kyuusanq3.mixauto.data.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.domain.map.MapUiState
import com.kyuusanq3.mixauto.domain.map.PlaceResult
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.engine.LocationEngine
import org.maplibre.android.location.engine.LocationEngineCallback
import org.maplibre.android.location.engine.LocationEngineProxy
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.engine.LocationEngineResult
import org.maplibre.android.location.engine.MapLibreFusedLocationEngineImpl
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

private data class LegStep(
    val maneuverLat: Double,
    val maneuverLng: Double,
    val instruction: String,
    val distanceLabel: String,
    val streetName: String,
)

private data class RouteResult(
    val geometryJson: String,
    val streetName: String,
    val instruction: String,
    val distance: String,
    val steps: List<LegStep> = emptyList(),
)

private data class ResolvedLocation(
    val latLng: LatLng,
    val zoom: Double,
    val fromGps: Boolean,
)

class MapLibreEngineImpl : CarMapEngine {

    private val _uiState = MutableStateFlow(MapUiState())
    override val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var mapView: MapView? = null
    private var mapLibreMap: org.maplibre.android.maps.MapLibreMap? = null
    private var appContext: Context? = null
    private var mapLibreInitialized = false
    private var lastKnownLocation: LatLng? = null
    private var fullRouteSteps: List<LegStep> = emptyList()
    private var currentStepIndex: Int = 0
    private var destinationLatLng: LatLng? = null
    private var navigationArrivalTriggered = false
    private var hasSnappedCameraToGps = false
    private var locationEngine: LocationEngine? = null
    private var locationEngineCallback: LocationEngineCallback<LocationEngineResult>? = null
    private var freshLocationListener: LocationListener? = null
    private var listenersRegistered = false
    private var pendingLocationActivation = false
    private var pendingLocationFix: Location? = null
    private var locationPollJob: Job? = null
    private var locationRetryJob: Job? = null

    override fun createMapView(context: Context): View {
        mapView?.let { existing ->
            (existing.parent as? ViewGroup)?.removeView(existing)
            return existing
        }

        if (!mapLibreInitialized) {
            MapLibre.getInstance(context.applicationContext)
            mapLibreInitialized = true
        }

        appContext = context.applicationContext
        resolveInitialLocation(context)

        return MapView(context).also { view ->
            view.onCreate(null)
            view.onStart()
            view.onResume()
            view.getMapAsync { map ->
                mapLibreMap = map
                map.setStyle(Style.Builder().fromJson(OSM_STYLE_JSON)) { style ->
                    activateLocationTracking(map, style)
                    if (pendingLocationActivation && hasLocationPermission(context)) {
                        beginLocationAcquisition(context)
                        pendingLocationActivation = false
                    }
                    startFreeDrive()
                }
            }
            mapView = view
        }
    }

    override fun onStart() {
        mapView?.onStart()
    }

    override fun onResume() {
        mapView?.onResume()
        appContext?.let { context ->
            if (hasLocationPermission(context)) {
                refreshLocationOnly(context)
            }
        }
    }

    override fun onPause() {
        mapView?.onPause()
    }

    override fun onStop() {
        mapView?.onStop()
    }

    override fun onDestroy() {
        locationPollJob?.cancel()
        locationPollJob = null
        locationRetryJob?.cancel()
        locationRetryJob = null
        pendingLocationFix = null
        engineScope.cancel()
        removeFreshLocationListener()
        listenersRegistered = false
        locationEngineCallback?.let { callback ->
            locationEngine?.removeLocationUpdates(callback)
        }
        locationEngineCallback = null
        locationEngine = null
        mapView?.onDestroy()
        mapView = null
        mapLibreMap = null
        hasSnappedCameraToGps = false
        appContext = null
    }

    override suspend fun searchDestination(query: String): List<PlaceResult> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL(
                "https://nominatim.openstreetmap.org/search" +
                    "?q=$encoded&format=json&limit=5&addressdetails=0",
            )
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "MixAutoCarLauncher/1.0")
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            try {
                if (connection.responseCode !in 200..299) {
                    Log.w(TAG, "Nominatim HTTP error: ${connection.responseCode}")
                    return@withContext emptyList()
                }
                val body = connection.inputStream.bufferedReader().readText()
                parseNominatimResponse(body)
            } catch (e: Exception) {
                Log.w(TAG, "Nominatim search failed: ${e.message}")
                emptyList()
            } finally {
                connection.disconnect()
            }
        }

    private fun parseNominatimResponse(json: String): List<PlaceResult> {
        val array = JSONArray(json)
        return (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            PlaceResult(
                displayName = obj.getString("display_name"),
                lat = obj.getDouble("lat"),
                lng = obj.getDouble("lon"),
            )
        }
    }

    override fun retryLocationActivation() {
        if (appContext == null) {
            pendingLocationActivation = true
            return
        }
        val ctx = appContext ?: return
        if (!hasLocationPermission(ctx)) return

        refreshLocationOnly(ctx)

        val map = mapLibreMap
        if (map != null) {
            map.getStyle { style ->
                activateLocationTracking(map, style)
                pendingLocationActivation = false
            }
        } else {
            pendingLocationActivation = true
            mapView?.getMapAsync { loadedMap ->
                loadedMap.getStyle { style ->
                    activateLocationTracking(loadedMap, style)
                    pendingLocationActivation = false
                }
            }
        }
    }

    override fun startFreeDrive() {
        fullRouteSteps = emptyList()
        currentStepIndex = 0
        destinationLatLng = null
        navigationArrivalTriggered = false
        hasSnappedCameraToGps = false

        _uiState.value = MapUiState(
            isNavigating = false,
            streetName = "Free Drive",
        )

        val map = mapLibreMap
        if (map != null) {
            applyFreeDriveToMap(map)
        } else {
            mapView?.getMapAsync { loadedMap -> applyFreeDriveToMap(loadedMap) }
        }
    }

    private fun applyFreeDriveToMap(map: MapLibreMap) {
        map.getStyle { style ->
            runCatching { style.removeLayer(ROUTE_LAYER_ID) }
            runCatching { style.removeSource(ROUTE_SOURCE_ID) }

            val target = resolveFreeDriveTarget(map)
            if (target != null) {
                snapCameraToGpsIfNeeded(target)
            } else if (isFreeDriveZoomTooWide(map)) {
                activateFreeDriveTrackingMode(map)
            }
        }
    }

    private fun isFreeDriveZoomTooWide(map: MapLibreMap): Boolean =
        map.cameraPosition.zoom < FREE_DRIVE_ZOOM - 0.5

    private fun needsFreeDriveCameraSnap(map: MapLibreMap): Boolean =
        !hasSnappedCameraToGps || isFreeDriveZoomTooWide(map)

    private fun formatCameraTarget(map: MapLibreMap): String {
        val target = map.cameraPosition.target
        return if (target != null) "${target.latitude},${target.longitude}" else "null"
    }

    private fun resolveFreeDriveTarget(map: MapLibreMap): LatLng? {
        lastKnownLocation?.let { return it }
        val component = map.locationComponent
        if (component.isLocationComponentActivated) {
            component.lastKnownLocation?.let { return LatLng(it.latitude, it.longitude) }
        }
        appContext?.let { readLastKnownLocation(it) }?.let { return it }
        return null
    }

    private fun activateFreeDriveTrackingMode(map: MapLibreMap) {
        if (_uiState.value.isNavigating) return

        val component = map.locationComponent
        if (!component.isLocationComponentActivated || !component.isLocationComponentEnabled) return
        if (component.cameraMode == CameraMode.TRACKING_GPS) return

        component.cameraMode = CameraMode.TRACKING_GPS
    }

    private fun snapCameraToGpsIfNeeded(latLng: LatLng) {
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
                "zoom=$FREE_DRIVE_ZOOM tilt=$FREE_DRIVE_TILT (current=${map.cameraPosition.zoom}, " +
                "target=${formatCameraTarget(map)})",
        )

        if (componentReady) {
            component.cameraMode = CameraMode.NONE
        }

        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(latLng)
                    .tilt(FREE_DRIVE_TILT)
                    .zoom(FREE_DRIVE_ZOOM)
                    .bearing(bearing)
                    .build(),
            ),
        )

        if (componentReady) {
            component.cameraMode = CameraMode.TRACKING_GPS
        }

        hasSnappedCameraToGps = true
        _uiState.update { it.copy(streetName = "Free Drive") }
    }

    override fun navigateToCoordinates(lat: Double, lng: Double) {
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

    private fun startNavigation(origin: LatLng, lat: Double, lng: Double) {
        _uiState.update { it.copy(isNavigating = true, streetName = "Calculating route...") }

        engineScope.launch {
            try {
                val route = withContext(Dispatchers.IO) {
                    fetchOsrmRoute(origin.longitude, origin.latitude, lng, lat)
                }
                if (route != null) {
                    drawRoute(route.geometryJson)
                    fullRouteSteps = route.steps
                    currentStepIndex = 0
                    destinationLatLng = LatLng(lat, lng)
                    navigationArrivalTriggered = false
                    _uiState.update {
                        it.copy(
                            isNavigating = true,
                            streetName = route.streetName,
                            turnInstruction = route.instruction,
                            distanceToNextTurn = route.distance,
                        )
                    }
                    showRouteThenDive(origin, LatLng(lat, lng))
                } else {
                    _uiState.update { it.copy(isNavigating = false, streetName = "Route not found") }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Route fetch failed: ${e.message}", e)
                _uiState.update { it.copy(isNavigating = false, streetName = "Routing failed") }
            }
        }
    }

    private fun fetchOsrmRoute(
        lngA: Double,
        latA: Double,
        lngB: Double,
        latB: Double,
    ): RouteResult? {
        val url = URL(
            "https://routing.openstreetmap.de/routed-car/route/v1/driving/" +
                "$lngA,$latA;$lngB,$latB?geometries=geojson&steps=true&overview=full",
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "MixAutoCarLauncher/1.0")
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        return try {
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "OSRM HTTP error: ${connection.responseCode}")
                return null
            }
            val body = connection.inputStream.bufferedReader().readText()
            parseOsrmResponse(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseOsrmResponse(json: String): RouteResult? {
        val root = JSONObject(json)
        if (root.optString("code") != "Ok") return null
        val routes = root.getJSONArray("routes")
        if (routes.length() == 0) return null
        val route = routes.getJSONObject(0)

        val geometryJson = route.getJSONObject("geometry").toString()

        val legs = route.getJSONArray("legs")
        if (legs.length() == 0) return null
        val steps = legs.getJSONObject(0).getJSONArray("steps")
        if (steps.length() == 0) return null
        val allSteps = buildList {
            for (i in 0 until steps.length()) {
                val step = steps.getJSONObject(i)
                val maneuver = step.getJSONObject("maneuver")
                val location = maneuver.getJSONArray("location")
                val type = maneuver.optString("type", "")
                val modifier = maneuver.optString("modifier", "")
                val name = step.optString("name", "")
                add(
                    LegStep(
                        maneuverLat = location.getDouble(1),
                        maneuverLng = location.getDouble(0),
                        instruction = buildInstruction(type, modifier, name),
                        distanceLabel = formatDistance(step.optDouble("distance", 0.0)),
                        streetName = name,
                    ),
                )
            }
        }

        val firstStep = allSteps.first()

        return RouteResult(
            geometryJson = geometryJson,
            streetName = firstStep.streetName.ifBlank { "On route" },
            instruction = firstStep.instruction,
            distance = firstStep.distanceLabel,
            steps = allSteps,
        )
    }

    private fun buildInstruction(type: String, modifier: String, name: String): String {
        val action = when (type) {
            "depart" -> "Depart"
            "arrive" -> "Arrive at destination"
            "turn" -> "Turn ${modifier.replace('-', ' ')}"
            "new name" -> "Continue"
            "merge" -> "Merge ${modifier.replace('-', ' ')}"
            "on ramp" -> "Take the ramp"
            "off ramp" -> "Take the exit"
            "fork" -> "Keep ${modifier.replace('-', ' ')}"
            "end of road" -> "Turn ${modifier.replace('-', ' ')}"
            "roundabout" -> "Enter roundabout"
            else -> type.replaceFirstChar { it.uppercase() }
        }
        return if (name.isNotBlank() && type != "arrive") "$action onto $name" else action
    }

    private fun formatDistance(meters: Double): String = when {
        meters >= 1000 -> "%.1f km".format(meters / 1000.0)
        else -> "${meters.toInt()} m"
    }

    private fun showRouteThenDive(origin: LatLng, destination: LatLng) {
        val map = mapLibreMap ?: return
        val bounds = LatLngBounds.Builder()
            .include(origin)
            .include(destination)
            .build()
        map.animateCamera(
            CameraUpdateFactory.newLatLngBounds(bounds, ROUTE_OVERVIEW_PADDING_PX),
            ROUTE_OVERVIEW_DURATION_MS,
        )
        engineScope.launch {
            delay(NAV_DIVE_DELAY_MS)
            if (_uiState.value.isNavigating) {
                enterNavigationCamera()
            }
        }
    }

    private fun enterNavigationCamera() {
        val map = mapLibreMap ?: return
        val origin = lastKnownLocation ?: return
        val component = map.locationComponent
        if (!component.isLocationComponentActivated || !component.isLocationComponentEnabled) return

        component.cameraMode = CameraMode.TRACKING_GPS

        val bearing = component.lastKnownLocation?.bearing?.toDouble() ?: 0.0
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(origin)
                    .zoom(NAV_ZOOM)
                    .tilt(NAV_TILT)
                    .bearing(bearing)
                    .build(),
            ),
            NAV_CAMERA_DURATION_MS,
        )
    }

    private fun drawRoute(geometryJson: String) {
        val map = mapLibreMap ?: return
        val featureJson = """{"type":"Feature","geometry":$geometryJson,"properties":{}}"""
        map.getStyle { style ->
            val existing = style.getSource(ROUTE_SOURCE_ID)
            if (existing is GeoJsonSource) {
                existing.setGeoJson(featureJson)
            } else {
                style.addSource(GeoJsonSource(ROUTE_SOURCE_ID, featureJson))
                style.addLayer(
                    LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                        PropertyFactory.lineColor(ROUTE_COLOR),
                        PropertyFactory.lineWidth(ROUTE_WIDTH),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        PropertyFactory.lineOpacity(0.9f),
                    ),
                )
            }
        }
    }

    private fun activateLocationTracking(
        map: org.maplibre.android.maps.MapLibreMap,
        style: Style,
    ) {
        val ctx = appContext ?: return
        if (!hasLocationPermission(ctx)) {
            Log.w(TAG, "Location permission not granted; skipping LocationComponent activation")
            return
        }

        runCatching {
            val locationEngine = createLocationEngine(ctx)
            this.locationEngine = locationEngine
            val locationComponent = map.locationComponent
            val options = LocationComponentActivationOptions.builder(ctx, style)
                .locationEngine(locationEngine)
                .build()
            if (!locationComponent.isLocationComponentActivated) {
                locationComponent.activateLocationComponent(options)
            }
            locationComponent.isLocationComponentEnabled = true
            locationComponent.renderMode = RenderMode.COMPASS

            flushPendingLocationFix()
            beginLocationAcquisition(ctx)
            scheduleLocationRetries(ctx)

            locationEngine.getLastLocation(object : LocationEngineCallback<LocationEngineResult> {
                override fun onSuccess(result: LocationEngineResult) {
                    result.lastLocation?.let { location ->
                        applyAndroidLocation(location, snapCamera = !hasSnappedCameraToGps)
                    }
                }

                override fun onFailure(exception: Exception) {
                    Log.w(TAG, "Initial location fetch failed: ${exception.message}")
                }
            })
            registerSpeedUpdates(locationEngine)
        }.onFailure { error ->
            Log.w(TAG, "Failed to activate LocationComponent: ${error.message}", error)
        }
    }

    private fun createLocationEngine(context: Context): LocationEngine {
        Log.i(TAG, "Using MapLibre fused location engine (no Google Services)")
        return LocationEngineProxy(MapLibreFusedLocationEngineImpl(context))
    }

    private fun scheduleLocationRetries(context: Context) {
        locationRetryJob?.cancel()
        locationRetryJob = engineScope.launch {
            for (delayMs in LOCATION_RETRY_DELAYS_MS) {
                delay(delayMs)
                if (hasSnappedCameraToGps && lastKnownLocation != null) return@launch
                Log.i(TAG, "Scheduled location retry after ${delayMs}ms")
                refreshLocationOnly(context)
            }
        }
    }

    private fun flushPendingLocationFix() {
        val location = pendingLocationFix ?: return
        val component = mapLibreMap?.locationComponent ?: return
        if (!component.isLocationComponentActivated || !component.isLocationComponentEnabled) return
        component.forceLocationUpdate(location)
        pendingLocationFix = null
        Log.i(TAG, "Applied queued location fix to LocationComponent")
    }

    private fun registerSpeedUpdates(engine: LocationEngine) {
        locationEngineCallback?.let { previous ->
            locationEngine?.removeLocationUpdates(previous)
        }

        val request = LocationEngineRequest.Builder(LOCATION_INTERVAL_MS)
            .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
            .build()

        val callback = object : LocationEngineCallback<LocationEngineResult> {
            override fun onSuccess(result: LocationEngineResult) {
                val location = result.lastLocation ?: return
                applyAndroidLocation(location, snapCamera = !hasSnappedCameraToGps)
                val speedKmh = if (location.hasSpeed()) {
                    (location.speed * 3.6f).toInt().coerceAtLeast(0)
                } else {
                    0
                }
                _uiState.update { it.copy(currentSpeed = speedKmh) }
            }

            override fun onFailure(exception: Exception) {
                Log.w(TAG, "Location engine error: ${exception.message}")
            }
        }

        engine.requestLocationUpdates(request, callback, Looper.getMainLooper())
        locationEngineCallback = callback
    }

    private fun resolveMapViewOrigin(): LatLng? {
        val map = mapLibreMap ?: return null
        val position = map.cameraPosition
        if (position.zoom < ROUTING_MIN_ZOOM) return null
        return position.target
    }

    private fun logPermissionState(context: Context) {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        Log.i(TAG, "Location permission: fine=$fineGranted coarse=$coarseGranted")
    }

    private fun refreshLocationFromSystem(context: Context): LatLng? {
        val location = readLastKnownLocation(context) ?: return null
        applySystemLocation(location, snapCamera = !hasSnappedCameraToGps)
        return location
    }

    private fun applySystemLocation(latLng: LatLng, snapCamera: Boolean) {
        val location = Location("mixauto").apply {
            latitude = latLng.latitude
            longitude = latLng.longitude
            time = System.currentTimeMillis()
        }
        applyAndroidLocation(location, snapCamera)
    }

    private fun applyAndroidLocation(location: Location, snapCamera: Boolean) {
        val latLng = LatLng(location.latitude, location.longitude)
        lastKnownLocation = latLng
        val component = mapLibreMap?.locationComponent
        if (component != null && component.isLocationComponentActivated && component.isLocationComponentEnabled) {
            component.forceLocationUpdate(location)
            pendingLocationFix = null
        } else {
            pendingLocationFix = location
        }
        Log.i(
            TAG,
            "Location fix ${location.latitude}, ${location.longitude} " +
                "(provider=${location.provider}, snapped=$snapCamera, " +
                "zoom=${mapLibreMap?.cameraPosition?.zoom})",
        )
        when {
            _uiState.value.isNavigating -> evaluateStepAdvancement(location)
            !hasSnappedCameraToGps -> snapCameraToGpsIfNeeded(latLng)
            else -> _uiState.update { it.copy(streetName = "Free Drive") }
        }
    }

    private fun evaluateStepAdvancement(currentLocation: Location) {
        if (navigationArrivalTriggered) return

        val steps = fullRouteSteps
        if (steps.isEmpty()) return

        val dest = destinationLatLng
        if (dest != null) {
            val destLoc = Location("dest").apply {
                latitude = dest.latitude
                longitude = dest.longitude
            }
            if (currentLocation.distanceTo(destLoc) < ARRIVAL_THRESHOLD_M) {
                triggerArrival()
                return
            }
        }

        val nextIdx = currentStepIndex + 1
        if (nextIdx >= steps.size) {
            triggerArrival()
            return
        }

        val nextStep = steps[nextIdx]
        val maneuverLoc = Location("maneuver").apply {
            latitude = nextStep.maneuverLat
            longitude = nextStep.maneuverLng
        }
        val distToManeuver = currentLocation.distanceTo(maneuverLoc)

        _uiState.update {
            it.copy(distanceToNextTurn = formatDistance(distToManeuver.toDouble()))
        }

        if (distToManeuver < STEP_ADVANCE_THRESHOLD_M) {
            currentStepIndex = nextIdx
            val advanced = steps[currentStepIndex]
            _uiState.update {
                it.copy(
                    turnInstruction = advanced.instruction,
                    distanceToNextTurn = advanced.distanceLabel,
                    streetName = advanced.streetName.ifBlank { "On route" },
                )
            }
        }
    }

    private fun triggerArrival() {
        if (navigationArrivalTriggered) return
        navigationArrivalTriggered = true
        _uiState.update { it.copy(streetName = "Arrived at destination") }
        engineScope.launch {
            delay(ARRIVAL_FREE_DRIVE_DELAY_MS)
            startFreeDrive()
        }
    }

    private fun beginLocationAcquisition(context: Context) {
        if (!hasLocationPermission(context)) {
            Log.w(TAG, "beginLocationAcquisition skipped: permission not granted")
            return
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        Log.i(
            TAG,
            "beginLocationAcquisition: providers=${locationManager?.allProviders}, " +
                "enabled=${isLocationEnabled(context)}, " +
                "listenersRegistered=$listenersRegistered",
        )
        logPermissionState(context)

        refreshLocationFromSystem(context)
        ensureLocationListeners(context)
        startLocationPolling(context)
    }

    private fun refreshLocationOnly(context: Context) {
        if (!hasLocationPermission(context)) return
        refreshLocationFromSystem(context)
        flushPendingLocationFix()
    }

    private fun ensureLocationListeners(context: Context) {
        if (listenersRegistered) {
            Log.i(TAG, "Location listeners already active; skipping re-register")
            return
        }
        ensureGpsLocationListener(context)
        listenersRegistered = true
    }

    private fun ensureGpsLocationListener(context: Context) {
        if (freshLocationListener != null) return
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return

        val listener = LocationListener { location ->
            Log.i(
                TAG,
                "GPS update: ${location.latitude}, ${location.longitude} from ${location.provider}",
            )
            applyAndroidLocation(location, snapCamera = !hasSnappedCameraToGps)
        }
        freshLocationListener = listener

        runCatching {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                FRESH_LOCATION_MIN_TIME_MS,
                0f,
                listener,
                Looper.getMainLooper(),
            )
            Log.i(TAG, "GPS location listener registered")
        }.onFailure { error ->
            Log.w(TAG, "Failed to register GPS listener: ${error.message}")
            freshLocationListener = null
        }
    }

    private fun startLocationPolling(context: Context) {
        if (locationPollJob?.isActive == true) return
        locationPollJob?.cancel()
        locationPollJob = engineScope.launch {
            repeat(LOCATION_POLL_ATTEMPTS) {
                readLastKnownLocation(context)?.let { latLng ->
                    applySystemLocation(latLng, snapCamera = !hasSnappedCameraToGps)
                    if (hasSnappedCameraToGps) return@launch
                }
                delay(LOCATION_POLL_INTERVAL_MS)
            }
            if (lastKnownLocation == null) {
                Log.w(TAG, "Location polling timed out with no fix")
                _uiState.update { state ->
                    if (state.streetName == "Map ready" || state.streetName == "Locating..." ||
                        state.streetName == "Scanning Road..." || state.streetName == "Free Drive"
                    ) {
                        state.copy(streetName = "Zoom map to your area (no GPS fix)")
                    } else {
                        state
                    }
                }
            }
        }
    }

    private fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun removeFreshLocationListener() {
        val context = appContext ?: return
        val listener = freshLocationListener ?: return
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return
        runCatching { locationManager.removeUpdates(listener) }
        freshLocationListener = null
    }

    private fun resolveInitialLocation(context: Context): ResolvedLocation {
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
            _uiState.update { it.copy(streetName = "Locating...") }
            ResolvedLocation(
                latLng = location,
                zoom = FREE_DRIVE_ZOOM,
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

    companion object {
        private const val TAG = "MapLibreEngineImpl"
        private const val DEFAULT_ZOOM = 15.0
        private const val DEFAULT_ZOOM_FALLBACK = 6.0
        private const val ROUTING_MIN_ZOOM = 10.0
        private const val NAV_ZOOM = 17.5
        private const val NAV_TILT = 55.0
        private const val NAV_CAMERA_DURATION_MS = 1500
        private const val FREE_DRIVE_ZOOM = 16.0
        private const val FREE_DRIVE_TILT = 45.0
        private const val ROUTE_OVERVIEW_PADDING_PX = 120
        private const val ROUTE_OVERVIEW_DURATION_MS = 2000
        private const val NAV_DIVE_DELAY_MS = 2200L
        private const val LOCATION_INTERVAL_MS = 1000L
        private const val FRESH_LOCATION_MIN_TIME_MS = 500L
        private const val LOCATION_POLL_INTERVAL_MS = 1000L
        private const val LOCATION_POLL_ATTEMPTS = 15
        private const val LOCATION_ACQUIRE_TIMEOUT_MS = 8000L
        private val LOCATION_RETRY_DELAYS_MS = longArrayOf(1_000L, 3_000L, 8_000L)
        private const val ROUTE_SOURCE_ID = "mix-route-source"
        private const val ROUTE_LAYER_ID = "mix-route-layer"
        private const val ROUTE_COLOR = "#00CBD6"
        private const val ROUTE_WIDTH = 6f
        private const val STEP_ADVANCE_THRESHOLD_M = 25f
        private const val ARRIVAL_THRESHOLD_M = 15f
        private const val ARRIVAL_FREE_DRIVE_DELAY_MS = 1_500L
        private val DEFAULT_LOCATION = LatLng(12.8797, 121.7740)

        /**
         * Minimal MapLibre style that sources raster tiles from the public OSM tile server.
         * Global coverage, no API key required. Raster tiles don't support sharp 3D perspective
         * so tilt is kept moderate (30°). Replace with a vector style for a crisper driving view.
         */
        private val OSM_STYLE_JSON = """
            {
                "version": 8,
                "sources": {
                    "osm": {
                        "type": "raster",
                        "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
                        "tileSize": 256,
                        "attribution": "\u00a9 OpenStreetMap contributors"
                    }
                },
                "layers": [{
                    "id": "osm",
                    "type": "raster",
                    "source": "osm"
                }]
            }
        """.trimIndent()
    }
}
