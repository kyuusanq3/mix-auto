package com.kyuusanq3.mixauto.data.map

import android.app.Application
import android.content.Context
import android.view.View
import androidx.lifecycle.AndroidViewModel
import com.kyuusanq3.mixauto.ui.settings.LauncherPreferences

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
        useVectorTiles = useVectorTiles
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

    override fun clearRoutePreviewState() {
        cancelPoiPreviewRetries()
        cancelTopDownViewportSync()
        pendingPoiPreviewTarget = null
        topDownExploreUserAdjusted = false
    }
}