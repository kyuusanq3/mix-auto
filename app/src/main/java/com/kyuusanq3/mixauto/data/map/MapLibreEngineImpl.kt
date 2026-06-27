package com.kyuusanq3.mixauto.data.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.graphics.RectF
import android.util.Log
import android.view.Gravity
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
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.PropertyValue
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

private data class ClosestSegmentResult(val distM: Float, val lat: Double, val lng: Double)

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
    initialUseVectorTiles: Boolean = true,
    initialShow3dBuildings: Boolean = false,
    initialDrivingZoom: Double = 17.5,
    initialPuckHOffset: Float = 0.3f,
    initialPuckVOffset: Float = 0.4f,
    initialPuckScale: Float = 1.0f,
) : CarMapEngine {

    private val _uiState = MutableStateFlow(MapUiState())
    override val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var useVectorTiles = initialUseVectorTiles
    private var show3dBuildings = initialShow3dBuildings
    private var trafficEnabled = false
    private var tomTomApiKey = ""
    private var freeDriveZoom = initialDrivingZoom
    private var navZoom = initialDrivingZoom + 1.0
    private var puckHorizontalOffset = initialPuckHOffset
    private var puckVerticalOffset = initialPuckVOffset
    private var puckScale = initialPuckScale
    private var mapTapDismissHandler: (() -> Unit)? = null

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
    private var savedPlacesKeys = emptySet<String>()
    private var savedPlacesCache = emptyList<SearchResultPlace>()
    private var routeGeometryPoints: List<LatLng> = emptyList()
    private var offRouteCount: Int = 0
    private var rerouteCooldownUntilMs: Long = 0L
    private var isRerouteInProgress: Boolean = false
    private var previousLocationFix: Location? = null
    private var lastStableBearing: Float? = null
    private var deadReckoningJob: Job? = null
    private var lastDeadReckoningLocation: Location? = null
    private var pendingPoiPreviewTarget: LatLng? = null
    private var pendingPoiPreviewZoom: Double = POI_PREVIEW_ZOOM
    private var poiPreviewRetryCount = 0
    private var poiPreviewRetryRunnable: Runnable? = null
    private var topDownViewportSyncRunnable: Runnable? = null

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
            view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                if (v.width <= 0 || v.height <= 0) return@addOnLayoutChangeListener
                mapLibreMap?.let { map ->
                    view.post { handleMapLayoutChange(map) }
                }
            }
            view.getMapAsync { map ->
                mapLibreMap = map
                configureCompassPosition(map, context)
                map.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        _uiState.update { it.copy(isCameraDetached = true) }
                        updatePuckScreenPosition(map)
                        if (_uiState.value.isInTopDownView) {
                            cancelTopDownViewportSync()
                            cancelPoiPreviewRetries()
                            pendingPoiPreviewTarget = null
                            ensureTopDownCameraDetached(map)
                        }
                    }
                }
                registerPoiInteractions(map)
                map.addOnCameraMoveListener { updatePuckScreenPosition(map) }
                applyMapStyle(map, context)
            }
            mapView = view
        }
    }

    private fun configureCompassPosition(map: MapLibreMap, context: Context) {
        val marginPx = (COMPASS_MARGIN_DP * context.resources.displayMetrics.density).toInt()
        map.uiSettings.apply {
            setCompassGravity(Gravity.BOTTOM or Gravity.END)
            setCompassMargins(marginPx, marginPx, marginPx, marginPx)
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
                nearbyPois = emptyList(),
                routeOverviewProgress = 0f,
            )
        }

        applyMapStyle(map, ctx)
    }

    override fun setShow3dBuildings(show: Boolean) {
        show3dBuildings = show
        val map = mapLibreMap ?: return
        map.getStyle { style -> apply3dBuildingVisibility(style) }
    }

    private fun apply3dBuildingVisibility(style: Style) {
        val visibility = if (show3dBuildings) Property.VISIBLE else Property.NONE
        style.layers.forEach { layer ->
            if (layer is FillExtrusionLayer) {
                layer.setProperties(PropertyFactory.visibility(visibility))
            }
        }
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
        updatePuckScreenPosition(map)
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
    }

    private fun updatePuckScreenPosition(map: MapLibreMap) {
        val component = map.locationComponent
        val hasActivePuck = component.isLocationComponentActivated &&
            component.isLocationComponentEnabled &&
            lastKnownLocation != null
        if (!hasActivePuck) {
            val state = _uiState.value
            if (state.puckScreenX == null && state.puckScreenY == null) return
            _uiState.update { it.copy(puckScreenX = null, puckScreenY = null) }
            return
        }

        val state = _uiState.value
        val point = if (
            !state.isCameraDetached &&
            !state.isInTopDownView &&
            component.cameraMode == CameraMode.TRACKING_GPS
        ) {
            val padding = computeDrivingViewportPadding(map)
            val w = map.width.toFloat()
            val h = map.height.toFloat()
            android.graphics.PointF(
                (w + padding.left) / 2f,
                (h + padding.top) / 2f,
            )
        } else {
            map.projection.toScreenLocation(lastKnownLocation!!)
        }
        if (state.puckScreenX == point.x && state.puckScreenY == point.y) return
        _uiState.update { it.copy(puckScreenX = point.x, puckScreenY = point.y) }
    }

    override fun setMapTapDismissHandler(handler: (() -> Unit)?) {
        mapTapDismissHandler = handler
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
            PoiIconFactory.createAllIcons(context).forEach { (id, bitmap) ->
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
            apply3dBuildingVisibility(style)
            updateSavedPlacesLayer(savedPlacesCache)
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
        cancelPoiPreviewRetries()
        cancelTopDownViewportSync()
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

        val (searchLat, searchLng) = resolveSearchOrigin(currentLat, currentLng)

        fun applyDistanceLimit(places: List<SearchResultPlace>): List<SearchResultPlace> =
            if (limitDistance) {
                places.filter { it.distanceInMeters <= MAX_SEARCH_RADIUS_M }
            } else {
                places
            }

        val hasOfflineData = localPlaces?.hasInstalledDatabase == true
        val local = if (hasOfflineData) {
            localPlaces?.searchPlaces(query, searchLat, searchLng).orEmpty()
        } else {
            emptyList()
        }
        val cacheResults = withContext(Dispatchers.Main) {
            searchPoiCache(query, searchLat, searchLng)
        }
        val localAndCache = mergeAndDeduplicate(local, cacheResults)
        val localAndCacheFiltered = applyDistanceLimit(localAndCache)
        if (localAndCacheFiltered.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                onLocalResults(localAndCacheFiltered)
            }
        }

        val photon = fetchPhoton(query, searchLat, searchLng)
        val finalResults = applyDistanceLimit(mergeAndDeduplicate(localAndCache, photon))
        Log.i(
            TAG,
            "searchDestination query=\"$query\" origin=$searchLat,$searchLng " +
                "reliable=${hasReliableSearchOrigin()} local=${local.size} cache=${cacheResults.size} " +
                "photon=${photon.size} final=${finalResults.size}",
        )
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

        val (searchLat, searchLng) = resolveSearchOrigin(lat, lng)
        val cacheResults = poiCache.values
            .map { place ->
                val distanceResults = FloatArray(1)
                Location.distanceBetween(
                    searchLat,
                    searchLng,
                    place.latitude,
                    place.longitude,
                    distanceResults,
                )
                place.copy(distanceInMeters = distanceResults[0])
            }
            .sortedBy { it.distanceInMeters }
            .take(limit)

        if (cacheResults.isNotEmpty()) return cacheResults

        val repo = localPlaces ?: return emptyList()
        if (!repo.hasInstalledDatabase) return emptyList()

        return repo.getPlacesInBounds(
            minLat = searchLat - NEARBY_SEARCH_BBOX_DELTA,
            maxLat = searchLat + NEARBY_SEARCH_BBOX_DELTA,
            minLng = searchLng - NEARBY_SEARCH_BBOX_DELTA,
            maxLng = searchLng + NEARBY_SEARCH_BBOX_DELTA,
            limit = limit,
        ).map { place ->
            val distanceResults = FloatArray(1)
            Location.distanceBetween(
                searchLat,
                searchLng,
                place.latitude,
                place.longitude,
                distanceResults,
            )
            place.copy(
                distanceInMeters = distanceResults[0],
                category = normalizeOvertureCategory(place.category),
            )
        }.sortedBy { it.distanceInMeters }
    }

    override fun resolveSearchOrigin(): Pair<Double, Double> {
        return resolveSearchOrigin(0.0, 0.0)
    }

    override fun hasReliableSearchOrigin(): Boolean {
        val state = _uiState.value
        if (state.currentLat != null && state.currentLng != null &&
            isValidSearchOrigin(state.currentLat, state.currentLng)
        ) {
            return true
        }
        lastKnownLocation?.let { loc ->
            if (isValidSearchOrigin(loc.latitude, loc.longitude)) return true
        }
        resolveMapViewOriginForSearch()?.let { return true }
        return false
    }

    override fun refreshSearchOrigin() {
        val ctx = appContext ?: return
        if (!hasLocationPermission(ctx)) return
        refreshLocationOnly(ctx)
        readLastKnownLocation(ctx)?.let { latLng ->
            lastKnownLocation = latLng
            syncSearchOriginToUiState(latLng.latitude, latLng.longitude)
        }
    }

    private fun resolveSearchOrigin(fallbackLat: Double, fallbackLng: Double): Pair<Double, Double> {
        val state = _uiState.value
        if (state.currentLat != null && state.currentLng != null &&
            isValidSearchOrigin(state.currentLat, state.currentLng)
        ) {
            return state.currentLat to state.currentLng
        }
        lastKnownLocation?.let { loc ->
            if (isValidSearchOrigin(loc.latitude, loc.longitude)) {
                return loc.latitude to loc.longitude
            }
        }
        resolveMapViewOriginForSearch()?.let { target ->
            return target.latitude to target.longitude
        }
        if (isValidSearchOrigin(fallbackLat, fallbackLng)) {
            return fallbackLat to fallbackLng
        }
        appContext?.let { ctx ->
            if (hasLocationPermission(ctx)) {
                readLastKnownLocation(ctx)?.let { latLng ->
                    if (isValidSearchOrigin(latLng.latitude, latLng.longitude)) {
                        lastKnownLocation = latLng
                        syncSearchOriginToUiState(latLng.latitude, latLng.longitude)
                        return latLng.latitude to latLng.longitude
                    }
                }
            }
        }
        Log.w(TAG, "Search origin unresolved; using Philippines fallback")
        return DEFAULT_LOCATION.latitude to DEFAULT_LOCATION.longitude
    }

    private fun isValidSearchOrigin(lat: Double, lng: Double): Boolean {
        if (lat == 0.0 && lng == 0.0) return false
        if (kotlin.math.abs(lat) < 0.01 && kotlin.math.abs(lng) < 0.01) return false
        return true
    }

    private fun syncSearchOriginToUiState(lat: Double, lng: Double) {
        _uiState.update { state ->
            if (state.currentLat == lat && state.currentLng == lng) {
                state
            } else {
                state.copy(currentLat = lat, currentLng = lng)
            }
        }
    }

    private fun resolveMapViewOriginForSearch(): LatLng? {
        val map = mapLibreMap ?: return null
        val position = map.cameraPosition
        val target = position.target ?: return null
        if (!_uiState.value.isNavigating && position.zoom < ROUTING_MIN_ZOOM) {
            return null
        }
        if (!isValidSearchOrigin(target.latitude, target.longitude)) {
            return null
        }
        return target
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
            cancelTopDownViewportSync()
            cancelPoiPreviewRetries()
            pendingPoiPreviewTarget = null
            _uiState.update { it.copy(isCameraDetached = false, isInTopDownView = false) }
            hasSnappedCameraToGps = false
            val target = lastKnownLocation ?: resolveFreeDriveTarget(map)
            if (target != null) {
                snapCameraToGpsIfNeeded(target)
            } else {
                activateFreeDriveTrackingMode(map)
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
        cancelTopDownViewportSync()
        cancelPoiPreviewRetries()
        pendingPoiPreviewTarget = null
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
            clearCustomPin()
        } else {
            mapView?.getMapAsync { loadedMap ->
                applyFreeDriveToMap(loadedMap)
                clearPoiLayer()
                clearCustomPin()
            }
        }
    }

    override fun dismissSelectedPoi() {
        cancelPoiPreviewRetries()
        pendingPoiPreviewTarget = null
        _uiState.update {
            it.copy(
                selectedPoi = null,
                nearbyPois = emptyList(),
                isInTopDownView = false,
            )
        }
        clearCustomPin()
        updatePoiLayerFromCache()
        mapLibreMap?.triggerRepaint()
    }

    override fun focusOnLocation(lat: Double, lng: Double) {
        animateTopDownCamera(lat, lng, POI_PREVIEW_ZOOM)
    }

    override fun focusOnPoi(place: SearchResultPlace, moveCamera: Boolean) {
        if (moveCamera) {
            _uiState.update { it.copy(isCameraDetached = true, isInTopDownView = true) }
        }
        val distanceInMeters = computeDistanceFromReference(place.latitude, place.longitude)
        val nearbyPois = if (place.isDroppedPin) {
            getNearbyPois(place.latitude, place.longitude, limit = 10)
                .filter { nearby ->
                    val distanceResults = FloatArray(1)
                    Location.distanceBetween(
                        place.latitude,
                        place.longitude,
                        nearby.latitude,
                        nearby.longitude,
                        distanceResults,
                    )
                    distanceResults[0] >= NEARBY_PIN_DEDUP_THRESHOLD_M
                }
                .take(2)
        } else {
            emptyList()
        }
        _uiState.update {
            it.copy(
                selectedPoi = place.copy(distanceInMeters = distanceInMeters),
                nearbyPois = nearbyPois,
            )
        }
        if (place.isDroppedPin) {
            if (isSavedPlace(place)) {
                clearCustomPin()
            } else {
                placeCustomPin(place.latitude, place.longitude, pending = false)
            }
        } else {
            clearCustomPin()
        }
        if (moveCamera) {
            clearPoiOverlay()
            focusOnLocation(place.latitude, place.longitude)
        }
    }

    override fun onMapHostLayoutChanged() {
        val map = mapLibreMap ?: return
        val view = mapView ?: return
        if (view.width <= 0 || view.height <= 0) return
        view.post { handleMapLayoutChange(map) }
    }

    override fun enterTopDownView() {
        val map = mapLibreMap ?: return
        val target = lastKnownLocation
            ?: resolveFreeDriveTarget(map)
            ?: map.cameraPosition.target
            ?: return
        animateTopDownCamera(
            target.latitude,
            target.longitude,
            TOP_DOWN_EXPLORE_ZOOM,
            exploreMode = true,
        )
    }

    private fun animateTopDownCamera(
        lat: Double,
        lng: Double,
        zoom: Double,
        exploreMode: Boolean = false,
    ) {
        val map = mapLibreMap ?: return
        val targetZoom = if (exploreMode) {
            zoom
        } else {
            map.cameraPosition.zoom.coerceAtLeast(zoom)
        }
        deadReckoningJob?.cancel()
        deadReckoningJob = null
        val component = map.locationComponent
        if (component.isLocationComponentActivated && component.isLocationComponentEnabled) {
            component.cameraMode = CameraMode.NONE
        }
        _uiState.update { it.copy(isCameraDetached = true, isInTopDownView = true) }
        clearViewportPaddingForPreview(map)
        if (useVectorTiles) {
            map.getStyle { showNativeVectorPoiLayers(it) }
        }
        clearPoiOverlay()
        pendingPoiPreviewTarget = LatLng(lat, lng)
        pendingPoiPreviewZoom = targetZoom
        poiPreviewRetryCount = 0
        mapView?.requestLayout()
        schedulePoiPreviewCameraRetry(immediate = true)
    }

    private fun cancelPoiPreviewRetries() {
        poiPreviewRetryRunnable?.let { runnable ->
            mapView?.removeCallbacks(runnable)
        }
        poiPreviewRetryRunnable = null
        poiPreviewRetryCount = 0
    }

    private fun schedulePoiPreviewCameraRetry(immediate: Boolean = false) {
        val map = mapLibreMap ?: return
        val view = mapView ?: return
        poiPreviewRetryRunnable?.let { view.removeCallbacks(it) }
        poiPreviewRetryRunnable = null
        val runnable = Runnable {
            poiPreviewRetryRunnable = null
            if (applyPoiPreviewCamera(map)) return@Runnable
            if (poiPreviewRetryCount < POI_PREVIEW_MAX_RETRIES) {
                poiPreviewRetryCount++
                schedulePoiPreviewCameraRetry(immediate = false)
            } else {
                poiPreviewRetryCount = 0
            }
        }
        poiPreviewRetryRunnable = runnable
        if (immediate) {
            view.post { view.post(runnable) }
        } else {
            view.postDelayed(runnable, POI_PREVIEW_RETRY_DELAY_MS * poiPreviewRetryCount.coerceAtLeast(1))
        }
    }

    private fun applyPoiPreviewCamera(map: MapLibreMap): Boolean {
        val view = mapView ?: return false
        if (view.width <= 0 || view.height <= 0) return false
        val target = pendingPoiPreviewTarget ?: return true

        val component = map.locationComponent
        if (component.isLocationComponentActivated && component.isLocationComponentEnabled) {
            component.cameraMode = CameraMode.NONE
        }
        clearViewportPaddingForPreview(map)
        if (useVectorTiles) {
            map.getStyle { style -> showNativeVectorPoiLayers(style) }
        }

        val zoom = pendingPoiPreviewZoom
        map.cancelTransitions()
        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(target)
                    .zoom(zoom)
                    .tilt(0.0)
                    .bearing(0.0)
                    .build(),
            ),
        )
        pendingPoiPreviewTarget = null
        map.triggerRepaint()
        view.invalidate()
        scheduleTopDownViewportSync(map)
        return true
    }

    private fun ensureTopDownCameraDetached(map: MapLibreMap) {
        val component = map.locationComponent
        if (component.isLocationComponentActivated && component.isLocationComponentEnabled) {
            component.cameraMode = CameraMode.NONE
        }
    }

    /** Re-apply zero padding on the next frame so tiles fill the full MapView. */
    private fun syncTopDownViewportPaddingOnly(map: MapLibreMap) {
        ensureTopDownCameraDetached(map)
        clearViewportPaddingForPreview(map)
        map.triggerRepaint()
        mapView?.invalidate()
    }

    private fun cancelTopDownViewportSync() {
        topDownViewportSyncRunnable?.let { runnable ->
            mapView?.removeCallbacks(runnable)
        }
        topDownViewportSyncRunnable = null
    }

    private fun scheduleTopDownViewportSync(map: MapLibreMap) {
        val view = mapView ?: return
        cancelTopDownViewportSync()
        val runnable = Runnable {
            topDownViewportSyncRunnable = null
            if (!_uiState.value.isInTopDownView) return@Runnable
            syncTopDownViewportPaddingOnly(map)
        }
        topDownViewportSyncRunnable = runnable
        view.post(runnable)
    }

    private fun recenterOnSelectedPoi(map: MapLibreMap, place: SearchResultPlace) {
        refreshTopDownCamera(
            map,
            LatLng(place.latitude, place.longitude),
            map.cameraPosition.zoom.coerceAtLeast(POI_PREVIEW_ZOOM),
        )
    }

    private fun refreshTopDownCamera(map: MapLibreMap, target: LatLng, zoom: Double) {
        val view = mapView ?: return
        val component = map.locationComponent
        if (component.isLocationComponentActivated && component.isLocationComponentEnabled) {
            component.cameraMode = CameraMode.NONE
        }
        clearViewportPaddingForPreview(map)
        map.cancelTransitions()
        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(target)
                    .zoom(zoom)
                    .tilt(0.0)
                    .bearing(0.0)
                    .build(),
            ),
        )
        map.triggerRepaint()
        view.invalidate()
        scheduleTopDownViewportSync(map)
    }

    private fun handleMapLayoutChange(map: MapLibreMap) {
        val view = mapView ?: return
        if (view.width <= 0 || view.height <= 0) return

        val state = _uiState.value
        if (state.isInTopDownView) {
            when {
                pendingPoiPreviewTarget != null -> schedulePoiPreviewCameraRetry(immediate = true)
                state.selectedPoi != null -> recenterOnSelectedPoi(map, state.selectedPoi)
                else -> syncTopDownViewportPaddingOnly(map)
            }
            updatePuckScreenPosition(map)
            return
        }
        applyDrivingTrackingPadding(map)
        updatePuckScreenPosition(map)
    }

    private fun applyMapPaddingImmediate(map: MapLibreMap, padding: ViewportPadding) {
        map.moveCamera(
            CameraUpdateFactory.paddingTo(
                padding.left.toDouble(),
                padding.top.toDouble(),
                padding.right.toDouble(),
                padding.bottom.toDouble(),
            ),
        )
    }

    private fun clearViewportPaddingForPreview(map: MapLibreMap) {
        applyMapPaddingImmediate(map, ViewportPadding(0, 0, 0, 0))
        val component = map.locationComponent
        if (component.isLocationComponentActivated && component.isLocationComponentEnabled) {
            component.paddingWhileTracking(doubleArrayOf(0.0, 0.0, 0.0, 0.0))
        }
    }

    override fun setSavedPlaces(places: List<SearchResultPlace>) {
        savedPlacesCache = places
        savedPlacesKeys = places.map { savedPlaceKey(it) }.toSet()
        updatePoiLayerFromCache()
        updateSavedPlacesLayer(places)
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
            } else {
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
        if (_uiState.value.isNavigating || _uiState.value.isInTopDownView) return

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
        }

        hasSnappedCameraToGps = true
        _uiState.update {
            it.copy(streetName = "Free Drive", isCameraDetached = false, isInTopDownView = false)
        }
        updatePuckScreenPosition(map)
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
            clearCustomPin()
        }
        _uiState.update {
            it.copy(
                isNavigating = true,
                streetName = if (isReroute) "Re-routing..." else "Calculating route...",
                selectedPoi = null,
                nearbyPois = emptyList(),
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
        applyMapPaddingImmediate(map, ViewportPadding(0, 0, 0, 0))

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
        _uiState.update { it.copy(isCameraDetached = false, isInTopDownView = false) }
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
        if (_uiState.value.isInTopDownView) return
        applyMapPaddingImmediate(map, computeDrivingViewportPadding(map))
    }

    /** Map padding alone does not offset the puck during TRACKING_GPS — use paddingWhileTracking too. */
    private fun applyDrivingTrackingPadding(map: MapLibreMap) {
        if (_uiState.value.isInTopDownView) return
        val padding = computeDrivingViewportPadding(map)
        val component = map.locationComponent
        val componentReady = component.isLocationComponentActivated && component.isLocationComponentEnabled
        val alreadyTrackingGps = componentReady && component.cameraMode == CameraMode.TRACKING_GPS
        // moveCamera(paddingTo) while TRACKING_GPS is active drops out of follow mode — update
        // paddingWhileTracking only and force a puck refresh to reposition the camera.
        if (!alreadyTrackingGps) {
            applyMapPaddingImmediate(map, padding)
        }
        if (!componentReady) return
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
        if (alreadyTrackingGps) {
            forceLocationUpdateForImmediateRender(map)
        }
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
                val anchor = resolveMapOverlayAnchorLayerId(style)
                if (anchor != null) {
                    style.addLayerAbove(casingLayer, anchor)
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
            if (_uiState.value.isNavigating ||
                _uiState.value.selectedPoi != null ||
                _uiState.value.isInTopDownView
            ) {
                return@addOnCameraIdleListener
            }

            val zoom = map.cameraPosition.zoom
            if (zoom < MIN_POI_ZOOM) {
                clearPoiOverlay()
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
                trimPoiCacheToMax(center)
                refreshPoiOverlay()

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
                    trimPoiCacheToMax(center)
                    refreshPoiOverlay()
                }
            }
        }

        map.addOnMapClickListener {
            mapTapDismissHandler?.let { handler ->
                handler()
                return@addOnMapClickListener true
            }
            val loadedMap = mapLibreMap ?: return@addOnMapClickListener false
            handleMapPointSelection(loadedMap, it)
        }

        map.addOnMapLongClickListener { latLng ->
            val loadedMap = mapLibreMap ?: return@addOnMapLongClickListener false
            if (_uiState.value.isNavigating) return@addOnMapLongClickListener false
            if (handleMapPointSelection(loadedMap, latLng)) return@addOnMapLongClickListener true
            startCustomPinDraft(latLng.latitude, latLng.longitude)
            true
        }
    }

    private fun handleMapPointSelection(map: MapLibreMap, latLng: LatLng): Boolean {
        val screenPoint = map.projection.toScreenLocation(latLng)

        run {
            val feature = map.queryRenderedFeatures(screenPoint, SAVED_PLACES_LAYER_ID).firstOrNull()
                ?: return@run
            val lat = feature.getNumberProperty("lat")?.toDouble() ?: return@run
            val lng = feature.getNumberProperty("lng")?.toDouble() ?: return@run
            if (!isTapNearPinIcon(map, screenPoint, lat, lng)) return@run
            val place = findSavedPlaceAt(lat, lng) ?: placeFromSymbolFeature(feature) ?: return@run
            focusOnPoi(place, moveCamera = true)
            return true
        }

        run {
            val feature = map.queryRenderedFeatures(screenPoint, CUSTOM_PIN_LAYER_ID).firstOrNull()
                ?: return@run
            val coords = extractPointCoordinates(feature.geometry()) ?: return@run
            val (pinLat, pinLng) = coords
            if (!isTapNearPinIcon(map, screenPoint, pinLat, pinLng)) return@run
            val current = _uiState.value.selectedPoi
            val place = when {
                current != null &&
                    coordinatesNear(current.latitude, current.longitude, pinLat, pinLng) ->
                    current
                else -> findSavedPlaceAt(pinLat, pinLng)
                    ?: SearchResultPlace(
                        name = current?.name ?: "Dropped Pin",
                        subTitle = formatLatLng(pinLat, pinLng),
                        latitude = pinLat,
                        longitude = pinLng,
                        isDroppedPin = true,
                    )
            }
            focusOnPoi(place, moveCamera = true)
            return true
        }

        map.queryRenderedFeatures(screenPoint, POI_LAYER_ID).firstOrNull()?.let { feature ->
            val place = placeFromSymbolFeature(feature) ?: return false
            focusOnPoi(place, moveCamera = true)
            return true
        }

        if (useVectorTiles && !_uiState.value.isNavigating) {
            val tapBounds = geoBoundsAround(latLng.latitude, latLng.longitude)
            val place = map.queryRenderedFeatures(screenPoint, *VECTOR_POI_LAYER_IDS)
                .firstNotNullOfOrNull { feature ->
                    tileFeatureToPlace(feature, lastKnownLocation, tapBounds)
                }
            if (place != null) {
                val enriched = poiCache.values.find { cached ->
                    coordinatesNear(cached.latitude, cached.longitude, place.latitude, place.longitude)
                }?.let { cached ->
                    place.copy(
                        subTitle = cached.subTitle.ifBlank { place.subTitle },
                        category = cached.category.ifBlank { place.category },
                    )
                } ?: place
                focusOnPoi(enriched, moveCamera = true)
                mergeIntoPoiCache(listOf(enriched))
                return true
            }
        }

        return false
    }

    private fun exitFreeDriveToTopViewIfNeeded(lat: Double, lng: Double) {
        if (_uiState.value.isNavigating || _uiState.value.isCameraDetached) return
        animateTopDownCamera(lat, lng, POI_PREVIEW_ZOOM)
    }

    private fun startCustomPinDraft(lat: Double, lng: Double) {
        exitFreeDriveToTopViewIfNeeded(lat, lng)
        val selectedPlace = SearchResultPlace(
            name = "Dropped Pin",
            subTitle = formatLatLng(lat, lng),
            latitude = lat,
            longitude = lng,
            isDroppedPin = true,
        )
        focusOnPoi(selectedPlace, moveCamera = false)
        placeCustomPin(lat, lng, pending = true)
        engineScope.launch {
            val streetName = reverseGeocode(lat, lng)
            _uiState.update { state ->
                val current = state.selectedPoi
                if (current?.isDroppedPin == true &&
                    current.latitude == lat &&
                    current.longitude == lng
                ) {
                    state.copy(selectedPoi = current.copy(name = streetName))
                } else {
                    state
                }
            }
            val stillSelected = _uiState.value.selectedPoi
            if (stillSelected?.isDroppedPin == true &&
                stillSelected.latitude == lat &&
                stillSelected.longitude == lng &&
                !isSavedPlace(stillSelected)
            ) {
                placeCustomPin(lat, lng, pending = false)
            }
        }
    }

    private fun isTapNearPinIcon(
        map: MapLibreMap,
        screenPoint: android.graphics.PointF,
        pinLat: Double,
        pinLng: Double,
    ): Boolean {
        val pinScreen = map.projection.toScreenLocation(LatLng(pinLat, pinLng))
        val distPx = hypot(
            (screenPoint.x - pinScreen.x).toDouble(),
            (screenPoint.y - pinScreen.y).toDouble(),
        ).toFloat()
        val density = mapView?.context?.resources?.displayMetrics?.density ?: 2.5f
        return distPx <= MAP_PIN_ICON_HIT_RADIUS_DP * density
    }

    private suspend fun reverseGeocode(lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        val url = URL(
            "https://nominatim.openstreetmap.org/reverse" +
                "?lat=$lat&lon=$lng&format=json",
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "MixAutoCarLauncher/1.0")
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        try {
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "Reverse geocode HTTP error: ${connection.responseCode}")
                return@withContext formatLatLng(lat, lng)
            }
            val body = connection.inputStream.bufferedReader().readText()
            val root = JSONObject(body)
            val address = root.optJSONObject("address")
            if (address != null) {
                listOf("road", "suburb", "city_district", "neighbourhood", "town", "city")
                    .forEach { key ->
                        val value = address.optString(key).trim()
                        if (value.isNotBlank()) return@withContext value
                    }
            }
            formatLatLng(lat, lng)
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocode failed: ${e.message}", e)
            formatLatLng(lat, lng)
        } finally {
            connection.disconnect()
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
        val view = mapView ?: return emptyList()
        val reference = lastKnownLocation
        return runCatching {
            val w = view.width.toFloat()
            val h = view.height.toFloat()
            if (w == 0f || h == 0f) return emptyList()
            val screenBounds = RectF(0f, 0f, w, h)
            map.queryRenderedFeatures(screenBounds, *VECTOR_POI_LAYER_IDS)
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

    private fun placeFromSymbolFeature(feature: org.maplibre.geojson.Feature): SearchResultPlace? {
        val name = feature.getStringProperty("name") ?: return null
        val lat = feature.getNumberProperty("lat")?.toDouble() ?: return null
        val lng = feature.getNumberProperty("lng")?.toDouble() ?: return null
        return SearchResultPlace(
            name = name,
            subTitle = feature.getStringProperty("subtitle").orEmpty(),
            latitude = lat,
            longitude = lng,
            category = feature.getStringProperty("category").orEmpty(),
        )
    }

    private fun findSavedPlaceAt(lat: Double, lng: Double): SearchResultPlace? {
        val tapKey = savedPlaceKey(
            SearchResultPlace(
                name = "",
                subTitle = "",
                latitude = lat,
                longitude = lng,
            ),
        )
        return savedPlacesCache.find { savedPlaceKey(it) == tapKey }
            ?: savedPlacesCache.find { saved ->
                coordinatesNear(saved.latitude, saved.longitude, lat, lng)
            }
    }

    private fun isSavedPlace(place: SearchResultPlace): Boolean =
        savedPlacesKeys.contains(savedPlaceKey(place))

    private fun coordinatesNear(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double,
        maxM: Float = NEARBY_PIN_DEDUP_THRESHOLD_M,
    ): Boolean {
        val distanceResults = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, distanceResults)
        return distanceResults[0] < maxM
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

    private fun geoBoundsAround(lat: Double, lng: Double, deltaDegrees: Double = 0.001): GeoBounds {
        return GeoBounds(
            minLat = lat - deltaDegrees,
            maxLat = lat + deltaDegrees,
            minLng = lng - deltaDegrees,
            maxLng = lng + deltaDegrees,
        )
    }

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

    private fun trimPoiCacheToMax(center: LatLng) {
        if (poiCache.size <= MAX_POI_PINS) return
        val keepKeys = poiCache.values
            .sortedBy { place ->
                val distanceResults = FloatArray(1)
                Location.distanceBetween(
                    center.latitude,
                    center.longitude,
                    place.latitude,
                    place.longitude,
                    distanceResults,
                )
                distanceResults[0]
            }
            .take(MAX_POI_PINS)
            .map { poiCacheKey(it) }
            .toSet()
        poiCache.entries.removeIf { it.key !in keepKeys }
    }

    private fun refreshPoiOverlay() {
        if (_uiState.value.selectedPoi != null || _uiState.value.isInTopDownView) return

        val pins = mergePoiPins(poiCache.values.toList(), emptyList())
        if (pins.isNotEmpty()) {
            updatePoiLayer(pins)
        } else {
            clearPoiOverlay()
        }
    }

    private fun updatePoiLayerFromCache() {
        if (_uiState.value.selectedPoi != null || _uiState.value.isInTopDownView) return

        val map = mapLibreMap
        if (useVectorTiles && map != null) {
            val bounds = map.projection.visibleRegion.latLngBounds
            val latSpan = bounds.northEast.latitude - bounds.southWest.latitude
            val lngSpan = bounds.northEast.longitude - bounds.southWest.longitude
            val padLat = latSpan * BBOX_PADDING_FACTOR / 2
            val padLng = lngSpan * BBOX_PADDING_FACTOR / 2
            val queryBounds = expandGeoBounds(bounds, padLat, padLng)
            mergeIntoPoiCache(queryTilePois(map, queryBounds))
            map.cameraPosition.target?.let { trimPoiCacheToMax(it) }
        }
        refreshPoiOverlay()
    }

    private fun showNativeVectorPoiLayers(style: Style) {
        VECTOR_POI_LAYER_IDS.forEach { id ->
            style.getLayer(id)?.setProperties(PropertyFactory.visibility(Property.VISIBLE))
        }
    }

    private fun resolveMapOverlayAnchorLayerId(style: Style): String? {
        return when {
            style.getLayer(TRAFFIC_LAYER_ID) != null -> TRAFFIC_LAYER_ID
            style.getLayer(RASTER_BASE_LAYER_ID) != null -> RASTER_BASE_LAYER_ID
            style.getLayer("poi_transit") != null -> "poi_transit"
            style.getLayer("road_motorway") != null -> "road_motorway"
            else -> null
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
                val poiLayer = SymbolLayer(POI_LAYER_ID, POI_SOURCE_ID).withProperties(
                    *poiLabelLayerProperties(mixPoiIconExpression()),
                )
                val anchor = resolveMapOverlayAnchorLayerId(style)
                if (anchor != null) {
                    style.addLayerAbove(poiLayer, anchor)
                } else {
                    style.addLayer(poiLayer)
                }
            }
        }
    }

    private fun clearPoiOverlay() {
        val map = mapLibreMap ?: return
        map.getStyle { style ->
            val existing = style.getSource(POI_SOURCE_ID)
            if (existing is GeoJsonSource) {
                existing.setGeoJson(EMPTY_POI_GEOJSON)
            }
        }
    }

    /** Clears overlay GeoJSON and in-memory POI cache (navigation teardown, style reset). */
    private fun clearPoiLayer() {
        clearPoiCache()
        clearPoiOverlay()
    }

    private fun placeCustomPin(lat: Double, lng: Double, pending: Boolean) {
        val map = mapLibreMap ?: return
        val geoJson = buildCustomPinGeoJson(lat, lng, pending)
        map.getStyle { style ->
            val existing = style.getSource(CUSTOM_PIN_SOURCE_ID)
            if (existing is GeoJsonSource) {
                existing.setGeoJson(geoJson)
            } else {
                style.addSource(GeoJsonSource(CUSTOM_PIN_SOURCE_ID, geoJson))
                val customPinLayer = SymbolLayer(CUSTOM_PIN_LAYER_ID, CUSTOM_PIN_SOURCE_ID).withProperties(
                    PropertyFactory.iconImage(customPinIconExpression()),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                    PropertyFactory.iconSize(1f),
                )
                when {
                    style.getLayer(SAVED_PLACES_LAYER_ID) != null -> {
                        style.addLayerAbove(customPinLayer, SAVED_PLACES_LAYER_ID)
                    }
                    style.getLayer(POI_LAYER_ID) != null -> {
                        style.addLayerAbove(customPinLayer, POI_LAYER_ID)
                    }
                    else -> {
                        val anchor = resolveMapOverlayAnchorLayerId(style)
                        if (anchor != null) {
                            style.addLayerAbove(customPinLayer, anchor)
                        } else {
                            style.addLayer(customPinLayer)
                        }
                    }
                }
            }
        }
    }

    private fun clearCustomPin() {
        val map = mapLibreMap ?: return
        map.getStyle { style ->
            val existing = style.getSource(CUSTOM_PIN_SOURCE_ID)
            if (existing is GeoJsonSource) {
                existing.setGeoJson(EMPTY_CUSTOM_PIN_GEOJSON)
            }
        }
    }

    private fun updateSavedPlacesLayer(places: List<SearchResultPlace>) {
        val map = mapLibreMap ?: return
        val geoJson = if (places.isEmpty()) {
            EMPTY_POI_GEOJSON
        } else {
            buildPoiGeoJson(places, forceStarred = true)
        }
        map.getStyle { style ->
            val existing = style.getSource(SAVED_PLACES_SOURCE_ID)
            if (existing is GeoJsonSource) {
                existing.setGeoJson(geoJson)
            } else if (places.isNotEmpty()) {
                style.addSource(GeoJsonSource(SAVED_PLACES_SOURCE_ID, geoJson))
                val savedLayer = SymbolLayer(SAVED_PLACES_LAYER_ID, SAVED_PLACES_SOURCE_ID).withProperties(
                    *poiLabelLayerProperties(poiCategoryIconExpression()),
                )
                when {
                    style.getLayer(POI_LAYER_ID) != null -> {
                        style.addLayerAbove(savedLayer, POI_LAYER_ID)
                    }
                    else -> {
                        val anchor = resolveMapOverlayAnchorLayerId(style)
                        if (anchor != null) {
                            style.addLayerAbove(savedLayer, anchor)
                        } else {
                            style.addLayer(savedLayer)
                        }
                    }
                }
            }
        }
    }

    private fun buildCustomPinGeoJson(lat: Double, lng: Double, pending: Boolean): String {
        return JSONObject().apply {
            put("type", "FeatureCollection")
            put(
                "features",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("type", "Feature")
                            put(
                                "geometry",
                                JSONObject().apply {
                                    put("type", "Point")
                                    put(
                                        "coordinates",
                                        JSONArray().apply {
                                            put(lng)
                                            put(lat)
                                        },
                                    )
                                },
                            )
                            put(
                                "properties",
                                JSONObject().apply {
                                    put("pending", pending)
                                },
                            )
                        },
                    )
                },
            )
        }.toString()
    }

    private fun buildPoiGeoJson(
        places: List<SearchResultPlace>,
        forceStarred: Boolean = false,
    ): String {
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
                        put(
                            "starred",
                            forceStarred || savedPlacesKeys.contains(savedPlaceKey(place)),
                        )
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
            updatePuckScreenPosition(map)

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

        // During navigation, snap the display location onto the nearest route segment so the
        // puck stays on the road despite GPS jitter. Off-route evaluation always uses the raw
        // bearing-enriched fix so reroute detection is not suppressed by the snapping.
        val displayLocation = if (_uiState.value.isNavigating) {
            snapLocationToRoute(locationWithBearing) ?: locationWithBearing
        } else {
            locationWithBearing
        }

        val latLng = LatLng(displayLocation.latitude, displayLocation.longitude)
        lastKnownLocation = latLng
        _uiState.update {
            it.copy(
                currentLat = displayLocation.latitude,
                currentLng = displayLocation.longitude,
            )
        }
        val component = mapLibreMap?.locationComponent
        if (component != null && component.isLocationComponentActivated && component.isLocationComponentEnabled) {
            component.forceLocationUpdate(displayLocation)
            pendingLocationFix = null
        } else {
            pendingLocationFix = displayLocation
        }
        startDeadReckoning(displayLocation)
        mapLibreMap?.let { updatePuckScreenPosition(it) }
        Log.i(
            TAG,
            "Location fix ${displayLocation.latitude}, ${displayLocation.longitude} " +
                "(provider=${displayLocation.provider}, bearing=${displayLocation.bearing}, " +
                "roadSnapped=${displayLocation !== locationWithBearing}, " +
                "snapCamera=$snapCamera, zoom=${mapLibreMap?.cameraPosition?.zoom})",
        )
        when {
            // Pass the raw bearing-enriched fix to step/off-route logic so actual GPS deviation
            // is measured — the snapped display location would always appear on the route.
            _uiState.value.isNavigating -> evaluateStepAdvancement(locationWithBearing)
            _uiState.value.isInTopDownView -> mapLibreMap?.let { ensureTopDownCameraDetached(it) }
            !hasSnappedCameraToGps -> snapCameraToGpsIfNeeded(latLng)
            else -> {
                _uiState.update { it.copy(streetName = "Free Drive") }
                if (!_uiState.value.isCameraDetached) {
                    mapLibreMap?.let { activateFreeDriveTrackingMode(it) }
                }
            }
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

    /**
     * Returns the closest point on the given segment together with its distance from [point].
     * Mirrors the math in [distanceToSegmentMeters] but also returns the projected coordinates.
     */
    private fun closestPointOnSegment(
        point: Location,
        segStart: LatLng,
        segEnd: LatLng,
    ): ClosestSegmentResult {
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
            return ClosestSegmentResult(results[0], ay, ax)
        }
        var t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)
        t = t.coerceIn(0.0, 1.0)
        val closestLat = ay + t * dy
        val closestLng = ax + t * dx
        val results = FloatArray(1)
        Location.distanceBetween(py, px, closestLat, closestLng, results)
        return ClosestSegmentResult(results[0], closestLat, closestLng)
    }

    /**
     * When navigating, snaps [location] onto the nearest route segment if the GPS fix is within
     * [SNAP_TO_ROUTE_MAX_M] metres of the route. Preserves all other location fields (bearing,
     * speed, accuracy) so the HUD and dead-reckoning remain unaffected.
     *
     * Returns null when there is no active route or the fix is too far away (off-route territory —
     * let [checkOffRoute] handle that case with the original coordinates).
     */
    private fun snapLocationToRoute(location: Location): Location? {
        val points = routeGeometryPoints
        if (points.size < 2) return null

        var minDist = Float.MAX_VALUE
        var snappedLat = location.latitude
        var snappedLng = location.longitude

        for (i in 0 until points.size - 1) {
            val result = closestPointOnSegment(location, points[i], points[i + 1])
            if (result.distM < minDist) {
                minDist = result.distM
                snappedLat = result.lat
                snappedLng = result.lng
            }
        }

        if (minDist > SNAP_TO_ROUTE_MAX_M) return null

        return Location(location).apply {
            latitude = snappedLat
            longitude = snappedLng
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

    private fun poiLabelLayerProperties(iconImageExpression: Expression): Array<PropertyValue<*>> {
        return arrayOf(
            PropertyFactory.iconImage(iconImageExpression),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconSize(1f),
            PropertyFactory.textField(Expression.get("name")),
            PropertyFactory.textFont(arrayOf("Noto Sans Italic")),
            PropertyFactory.textSize(12f),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
            PropertyFactory.textOffset(arrayOf(0f, 0.6f)),
            PropertyFactory.textColor("#666666"),
            PropertyFactory.textHaloColor("#ffffff"),
            PropertyFactory.textHaloWidth(1f),
            PropertyFactory.textHaloBlur(0.5f),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
            PropertyFactory.textMaxWidth(9f),
        )
    }

    private fun customPinIconExpression(): Expression {
        return Expression.switchCase(
            Expression.eq(Expression.get("pending"), Expression.literal(true)),
            Expression.literal(PoiIconFactory.CUSTOM_PIN_PENDING_ICON_ID),
            Expression.literal(PoiIconFactory.CUSTOM_PIN_ICON_ID),
        )
    }

    /** Mix overlay always uses registered bitmap teardrops, not Liberty style sprites. */
    private fun mixPoiIconExpression(): Expression {
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

    private fun poiCategoryIconExpression(): Expression {
        return Expression.switchCase(
            Expression.eq(Expression.get("starred"), Expression.literal(true)),
            starredCategoryIconExpression(),
            normalCategoryIconExpression(),
        )
    }

    private fun normalCategoryIconExpression(): Expression = mixPoiIconExpression()

    private fun starredCategoryIconExpression(): Expression {
        return Expression.match(
            Expression.get("category"),
            Expression.literal(PoiIconFactory.starredIconId("poi_icon_default")),
            Expression.stop("food", Expression.literal(PoiIconFactory.starredIconId("poi_icon_food"))),
            Expression.stop("fuel", Expression.literal(PoiIconFactory.starredIconId("poi_icon_fuel"))),
            Expression.stop("health", Expression.literal(PoiIconFactory.starredIconId("poi_icon_health"))),
            Expression.stop(
                "accommodation",
                Expression.literal(PoiIconFactory.starredIconId("poi_icon_accommodation")),
            ),
            Expression.stop("finance", Expression.literal(PoiIconFactory.starredIconId("poi_icon_finance"))),
            Expression.stop("shopping", Expression.literal(PoiIconFactory.starredIconId("poi_icon_shopping"))),
            Expression.stop(
                "recreation",
                Expression.literal(PoiIconFactory.starredIconId("poi_icon_recreation")),
            ),
        )
    }

    private fun savedPlaceKey(place: SearchResultPlace): String {
        val lat = (place.latitude * 100_000.0).roundToInt() / 100_000.0
        val lng = (place.longitude * 100_000.0).roundToInt() / 100_000.0
        return "$lat,$lng"
    }

    private fun findNearestPoiInCache(lat: Double, lng: Double): SearchResultPlace? {
        var nearest: SearchResultPlace? = null
        var nearestDistance = MAP_TAP_NEAREST_POI_MAX_M
        for (place in poiCache.values) {
            val distanceResults = FloatArray(1)
            Location.distanceBetween(lat, lng, place.latitude, place.longitude, distanceResults)
            val distance = distanceResults[0]
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearest = place
            }
        }
        return nearest
    }

    private fun computeDistanceFromReference(lat: Double, lng: Double): Float {
        val reference = lastKnownLocation ?: return 0f
        val distanceResults = FloatArray(1)
        Location.distanceBetween(
            reference.latitude,
            reference.longitude,
            lat,
            lng,
            distanceResults,
        )
        return distanceResults[0]
    }

    private fun formatLatLng(lat: Double, lng: Double): String {
        val latDir = if (lat >= 0) "N" else "S"
        val lngDir = if (lng >= 0) "E" else "W"
        return String.format(
            java.util.Locale.US,
            "%.5f° %s, %.5f° %s",
            kotlin.math.abs(lat),
            latDir,
            kotlin.math.abs(lng),
            lngDir,
        )
    }

    companion object {
        private const val TAG = "MapLibreEngineImpl"
        private const val COMPASS_MARGIN_DP = 16f
        private const val DEFAULT_ZOOM = 15.0
        private const val DEFAULT_ZOOM_FALLBACK = 6.0
        private const val ROUTING_MIN_ZOOM = 10.0
        private const val NAV_TILT = 58.0
        private const val NAV_CAMERA_DURATION_MS = 2500
        private const val FREE_DRIVE_TILT = 50.0
        private const val POI_PREVIEW_ZOOM = 15.5
        /** Top-down explore view centered on puck (CropFree button). */
        private const val TOP_DOWN_EXPLORE_ZOOM = 15.0
        private const val POI_PREVIEW_MAX_RETRIES = 8
        private const val POI_PREVIEW_RETRY_DELAY_MS = 50L
        private const val NEARBY_PIN_DEDUP_THRESHOLD_M = 50f
        private const val RASTER_BASE_LAYER_ID = "osm"
        private const val TRAFFIC_SOURCE_ID = "mix-traffic-source"
        private const val TRAFFIC_LAYER_ID = "mix-traffic-layer"
        private const val REROUTE_THRESHOLD_M = 75f
        private const val SNAP_TO_ROUTE_MAX_M = 40f
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
        private val VECTOR_POI_LAYER_IDS = arrayOf("poi_r1", "poi_r7", "poi_r20", "poi_transit")
        private const val POI_SOURCE_ID = "mix-poi-source"
        private const val POI_LAYER_ID = "mix-poi-layer"
        private const val CUSTOM_PIN_SOURCE_ID = "mix-custom-pin-source"
        private const val CUSTOM_PIN_LAYER_ID = "mix-custom-pin-layer"
        private const val SAVED_PLACES_SOURCE_ID = "mix-saved-source"
        private const val SAVED_PLACES_LAYER_ID = "mix-saved-layer"
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
        private const val NEARBY_SEARCH_BBOX_DELTA = 0.08
        private const val BBOX_PADDING_FACTOR = 1.5
        private const val PHOTON_MOVE_THRESHOLD_M = 300f
        private const val MAP_TAP_NEAREST_POI_MAX_M = 500f
        /** Screen-space hit radius for compact 24 dp circular POI icons. */
        private const val MAP_PIN_ICON_HIT_RADIUS_DP = 20f
        private const val EMPTY_POI_GEOJSON = """{"type":"FeatureCollection","features":[]}"""
        private const val EMPTY_CUSTOM_PIN_GEOJSON = """{"type":"FeatureCollection","features":[]}"""
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
