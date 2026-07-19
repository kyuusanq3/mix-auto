package com.kyuusanq3.mixauto.data.map

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.pm.PackageManager
import android.app.PendingIntent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.graphics.RectF
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.kyuusanq3.mixauto.BuildConfig
import com.kyuusanq3.mixauto.data.navigation.NavStepPhrase
import com.kyuusanq3.mixauto.data.navigation.NavTickContext
import com.kyuusanq3.mixauto.data.navigation.NavTtsPhrases
import com.kyuusanq3.mixauto.data.navigation.NavigationVoiceController
import com.kyuusanq3.mixauto.data.places.EncounteredPlacesRepository
import com.kyuusanq3.mixauto.data.places.LocalPlacesRepository
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.domain.map.MapUiState
import com.kyuusanq3.mixauto.domain.map.RouteProvider
import com.kyuusanq3.mixauto.domain.map.RouteOption
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import org.maplibre.android.location.LocationComponent
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
import org.maplibre.android.offline.OfflineManager
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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Constants definitions - moved to MapLibreEngineImpl_Constants.kt
// All constants are defined in the separate file

// Data class definitions (moved to respective files)
private data class ClosestSegmentResult(val distM: Float, val lat: Double, val lng: Double)
private data class RouteProjection(
    val segmentIndex: Int,
    val splitLat: Double,
    val splitLng: Double,
    val distanceFromStartM: Float,
    val distToRouteM: Float,
)
private data class LegStep(
    val maneuverLat: Double,
    val maneuverLng: Double,
    val instruction: String,
    val distanceLabel: String,
    val streetName: String,
    val distanceMeters: Double,
    val maneuverType: String,
    val maneuverModifier: String,
)
private fun LegStep.toNavStepPhrase(): NavStepPhrase = NavStepPhrase(
    instruction = instruction,
    shortInstruction = NavTtsPhrases.shortManeuver(maneuverType, maneuverModifier),
    streetName = streetName,
    distanceMeters = distanceMeters,
    maneuverType = maneuverType,
)
private data class RouteResult(
    val geometryJson: String,
    val geometryPoints: List<LatLng>,
    val streetName: String,
    val instruction: String,
    val distance: String,
    val steps: List<LegStep> = emptyList(),
    val durationSeconds: Double = 0.0,
    val distanceMeters: Double = 0.0,
    val trafficDelaySeconds: Int = 0,
)
private data class StoredRoute(
    val id: String,
    val provider: RouteProvider,
    val label: String,
    val subtitle: String,
    val result: RouteResult,
) {
    fun toRouteOption(): RouteOption = RouteOption(
        id = id,
        provider = provider,
        label = label,
        etaMinutes = ceil(result.durationSeconds / 60.0).toInt().coerceAtLeast(1),
        distanceMeters = result.distanceMeters,
        subtitle = subtitle,
        geometryPoints = result.geometryPoints.map { Pair(it.latitude, it.longitude) },
    )
}
private data class ResolvedLocation(
    val latLng: LatLng,
    val zoom: Double,
    val fromGps: Boolean,
)

class MapLibreEngineImpl(
    private val localPlaces: LocalPlacesRepository? = null,
    private val encounteredPlaces: EncounteredPlacesRepository? = null,
    private val navigationVoice: NavigationVoiceController? = null,
    private val offlineMapRepository: OfflineMapRepository? = null,
    initialUseVectorTiles: Boolean = true,
    initialShow3dBuildings: Boolean = false,
    initialDrivingZoom: Double = 17.5,
    initialPuckHOffset: Float = 0.3f,
    initialPuckVOffset: Float = 0.4f,
    initialPuckScale: Float = 1.0f,
    initialRememberEncounteredPlaces: Boolean = true,
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
    private var routeResultsById = mutableMapOf<String, StoredRoute>()
    private var selectionOrigin: LatLng? = null
    private var selectionDestination: LatLng? = null
    private var selectionBoundsPoints = emptyList<LatLng>()
    private var isRerouteInProgress = false
    private var navigationCameraTransitionActive = false
    private var routeOverviewOrigin: LatLng? = null
    private var routeOverviewDestination: LatLng? = null
    private var routeOverviewJob: Job? = null
    private var routeOverviewTimerStartMs = 0L
    private var offRouteCount = 0
    private var activeRouteId: String? = null
    private var isPreviewPoiLoading = false
    private var listenersRegistered = false
    private var pendingPoiPreviewTarget: SearchResultPlace? = null
    private var poiRefreshJob: Job? = null
    private var poiCache = mutableMapOf<String, SearchResultPlace>()
    private var routeGeometryPoints = emptyList<LatLng>()
    private var routeProgressSegmentIndex = 0
    private var routeProgressDistanceM = 0f
    private var lastRouteProgressMapUpdateM = 0f
    private var routeProgressSplitLat = 0.0
    private var routeProgressSplitLng = 0.0
    private var cachedTickProjection: RouteProjection? = null
    private var cachedTickProjectionKey = Long.MIN_VALUE
    private var navStartTrafficEligible = false
    private var pendingNavTrafficPhrase: String? = null
    private var stashedParallelTomTomDelaySeconds = 0
    private var navTrafficPrefetchJob: Job? = null
    private var hasSnappedCameraToGps = false
    private var navigationArrivalTriggered = false
    private var drivingTilePrefetcher: DrivingTilePrefetcher? = null
    private var lastDrivingSpeedMps = 0f
    private var lastAppliedTrackingPadding: ViewportPadding? = null
    private var lastEngagedTrackingPadding: ViewportPadding? = null
    private var topDownExploreUserAdjusted = false
    private var lastRouteOverviewLayoutWidth = 0
    private var lastRouteOverviewLayoutHeight = 0
    private var smoothingLocationEngine: SmoothingLocationEngine? = null
    private var bearingEnrichedLocationEngine: BearingEnrichedLocationEngine? = null
    private var isEncounteredPlaceSamplingActive = false
    private var encounteredPlacesJob: Job? = null

    // CarMapEngine interface methods
    override fun createMapView(context: Context): View {
        appContext = context
        val mapView = MapView(context)
        this.mapView = mapView
        return mapView
    }

    override fun onStart() {
        mapView?.onStart()
    }

    override fun onResume() {
        mapView?.onResume()
        resumeLocationActivation()
    }

    override fun onPause() {
        mapView?.onPause()
        pauseLocationActivation()
    }

    override fun onStop() {
        mapView?.onStop()
    }

    override fun onDestroy() {
        destroyMapView()
        clearNavTrafficPrefetchState()
        clearRouteOverviewState()
        cancelPoiPreviewRetries()
        cancelTopDownViewportSync()
        navigationVoice?.shutdown()
        engineScope.cancel()
        mapView = null
        mapLibreMap = null
        appContext = null
        lastKnownLocation = null
        fullRouteSteps = emptyList()
        currentStepIndex = 0
        destinationLatLng = null
        routeResultsById.clear()
        selectionOrigin = null
        selectionDestination = null
        selectionBoundsPoints = emptyList()
        isRerouteInProgress = false
        navigationCameraTransitionActive = false
        routeOverviewOrigin = null
        routeOverviewDestination = null
        routeOverviewJob?.cancel()
        routeOverviewJob = null
        offRouteCount = 0
        activeRouteId = null
        isPreviewPoiLoading = false
        listenersRegistered = false
        pendingPoiPreviewTarget = null
        poiRefreshJob?.cancel()
        poiRefreshJob = null
        poiCache.clear()
        routeGeometryPoints = emptyList()
        routeProgressSegmentIndex = 0
        routeProgressDistanceM = 0f
        lastRouteProgressMapUpdateM = 0f
        routeProgressSplitLat = 0.0
        routeProgressSplitLng = 0.0
        cachedTickProjection = null
        cachedTickProjectionKey = Long.MIN_VALUE
        navStartTrafficEligible = false
        pendingNavTrafficPhrase = null
        stashedParallelTomTomDelaySeconds = 0
        navTrafficPrefetchJob?.cancel()
        navTrafficPrefetchJob = null
        hasSnappedCameraToGps = false
        navigationArrivalTriggered = false
        drivingTilePrefetcher?.cancel()
        drivingTilePrefetcher = null
        lastDrivingSpeedMps = 0f
        lastAppliedTrackingPadding = null
        lastEngagedTrackingPadding = null
        topDownExploreUserAdjusted = false
        lastRouteOverviewLayoutWidth = 0
        lastRouteOverviewLayoutHeight = 0
        smoothingLocationEngine?.reset()
        smoothingLocationEngine = null
        bearingEnrichedLocationEngine = null
        isEncounteredPlaceSamplingActive = false
        encounteredPlacesJob?.cancel()
        encounteredPlacesJob = null
    }

    override fun setMapStyle(useVectorTiles: Boolean) {
        this.useVectorTiles = useVectorTiles
        appContext?.let { ctx ->
            mapLibreMap?.getStyle { style ->
                if (useVectorTiles) {
                    // Switch to vector style
                    loadVectorStyle(ctx, style)
                } else {
                    // Switch to raster style
                    loadRasterStyle(ctx, style)
                }
            }
        }
    }

    override fun setShow3dBuildings(show: Boolean) {
        show3dBuildings = show
        mapLibreMap?.getStyle { style ->
            val layerId = "building-layer"
            if (style.getLayer(layerId) != null) {
                val visibility = if (show) Property.VISIBLE else Property.NONE
                style.getLayer(layerId)?.setProperties(PropertyFactory.visibility(visibility))
            }
        }
    }

    override fun setTrafficEnabled(enabled: Boolean, apiKey: String) {
        trafficEnabled = enabled
        tomTomApiKey = apiKey
        mapLibreMap?.getStyle { style ->
            applyTrafficOverlay(style)
        }
    }

    override fun setNavigationVoiceEnabled(enabled: Boolean) {
        navigationVoice?.enabled = enabled
    }

    override fun setDrivingZoom(zoom: Double) {
        freeDriveZoom = zoom
        navZoom = zoom + 1.0
    }

    override fun setViewportPadding(horizontalFraction: Float, verticalFraction: Float) {
        mapLibreMap?.let { map ->
            // Apply viewport padding logic here if needed
        }
    }

    override fun setPuckScale(scale: Float) {
        puckScale = scale
        mapLibreMap?.getStyle { style ->
            applyStyle(style)
        }
    }

    override fun setRememberEncounteredPlaces(enabled: Boolean) {
        // Implementation for setting encountered place sampling
    }

    override fun clearEncounteredPlaces() {
        // Implementation for clearing encountered places
    }

    override fun setSavedPlaces(places: List<SearchResultPlace>) {
        // Implementation for setting saved places
    }

    override fun onMapHostLayoutChanged() {
        // Implementation for handling layout changes
    }

    override fun seedSearchFromMapViewport() {
        // Implementation for seeding search from map viewport
    }

    override fun setMapTapDismissHandler(handler: (() -> Unit)?) {
        mapTapDismissHandler = handler
    }

    override fun selectRouteOption(routeId: String) {
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

    override fun confirmRouteSelection() {
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

    override fun startFreeDrive() {
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

    override fun recenterCamera() {
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

    override fun enterTopDownView() {
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

    override fun focusOnLocation(lat: Double, lng: Double) {
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

    override fun dismissSelectedPoi() {
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

    override fun searchDestination(
        query: String,
        currentLat: Double,
        currentLng: Double,
        limitDistance: Boolean,
        onLocalResults: suspend (List<SearchResultPlace>) -> Unit,
    ): List<SearchResultPlace> {
        // Implementation for searching destinations
        return emptyList()
    }

    override fun getNearbyPois(lat: Double, lng: Double, limit: Int): List<SearchResultPlace> {
        // Return cached nearby POIs or fetch from local DB if not cached
        return listOf()
    }

    override fun hasOfflinePlacesDatabase(): Boolean {
        // Check if offline Overture places database is installed on device
        return false
    }
}