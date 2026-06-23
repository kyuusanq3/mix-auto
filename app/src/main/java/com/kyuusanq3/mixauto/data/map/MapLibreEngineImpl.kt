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
import com.kyuusanq3.mixauto.data.places.LocalPlacesRepository
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.domain.map.MapUiState
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.geojson.Geometry
import org.maplibre.geojson.MultiPoint
import org.maplibre.geojson.Point
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
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
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.style.sources.VectorSource
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private data class LegStep(
    val maneuverLat: Double,
    val maneuverLng: Double,
    val instruction: String,
    val distanceLabel: String,
    val streetName: String,
)

private data class RouteResult(
    val geometryJson: String,
    val geometryPoints: List<LatLng>,
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

class MapLibreEngineImpl(
    private val localPlaces: LocalPlacesRepository? = null,
    initialUseVectorTiles: Boolean = false,
    initialDrivingZoom: Double = 17.5,
    initialPuckHOffset: Float = 0.3f,
    initialPuckVOffset: Float = 0.4f,
    initialPuckScale: Float = 1.0f,
) : CarMapEngine {

    private val _uiState = MutableStateFlow(MapUiState())
    override val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var useVectorTiles = initialUseVectorTiles
    private var trafficEnabled = false
    private var tomTomApiKey = ""
    private var freeDriveZoom = initialDrivingZoom
    private var navZoom = initialDrivingZoom + 1.0
    private var puckHorizontalOffset = initialPuckHOffset
    private var puckVerticalOffset = initialPuckVOffset
    private var puckScale = initialPuckScale

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
    private var routeOverviewJob: Job? = null
    private var poiRefreshJob: Job? = null
    private var lastPhotonQueryCenter: LatLng? = null
    private val poiCache = mutableMapOf<String, SearchResultPlace>()
    private var routeGeometryPoints: List<LatLng> = emptyList()
    private var offRouteCount: Int = 0
    private var rerouteCooldownUntilMs: Long = 0L
    private var isRerouteInProgress: Boolean = false
    private var previousLocationFix: Location? = null
    private var lastStableBearing: Float? = null
    private var deadReckoningJob: Job? = null
    private var lastDeadReckoningLocation: Location? = null

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
            view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                mapLibreMap?.let { map -> applyDrivingTrackingPadding(map) }
            }
            view.getMapAsync { map ->
                mapLibreMap = map
                map.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        _uiState.update { it.copy(isCameraDetached = true) }
                    }
                }
                registerPoiInteractions(map)
                applyMapStyle(map, context)
            }
            mapView = view
        }
    }

    override fun setMapStyle(useVectorTiles: Boolean) {
        val map = mapLibreMap ?: return
        val ctx = appContext ?: return

        this.useVectorTiles = useVectorTiles

        poiRefreshJob?.cancel()
        poiRefreshJob = null
        routeOverviewJob?.cancel()
        routeOverviewJob = null
        fullRouteSteps = emptyList()
        currentStepIndex = 0
        destinationLatLng = null
        navigationArrivalTriggered = false
        routeGeometryPoints = emptyList()
        offRouteCount = 0
        isRerouteInProgress = false

        _uiState.update {
            it.copy(
                isNavigating = false,
                streetName = "Loading map...",
                selectedPoi = null,
                routeOverviewProgress = 0f,
            )
        }

        applyMapStyle(map, ctx)
    }

    override fun setTrafficEnabled(enabled: Boolean, apiKey: String) {
        trafficEnabled = enabled
        tomTomApiKey = apiKey.trim()
        applyTrafficOverlay()
    }

    override fun setDrivingZoom(zoom: Double) {
        freeDriveZoom = zoom
        navZoom = zoom + 1.0
        mapLibreMap?.animateCamera(
            CameraUpdateFactory.zoomTo(
                if (_uiState.value.isNavigating) navZoom else freeDriveZoom,
            ),
        )
    }

    override fun setViewportPadding(horizontalFraction: Float, verticalFraction: Float) {
        puckHorizontalOffset = horizontalFraction
        puckVerticalOffset = verticalFraction
        val map = mapLibreMap ?: return
        applyDrivingViewportPadding(map)
        applyDrivingTrackingPadding(map)
        forceLocationUpdateForImmediateRender(map)
    }

    override fun setPuckScale(scale: Float) {
        puckScale = scale
        val component = mapLibreMap?.locationComponent ?: return
        if (!component.isLocationComponentActivated) return
        val updated = component.locationComponentOptions
            .toBuilder()
            .maxZoomIconScale(scale)
            .minZoomIconScale(scale)
            .build()
        component.applyStyle(updated)
        forceLocationUpdateForImmediateRender(mapLibreMap ?: return)
    }

    /**
     * After changing tracking padding or puck style, force a forceLocationUpdate so the map
     * camera repositions immediately without waiting for the next GPS fix or dead-reckoning tick.
     */
    private fun forceLocationUpdateForImmediateRender(map: MapLibreMap) {
        val component = map.locationComponent
        if (!component.isLocationComponentActivated || !component.isLocationComponentEnabled) return
        val location = component.lastKnownLocation
            ?: lastKnownLocation?.let { ll ->
                Location("forced").apply {
                    latitude = ll.latitude
                    longitude = ll.longitude
                }
            }
            ?: return
        component.forceLocationUpdate(location)
    }

    private fun applyMapStyle(map: MapLibreMap, context: Context) {
        val builder = if (useVectorTiles) {
            Style.Builder().fromUri(VECTOR_STYLE_URL)
        } else {
            Style.Builder().fromJson(OSM_STYLE_JSON)
        }

        map.setStyle(builder) { style ->
            val density = context.resources.displayMetrics.density
            PoiIconFactory.createAllIcons(density).forEach { (id, bitmap) ->
                style.addImage(id, bitmap)
            }
            activateLocationTracking(map, style)
            if (pendingLocationActivation && hasLocationPermission(context)) {
                beginLocationAcquisition(context)
                pendingLocationActivation = false
            }
            hasSnappedCameraToGps = false
            startFreeDrive()
            applyTrafficOverlay()
        }
    }

    private fun applyTrafficOverlay() {
        val map = mapLibreMap ?: return
        val style = map.style ?: return

        if (style.getLayer(TRAFFIC_LAYER_ID) != null) {
            style.removeLayer(TRAFFIC_LAYER_ID)
        }
        if (style.getSource(TRAFFIC_SOURCE_ID) != null) {
            style.removeSource(TRAFFIC_SOURCE_ID)
        }

        if (!trafficEnabled || tomTomApiKey.isBlank()) return

        val tileUrl =
            "https://api.tomtom.com/maps/orbis/traffic/flow/raster/tile/{z}/{x}/{y}" +
                "?apiVersion=2&key=$tomTomApiKey&style=light&tileSize=256"
        val tileSet = TileSet("2.1.0", tileUrl)
        style.addSource(RasterSource(TRAFFIC_SOURCE_ID, tileSet, 256))

        val trafficLayer = RasterLayer(TRAFFIC_LAYER_ID, TRAFFIC_SOURCE_ID).withProperties(
            PropertyFactory.rasterOpacity(0.7f),
        )
        if (style.getLayer(RASTER_BASE_LAYER_ID) != null) {
            style.addLayerAbove(trafficLayer, RASTER_BASE_LAYER_ID)
        } else {
            style.addLayer(trafficLayer)
        }
        val existingCasing = style.getLayer(ROUTE_CASING_LAYER_ID)
        val existingRoute = style.getLayer(ROUTE_LAYER_ID)
        if (existingCasing != null && existingRoute != null) {
            style.removeLayer(existingCasing)
            style.removeLayer(existingRoute)
            style.addLayerAbove(existingCasing, TRAFFIC_LAYER_ID)
            style.addLayerAbove(existingRoute, ROUTE_CASING_LAYER_ID)
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
        deadReckoningJob?.cancel()
        deadReckoningJob = null
        locationPollJob?.cancel()
        locationPollJob = null
        locationRetryJob?.cancel()
        locationRetryJob = null
        poiRefreshJob?.cancel()
        poiRefreshJob = null
        lastPhotonQueryCenter = null
        clearPoiCache()
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

    override suspend fun searchDestination(
        query: String,
        currentLat: Double,
        currentLng: Double,
        limitDistance: Boolean,
        onLocalResults: suspend (List<SearchResultPlace>) -> Unit,
    ): List<SearchResultPlace> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        fun applyDistanceLimit(places: List<SearchResultPlace>): List<SearchResultPlace> =
            if (limitDistance) {
                places.filter { it.distanceInMeters <= MAX_SEARCH_RADIUS_M }
            } else {
                places
            }

        val hasOfflineData = localPlaces?.hasInstalledDatabase == true
        val local = if (hasOfflineData) {
            localPlaces?.searchPlaces(query, currentLat, currentLng).orEmpty()
        } else {
            emptyList()
        }
        val cacheResults = withContext(Dispatchers.Main) {
            searchPoiCache(query, currentLat, currentLng)
        }
        val localAndCache = mergeAndDeduplicate(local, cacheResults)
        val localAndCacheFiltered = applyDistanceLimit(localAndCache)
        if (localAndCacheFiltered.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                onLocalResults(localAndCacheFiltered)
            }
        }

        val photon = fetchPhoton(query, currentLat, currentLng)
        val finalResults = applyDistanceLimit(mergeAndDeduplicate(localAndCache, photon))
        if (finalResults.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                mergeIntoPoiCache(finalResults)
                updatePoiLayerFromCache()
            }
        }
        finalResults
    }

    override fun getNearbyPois(lat: Double, lng: Double, limit: Int): List<SearchResultPlace> {
        if (limit <= 0) return emptyList()
        return poiCache.values
            .map { place ->
                val distanceResults = FloatArray(1)
                Location.distanceBetween(
                    lat,
                    lng,
                    place.latitude,
                    place.longitude,
                    distanceResults,
                )
                place.copy(distanceInMeters = distanceResults[0])
            }
            .sortedBy { it.distanceInMeters }
            .take(limit)
    }

    private suspend fun fetchPhoton(
        query: String,
        currentLat: Double,
        currentLng: Double,
    ): List<SearchResultPlace> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL(
            "https://photon.komoot.io/api/" +
                "?q=$encoded&lat=$currentLat&lon=$currentLng&limit=10",
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "MixAutoCarLauncher/1.0")
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        return try {
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "Photon HTTP error: ${connection.responseCode}")
                emptyList()
            } else {
                val body = connection.inputStream.bufferedReader().readText()
                parsePhotonResponse(body, currentLat, currentLng)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Photon search failed: ${e.message}")
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    private fun mergeAndDeduplicate(
        local: List<SearchResultPlace>,
        photon: List<SearchResultPlace>,
    ): List<SearchResultPlace> {
        val merged = mutableListOf<SearchResultPlace>()
        val seen = mutableListOf<Pair<Double, Double>>()

        fun isDuplicate(place: SearchResultPlace): Boolean {
            for ((lat, lng) in seen) {
                val distanceResults = FloatArray(1)
                Location.distanceBetween(
                    lat,
                    lng,
                    place.latitude,
                    place.longitude,
                    distanceResults,
                )
                if (distanceResults[0] < DEDUP_THRESHOLD_M) return true
            }
            return false
        }

        for (place in local + photon) {
            if (!isDuplicate(place)) {
                merged.add(place)
                seen.add(place.latitude to place.longitude)
            }
        }
        return merged.sortedBy { it.distanceInMeters }
    }

    /**
     * Merges local + Photon POIs for map pins. Unlike [mergeAndDeduplicate], keeps local
     * results first (preserving Overture categories) and does not re-sort by distance —
     * otherwise closer uncategorized Photon pins evict categorized local pins after phase 2.
     */
    private fun mergePoiPins(
        local: List<SearchResultPlace>,
        photon: List<SearchResultPlace>,
    ): List<SearchResultPlace> {
        val merged = mutableListOf<SearchResultPlace>()

        fun findDuplicateIndex(place: SearchResultPlace): Int? {
            for (i in merged.indices) {
                val existing = merged[i]
                val distanceResults = FloatArray(1)
                Location.distanceBetween(
                    existing.latitude,
                    existing.longitude,
                    place.latitude,
                    place.longitude,
                    distanceResults,
                )
                if (distanceResults[0] < DEDUP_THRESHOLD_M) return i
            }
            return null
        }

        for (place in local + photon) {
            val duplicateIndex = findDuplicateIndex(place)
            if (duplicateIndex == null) {
                merged.add(place)
            } else if (merged[duplicateIndex].category.isBlank() && place.category.isNotBlank()) {
                merged[duplicateIndex] = merged[duplicateIndex].copy(category = place.category)
            }
        }
        return merged.take(MAX_POI_PINS)
    }

    private fun parsePhotonResponse(
        json: String,
        currentLat: Double,
        currentLng: Double,
    ): List<SearchResultPlace> {
        val root = JSONObject(json)
        val features = root.optJSONArray("features") ?: return emptyList()
        return (0 until features.length()).mapNotNull { index ->
            val feature = features.getJSONObject(index)
            val geometry = feature.optJSONObject("geometry") ?: return@mapNotNull null
            val coordinates = geometry.optJSONArray("coordinates") ?: return@mapNotNull null
            if (coordinates.length() < 2) return@mapNotNull null

            val placeLng = coordinates.getDouble(0)
            val placeLat = coordinates.getDouble(1)
            val properties = feature.optJSONObject("properties") ?: return@mapNotNull null

            val name = properties.optString("name").takeIf { it.isNotBlank() }
                ?: properties.optString("street").takeIf { it.isNotBlank() }
                ?: properties.optString("city").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null

            val subTitle = listOfNotNull(
                properties.optString("street").takeIf { it.isNotBlank() && it != name },
                properties.optString("city").takeIf { it.isNotBlank() },
                properties.optString("country").takeIf { it.isNotBlank() },
            ).joinToString(", ")

            val distanceResults = FloatArray(1)
            Location.distanceBetween(currentLat, currentLng, placeLat, placeLng, distanceResults)

            val osmKey = properties.optString("osm_key")
            val osmValue = properties.optString("osm_value")
            val category = photonToCategory(osmKey, osmValue)

            SearchResultPlace(
                name = name,
                subTitle = subTitle,
                latitude = placeLat,
                longitude = placeLng,
                distanceInMeters = distanceResults[0],
                category = category,
            )
        }.sortedBy { it.distanceInMeters }
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

    override fun recenterCamera() {
        val map = mapLibreMap ?: return
        if (_uiState.value.isNavigating) {
            enterNavigationCamera()
        } else {
            hasSnappedCameraToGps = false
            val target = lastKnownLocation ?: resolveFreeDriveTarget(map)
            if (target != null) {
                snapCameraToGpsIfNeeded(target)
            } else {
                activateFreeDriveTrackingMode(map)
                _uiState.update { it.copy(isCameraDetached = false) }
            }
        }
    }

    override fun startFreeDrive() {
        deadReckoningJob?.cancel()
        deadReckoningJob = null
        routeOverviewJob?.cancel()
        routeOverviewJob = null
        poiRefreshJob?.cancel()
        poiRefreshJob = null
        fullRouteSteps = emptyList()
        currentStepIndex = 0
        destinationLatLng = null
        navigationArrivalTriggered = false
        routeGeometryPoints = emptyList()
        offRouteCount = 0
        isRerouteInProgress = false
        hasSnappedCameraToGps = false

        _uiState.value = MapUiState(
            isNavigating = false,
            streetName = "Free Drive",
            routeOverviewProgress = 0f,
        )

        val map = mapLibreMap
        if (map != null) {
            applyFreeDriveToMap(map)
            clearPoiLayer()
        } else {
            mapView?.getMapAsync { loadedMap ->
                applyFreeDriveToMap(loadedMap)
                clearPoiLayer()
            }
        }
    }

    override fun dismissSelectedPoi() {
        _uiState.update { it.copy(selectedPoi = null) }
    }

    private fun applyFreeDriveToMap(map: MapLibreMap) {
        applyDrivingViewportPadding(map)
        map.getStyle { style ->
            runCatching { style.removeLayer(ROUTE_LAYER_ID) }
            runCatching { style.removeLayer(ROUTE_CASING_LAYER_ID) }
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
        map.cameraPosition.zoom < freeDriveZoom - 0.5

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

        val alreadyTracking = component.cameraMode == CameraMode.TRACKING_GPS &&
            component.renderMode == RenderMode.GPS
        if (!alreadyTracking) {
            component.renderMode = RenderMode.GPS
            component.cameraMode = CameraMode.TRACKING_GPS
        }
        applyDrivingTrackingPadding(map)
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
        }

        hasSnappedCameraToGps = true
        _uiState.update { it.copy(streetName = "Free Drive", isCameraDetached = false) }
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

    private fun startNavigation(
        origin: LatLng,
        lat: Double,
        lng: Double,
        isReroute: Boolean = false,
    ) {
        if (!isReroute) {
            poiRefreshJob?.cancel()
            poiRefreshJob = null
            clearPoiLayer()
        }
        _uiState.update {
            it.copy(
                isNavigating = true,
                streetName = if (isReroute) "Re-routing..." else "Calculating route...",
                selectedPoi = null,
            )
        }

        engineScope.launch {
            try {
                val route = withContext(Dispatchers.IO) {
                    fetchOsrmRoute(origin.longitude, origin.latitude, lng, lat)
                }
                if (route != null) {
                    routeGeometryPoints = route.geometryPoints
                    drawRoute(route.geometryJson)
                    fullRouteSteps = route.steps
                    currentStepIndex = 0
                    destinationLatLng = LatLng(lat, lng)
                    navigationArrivalTriggered = false
                    offRouteCount = 0
                    _uiState.update {
                        it.copy(
                            isNavigating = true,
                            streetName = route.streetName,
                            turnInstruction = route.instruction,
                            distanceToNextTurn = route.distance,
                        )
                    }
                    if (isReroute) {
                        isRerouteInProgress = false
                        enterNavigationCamera()
                    } else {
                        showRouteThenDive(origin, LatLng(lat, lng))
                    }
                } else {
                    isRerouteInProgress = false
                    _uiState.update { it.copy(isNavigating = false, streetName = "Route not found") }
                }
            } catch (e: Exception) {
                isRerouteInProgress = false
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

        val geometry = route.getJSONObject("geometry")
        val geometryJson = geometry.toString()
        val geometryPoints = parseRouteGeometryPoints(geometry)

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
            geometryPoints = geometryPoints,
            streetName = firstStep.streetName.ifBlank { "On route" },
            instruction = firstStep.instruction,
            distance = firstStep.distanceLabel,
            steps = allSteps,
        )
    }

    private fun parseRouteGeometryPoints(geometry: JSONObject): List<LatLng> {
        val coordinates = geometry.optJSONArray("coordinates") ?: return emptyList()
        return buildList {
            for (i in 0 until coordinates.length()) {
                val point = coordinates.optJSONArray(i) ?: continue
                if (point.length() < 2) continue
                add(LatLng(point.getDouble(1), point.getDouble(0)))
            }
        }
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
        routeOverviewJob?.cancel()

        val component = map.locationComponent
        if (component.isLocationComponentActivated && component.isLocationComponentEnabled) {
            component.cameraMode = CameraMode.NONE
            component.paddingWhileTracking(doubleArrayOf(0.0, 0.0, 0.0, 0.0))
        }
        map.cancelTransitions()
        map.setPadding(0, 0, 0, 0)

        val bounds = buildRouteOverviewBounds(origin, destination)
        val padding = computeRouteOverviewPadding(map)
        val animateToBounds = {
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
            map.animateCamera(
                CameraUpdateFactory.newLatLngBounds(
                    bounds,
                    padding.left,
                    padding.top,
                    padding.right,
                    padding.bottom,
                ),
                ROUTE_OVERVIEW_ANIMATION_MS,
            )
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
            _uiState.update { it.copy(routeOverviewProgress = 0f) }
            if (_uiState.value.isNavigating) {
                enterNavigationCamera()
            }
        }
    }

    private fun buildRouteOverviewBounds(origin: LatLng, destination: LatLng): LatLngBounds {
        val builder = LatLngBounds.Builder()
        if (routeGeometryPoints.isNotEmpty()) {
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

    private fun enterNavigationCamera() {
        val map = mapLibreMap ?: return
        val component = map.locationComponent
        val componentReady = component.isLocationComponentActivated &&
            component.isLocationComponentEnabled
        val target = lastKnownLocation ?: map.cameraPosition.target ?: return

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
        _uiState.update { it.copy(isCameraDetached = false) }
    }

    private fun activateNavigationTracking(componentReady: Boolean) {
        if (!_uiState.value.isNavigating) return
        val map = mapLibreMap ?: return
        val component = map.locationComponent
        if (componentReady && component.isLocationComponentActivated && component.isLocationComponentEnabled) {
            component.renderMode = RenderMode.GPS
            component.cameraMode = CameraMode.TRACKING_GPS
            applyDrivingTrackingPadding(map)
        }
    }

    private data class ViewportPadding(
        val left: Int,
        val top: Int,
        val right: Int = 0,
        val bottom: Int = 0,
    )

    private fun computeDrivingViewportPadding(map: MapLibreMap): ViewportPadding {
        val w = map.width
        val h = map.height
        if (w > 0 && h > 0) {
            return ViewportPadding(
                left = (w * puckHorizontalOffset).toInt(),
                top = (h * puckVerticalOffset).toInt(),
            )
        }
        val dm = appContext?.resources?.displayMetrics
        val fallbackW = dm?.widthPixels?.takeIf { it > 0 } ?: 1080
        val fallbackH = dm?.heightPixels?.takeIf { it > 0 } ?: 1920
        return ViewportPadding(
            left = (fallbackW * puckHorizontalOffset).toInt(),
            top = (fallbackH * puckVerticalOffset).toInt(),
        )
    }

    private fun applyDrivingViewportPadding(map: MapLibreMap) {
        val padding = computeDrivingViewportPadding(map)
        map.setPadding(padding.left, padding.top, padding.right, padding.bottom)
    }

    /** Map padding alone does not offset the puck during TRACKING_GPS — use paddingWhileTracking too. */
    private fun applyDrivingTrackingPadding(map: MapLibreMap) {
        val padding = computeDrivingViewportPadding(map)
        map.setPadding(padding.left, padding.top, padding.right, padding.bottom)
        val component = map.locationComponent
        if (!component.isLocationComponentActivated || !component.isLocationComponentEnabled) return
        // Always store the tracking padding regardless of current camera mode — it will take
        // effect immediately in TRACKING_GPS mode and when tracking resumes after detach.
        component.paddingWhileTracking(
            doubleArrayOf(
                padding.left.toDouble(),
                padding.top.toDouble(),
                padding.right.toDouble(),
                padding.bottom.toDouble(),
            ),
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
                val casingLayer = LineLayer(ROUTE_CASING_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                    PropertyFactory.lineColor(ROUTE_CASING_COLOR),
                    PropertyFactory.lineWidth(ROUTE_CASING_WIDTH),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineOpacity(1f),
                )
                val routeLayer = LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                    PropertyFactory.lineColor(ROUTE_COLOR),
                    PropertyFactory.lineWidth(ROUTE_WIDTH),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineOpacity(0.9f),
                )
                val baseLayerId = if (style.getLayer(TRAFFIC_LAYER_ID) != null) {
                    TRAFFIC_LAYER_ID
                } else {
                    RASTER_BASE_LAYER_ID
                }
                if (style.getLayer(baseLayerId) != null) {
                    style.addLayerAbove(casingLayer, baseLayerId)
                    style.addLayerAbove(routeLayer, ROUTE_CASING_LAYER_ID)
                } else {
                    style.addLayer(casingLayer)
                    style.addLayer(routeLayer)
                }
            }
        }
    }

    private fun registerPoiInteractions(map: MapLibreMap) {
        map.addOnCameraIdleListener {
            if (_uiState.value.isNavigating) {
                clearPoiLayer()
                return@addOnCameraIdleListener
            }

            val zoom = map.cameraPosition.zoom
            if (zoom < MIN_POI_ZOOM) {
                clearPoiLayer()
                return@addOnCameraIdleListener
            }

            poiRefreshJob?.cancel()
            poiRefreshJob = engineScope.launch {
                delay(POI_DEBOUNCE_MS)
                if (!isActive) return@launch
                val bounds = map.projection.visibleRegion.latLngBounds
                val center = map.cameraPosition.target ?: return@launch

                val latSpan = bounds.northEast.latitude - bounds.southWest.latitude
                val lngSpan = bounds.northEast.longitude - bounds.southWest.longitude
                val padLat = latSpan * BBOX_PADDING_FACTOR / 2
                val padLng = lngSpan * BBOX_PADDING_FACTOR / 2
                val queryBounds = expandGeoBounds(bounds, padLat, padLng)

                val tileResults = queryTilePois(map, queryBounds)
                val localResults = withContext(Dispatchers.IO) {
                    (localPlaces?.getPlacesInBounds(
                        minLat = queryBounds.minLat,
                        maxLat = queryBounds.maxLat,
                        minLng = queryBounds.minLng,
                        maxLng = queryBounds.maxLng,
                        limit = MAX_POI_PINS,
                    ) ?: emptyList()).map { place ->
                        place.copy(category = normalizeOvertureCategory(place.category))
                    }
                }
                if (!isActive) return@launch

                val dedupedFirstPass = mergePoiPins(localResults + tileResults, emptyList())
                mergeIntoPoiCache(dedupedFirstPass)
                evictPoiCacheOutsideBounds(bounds, padLat * 2, padLng * 2)
                if (poiCache.isNotEmpty()) {
                    updatePoiLayerFromCache()
                } else {
                    clearPoiLayer()
                }

                val photonResults = withContext(Dispatchers.IO) {
                    if (shouldQueryPhoton(center)) {
                        fetchPhotonNearby(center, queryBounds)
                    } else {
                        emptyList()
                    }
                }
                if (!isActive) return@launch

                if (photonResults.isNotEmpty()) {
                    mergeIntoPoiCache(mergePoiPins(localResults, photonResults))
                    evictPoiCacheOutsideBounds(bounds, padLat * 2, padLng * 2)
                    updatePoiLayerFromCache()
                } else if (poiCache.isEmpty()) {
                    clearPoiLayer()
                }
            }
        }

        map.addOnMapClickListener { latLng ->
            val screenPoint = map.projection.toScreenLocation(latLng)
            val features = map.queryRenderedFeatures(screenPoint, POI_LAYER_ID)
            val feature = features.firstOrNull() ?: return@addOnMapClickListener false

            val name = feature.getStringProperty("name") ?: return@addOnMapClickListener false
            val subtitle = feature.getStringProperty("subtitle").orEmpty()
            val category = feature.getStringProperty("category").orEmpty()
            val lat = feature.getNumberProperty("lat")?.toDouble() ?: return@addOnMapClickListener false
            val lng = feature.getNumberProperty("lng")?.toDouble() ?: return@addOnMapClickListener false

            val distanceResults = FloatArray(1)
            val reference = lastKnownLocation
            val distanceInMeters = if (reference != null) {
                Location.distanceBetween(
                    reference.latitude,
                    reference.longitude,
                    lat,
                    lng,
                    distanceResults,
                )
                distanceResults[0]
            } else {
                0f
            }

            _uiState.update {
                it.copy(
                    selectedPoi = SearchResultPlace(
                        name = name,
                        subTitle = subtitle,
                        latitude = lat,
                        longitude = lng,
                        distanceInMeters = distanceInMeters,
                        category = category,
                    ),
                )
            }
            true
        }
    }

    private suspend fun fetchPhotonNearby(
        center: LatLng,
        bounds: GeoBounds,
    ): List<SearchResultPlace> {
        val encodedQuery = URLEncoder.encode("+", "UTF-8")
        val url = URL(
            "https://photon.komoot.io/api/" +
                "?q=$encodedQuery&lat=${center.latitude}&lon=${center.longitude}&limit=50",
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "MixAutoCarLauncher/1.0")
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        return try {
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "Photon nearby HTTP error: ${connection.responseCode}")
                emptyList()
            } else {
                val body = connection.inputStream.bufferedReader().readText()
                lastPhotonQueryCenter = center
                filterPlacesToBounds(
                    places = parsePhotonResponse(body, center.latitude, center.longitude),
                    bounds = bounds,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Photon nearby fetch failed: ${e.message}")
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    private fun searchPoiCache(
        query: String,
        currentLat: Double,
        currentLng: Double,
    ): List<SearchResultPlace> {
        val tokens = query.trim()
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }
        if (tokens.isEmpty()) return emptyList()

        return poiCache.values
            .filter { place ->
                tokens.all { token -> place.name.lowercase().contains(token) }
            }
            .map { place ->
                val distanceResults = FloatArray(1)
                Location.distanceBetween(
                    currentLat,
                    currentLng,
                    place.latitude,
                    place.longitude,
                    distanceResults,
                )
                place.copy(distanceInMeters = distanceResults[0])
            }
            .sortedBy { it.distanceInMeters }
            .take(POI_CACHE_SEARCH_LIMIT)
    }

    private fun queryTilePois(map: MapLibreMap, queryBounds: GeoBounds): List<SearchResultPlace> {
        if (!useVectorTiles) return emptyList()

        val source = map.style?.getSourceAs<VectorSource>(OPENMAPTILES_SOURCE_ID) ?: return emptyList()
        val reference = lastKnownLocation
        return runCatching {
            source.querySourceFeatures(arrayOf(OPENMAPTILES_POI_LAYER), null)
                .mapNotNull { feature -> tileFeatureToPlace(feature, reference, queryBounds) }
                .distinctBy { "${it.latitude},${it.longitude}" }
                .take(MAX_POI_PINS)
        }.getOrElse { error ->
            Log.w(TAG, "Tile POI query failed: ${error.message}")
            emptyList()
        }
    }

    private fun tileFeatureToPlace(
        feature: org.maplibre.geojson.Feature,
        reference: LatLng?,
        queryBounds: GeoBounds,
    ): SearchResultPlace? {
        val name = resolveTileFeatureName(feature) ?: return null
        val (lat, lng) = extractPointCoordinates(feature.geometry()) ?: return null
        if (lat !in queryBounds.minLat..queryBounds.maxLat ||
            lng !in queryBounds.minLng..queryBounds.maxLng
        ) {
            return null
        }

        val cls = feature.getStringProperty("class").orEmpty()
        val sub = feature.getStringProperty("subclass").orEmpty()
        val distanceInMeters = if (reference != null) {
            val distanceResults = FloatArray(1)
            Location.distanceBetween(
                reference.latitude,
                reference.longitude,
                lat,
                lng,
                distanceResults,
            )
            distanceResults[0]
        } else {
            0f
        }
        return SearchResultPlace(
            name = name,
            subTitle = cls.ifBlank { sub },
            latitude = lat,
            longitude = lng,
            distanceInMeters = distanceInMeters,
            category = maplibreClassToCategory(cls, sub),
        )
    }

    private fun resolveTileFeatureName(feature: org.maplibre.geojson.Feature): String? {
        return listOf("name", "name_en", "name:latin", "name:nonlatin")
            .asSequence()
            .mapNotNull { key -> feature.getStringProperty(key)?.takeIf { it.isNotBlank() } }
            .firstOrNull()
    }

    private fun extractPointCoordinates(geometry: Geometry?): Pair<Double, Double>? {
        return when (geometry) {
            is Point -> geometry.latitude() to geometry.longitude()
            is MultiPoint -> {
                val first = geometry.coordinates().firstOrNull() ?: return null
                first.latitude() to first.longitude()
            }
            else -> null
        }
    }

    private fun filterPlacesToBounds(
        places: List<SearchResultPlace>,
        bounds: GeoBounds,
    ): List<SearchResultPlace> {
        return places.filter { place -> placeInBounds(place, bounds) }
    }

    private data class GeoBounds(
        val minLat: Double,
        val maxLat: Double,
        val minLng: Double,
        val maxLng: Double,
    )

    private fun expandGeoBounds(
        bounds: LatLngBounds,
        padLat: Double,
        padLng: Double,
    ): GeoBounds {
        return GeoBounds(
            minLat = bounds.southWest.latitude - padLat,
            maxLat = bounds.northEast.latitude + padLat,
            minLng = bounds.southWest.longitude - padLng,
            maxLng = bounds.northEast.longitude + padLng,
        )
    }

    private fun placeInBounds(place: SearchResultPlace, bounds: GeoBounds): Boolean {
        return place.latitude in bounds.minLat..bounds.maxLat &&
            place.longitude in bounds.minLng..bounds.maxLng
    }

    private fun poiCacheKey(place: SearchResultPlace): String =
        "${place.latitude},${place.longitude}"

    private fun mergeIntoPoiCache(places: List<SearchResultPlace>) {
        for (place in places) {
            val key = poiCacheKey(place)
            val existing = poiCache[key]
            if (existing == null) {
                poiCache[key] = place
            } else if (existing.category.isBlank() && place.category.isNotBlank()) {
                poiCache[key] = existing.copy(category = place.category)
            }
        }
    }

    private fun evictPoiCacheOutsideBounds(
        visibleBounds: LatLngBounds,
        padLat: Double,
        padLng: Double,
    ) {
        val evictBounds = expandGeoBounds(visibleBounds, padLat, padLng)
        poiCache.entries.removeIf { (_, place) -> !placeInBounds(place, evictBounds) }
    }

    private fun updatePoiLayerFromCache() {
        val pins = mergePoiPins(poiCache.values.toList(), emptyList())
        if (pins.isNotEmpty()) {
            updatePoiLayer(pins)
        } else {
            clearPoiLayer()
        }
    }

    private fun clearPoiCache() {
        poiCache.clear()
    }

    private fun shouldQueryPhoton(center: LatLng): Boolean {
        val lastCenter = lastPhotonQueryCenter ?: return true
        val distanceResults = FloatArray(1)
        Location.distanceBetween(
            lastCenter.latitude,
            lastCenter.longitude,
            center.latitude,
            center.longitude,
            distanceResults,
        )
        return distanceResults[0] > PHOTON_MOVE_THRESHOLD_M
    }

    private fun updatePoiLayer(places: List<SearchResultPlace>) {
        val map = mapLibreMap ?: return
        val geoJson = buildPoiGeoJson(places)
        map.getStyle { style ->
            val existing = style.getSource(POI_SOURCE_ID)
            if (existing is GeoJsonSource) {
                existing.setGeoJson(geoJson)
            } else {
                style.addSource(GeoJsonSource(POI_SOURCE_ID, geoJson))
                style.addLayer(
                    SymbolLayer(POI_LAYER_ID, POI_SOURCE_ID).withProperties(
                        PropertyFactory.iconImage(poiCategoryIconExpression()),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                        PropertyFactory.iconSize(1f),
                    ),
                )
            }
        }
    }

    private fun clearPoiLayer() {
        clearPoiCache()
        val map = mapLibreMap ?: return
        map.getStyle { style ->
            val existing = style.getSource(POI_SOURCE_ID)
            if (existing is GeoJsonSource) {
                existing.setGeoJson(EMPTY_POI_GEOJSON)
            }
        }
    }

    private fun buildPoiGeoJson(places: List<SearchResultPlace>): String {
        val features = places.map { place ->
            JSONObject().apply {
                put("type", "Feature")
                put(
                    "geometry",
                    JSONObject().apply {
                        put("type", "Point")
                        put(
                            "coordinates",
                            JSONArray().apply {
                                put(place.longitude)
                                put(place.latitude)
                            },
                        )
                    },
                )
                put(
                    "properties",
                    JSONObject().apply {
                        put("name", place.name)
                        put("subtitle", place.subTitle)
                        put("lat", place.latitude)
                        put("lng", place.longitude)
                        put("category", place.category)
                    },
                )
            }
        }
        return JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", JSONArray(features))
        }.toString()
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
            val componentOptions = LocationComponentOptions.builder(ctx)
                .maxZoomIconScale(puckScale)
                .minZoomIconScale(puckScale)
                .build()
            val options = LocationComponentActivationOptions.builder(ctx, style)
                .locationEngine(locationEngine)
                .locationComponentOptions(componentOptions)
                .build()
            if (!locationComponent.isLocationComponentActivated) {
                locationComponent.activateLocationComponent(options)
            } else {
                locationComponent.applyStyle(componentOptions)
            }
            locationComponent.isLocationComponentEnabled = true
            locationComponent.renderMode = RenderMode.GPS
            applyDrivingTrackingPadding(map)

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

    private fun enrichLocationWithBearing(location: Location): Location {
        val prev = previousLocationFix
        previousLocationFix = location
        val isStopped = location.hasSpeed() && location.speed < STOPPED_SPEED_MPS
        if (isStopped) {
            val frozen = Location(location)
            frozen.bearing = lastStableBearing ?: location.bearing
            return frozen
        }
        if (location.hasBearing() && location.bearing != 0f) {
            lastStableBearing = location.bearing
            return location
        }
        // Distance gate: need >= 10 m of movement for a stable heading (above GPS jitter floor)
        if (prev != null && prev.distanceTo(location) > 10f) {
            val enriched = Location(location)
            enriched.bearing = prev.bearingTo(location)
            lastStableBearing = enriched.bearing
            return enriched
        }
        return location
    }

    private fun startDeadReckoning(from: Location) {
        deadReckoningJob?.cancel()
        val speed = if (from.hasSpeed()) from.speed else return
        if (speed < STOPPED_SPEED_MPS) return
        val bearing = from.bearing
        lastDeadReckoningLocation = from
        deadReckoningJob = engineScope.launch {
            val ref = from
            while (isActive) {
                delay(DEAD_RECKONING_INTERVAL_MS)
                val elapsed = (System.currentTimeMillis() - ref.time).coerceAtLeast(0)
                val dist = speed * (elapsed / 1000.0)
                val bearingRad = Math.toRadians(bearing.toDouble())
                val latRad = Math.toRadians(ref.latitude)
                val lat = ref.latitude + cos(bearingRad) * dist / METERS_PER_DEGREE_LAT
                val lng = ref.longitude + sin(bearingRad) * dist / (METERS_PER_DEGREE_LAT * cos(latRad))
                val extrapolated = Location(ref).apply {
                    latitude = lat
                    longitude = lng
                    time = System.currentTimeMillis()
                    this.bearing = bearing
                }
                lastDeadReckoningLocation = extrapolated
                val component = mapLibreMap?.locationComponent ?: break
                if (component.isLocationComponentActivated && component.isLocationComponentEnabled) {
                    component.forceLocationUpdate(extrapolated)
                }
            }
        }
    }

    private fun applyAndroidLocation(location: Location, snapCamera: Boolean) {
        val locationWithBearing = enrichLocationWithBearing(location)
        val latLng = LatLng(locationWithBearing.latitude, locationWithBearing.longitude)
        lastKnownLocation = latLng
        _uiState.update {
            it.copy(
                currentLat = locationWithBearing.latitude,
                currentLng = locationWithBearing.longitude,
            )
        }
        val component = mapLibreMap?.locationComponent
        if (component != null && component.isLocationComponentActivated && component.isLocationComponentEnabled) {
            component.forceLocationUpdate(locationWithBearing)
            pendingLocationFix = null
        } else {
            pendingLocationFix = locationWithBearing
        }
        startDeadReckoning(locationWithBearing)
        Log.i(
            TAG,
            "Location fix ${locationWithBearing.latitude}, ${locationWithBearing.longitude} " +
                "(provider=${locationWithBearing.provider}, bearing=${locationWithBearing.bearing}, " +
                "snapped=$snapCamera, zoom=${mapLibreMap?.cameraPosition?.zoom})",
        )
        when {
            _uiState.value.isNavigating -> evaluateStepAdvancement(locationWithBearing)
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

        checkOffRoute(currentLocation)
    }

    private fun checkOffRoute(currentLocation: Location) {
        if (navigationArrivalTriggered) return
        if (routeGeometryPoints.size < 2) return
        if (System.currentTimeMillis() < rerouteCooldownUntilMs) return
        if (isRerouteInProgress) return

        val distToRoute = distanceToRouteMeters(currentLocation, routeGeometryPoints)
        if (distToRoute > REROUTE_THRESHOLD_M) {
            offRouteCount++
            Log.i(TAG, "Off route: ${distToRoute.toInt()}m from path (count=$offRouteCount)")
        } else {
            offRouteCount = 0
            return
        }

        if (offRouteCount < REROUTE_CONFIRM_COUNT) return

        val dest = destinationLatLng ?: return
        offRouteCount = 0
        isRerouteInProgress = true
        rerouteCooldownUntilMs = System.currentTimeMillis() + REROUTE_COOLDOWN_MS
        Log.i(TAG, "Re-routing from alternate path")
        val origin = LatLng(currentLocation.latitude, currentLocation.longitude)
        lastKnownLocation = origin
        startNavigation(origin, dest.latitude, dest.longitude, isReroute = true)
    }

    private fun distanceToRouteMeters(location: Location, routePoints: List<LatLng>): Float {
        if (routePoints.isEmpty()) return Float.MAX_VALUE
        if (routePoints.size == 1) {
            val results = FloatArray(1)
            Location.distanceBetween(
                location.latitude,
                location.longitude,
                routePoints[0].latitude,
                routePoints[0].longitude,
                results,
            )
            return results[0]
        }

        var minDist = Float.MAX_VALUE
        for (i in 0 until routePoints.size - 1) {
            val segmentDist = distanceToSegmentMeters(
                location,
                routePoints[i],
                routePoints[i + 1],
            )
            if (segmentDist < minDist) {
                minDist = segmentDist
            }
        }
        return minDist
    }

    private fun distanceToSegmentMeters(
        point: Location,
        segStart: LatLng,
        segEnd: LatLng,
    ): Float {
        val px = point.longitude
        val py = point.latitude
        val ax = segStart.longitude
        val ay = segStart.latitude
        val bx = segEnd.longitude
        val by = segEnd.latitude

        val dx = bx - ax
        val dy = by - ay
        if (dx == 0.0 && dy == 0.0) {
            val results = FloatArray(1)
            Location.distanceBetween(py, px, ay, ax, results)
            return results[0]
        }

        var t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)
        t = t.coerceIn(0.0, 1.0)
        val closestLat = ay + t * dy
        val closestLng = ax + t * dx

        val results = FloatArray(1)
        Location.distanceBetween(py, px, closestLat, closestLng, results)
        return results[0]
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

    private fun normalizeOvertureCategory(raw: String): String {
        if (raw in POI_CATEGORY_GROUPS) return raw
        return when (raw.lowercase().trim()) {
            "food_and_beverage" -> "food"
            "gas_station" -> "fuel"
            "health_and_medical" -> "health"
            "accommodation" -> "accommodation"
            "financial" -> "finance"
            "retail" -> "shopping"
            "recreation", "entertainment" -> "recreation"
            else -> ""
        }
    }

    private fun maplibreClassToCategory(cls: String, sub: String): String {
        val values = listOf(cls, sub).map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        for (value in values) {
            when (value) {
                "food", "restaurant", "fast_food", "cafe", "bar", "pub", "food_court" -> return "food"
                "fuel" -> return "fuel"
                "hospital", "pharmacy", "clinic", "doctor", "doctors" -> return "health"
                "hotel", "hostel", "motel", "guest_house" -> return "accommodation"
                "bank", "atm" -> return "finance"
                "shop", "mall", "department_store", "supermarket" -> return "shopping"
                "attraction", "museum", "park", "stadium", "viewpoint" -> return "recreation"
            }
        }
        return ""
    }

    private fun photonToCategory(osmKey: String, osmValue: String): String {
        val key = osmKey.lowercase().trim()
        val value = osmValue.lowercase().trim()
        return when (key) {
            "amenity" -> when (value) {
                "restaurant", "cafe", "fast_food", "bar", "pub", "food_court" -> "food"
                "fuel" -> "fuel"
                "hospital", "pharmacy", "clinic", "doctors" -> "health"
                "bank", "atm" -> "finance"
                else -> ""
            }
            "tourism" -> when (value) {
                "hotel", "hostel", "motel", "guest_house" -> "accommodation"
                "attraction", "museum", "viewpoint" -> "recreation"
                else -> ""
            }
            "shop" -> "shopping"
            "leisure" -> "recreation"
            else -> ""
        }
    }

    private fun poiCategoryIconExpression(): Expression {
        return Expression.match(
            Expression.get("category"),
            Expression.literal("poi_icon_default"),
            Expression.stop("food", Expression.literal("poi_icon_food")),
            Expression.stop("fuel", Expression.literal("poi_icon_fuel")),
            Expression.stop("health", Expression.literal("poi_icon_health")),
            Expression.stop("accommodation", Expression.literal("poi_icon_accommodation")),
            Expression.stop("finance", Expression.literal("poi_icon_finance")),
            Expression.stop("shopping", Expression.literal("poi_icon_shopping")),
            Expression.stop("recreation", Expression.literal("poi_icon_recreation")),
        )
    }

    companion object {
        private const val TAG = "MapLibreEngineImpl"
        private const val DEFAULT_ZOOM = 15.0
        private const val DEFAULT_ZOOM_FALLBACK = 6.0
        private const val ROUTING_MIN_ZOOM = 10.0
        private const val NAV_TILT = 58.0
        private const val NAV_CAMERA_DURATION_MS = 2500
        private const val FREE_DRIVE_TILT = 50.0
        private const val RASTER_BASE_LAYER_ID = "osm"
        private const val TRAFFIC_SOURCE_ID = "mix-traffic-source"
        private const val TRAFFIC_LAYER_ID = "mix-traffic-layer"
        private const val REROUTE_THRESHOLD_M = 75f
        private const val REROUTE_CONFIRM_COUNT = 1
        private const val REROUTE_COOLDOWN_MS = 5_000L
        private const val ROUTE_OVERVIEW_BOUNDS_EXPAND_FRACTION = 0.12
        private const val ROUTE_OVERVIEW_MIN_BOUNDS_PAD_DEGREES = 0.0008
        private const val ROUTE_OVERVIEW_ANIMATION_MS = 2000
        private const val ROUTE_OVERVIEW_HOLD_MS = 10_000L
        private const val LOCATION_INTERVAL_MS = 1000L
        private const val FRESH_LOCATION_MIN_TIME_MS = 500L
        private const val LOCATION_POLL_INTERVAL_MS = 1000L
        private const val LOCATION_POLL_ATTEMPTS = 15
        private const val LOCATION_ACQUIRE_TIMEOUT_MS = 8000L
        private val LOCATION_RETRY_DELAYS_MS = longArrayOf(1_000L, 3_000L, 8_000L)
        private const val ROUTE_SOURCE_ID = "mix-route-source"
        private const val ROUTE_CASING_LAYER_ID = "mix-route-casing-layer"
        private const val ROUTE_LAYER_ID = "mix-route-layer"
        private const val OPENMAPTILES_SOURCE_ID = "openmaptiles"
        private const val OPENMAPTILES_POI_LAYER = "poi"
        private const val POI_SOURCE_ID = "mix-poi-source"
        private const val POI_LAYER_ID = "mix-poi-layer"
        private val POI_CATEGORY_GROUPS = setOf(
            "food",
            "fuel",
            "health",
            "accommodation",
            "finance",
            "shopping",
            "recreation",
        )
        private const val MIN_POI_ZOOM = 13.0
        private const val MAX_POI_PINS = 100
        private const val POI_CACHE_SEARCH_LIMIT = 15
        private const val POI_DEBOUNCE_MS = 400L
        private const val BBOX_PADDING_FACTOR = 1.5
        private const val PHOTON_MOVE_THRESHOLD_M = 300f
        private const val EMPTY_POI_GEOJSON = """{"type":"FeatureCollection","features":[]}"""
        private const val ROUTE_CASING_COLOR = "#CC000000"
        private const val ROUTE_CASING_WIDTH = 18f
        private const val ROUTE_COLOR = "#00CBD6"
        private const val ROUTE_WIDTH = 14f
        private const val STOPPED_SPEED_MPS = 1.4f
        private const val DEAD_RECKONING_INTERVAL_MS = 16L
        private const val METERS_PER_DEGREE_LAT = 111_320.0
        private const val STEP_ADVANCE_THRESHOLD_M = 25f
        private const val ARRIVAL_THRESHOLD_M = 15f
        private const val DEDUP_THRESHOLD_M = 50f
        private const val MAX_SEARCH_RADIUS_M = 500_000f
        private const val ARRIVAL_FREE_DRIVE_DELAY_MS = 1_500L
        private const val VECTOR_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
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
