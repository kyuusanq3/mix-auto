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
import org.maplibre.android.location.LocationComponentConstants
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
import org.maplibre.android.style.layers.Layer
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

internal data class LegStep(
    val maneuverLat: Double,
    val maneuverLng: Double,
    val instruction: String,
    val distanceLabel: String,
    val streetName: String,
    val distanceMeters: Double,
    val maneuverType: String,
    val maneuverModifier: String,
)

internal data class GeoBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
)

internal fun LegStep.toNavStepPhrase(): NavStepPhrase = NavStepPhrase(
    instruction = instruction,
    shortInstruction = NavTtsPhrases.shortManeuver(maneuverType, maneuverModifier),
    streetName = streetName,
    distanceMeters = distanceMeters,
    maneuverType = maneuverType,
)

/** Route rendering/progress constants shared with [RouteRenderer] and [MapLibreEngineImpl]. */
internal const val REROUTE_THRESHOLD_M = 75f
internal const val ROUTE_PROJECTION_SEARCH_RADIUS = 20
internal const val ROUTE_PROGRESS_HIGHWAY_SPEED_MPS = 15f
internal const val ROUTE_PROGRESS_MAP_MIN_ADVANCE_HIGHWAY_M = 25f
internal const val ROUTE_PROGRESS_MAP_MIN_ADVANCE_M = 10f
internal const val ROUTE_PROGRESS_BACKTRACK_TOLERANCE_M = 5f
internal const val ROUTE_TRAVELED_SOURCE_ID = "mix-route-traveled-source"
internal const val ROUTE_REMAINING_SOURCE_ID = "mix-route-remaining-source"
internal const val ROUTE_TRAVELED_CASING_LAYER_ID = "mix-route-traveled-casing-layer"
internal const val ROUTE_TRAVELED_LAYER_ID = "mix-route-traveled-layer"
internal const val ROUTE_REMAINING_CASING_LAYER_ID = "mix-route-remaining-casing-layer"
internal const val ROUTE_REMAINING_LAYER_ID = "mix-route-remaining-layer"
/** Legacy single-source IDs — removed on teardown for in-flight upgrades. */
internal const val ROUTE_SOURCE_ID = "mix-route-source"
internal const val ROUTE_CASING_LAYER_ID = "mix-route-casing-layer"
internal const val ROUTE_LAYER_ID = "mix-route-layer"
internal const val ROUTE_TOMTOM_SOURCE_ID = "mix-route-tomtom-source"
internal const val ROUTE_TOMTOM_LAYER_ID = "mix-route-tomtom-layer"
internal const val ROUTE_OSRM_ALT_SOURCE_ID = "mix-route-osrm-alt-source"
internal const val ROUTE_OSRM_ALT_LAYER_ID = "mix-route-osrm-alt-layer"
internal const val ROUTE_OSRM_PRIMARY_PREVIEW_SOURCE_ID = "mix-route-osrm-primary-preview-source"
internal const val ROUTE_OSRM_PRIMARY_PREVIEW_LAYER_ID = "mix-route-osrm-primary-preview-layer"
internal const val ROUTE_TOMTOM_COLOR = "#FFB300"
internal const val ROUTE_TOMTOM_WIDTH = 10f
internal const val ROUTE_TOMTOM_OPACITY = 0.7f
internal const val ROUTE_OSRM_ALT_COLOR = "#6B7280"
internal const val ROUTE_OSRM_ALT_WIDTH = 8f
internal const val ROUTE_OSRM_ALT_OPACITY = 0.45f
internal const val ROUTE_CASING_COLOR = "#CC000000"
internal const val ROUTE_CASING_WIDTH = 18f
internal const val ROUTE_COLOR = "#00CBD6"
internal const val ROUTE_TRAVELED_COLOR = "#6B7280"
internal const val ROUTE_TRAVELED_OPACITY = 0.85f
internal const val ROUTE_WIDTH = 14f

/** Shared by [BearingEnricher]'s enrichment cache and [MapLibreEngineImpl.isDuplicateLocationFix]. */
internal const val LOCATION_FIX_DEDUP_DIST_M = 2f

internal data class RouteResult(
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

internal data class StoredRoute(
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

    private val mapStyleController by lazy {
        MapStyleController(
            trafficSourceId = TRAFFIC_SOURCE_ID,
            trafficLayerId = TRAFFIC_LAYER_ID,
            rasterBaseLayerId = RASTER_BASE_LAYER_ID,
            routeTraveledCasingLayerId = ROUTE_TRAVELED_CASING_LAYER_ID,
            ensurePuckAboveOverlays = { locationTracking.ensurePuckAboveOverlays() },
            addLayerBelowPuckOrAbove = { style, layer, fallbackAboveId ->
                addLayerBelowPuckOrAbove(style, layer, fallbackAboveId)
            },
            restackRouteLayersAbove = { style, anchorLayerId ->
                restackRouteLayersAbove(style, anchorLayerId)
            },
        )
    }

    private val poiOverlayRenderer = PoiOverlayRenderer(
        poiSourceId = POI_SOURCE_ID,
        poiLayerId = POI_LAYER_ID,
        poiLabelLayerId = POI_LABEL_LAYER_ID,
        savedPlacesSourceId = SAVED_PLACES_SOURCE_ID,
        savedPlacesLayerId = SAVED_PLACES_LAYER_ID,
        vectorPoiLayerIds = VECTOR_POI_LAYER_IDS,
        minPoiZoom = MIN_POI_ZOOM,
        resolveAnchorLayerId = { style -> resolveMapOverlayAnchorLayerId(style) },
    )

    private val routeRenderer by lazy {
        RouteRenderer(
            resolveAnchorLayerId = { style -> resolveMapOverlayAnchorLayerId(style) },
            ensurePuckAboveOverlays = { locationTracking.ensurePuckAboveOverlays() },
        )
    }

    private val customPinController = CustomPinController(
        customPinSourceId = CUSTOM_PIN_SOURCE_ID,
        customPinLayerId = CUSTOM_PIN_LAYER_ID,
        savedPlacesLayerId = SAVED_PLACES_LAYER_ID,
        poiLayerId = POI_LAYER_ID,
        pinHitRadiusDp = MAP_PIN_ICON_HIT_RADIUS_DP,
        resolveAnchorLayerId = { style -> resolveMapOverlayAnchorLayerId(style) },
    )

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
    private var pendingLocationActivation = false
    private var routeOverviewJob: Job? = null
    private var poiRefreshJob: Job? = null
    private var rememberEncounteredPlaces = initialRememberEncounteredPlaces
    private var lastPhotonQueryCenter: LatLng? = null
    private val poiCache = mutableMapOf<String, SearchResultPlace>()
    private var mixPoiOverlayActive = false
    private var savedPlacesKeys = emptySet<String>()
    private var savedPlacesCache = emptyList<SearchResultPlace>()
    private var routeGeometryPoints: List<LatLng> = emptyList()
    private val offRouteDetector = OffRouteDetector(
        projectionForLocation = ::projectionForLocation,
        onReroute = { origin, destLat, destLng ->
            lastKnownLocation = origin
            startNavigation(origin, destLat, destLng, isReroute = true)
        },
    )
    private val encounteredPlacesSampler = EncounteredPlacesSampler(
        engineScope = engineScope,
        localPlaces = localPlaces,
        encounteredPlaces = encounteredPlaces,
        maxResults = MAX_POI_PINS,
        isRememberEnabled = { rememberEncounteredPlaces },
        isNavigating = { _uiState.value.isNavigating },
        useVectorTiles = { useVectorTiles },
        currentZoom = { mapLibreMap?.cameraPosition?.zoom },
        routeGeometryPoints = { routeGeometryPoints },
        projectOntoRoute = ::projectOntoRoute,
        queryVectorPois = { bounds -> mapLibreMap?.let { queryTilePois(it, bounds) } ?: emptyList() },
        onPlacesCollected = { places ->
            mergeIntoPoiCache(places)
            lastKnownLocation?.let { trimPoiCacheToMax(it) }
        },
    )
    private val bearingEnricher = BearingEnricher(
        cameraBearingFallback = { mapLibreMap?.cameraPosition?.bearing?.toFloat() },
    )
    private lateinit var locationTracking: LocationTrackingController
    private lateinit var navigationCamera: NavigationCameraController
    private var deadReckoningJob: Job? = null
    private var lastDeadReckoningLocation: Location? = null
    private var drAnchor: Location? = null
    private var routeOverviewOrigin: LatLng? = null
    private var routeOverviewDestination: LatLng? = null
    private var lastRouteOverviewLayoutWidth = 0
    private var lastRouteOverviewLayoutHeight = 0
    private val routeResultsById = mutableMapOf<String, StoredRoute>()
    private var selectionOrigin: LatLng? = null
    private var selectionDestination: LatLng? = null
    private var selectionBoundsPoints: List<LatLng> = emptyList()
    private var routeOverviewTimerStartMs = 0L
    /** TomTom delay from parallel fetch when OSRM route is selected (nav-start TTS tier 2). */
    private var stashedParallelTomTomDelaySeconds = 0
    private var pendingNavTrafficPhrase: String? = null
    private var navTrafficPrefetchJob: Job? = null
    /** True until first [activateNavigationTracking] consumes the nav-start traffic line. */
    private var navStartTrafficEligible = false
    private var drivingTilePrefetcher: DrivingTilePrefetcher? = null

    private fun onNavigationTrackingEngaged() {
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

    private fun updateLocationEngineInterval() {
        // Interval is owned by LocationComponent's single subscription through the engine chain.
    }

    init {
        var locRef: LocationTrackingController? = null
        var navRef: NavigationCameraController? = null
        navRef = NavigationCameraController(
            mapView = { mapView },
            mapLibreMap = { mapLibreMap },
            appContext = { appContext },
            uiState = { _uiState.value },
            updateUiState = { transform -> _uiState.update(transform) },
            freeDriveZoom = { freeDriveZoom },
            navZoom = { navZoom },
            puckHorizontalOffset = { puckHorizontalOffset },
            puckVerticalOffset = { puckVerticalOffset },
            useVectorTiles = { useVectorTiles },
            lastKnownLocation = { lastKnownLocation },
            setLastKnownLocation = { lastKnownLocation = it },
            hasSnappedCameraToGps = { hasSnappedCameraToGps },
            setHasSnappedCameraToGps = { hasSnappedCameraToGps = it },
            isRouteOverviewActive = ::isRouteOverviewActive,
            lastDrivingSpeedMps = { locRef!!.lastDrivingSpeedMps() },
            readLastKnownLocation = { ctx -> locRef!!.readLastKnownLocation(ctx) },
            shouldSmoothPuckMotion = { locRef!!.shouldSmoothPuckMotion() },
            forceLocationUpdateForImmediateRender = { map, bypass, allow ->
                locRef!!.forceLocationUpdateForImmediateRender(map, bypass, allow)
            },
            resetSmoothingMotion = { locRef!!.resetSmoothingMotion() },
            stopDeadReckoning = ::stopDeadReckoning,
            showNativeVectorPoiLayers = ::showNativeVectorPoiLayers,
            clearPoiOverlay = ::clearPoiOverlay,
            fitRouteOverviewCamera = ::fitRouteOverviewCamera,
            routeOverviewOrigin = { routeOverviewOrigin },
            routeOverviewDestination = { routeOverviewDestination },
            lastRouteOverviewLayoutWidth = { lastRouteOverviewLayoutWidth },
            lastRouteOverviewLayoutHeight = { lastRouteOverviewLayoutHeight },
            setLastRouteOverviewLayoutSize = { w, h ->
                lastRouteOverviewLayoutWidth = w
                lastRouteOverviewLayoutHeight = h
            },
            onNavigationTrackingEngaged = ::onNavigationTrackingEngaged,
        )
        locRef = LocationTrackingController(
            engineScope = engineScope,
            mapLibreMap = { mapLibreMap },
            appContext = { appContext },
            uiState = { _uiState.value },
            updateUiState = { transform -> _uiState.update(transform) },
            puckScale = { puckScale },
            useVectorTiles = { useVectorTiles },
            hasSnappedCameraToGps = { hasSnappedCameraToGps },
            setHasSnappedCameraToGps = { hasSnappedCameraToGps = it },
            lastKnownLocation = { lastKnownLocation },
            setLastKnownLocation = { lastKnownLocation = it },
            isRouteOverviewActive = ::isRouteOverviewActive,
            navigationCameraTransitionActive = { navRef!!.navigationCameraTransitionActive },
            fullRouteSteps = { fullRouteSteps },
            currentStepIndex = { currentStepIndex },
            setCurrentStepIndex = { currentStepIndex = it },
            destinationLatLng = { destinationLatLng },
            navigationArrivalTriggered = { navigationArrivalTriggered },
            setNavigationArrivalTriggered = { navigationArrivalTriggered = it },
            routeGeometryPoints = { routeGeometryPoints },
            offRouteDetector = offRouteDetector,
            bearingEnricher = bearingEnricher,
            navigationVoice = { navigationVoice },
            offlineMapRepository = { offlineMapRepository },
            drivingTilePrefetcher = { drivingTilePrefetcher },
            encounteredPlacesSampler = encounteredPlacesSampler,
            clearTickProjectionCache = ::clearTickProjectionCache,
            updateRouteProgress = ::updateRouteProgress,
            onReroute = { origin, destLat, destLng ->
                lastKnownLocation = origin
                startNavigation(origin, destLat, destLng, isReroute = true)
            },
            startFreeDrive = ::startFreeDrive,
            invalidateDrivingPaddingCache = { navRef!!.invalidateDrivingPaddingCache() },
            applyDrivingTrackingPadding = { navRef!!.applyDrivingTrackingPadding(it) },
            maybeApplyDrivingPaddingForSpeedChange = { navRef!!.maybeApplyDrivingPaddingForSpeedChange(it) },
            updateLookaheadPaddingState = { navRef!!.updateLookaheadPaddingState(it) },
            snapCameraToGpsIfNeeded = { navRef!!.snapCameraToGpsIfNeeded(it) },
            activateFreeDriveTrackingMode = { navRef!!.activateFreeDriveTrackingMode(it) },
            ensureTopDownCameraDetached = { navRef!!.ensureTopDownCameraDetached(it) },
            maybePrefetchDrivingTiles = ::maybePrefetchDrivingTiles,
        )
        navigationCamera = navRef!!
        locationTracking = locRef!!
    }

    override fun createMapView(context: Context): View {
        mapView?.let { existing ->
            (existing.parent as? ViewGroup)?.removeView(existing)
            return existing
        }

        if (!mapLibreInitialized) {
            MapLibreAppBootstrap.ensureInitialized(context)
            mapLibreInitialized = true
        }

        appContext = context.applicationContext
        drivingTilePrefetcher = DrivingTilePrefetcher(context.applicationContext, engineScope)
        resolveInitialLocation(context)

        return MapView(context).also { view ->
            view.onCreate(null)
            view.onStart()
            view.onResume()
            view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                if (v.width <= 0 || v.height <= 0) return@addOnLayoutChangeListener
                mapLibreMap?.let { map ->
                    view.post { navigationCamera.handleMapLayoutChange(map) }
                }
            }
            view.getMapAsync { map ->
                mapLibreMap = map
                mapStyleController.configureMapUiChrome(map, context)
                map.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        _uiState.update { it.copy(isCameraDetached = true) }
                        navigationCamera.onCameraGestureStarted(map)
                        stopDeadReckoning()
                        locationTracking.resetSmoothingMotion()
                        map.getStyle { syncPoiOverlayVisibility(it) }
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

        drivingTilePrefetcher?.cancel()
        resetSmoothingMotion()
        poiRefreshJob?.cancel()
        poiRefreshJob = null
        encounteredPlacesSampler.cancel()
        clearRouteOverviewState()
        fullRouteSteps = emptyList()
        currentStepIndex = 0
        destinationLatLng = null
        navigationArrivalTriggered = false
        routeGeometryPoints = emptyList()
        resetRouteProgress()
        offRouteDetector.reset()

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
        map.getStyle { style -> mapStyleController.apply3dBuildingVisibility(style, show3dBuildings) }
    }

    override fun setTrafficEnabled(enabled: Boolean, apiKey: String) {
        trafficEnabled = enabled
        tomTomApiKey = apiKey.trim()
        val map = mapLibreMap ?: return
        val style = map.style ?: return
        mapStyleController.applyTrafficOverlay(style, trafficEnabled, tomTomApiKey)
    }

    override fun setNavigationVoiceEnabled(enabled: Boolean) {
        navigationVoice?.enabled = enabled
        if (!enabled) {
            navigationVoice?.onNavigationEnded()
        }
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
        navigationCamera.applyPuckPaddingUpdate(map, bypassRenderThrottle = true)
    }

    override fun setPuckScale(scale: Float) {
        puckScale = scale
        val map = mapLibreMap ?: return
        val ctx = appContext ?: return
        val component = map.locationComponent
        if (!component.isLocationComponentActivated) return
        map.getStyle { style ->
            component.applyStyle(locationTracking.buildLocationComponentOptions(ctx, style))
        }
    }

    private fun addLayerBelowPuckOrAbove(style: Style, layer: Layer, fallbackAboveId: String?) {
        val puckAnchor = locationTracking.findPuckAnchorLayerId(style)
        when {
            puckAnchor != null -> style.addLayerBelow(layer, puckAnchor)
            fallbackAboveId != null && style.getLayer(fallbackAboveId) != null ->
                style.addLayerAbove(layer, fallbackAboveId)
            else -> style.addLayer(layer)
        }
    }

    override fun setMapTapDismissHandler(handler: (() -> Unit)?) {
        mapTapDismissHandler = handler
    }

    /**
     * Push puck position when it materially moved. LocationComponent already receives engine
     * updates in free drive â€” extra forceLocationUpdate calls race MapLibre's RenderThread and
     * can SIGSEGV on the emulator (fault addr 0x30 in MapRenderer::render).
     */
    private fun pushPuckLocationIfNeeded(location: Location, force: Boolean = false) {
        locationTracking.pushPuckLocationIfNeeded(location, force)
    }

    private fun forceLocationUpdateForImmediateRender(
        map: MapLibreMap,
        bypassThrottle: Boolean = false,
        allowDuringSmoothing: Boolean = false,
    ) {
        locationTracking.forceLocationUpdateForImmediateRender(map, bypassThrottle, allowDuringSmoothing)
    }

    private fun resetSmoothingMotion() {
        locationTracking.resetSmoothingMotion()
    }

    private fun applyMapStyle(map: MapLibreMap, context: Context) {
        val builder = if (useVectorTiles) {
            Style.Builder().fromUri(MapStyleConstants.VECTOR_STYLE_URI)
        } else {
            Style.Builder().fromJson(OSM_STYLE_JSON)
        }

        map.setStyle(builder) { style ->
            PoiIconFactory.createAllIcons(context).forEach { (id, bitmap) ->
                style.addImage(id, bitmap)
            }
            locationTracking.activateLocationTracking(map, style)
            mapStyleController.configureDrivingTilePrefetch(map, context, useVectorTiles)
            if (pendingLocationActivation && locationTracking.hasLocationPermission(context)) {
                locationTracking.beginLocationAcquisition(context)
                pendingLocationActivation = false
            }
            hasSnappedCameraToGps = false
            startFreeDrive()
            mapStyleController.applyAutomotiveRoadBoost(style, useVectorTiles)
            mapStyleController.applyTrafficOverlay(style, trafficEnabled, tomTomApiKey)
            mapStyleController.apply3dBuildingVisibility(style, show3dBuildings)
            updateSavedPlacesLayer(savedPlacesCache)
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
        stopDeadReckoning()
        poiRefreshJob?.cancel()
        poiRefreshJob = null
        encounteredPlacesSampler.cancel()
        navigationCamera.cancelPoiPreviewRetries()
        navigationCamera.cancelTopDownViewportSync()
        lastPhotonQueryCenter = null
        clearPoiCache()
        engineScope.cancel()
        locationTracking.onDestroy()
        drivingTilePrefetcher?.cancel()
        drivingTilePrefetcher = null
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

        val (searchLat, searchLng) = if (isValidSearchOrigin(currentLat, currentLng)) {
            currentLat to currentLng
        } else {
            resolveSearchOrigin(currentLat, currentLng)
        }

        fun applyDistanceLimit(places: List<SearchResultPlace>): List<SearchResultPlace> =
            if (limitDistance) {
                places.filter { it.distanceInMeters <= MAX_SEARCH_RADIUS_M }
            } else {
                places
            }

        val hasOfflineData = localPlaces?.hasInstalledDatabase == true
        val local = if (hasOfflineData) {
            localPlaces?.searchPlaces(query, searchLat, searchLng).orEmpty().map { place ->
                place.copy(
                    category = normalizeOvertureCategory(place.category),
                    poiSource = POI_SOURCE_OVERTURE,
                )
            }
        } else {
            emptyList()
        }
        val encountered = if (rememberEncounteredPlaces) {
            encounteredPlaces?.searchPlaces(query, searchLat, searchLng).orEmpty()
        } else {
            emptyList()
        }
        val cacheResults = withContext(Dispatchers.Main) {
            mergeAndDeduplicate(
                searchPoiCache(query, searchLat, searchLng),
                searchViewportPoiCache(query, searchLat, searchLng),
            )
        }
        val localAndEncountered = mergeAndDeduplicate(local, encountered)
        val localAndCache = mergeAndDeduplicate(localAndEncountered, cacheResults)
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
                "reliable=${hasReliableSearchOrigin()} local=${local.size} encountered=${encountered.size} " +
                "cache=${cacheResults.size} photon=${photon.size} final=${finalResults.size}",
        )
        if (finalResults.isNotEmpty()) {
            if (rememberEncounteredPlaces) {
                encounteredPlaces?.upsertAll(finalResults, SOURCE_SEARCH)
                encounteredPlaces?.pruneToMaxRecords()
            }
            withContext(Dispatchers.Main) {
                mergeIntoPoiCache(finalResults)
                updatePoiLayerFromCache()
            }
        }
        finalResults
    }

    override suspend fun seedSearchFromMapViewport() {
        val map = mapLibreMap ?: return
        withContext(Dispatchers.Main) {
            seedViewportPoisIntoCache(map)
        }
    }

    override fun getNearbyPois(lat: Double, lng: Double, limit: Int): List<SearchResultPlace> {
        if (limit <= 0) return emptyList()

        val (searchLat, searchLng) = if (isValidSearchOrigin(lat, lng)) {
            lat to lng
        } else {
            resolveSearchOrigin(lat, lng)
        }

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

        val viewportCacheResults = poiCacheInViewport().map { place ->
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

        val repo = localPlaces
        val offlineResults = if (repo != null && repo.hasInstalledDatabase) {
            repo.getPlacesInBounds(
                minLat = searchLat - NEARBY_SEARCH_BBOX_DELTA,
                maxLat = searchLat + NEARBY_SEARCH_BBOX_DELTA,
                minLng = searchLng - NEARBY_SEARCH_BBOX_DELTA,
                maxLng = searchLng + NEARBY_SEARCH_BBOX_DELTA,
                limit = limit * 2,
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
                    poiSource = POI_SOURCE_OVERTURE,
                )
            }
        } else {
            emptyList()
        }

        val encounteredResults = if (rememberEncounteredPlaces) {
            encounteredPlaces?.getPlacesNear(
                lat = searchLat,
                lng = searchLng,
                maxRadiusM = ENCOUNTER_NEARBY_RADIUS_M,
                limit = limit,
            ).orEmpty()
        } else {
            emptyList()
        }

        return mergeAndDeduplicate(offlineResults, cacheResults + viewportCacheResults)
            .let { mergeAndDeduplicate(it, encounteredResults) }
            .sortedBy { it.distanceInMeters }
            .take(limit)
    }

    override fun hasOfflinePlacesDatabase(): Boolean =
        localPlaces?.hasInstalledDatabase == true

    override fun setRememberEncounteredPlaces(enabled: Boolean) {
        rememberEncounteredPlaces = enabled
    }

    override fun clearEncounteredPlaces() {
        encounteredPlaces?.clearAll()
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

    /**
     * Merges local + Photon POIs for map pins. Unlike [mergeAndDeduplicate], keeps local
     * results first (preserving Overture categories) and does not re-sort by distance â€”
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
            if (savedPlacesKeys.contains(savedPlaceKey(place))) continue
            val duplicateIndex = findDuplicateIndex(place)
            if (duplicateIndex == null) {
                merged.add(place)
            } else {
                merged[duplicateIndex] = preferPoiEntry(merged[duplicateIndex], place)
            }
        }
        return merged.take(MAX_POI_PINS)
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
                locationTracking.activateLocationTracking(map, style)
                pendingLocationActivation = false
            }
        } else {
            pendingLocationActivation = true
            mapView?.getMapAsync { loadedMap ->
                loadedMap.getStyle { style ->
                    locationTracking.activateLocationTracking(loadedMap, style)
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
            clearRoutePreviewState()
            _uiState.update { it.copy(isCameraDetached = false, isInTopDownView = false) }
            hasSnappedCameraToGps = false
            val target = lastKnownLocation ?: resolveFreeDriveTarget(map)
            if (target != null) {
                snapCameraToGpsIfNeeded(target)
            } else {
                activateFreeDriveTrackingMode(map)
            }
            scheduleFreeDrivePaddingRestore(map)
            map.getStyle { syncPoiOverlayVisibility(it) }
        }
    }

    override fun startFreeDrive() {
        navigationVoice?.onNavigationEnded()
        stopDeadReckoning()
        clearRouteOverviewState()
        poiRefreshJob?.cancel()
        poiRefreshJob = null
        encounteredPlacesSampler.cancel()
        clearRoutePreviewState()
        clearForcedPreviewPoi()
        fullRouteSteps = emptyList()
        currentStepIndex = 0
        destinationLatLng = null
        navigationArrivalTriggered = false
        routeGeometryPoints = emptyList()
        resetRouteProgress()
        offRouteDetector.reset()
        hasSnappedCameraToGps = false
        routeResultsById.clear()
        selectionOrigin = null
        selectionDestination = null
        selectionBoundsPoints = emptyList()
        clearNavTrafficPrefetchState()

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
            map.getStyle { showNativeVectorPoiLayers(it) }
        } else {
            mapView?.getMapAsync { loadedMap ->
                applyFreeDriveToMap(loadedMap)
                clearPoiLayer()
                clearCustomPin()
                loadedMap.getStyle { showNativeVectorPoiLayers(it) }
            }
        }
        updateLocationEngineInterval()
        locationTracking.resetSmoothingMotion()
        navigationCamera.resetLookaheadPaddingActive()
    }

    override fun dismissSelectedPoi() {
        navigationCamera.cancelPoiPreviewRetries()
        navigationCamera.resetTopDownExploreUserAdjusted()
        clearForcedPreviewPoi()
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
        clearForcedPreviewPoi()
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
                placeCustomPin(place.latitude, place.longitude, pending = true)
            }
        } else {
            clearCustomPin()
        }
        if (moveCamera) {
            clearPoiOverlay()
            if (!place.isDroppedPin && !isPlaceRenderedOnMap(place)) {
                val enriched = place.copy(
                    poiSource = place.poiSource.ifBlank { POI_SOURCE_SEARCH },
                    category = place.category.ifBlank { normalizeOvertureCategory(place.category) },
                )
                encounteredPlaces?.upsertAll(listOf(enriched), SOURCE_SEARCH)
                encounteredPlaces?.pruneToMaxRecords()
                mergeIntoPoiCache(listOf(enriched))
                showForcedPreviewPoi(enriched)
            }
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
        navigationCamera.enterTopDownExploreView()
    }

    override fun setSavedPlaces(places: List<SearchResultPlace>) {
        savedPlacesCache = places
        savedPlacesKeys = places.map { savedPlaceKey(it) }.toSet()
        updatePoiLayerFromCache()
        updateSavedPlacesLayer(places)
        val selected = _uiState.value.selectedPoi
        if (selected != null && selected.isDroppedPin) {
            if (isSavedPlace(selected)) {
                clearCustomPin()
            } else {
                placeCustomPin(selected.latitude, selected.longitude, pending = true)
            }
        }
    }

    private fun applyFreeDriveToMap(map: MapLibreMap) {
        applyDrivingViewportPadding(map)
        map.getStyle { style ->
            removeRouteLayers(style)

            val target = resolveFreeDriveTarget(map)
            if (target != null) {
                snapCameraToGpsIfNeeded(target)
            } else {
                activateFreeDriveTrackingMode(map)
            }
        }
    }

    private fun removeRouteLayers(style: Style) {
        routeRenderer.removeRouteLayers(style)
    }

    private fun removeAlternateRouteLayers() {
        val map = mapLibreMap ?: return
        map.getStyle { style -> routeRenderer.removeAlternateRouteLayers(style) }
    }

    private fun updateSelectedRouteHighlight(selectedId: String) {
        val map = mapLibreMap ?: return
        map.getStyle { style ->
            routeRenderer.updateSelectedRouteHighlight(style, selectedId, routeResultsById)
        }
    }

    private fun restackRouteLayersAbove(style: Style, anchorLayerId: String) {
        routeRenderer.restackRouteLayersAbove(style, anchorLayerId)
    }

    private fun ensureRouteLayers(style: Style) {
        routeRenderer.ensureRouteLayers(style)
    }

    private fun resetRouteProgress() {
        routeRenderer.resetRouteProgress(routeGeometryPoints)
    }

    private fun projectOntoRoute(location: Location): RouteProjection? {
        return routeRenderer.projectOntoRoute(location, routeGeometryPoints)
    }

    private fun projectionForLocation(location: Location): RouteProjection? {
        return routeRenderer.projectionForLocation(location, routeGeometryPoints)
    }

    private fun clearTickProjectionCache() {
        routeRenderer.clearTickProjectionCache()
    }

    private fun updateRouteProgress(location: Location) {
        routeRenderer.updateRouteProgress(location, routeGeometryPoints, mapLibreMap)
    }

    private fun drawRoute() {
        val map = mapLibreMap ?: return
        routeRenderer.drawRoute(map, routeGeometryPoints)
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
                        offRouteDetector.offRouteCount = 0
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
                        offRouteDetector.isRerouteInProgress = false
                        enterNavigationCamera()
                    } else {
                        offRouteDetector.isRerouteInProgress = false
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
                offRouteDetector.offRouteCount = 0
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
                offRouteDetector.isRerouteInProgress = false
                Log.w(TAG, "Route fetch failed: ${e.message}", e)
                _uiState.update { it.copy(isNavigating = false, streetName = "Routing failed") }
            }
        }
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

    /** Ends route selection and enters turn-by-turn camera â€” no second overview hold. */
    private fun beginNavigationAfterRouteSelection() {
        if (mapLibreMap == null) return
        clearRoutePreviewState()
        _uiState.update { it.copy(isCameraDetached = false, isInTopDownView = false) }
        if (_uiState.value.isNavigating) {
            navigationCamera.setNavigationCameraTransitionActive(true)
            val dive = { enterNavigationCamera() }
            mapView?.post(dive) ?: dive()
        }
    }

    private fun applyActiveRoute(route: RouteResult) {
        routeGeometryPoints = route.geometryPoints
        fullRouteSteps = route.steps
        currentStepIndex = 0
        drawRoute()
        prefetchNavTrafficHint(route)
    }

    private fun clearNavTrafficPrefetchState() {
        navTrafficPrefetchJob?.cancel()
        navTrafficPrefetchJob = null
        pendingNavTrafficPhrase = null
        stashedParallelTomTomDelaySeconds = 0
        navStartTrafficEligible = false
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

    private fun buildRouteCandidates(
        osrmRoutes: List<RouteResult>,
        tomtomRoute: TomTomRouteResult?,
    ): List<StoredRoute> {
        val candidates = mutableListOf<StoredRoute>()
        val fastest = osrmRoutes.firstOrNull()
        if (fastest != null) {
            candidates.add(
                osrmToStoredRoute(
                    fastest,
                    RouteProvider.OSRM_FASTEST,
                    ROUTE_ID_OSRM_FASTEST,
                    "Fastest",
                    fastest.steps.firstOrNull()?.streetName?.takeIf { it.isNotBlank() } ?: "Shortest path",
                ),
            )
        }

        val fastestDuration = fastest?.durationSeconds ?: 0.0
        val fastestDistance = fastest?.distanceMeters ?: 0.0

        tomtomRoute?.let { tt ->
            val ttResult = tomTomToRouteResult(tt)
            if (fastest == null || !routesAreSimilar(fastest.geometryPoints, ttResult.geometryPoints)) {
                val deltaSec = tt.travelTimeSeconds - fastestDuration.toInt()
                val subtitle = when {
                    tt.trafficDelaySeconds > 60 -> "Live traffic Â· ${TomTomRoutingClient.formatEtaDeltaMinutes(deltaSec)}"
                    deltaSec < 0 -> TomTomRoutingClient.formatEtaDeltaMinutes(deltaSec)
                    deltaSec > 0 -> TomTomRoutingClient.formatEtaDeltaMinutes(deltaSec)
                    else -> "Traffic-aware route"
                }
                candidates.add(
                    StoredRoute(
                        id = ROUTE_ID_TOMTOM,
                        provider = RouteProvider.TOMTOM_TRAFFIC,
                        label = "Traffic smart",
                        subtitle = subtitle,
                        result = ttResult,
                    ),
                )
            }
        }

        if (osrmRoutes.size > 1) {
            val alt = osrmRoutes[1]
            if (fastest == null || !routesAreSimilar(fastest.geometryPoints, alt.geometryPoints)) {
                val distDeltaKm = (alt.distanceMeters - fastestDistance) / 1000.0
                val subtitle = when {
                    distDeltaKm > 0.1 -> "+${"%.1f".format(distDeltaKm)} km vs fastest"
                    distDeltaKm < -0.1 -> "${"%.1f".format(distDeltaKm)} km vs fastest"
                    else -> "Different roads"
                }
                candidates.add(
                    osrmToStoredRoute(
                        alt,
                        RouteProvider.OSRM_ALTERNATE,
                        ROUTE_ID_OSRM_ALT,
                        "Alternate",
                        subtitle,
                    ),
                )
            }
        }

        return candidates
    }

    private fun osrmToStoredRoute(
        result: RouteResult,
        provider: RouteProvider,
        id: String,
        label: String,
        subtitle: String,
    ) = StoredRoute(id = id, provider = provider, label = label, subtitle = subtitle, result = result)

    private fun tomTomToRouteResult(tt: TomTomRouteResult): RouteResult {
        val geometryPoints = tt.geometryPoints.map { LatLng(it.first, it.second) }
        val steps = tt.steps.map { step ->
            val maneuverType = NavTtsPhrases.inferManeuverType(step.instruction)
            val maneuverModifier = NavTtsPhrases.inferManeuverModifier(step.instruction)
            LegStep(
                maneuverLat = step.maneuverLat,
                maneuverLng = step.maneuverLng,
                instruction = step.instruction,
                distanceLabel = step.distanceLabel,
                streetName = step.streetName,
                distanceMeters = step.distanceMeters,
                maneuverType = maneuverType,
                maneuverModifier = maneuverModifier,
            )
        }
        val firstStep = steps.firstOrNull()
        val geometryJson = buildLineStringFeatureJson(geometryPoints)
        return RouteResult(
            geometryJson = geometryJson,
            geometryPoints = geometryPoints,
            streetName = firstStep?.streetName?.ifBlank { tt.primaryStreet } ?: tt.primaryStreet,
            instruction = firstStep?.instruction ?: "Depart",
            distance = firstStep?.distanceLabel ?: TomTomRoutingClient.formatDistance(tt.distanceMeters),
            steps = steps,
            durationSeconds = tt.travelTimeSeconds.toDouble(),
            distanceMeters = tt.distanceMeters,
            trafficDelaySeconds = tt.trafficDelaySeconds,
        )
    }

    private fun routesAreSimilar(a: List<LatLng>, b: List<LatLng>): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        val samplesA = sampleRoutePoints(a, 5)
        val samplesB = sampleRoutePoints(b, 5)
        val thresholdM = 50f
        val nearCount = samplesA.count { pointA ->
            samplesB.any { pointB ->
                val results = FloatArray(1)
                Location.distanceBetween(
                    pointA.latitude,
                    pointA.longitude,
                    pointB.latitude,
                    pointB.longitude,
                    results,
                )
                results[0] < thresholdM
            }
        }
        return nearCount >= minOf(samplesA.size, samplesB.size) - 1
    }

    private fun sampleRoutePoints(points: List<LatLng>, count: Int): List<LatLng> {
        if (points.size <= count) return points
        val step = (points.size - 1) / (count - 1).coerceAtLeast(1)
        return buildList {
            var i = 0
            while (i < points.size) {
                add(points[i])
                i += step
            }
            if (last() != points.last()) add(points.last())
        }
    }

    private fun fetchOsrmRoutesWithAlternatives(
        lngA: Double,
        latA: Double,
        lngB: Double,
        latB: Double,
    ): List<RouteResult> = NavigationRouteFetcher.fetchOsrmRoutesWithAlternatives(lngA, latA, lngB, latB)

    private fun fetchOsrmRoute(
        lngA: Double,
        latA: Double,
        lngB: Double,
        latB: Double,
    ): RouteResult? = NavigationRouteFetcher.fetchOsrmRoute(lngA, latA, lngB, latB)

    private fun clearRouteOverviewState() {
        routeOverviewJob?.cancel()
        routeOverviewJob = null
        routeOverviewOrigin = null
        routeOverviewDestination = null
        lastRouteOverviewLayoutWidth = 0
        lastRouteOverviewLayoutHeight = 0
        navigationCamera.clearNavigationCameraTransitionActive()
        drivingTilePrefetcher?.cancel()
        resetSmoothingMotion()
        _uiState.update { it.copy(routeOverviewProgress = 0f) }
    }

    private fun resetRouteOverviewLayoutCache() {
        lastRouteOverviewLayoutWidth = 0
        lastRouteOverviewLayoutHeight = 0
    }

    private fun isRouteOverviewActive(): Boolean = routeOverviewJob?.isActive == true

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
                navigationCamera.setNavigationCameraTransitionActive(true)
                val dive = { enterNavigationCamera() }
                mapView?.post(dive) ?: dive()
            }
        }
    }

    private fun fitRouteOverviewCamera(origin: LatLng, destination: LatLng, animate: Boolean) {
        val map = mapLibreMap ?: return
        val view = mapView ?: return
        if (view.width <= 0 || view.height <= 0) return

        val component = map.locationComponent
        if (component.isLocationComponentActivated && component.isLocationComponentEnabled) {
            component.cameraMode = CameraMode.NONE
        }
        invalidateDrivingPaddingCache()
        map.cancelTransitions()
        map.moveCamera(
            CameraUpdateFactory.paddingTo(0.0, 0.0, 0.0, 0.0),
        )

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
        return buildRouteOverviewBounds(
            origin,
            destination,
            selectionBoundsPoints,
            routeGeometryPoints,
            lastKnownLocation,
        )
    }

    private fun computeRouteOverviewPadding(map: MapLibreMap): ViewportPadding {
        val density = appContext?.resources?.displayMetrics?.density ?: 2f
        return computeRouteOverviewPadding(density, map.width, map.height)
    }

    private fun registerPoiInteractions(map: MapLibreMap) {
        map.addOnCameraIdleListener {
            if (_uiState.value.isNavigating ||
                _uiState.value.selectedPoi != null ||
                _uiState.value.isInTopDownView
            ) {
                return@addOnCameraIdleListener
            }

            val component = map.locationComponent
            if (component.isLocationComponentActivated &&
                component.cameraMode == CameraMode.TRACKING_GPS &&
                !_uiState.value.isCameraDetached
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
                        place.copy(
                            category = normalizeOvertureCategory(place.category),
                            poiSource = POI_SOURCE_OVERTURE,
                        )
                    }
                }
                if (!isActive) return@launch

                val dedupedFirstPass = mergePoiPins(localResults + tileResults, emptyList())
                mergeIntoPoiCache(dedupedFirstPass)
                encounteredPlacesSampler.persist(localResults, POI_SOURCE_OVERTURE)
                encounteredPlacesSampler.persist(tileResults, POI_SOURCE_VECTOR)
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
        }
    }

    private fun isTapNearPinIcon(
        map: MapLibreMap,
        screenPoint: android.graphics.PointF,
        pinLat: Double,
        pinLng: Double,
    ): Boolean {
        val density = mapView?.context?.resources?.displayMetrics?.density ?: 2.5f
        return customPinController.isTapNearPinIcon(map, screenPoint, pinLat, pinLng, density)
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
        val tokens = tokenizeSearchQuery(query)
        if (tokens.isEmpty()) return emptyList()

        return poiCache.values
            .filter { place -> placeMatchesQueryTokens(place, tokens) }
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

    private fun searchViewportPoiCache(
        query: String,
        currentLat: Double,
        currentLng: Double,
    ): List<SearchResultPlace> {
        val tokens = tokenizeSearchQuery(query)
        if (tokens.isEmpty()) return emptyList()
        val bounds = currentViewportBounds() ?: return emptyList()

        return poiCache.values
            .filter { place ->
                placeInBounds(place, bounds) && placeMatchesQueryTokens(place, tokens)
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

    private fun tokenizeSearchQuery(query: String): List<String> {
        val trimmed = query.trim().lowercase()
        if (trimmed.length < 2) return emptyList()
        return trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
    }

    private fun placeMatchesQueryTokens(place: SearchResultPlace, tokens: List<String>): Boolean {
        val haystack = "${place.name} ${place.subTitle} ${place.category}".lowercase()
        return tokens.all { token -> haystack.contains(token) }
    }

    private fun currentViewportBounds(): GeoBounds? = readOnMainThread {
        val map = mapLibreMap ?: return@readOnMainThread null
        if (map.cameraPosition.zoom < MIN_POI_ZOOM) return@readOnMainThread null
        val bounds = map.projection.visibleRegion.latLngBounds
        val latSpan = bounds.northEast.latitude - bounds.southWest.latitude
        val lngSpan = bounds.northEast.longitude - bounds.southWest.longitude
        val padLat = latSpan * BBOX_PADDING_FACTOR / 2
        val padLng = lngSpan * BBOX_PADDING_FACTOR / 2
        expandGeoBounds(bounds, padLat, padLng)
    }

    private fun <T> readOnMainThread(block: () -> T): T {
        if (Looper.getMainLooper().isCurrentThread) return block()
        return runBlocking(Dispatchers.Main.immediate) { block() }
    }

    private fun poiCacheInViewport(): List<SearchResultPlace> {
        val bounds = currentViewportBounds() ?: return emptyList()
        return poiCache.values.filter { place -> placeInBounds(place, bounds) }
    }

    private suspend fun seedViewportPoisIntoCache(map: MapLibreMap) {
        val zoom = map.cameraPosition.zoom
        if (zoom < MIN_POI_ZOOM) return
        val bounds = map.projection.visibleRegion.latLngBounds
        val center = map.cameraPosition.target ?: return
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
                place.copy(
                    category = normalizeOvertureCategory(place.category),
                    poiSource = POI_SOURCE_OVERTURE,
                )
            }
        }
        val deduped = mergePoiPins(localResults + tileResults, emptyList())
        mergeIntoPoiCache(deduped)
        trimPoiCacheToMax(center)
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
            poiSource = POI_SOURCE_VECTOR,
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
        return customPinController.findSavedPlaceAt(savedPlacesCache, lat, lng, NEARBY_PIN_DEDUP_THRESHOLD_M)
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

    private fun findPoiCacheEntryNear(place: SearchResultPlace): Pair<String, SearchResultPlace>? {
        for ((key, cached) in poiCache) {
            if (placesWithinMeters(
                    cached.latitude,
                    cached.longitude,
                    place.latitude,
                    place.longitude,
                    DEDUP_THRESHOLD_M,
                )
            ) {
                return key to cached
            }
        }
        return null
    }

    private fun mergeIntoPoiCache(places: List<SearchResultPlace>) {
        for (place in places) {
            val near = findPoiCacheEntryNear(place)
            if (near == null) {
                poiCache[poiCacheKey(place)] = place
            } else {
                val (existingKey, existing) = near
                val merged = preferPoiEntry(existing, place)
                if (existingKey != poiCacheKey(merged)) {
                    poiCache.remove(existingKey)
                }
                poiCache[poiCacheKey(merged)] = merged
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

        val pins = mergePoiPins(sortPoiPinsForMerge(poiCache.values.toList()), emptyList())
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

    private fun hideNativeVectorPoiLayers(style: Style) {
        VECTOR_POI_LAYER_IDS.forEach { id ->
            style.getLayer(id)?.setProperties(PropertyFactory.visibility(Property.NONE))
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
        val geoJson = buildPoiGeoJson(places, savedPlacesKeys)
        mixPoiOverlayActive = places.isNotEmpty()
        map.getStyle { style ->
            poiOverlayRenderer.ensureMixPoiOverlayLayers(style, geoJson)
            syncPoiOverlayVisibility(style)
        }
    }

    private fun clearPoiOverlay() {
        val map = mapLibreMap ?: return
        mixPoiOverlayActive = false
        map.getStyle { style ->
            poiOverlayRenderer.clearMixPoiSource(style, EMPTY_POI_GEOJSON)
            syncPoiOverlayVisibility(style)
        }
    }

    private fun shouldShowMixPoiLabels(): Boolean {
        val state = _uiState.value
        return state.isCameraDetached &&
            !state.isInTopDownView &&
            !state.isNavigating &&
            state.selectedPoi == null
    }

    private fun syncPoiOverlayVisibility(style: Style) {
        poiOverlayRenderer.syncPoiLabelVisibility(style, shouldShowMixPoiLabels() && mixPoiOverlayActive)
        syncNativePoiLayerVisibility(style)
    }

    private fun syncNativePoiLayerVisibility(style: Style) {
        val state = _uiState.value
        poiOverlayRenderer.syncNativePoiLayerVisibility(
            style = style,
            useVectorTiles = useVectorTiles,
            isNavigating = state.isNavigating,
            zoom = mapLibreMap?.cameraPosition?.zoom ?: 0.0,
            mixPoiOverlayActive = mixPoiOverlayActive,
            isInTopDownView = state.isInTopDownView,
            hasSelectedPoi = state.selectedPoi != null,
        )
    }

    private fun isPlaceRenderedOnMap(place: SearchResultPlace): Boolean {
        if (poiCache.values.any { cached ->
                placesWithinMeters(
                    cached.latitude,
                    cached.longitude,
                    place.latitude,
                    place.longitude,
                    DEDUP_THRESHOLD_M,
                )
            }
        ) {
            return true
        }
        val map = mapLibreMap ?: return false
        val screenPoint = map.projection.toScreenLocation(LatLng(place.latitude, place.longitude))
        if (map.queryRenderedFeatures(screenPoint, POI_LAYER_ID).isNotEmpty()) return true
        if (useVectorTiles &&
            map.queryRenderedFeatures(screenPoint, *VECTOR_POI_LAYER_IDS).isNotEmpty()
        ) {
            return true
        }
        return false
    }

    private fun showForcedPreviewPoi(place: SearchResultPlace) {
        val map = mapLibreMap ?: return
        val geoJson = buildPoiGeoJson(listOf(place), savedPlacesKeys)
        map.getStyle { style ->
            ensurePreviewPoiLayers(style, geoJson)
            style.getLayer(PREVIEW_POI_LAYER_ID)?.setProperties(PropertyFactory.visibility(Property.VISIBLE))
            style.getLayer(PREVIEW_POI_LABEL_LAYER_ID)?.setProperties(PropertyFactory.visibility(Property.VISIBLE))
            syncNativePoiLayerVisibility(style)
        }
    }

    private fun clearForcedPreviewPoi() {
        val map = mapLibreMap ?: return
        map.getStyle { style ->
            (style.getSource(PREVIEW_POI_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(EMPTY_POI_GEOJSON)
            style.getLayer(PREVIEW_POI_LAYER_ID)?.setProperties(PropertyFactory.visibility(Property.NONE))
            style.getLayer(PREVIEW_POI_LABEL_LAYER_ID)?.setProperties(PropertyFactory.visibility(Property.NONE))
            syncNativePoiLayerVisibility(style)
        }
    }

    private fun ensurePreviewPoiLayers(style: Style, geoJson: String) {
        val existing = style.getSource(PREVIEW_POI_SOURCE_ID)
        if (existing is GeoJsonSource) {
            existing.setGeoJson(geoJson)
            return
        }
        style.addSource(GeoJsonSource(PREVIEW_POI_SOURCE_ID, geoJson))
        val iconLayer = SymbolLayer(PREVIEW_POI_LAYER_ID, PREVIEW_POI_SOURCE_ID).withProperties(
            *poiIconOnlyLayerProperties(mixPoiIconExpression()),
        )
        val labelLayer = SymbolLayer(PREVIEW_POI_LABEL_LAYER_ID, PREVIEW_POI_SOURCE_ID).withProperties(
            *poiTextOnlyLayerProperties(),
        )
        when {
            style.getLayer(SAVED_PLACES_LAYER_ID) != null -> {
                style.addLayerBelow(iconLayer, SAVED_PLACES_LAYER_ID)
            }
            style.getLayer(POI_LABEL_LAYER_ID) != null -> {
                style.addLayerAbove(iconLayer, POI_LABEL_LAYER_ID)
            }
            style.getLayer(POI_LAYER_ID) != null -> {
                style.addLayerAbove(iconLayer, POI_LAYER_ID)
            }
            else -> {
                val anchor = resolveMapOverlayAnchorLayerId(style)
                if (anchor != null) {
                    style.addLayerAbove(iconLayer, anchor)
                } else {
                    style.addLayer(iconLayer)
                }
            }
        }
        style.addLayerAbove(labelLayer, PREVIEW_POI_LAYER_ID)
    }

    /** Clears overlay GeoJSON and in-memory POI cache (navigation teardown, style reset). */
    private fun clearPoiLayer() {
        clearPoiCache()
        clearPoiOverlay()
    }

    private fun placeCustomPin(lat: Double, lng: Double, pending: Boolean) {
        val map = mapLibreMap ?: return
        val geoJson = buildCustomPinGeoJson(lat, lng, pending)
        map.getStyle { style -> customPinController.placeCustomPin(style, geoJson) }
    }

    private fun clearCustomPin() {
        val map = mapLibreMap ?: return
        map.getStyle { style -> customPinController.clearCustomPin(style, EMPTY_CUSTOM_PIN_GEOJSON) }
    }

    private fun updateSavedPlacesLayer(places: List<SearchResultPlace>) {
        val map = mapLibreMap ?: return
        val geoJson = if (places.isEmpty()) {
            EMPTY_POI_GEOJSON
        } else {
            buildPoiGeoJson(places, savedPlacesKeys, forceStarred = true)
        }
        map.getStyle { style ->
            poiOverlayRenderer.updateSavedPlacesLayer(style, geoJson, places.isNotEmpty())
        }
    }



    private fun resolveMapViewOrigin(): LatLng? {
        val map = mapLibreMap ?: return null
        val position = map.cameraPosition
        if (position.zoom < ROUTING_MIN_ZOOM) return null
        return position.target
    }

    private fun stopDeadReckoning() {
        deadReckoningJob?.cancel()
        deadReckoningJob = null
        drAnchor = null
        lastDeadReckoningLocation = null
    }

    private fun maybePrefetchDrivingTiles(displayLocation: Location) {
        val map = mapLibreMap ?: return
        val context = appContext ?: return
        val component = map.locationComponent
        val trackingGps = component.isLocationComponentActivated &&
            component.isLocationComponentEnabled &&
            component.cameraMode == CameraMode.TRACKING_GPS
        val bearing = when {
            displayLocation.hasBearing() -> displayLocation.bearing
            bearingEnricher.lastBearing() != null -> bearingEnricher.lastBearing()!!
            else -> map.cameraPosition.bearing.toFloat()
        }
        drivingTilePrefetcher?.maybePrefetch(
            lat = displayLocation.latitude,
            lng = displayLocation.longitude,
            bearingDeg = bearing,
            zoom = map.cameraPosition.zoom,
            enabled = useVectorTiles &&
                trackingGps &&
                !_uiState.value.isCameraDetached &&
                !_uiState.value.isInTopDownView &&
                !isRouteOverviewActive() &&
                !_uiState.value.isRouteSelecting &&
                isNetworkAvailable(context),
        )
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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
            "%.5fÂ° %s, %.5fÂ° %s",
            kotlin.math.abs(lat),
            latDir,
            kotlin.math.abs(lng),
            lngDir,
        )
    }


    private fun animateTopDownCamera(lat: Double, lng: Double, zoom: Double, exploreMode: Boolean = false) {
        navigationCamera.animateTopDownCamera(lat, lng, zoom, exploreMode)
    }

    private fun cancelPoiPreviewRetries() = navigationCamera.cancelPoiPreviewRetries()

    private fun cancelTopDownViewportSync() = navigationCamera.cancelTopDownViewportSync()

    private fun clearRoutePreviewState() {
        navigationCamera.clearPoiPreviewState()
        navigationCamera.cancelTopDownViewportSync()
    }

    private fun handleMapLayoutChange(map: MapLibreMap) = navigationCamera.handleMapLayoutChange(map)

    private fun applyPuckPaddingUpdate(map: MapLibreMap, bypassRenderThrottle: Boolean = false) {
        navigationCamera.applyPuckPaddingUpdate(map, bypassRenderThrottle)
    }

    private fun clearViewportPaddingForPreview(map: MapLibreMap) =
        navigationCamera.clearViewportPaddingForPreview(map)

    private fun scheduleFreeDrivePaddingRestore(map: MapLibreMap) =
        navigationCamera.scheduleFreeDrivePaddingRestore(map)

    private fun resolveFreeDriveTarget(map: MapLibreMap): LatLng? =
        navigationCamera.resolveFreeDriveTarget(map)

    private fun activateFreeDriveTrackingMode(map: MapLibreMap) =
        navigationCamera.activateFreeDriveTrackingMode(map)

    private fun snapCameraToGpsIfNeeded(latLng: LatLng) = navigationCamera.snapCameraToGpsIfNeeded(latLng)

    private fun enterNavigationCamera() = navigationCamera.enterNavigationCamera()

    private fun computeDrivingViewportPadding(map: MapLibreMap): ViewportPadding =
        navigationCamera.computeDrivingViewportPadding(map)

    private fun invalidateDrivingPaddingCache() = navigationCamera.invalidateDrivingPaddingCache()

    private fun applyDrivingViewportPadding(map: MapLibreMap) = navigationCamera.applyDrivingViewportPadding(map)

    private fun applyDrivingTrackingPadding(map: MapLibreMap) = navigationCamera.applyDrivingTrackingPadding(map)

    private fun shouldSmoothPuckMotion(): Boolean = locationTracking.shouldSmoothPuckMotion()

    private fun beginLocationAcquisition(context: Context) = locationTracking.beginLocationAcquisition(context)

    private fun refreshLocationOnly(context: Context) = locationTracking.refreshLocationOnly(context)

    private fun hasLocationPermission(context: Context): Boolean =
        locationTracking.hasLocationPermission(context)

    private fun readLastKnownLocation(context: Context): LatLng? =
        locationTracking.readLastKnownLocation(context)

    companion object {
        private const val TAG = "MapLibreEngineImpl"
        /** Every layer id the LocationComponent (puck) can render, used to keep overlays below it. */
        private val PUCK_LAYER_IDS = setOf(
            LocationComponentConstants.SHADOW_LAYER,
            LocationComponentConstants.BACKGROUND_LAYER,
            LocationComponentConstants.FOREGROUND_LAYER,
            LocationComponentConstants.BEARING_LAYER,
            LocationComponentConstants.ACCURACY_LAYER,
            LocationComponentConstants.PULSING_CIRCLE_LAYER,
        )
        private const val MAP_UI_MARGIN_DP = 8f
        /** MapLibre logo width (~92 dp) plus a small gap before the â„¹ button. */
        private const val ATTRIBUTION_LEFT_MARGIN_DP = 98f
        private const val DEFAULT_ZOOM = 15.0
        private const val DEFAULT_ZOOM_FALLBACK = 6.0
        private const val ROUTING_MIN_ZOOM = 10.0
        private const val NAV_TILT = 63.0
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
        private const val OFF_ROUTE_GRACE_AFTER_MANEUVER_MS = 8_000L
        private const val ROUTE_OVERVIEW_ANIMATION_MS = 2000
        private const val ROUTE_OVERVIEW_HOLD_MS = 10_000L
        private const val FRESH_LOCATION_MIN_TIME_MS = 500L
        private const val LOCATION_FIX_DEDUP_TIME_MS = 50L
        private const val PUCK_PUSH_MIN_DIST_M = 3f
        private const val FORCE_PUCK_RENDER_MIN_MS = 400L
        private const val UI_STATE_COORD_THROTTLE_MS = 1000L
        private const val UI_STATE_COORD_MIN_MOVE_M = 20f
        private const val LOCATION_POLL_INTERVAL_MS = 1000L
        private const val LOCATION_POLL_ATTEMPTS = 15
        private const val LOCATION_ACQUIRE_TIMEOUT_MS = 8000L
        private val LOCATION_RETRY_DELAYS_MS = longArrayOf(1_000L, 3_000L, 8_000L)
        private const val ROUTE_ID_OSRM_FASTEST = "osrm_fastest"
        private const val ROUTE_ID_TOMTOM = "tomtom_traffic"
        private const val ROUTE_ID_OSRM_ALT = "osrm_alt"
        private val VECTOR_POI_LAYER_IDS = arrayOf("poi_r1", "poi_r7", "poi_r20", "poi_transit")
        private const val POI_SOURCE_ID = "mix-poi-source"
        private const val POI_LAYER_ID = "mix-poi-layer"
        private const val POI_LABEL_LAYER_ID = "mix-poi-label-layer"
        private const val PREVIEW_POI_SOURCE_ID = "mix-preview-poi-source"
        private const val PREVIEW_POI_LAYER_ID = "mix-preview-poi-layer"
        private const val PREVIEW_POI_LABEL_LAYER_ID = "mix-preview-poi-label-layer"
        private const val CUSTOM_PIN_SOURCE_ID = "mix-custom-pin-source"
        private const val CUSTOM_PIN_LAYER_ID = "mix-custom-pin-layer"
        private const val SAVED_PLACES_SOURCE_ID = "mix-saved-source"
        private const val SAVED_PLACES_LAYER_ID = "mix-saved-layer"
        private const val MIN_POI_ZOOM = 13.0
        private const val MAX_POI_PINS = 100
        private const val POI_CACHE_SEARCH_LIMIT = 15
        private const val NEARBY_POI_SUGGESTION_LIMIT = 20
        private const val POI_DEBOUNCE_MS = 400L
        private const val ENCOUNTER_NEARBY_RADIUS_M = 10_000f
        private const val SOURCE_SEARCH = "search"
        private const val NEARBY_SEARCH_BBOX_DELTA = 0.5 // aligned with LocalPlacesRepository text-search bbox
        private const val BBOX_PADDING_FACTOR = 1.5
        private const val PHOTON_MOVE_THRESHOLD_M = 300f
        private const val MAP_TAP_NEAREST_POI_MAX_M = 500f
        /** Screen-space hit radius for compact 24 dp circular POI icons. */
        private const val MAP_PIN_ICON_HIT_RADIUS_DP = 20f
        private const val EMPTY_POI_GEOJSON = """{"type":"FeatureCollection","features":[]}"""
        private const val EMPTY_CUSTOM_PIN_GEOJSON = """{"type":"FeatureCollection","features":[]}"""
        private const val METERS_PER_DEGREE_LAT = 111_320.0
        private const val STEP_ADVANCE_THRESHOLD_M = 25f
        private const val ARRIVAL_THRESHOLD_M = 15f
        private const val DEDUP_THRESHOLD_M = 50f
        private const val MAX_SEARCH_RADIUS_M = 500_000f
        private const val ARRIVAL_FREE_DRIVE_DELAY_MS = 5_000L
        private const val DRIVING_PREFETCH_ZOOM_DELTA = 3
        private const val AMBIENT_CACHE_MAX_BYTES = 256L * 1024L * 1024L
        private const val LOCATION_ENGINE_INTERVAL_MS = 750L
        private const val LOCATION_ENGINE_FASTEST_INTERVAL_MS = 500L
        private const val DRIVING_ANIMATION_FPS = 60
        private val DEFAULT_LOCATION = LatLng(12.8797, 121.7740)

        /**
         * Minimal MapLibre style that sources raster tiles from the public OSM tile server.
         * Global coverage, no API key required. Raster tiles don't support sharp 3D perspective
         * so tilt is kept moderate (30Â°). Replace with a vector style for a crisper driving view.
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
