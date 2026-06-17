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
import org.maplibre.android.location.engine.AndroidLocationEngineImpl
import org.maplibre.android.location.engine.LocationEngine
import org.maplibre.android.location.engine.LocationEngineCallback
import org.maplibre.android.location.engine.LocationEngineProxy
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.engine.LocationEngineResult
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

private data class RouteResult(
    val geometryJson: String,
    val streetName: String,
    val instruction: String,
    val distance: String,
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
    private var hasSnappedCameraToGps = false
    private var locationEngine: LocationEngine? = null
    private var locationEngineCallback: LocationEngineCallback<LocationEngineResult>? = null
    private var freshLocationListener: LocationListener? = null

    override fun createMapView(context: Context): View {
        mapView?.let { return it }

        if (!mapLibreInitialized) {
            MapLibre.getInstance(context.applicationContext)
            mapLibreInitialized = true
        }

        appContext = context.applicationContext
        val resolved = resolveInitialLocation(context)
        if (resolved.fromGps) {
            hasSnappedCameraToGps = true
        }

        return MapView(context).also { view ->
            view.onCreate(null)
            view.onStart()
            view.onResume()
            view.getMapAsync { map ->
                mapLibreMap = map
                map.setStyle(Style.Builder().fromJson(OSM_STYLE_JSON)) { style ->
                    val cameraPosition = CameraPosition.Builder()
                        .target(resolved.latLng)
                        .tilt(DEFAULT_TILT)
                        .zoom(resolved.zoom)
                        .build()
                    map.cameraPosition = cameraPosition
                    _uiState.update { state ->
                        state.copy(streetName = "Map ready")
                    }
                    activateLocationTracking(map, style)
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
                refreshLocationFromSystem(context)
                requestFreshLocationUpdate(context)
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
        engineScope.cancel()
        removeFreshLocationListener()
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
        val ctx = appContext ?: return
        if (!hasLocationPermission(ctx)) return

        refreshLocationFromSystem(ctx)
        requestFreshLocationUpdate(ctx)

        mapView?.getMapAsync { map ->
            map.getStyle { style ->
                activateLocationTracking(map, style)
            }
        }
    }

    override fun startFreeDrive() {
        _uiState.update {
            it.copy(
                isNavigating = false,
                streetName = "Free Drive",
                distanceToNextTurn = null,
                turnInstruction = null,
                currentSpeed = 0,
            )
        }
        lastKnownLocation?.let { location ->
            mapView?.getMapAsync { map ->
                map.locationComponent.cameraMode = CameraMode.TRACKING
                map.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(location)
                            .tilt(DEFAULT_TILT)
                            .zoom(DEFAULT_ZOOM)
                            .build(),
                    ),
                )
            }
        }
    }

    override fun navigateToCoordinates(lat: Double, lng: Double) {
        val ctx = appContext
        var origin = lastKnownLocation ?: ctx?.let { readLastKnownLocation(it) }

        if (origin == null && ctx != null && hasLocationPermission(ctx)) {
            _uiState.update { it.copy(streetName = "Acquiring location...") }
            requestFreshLocationUpdate(ctx)
            engineScope.launch {
                delay(LOCATION_ACQUIRE_TIMEOUT_MS)
                val resolvedOrigin = lastKnownLocation ?: readLastKnownLocation(ctx)
                if (resolvedOrigin == null) {
                    _uiState.update { it.copy(streetName = "Location unavailable") }
                    return@launch
                }
                lastKnownLocation = resolvedOrigin
                startNavigation(resolvedOrigin, lat, lng)
            }
            return
        }

        if (origin == null) {
            Log.w(TAG, "No known location; cannot route")
            _uiState.update { it.copy(streetName = "Location unavailable") }
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
                    _uiState.update {
                        it.copy(
                            isNavigating = true,
                            streetName = route.streetName,
                            turnInstruction = route.instruction,
                            distanceToNextTurn = route.distance,
                        )
                    }
                    fitCameraToRoute(origin, LatLng(lat, lng))
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
            "https://router.project-osrm.org/route/v1/driving/" +
                "$lngA,$latA;$lngB,$latB?geometries=geojson&steps=true&overview=full",
        )
        val connection = url.openConnection() as HttpURLConnection
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
        val firstStep = steps.getJSONObject(0)
        val stepName = firstStep.optString("name", "")
        val stepDistance = firstStep.optDouble("distance", 0.0)
        val maneuver = firstStep.getJSONObject("maneuver")
        val type = maneuver.optString("type", "")
        val modifier = maneuver.optString("modifier", "")

        return RouteResult(
            geometryJson = geometryJson,
            streetName = stepName.ifBlank { "On route" },
            instruction = buildInstruction(type, modifier, stepName),
            distance = formatDistance(stepDistance),
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

    private fun fitCameraToRoute(origin: LatLng, destination: LatLng) {
        val map = mapLibreMap ?: return
        val bounds = LatLngBounds.Builder()
            .include(origin)
            .include(destination)
            .build()
        map.animateCamera(
            CameraUpdateFactory.newLatLngBounds(bounds, ROUTE_PADDING_PX),
            ROUTE_FIT_DURATION_MS,
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
            refreshLocationFromSystem(ctx)
            requestFreshLocationUpdate(ctx)

            val locationEngine = LocationEngineProxy(AndroidLocationEngineImpl(ctx))
            this.locationEngine = locationEngine
            val locationComponent = map.locationComponent
            val options = LocationComponentActivationOptions.builder(ctx, style)
                .locationEngine(locationEngine)
                .build()
            if (!locationComponent.isLocationComponentActivated) {
                locationComponent.activateLocationComponent(options)
            }
            locationComponent.isLocationComponentEnabled = true
            locationComponent.cameraMode = CameraMode.TRACKING
            locationComponent.renderMode = RenderMode.COMPASS
            locationEngine.getLastLocation(object : LocationEngineCallback<LocationEngineResult> {
                override fun onSuccess(result: LocationEngineResult) {
                    result.lastLocation?.let { location ->
                        snapCameraToGpsIfNeeded(
                            LatLng(location.latitude, location.longitude),
                        )
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

    private fun registerSpeedUpdates(engine: LocationEngine) {
        val request = LocationEngineRequest.Builder(LOCATION_INTERVAL_MS)
            .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
            .build()

        val callback = object : LocationEngineCallback<LocationEngineResult> {
            override fun onSuccess(result: LocationEngineResult) {
                val location = result.lastLocation ?: return
                val latLng = LatLng(location.latitude, location.longitude)
                lastKnownLocation = latLng
                snapCameraToGpsIfNeeded(latLng)
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

    private fun snapCameraToGpsIfNeeded(latLng: LatLng) {
        lastKnownLocation = latLng
        if (hasSnappedCameraToGps) return
        val map = mapLibreMap ?: return
        hasSnappedCameraToGps = true
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(latLng)
                    .tilt(DEFAULT_TILT)
                    .zoom(DEFAULT_ZOOM)
                    .build(),
            ),
        )
        _uiState.update { it.copy(streetName = "Current location") }
    }

    private fun refreshLocationFromSystem(context: Context): LatLng? {
        val location = readLastKnownLocation(context) ?: return null
        applySystemLocation(location, snapCamera = !hasSnappedCameraToGps)
        return location
    }

    private fun applySystemLocation(latLng: LatLng, snapCamera: Boolean) {
        lastKnownLocation = latLng
        if (snapCamera) {
            snapCameraToGpsIfNeeded(latLng)
        } else {
            _uiState.update { it.copy(streetName = "Current location") }
        }
    }

    private fun requestFreshLocationUpdate(context: Context) {
        if (!hasLocationPermission(context)) return
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return

        removeFreshLocationListener()

        val listener = LocationListener { location ->
            removeFreshLocationListener()
            applySystemLocation(
                LatLng(location.latitude, location.longitude),
                snapCamera = !hasSnappedCameraToGps,
            )
        }
        freshLocationListener = listener

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        )
        for (provider in providers) {
            val started = runCatching {
                locationManager.requestLocationUpdates(
                    provider,
                    0L,
                    0f,
                    listener,
                    Looper.getMainLooper(),
                )
            }.isSuccess
            if (started) {
                Log.d(TAG, "Requested fresh location from $provider")
                return
            }
        }
        Log.w(TAG, "Could not request fresh location from any provider")
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
                zoom = DEFAULT_ZOOM,
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

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )

        val bestLocation = providers
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull(Location::getTime)

        return bestLocation?.let { LatLng(it.latitude, it.longitude) }
    }

    companion object {
        private const val TAG = "MapLibreEngineImpl"
        private const val DEFAULT_TILT = 30.0
        private const val DEFAULT_ZOOM = 15.0
        private const val DEFAULT_ZOOM_FALLBACK = 6.0
        private const val LOCATION_INTERVAL_MS = 1000L
        private const val LOCATION_ACQUIRE_TIMEOUT_MS = 3000L
        private const val ROUTE_SOURCE_ID = "mix-route-source"
        private const val ROUTE_LAYER_ID = "mix-route-layer"
        private const val ROUTE_COLOR = "#00CBD6"
        private const val ROUTE_WIDTH = 6f
        private const val ROUTE_PADDING_PX = 120
        private const val ROUTE_FIT_DURATION_MS = 1200
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
