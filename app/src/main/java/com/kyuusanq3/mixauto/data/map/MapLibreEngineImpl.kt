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
    initialDrivingTilt: Double = 40.0,
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
    private var freeDriveTilt = initialDrivingTilt
    private var navTilt = initialDrivingTilt + NAV_TILT_OFFSET
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
                routeRenderer.restackRouteLayersAbove(style, anchorLayerId)
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
    @Volatile
    private var mapReleased = false
    private var appContext: Context? = null
    private var mapLibreInitialized = false
    private var lastKnownLocation: LatLng? = null
    private var fullRouteSteps: List<LegStep> = emptyList()
    private var currentStepIndex: Int = 0
    private var destinationLatLng: LatLng? = null
    private var navigationArrivalTriggered = false
    private var hasSnappedCameraToGps = false
    private var pendingLocationActivation = false
    private var poiRefreshJob: Job? = null
    private var rememberEncounteredPlaces = initialRememberEncounteredPlaces
    private var savedPlacesKeys = emptySet<String>()
    private var savedPlacesCache = emptyList<SearchResultPlace>()
    private var routeGeometryPoints: List<LatLng> = emptyList()
    private val offRouteDetector = OffRouteDetector(
        projectionForLocation = ::projectionForLocation,
        onReroute = { origin, destLat, destLng ->
            lastKnownLocation = LatLng(origin.latitude, origin.longitude)
            val bearing = if (origin.hasBearing()) origin.bearing else null
            rerouteNavigation(
                LatLng(origin.latitude, origin.longitude),
                destLat,
                destLng,
                bearing,
            )
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
        queryVectorPois = { bounds ->
            mapLibreMap?.let { poiQueryCoordinator.queryTilePois(it, bounds) } ?: emptyList()
        },
        onPlacesCollected = { places ->
            poiOverlayCoordinator.mergeIntoPoiCache(places)
            lastKnownLocation?.let { poiOverlayCoordinator.trimPoiCacheToMax(it) }
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
    /** TomTom route stashed as a tap-to-switch lighter-traffic alternate during navigation. */
    private var stashedLighterTrafficRoute: RouteResult? = null
    /** TomTom delay from parallel fetch when conventional OSRM route is active (nav-start TTS tier 2). */
    private var stashedParallelTomTomDelaySeconds = 0
    private var pendingNavTrafficPhrase: String? = null
    private var navTrafficPrefetchJob: Job? = null
    /** True until first [activateNavigationTracking] consumes the nav-start traffic line. */
    private var navStartTrafficEligible = false
    private var drivingTilePrefetcher: DrivingTilePrefetcher? = null
    private lateinit var poiOverlayCoordinator: PoiOverlayCoordinator
    private lateinit var poiQueryCoordinator: PoiQueryCoordinator
    private lateinit var routeOverviewController: RouteOverviewController
    private lateinit var poiSelectionController: PoiSelectionController
    private lateinit var freeDriveSessionCoordinator: FreeDriveSessionCoordinator
    private val destinationSearchCoordinator by lazy {
        DestinationSearchCoordinator(
            localPlaces = { localPlaces },
            encounteredPlaces = { encounteredPlaces },
            rememberEncounteredPlaces = { rememberEncounteredPlaces },
            isValidSearchOrigin = searchOriginResolver::isValidSearchOrigin,
            resolveSearchOrigin = searchOriginResolver::resolveSearchOrigin,
            hasReliableSearchOrigin = searchOriginResolver::hasReliableSearchOrigin,
            poiQueryCoordinator = poiQueryCoordinator,
            mergeIntoPoiCache = poiOverlayCoordinator::mergeIntoPoiCache,
            updatePoiLayerFromCache = poiOverlayCoordinator::updatePoiLayerFromCache,
            maxSearchRadiusM = MAX_SEARCH_RADIUS_M,
        )
    }

    private val searchOriginResolver by lazy {
        SearchOriginResolver(
            uiState = { _uiState.value },
            updateUiState = { transform -> _uiState.update(transform) },
            lastKnownLocation = { lastKnownLocation },
            setLastKnownLocation = { lastKnownLocation = it },
            mapLibreMap = { mapLibreMap },
            appContext = { appContext },
            hasLocationPermission = ::hasLocationPermission,
            readLastKnownLocation = ::readLastKnownLocation,
            refreshLocationOnly = ::refreshLocationOnly,
        )
    }

    private val navigationSessionCoordinator by lazy {
        NavigationSessionCoordinator(
            uiState = { _uiState.value },
            updateUiState = { transform -> _uiState.update(transform) },
            engineScope = engineScope,
            appContext = { appContext },
            mapLibreMap = { mapLibreMap },
            routeRenderer = { routeRenderer },
            tomTomApiKey = { tomTomApiKey },
            offRouteDetector = { offRouteDetector },
            navigationVoice = { navigationVoice },
            getRouteGeometryPoints = { routeGeometryPoints },
            setRouteGeometryPoints = { routeGeometryPoints = it },
            getFullRouteSteps = { fullRouteSteps },
            setFullRouteSteps = { fullRouteSteps = it },
            getCurrentStepIndex = { currentStepIndex },
            setCurrentStepIndex = { currentStepIndex = it },
            getDestinationLatLng = { destinationLatLng },
            setDestinationLatLng = { destinationLatLng = it },
            getNavigationArrivalTriggered = { navigationArrivalTriggered },
            setNavigationArrivalTriggered = { navigationArrivalTriggered = it },
            getStashedLighterTrafficRoute = { stashedLighterTrafficRoute },
            setStashedLighterTrafficRoute = { stashedLighterTrafficRoute = it },
            getStashedParallelTomTomDelaySeconds = { stashedParallelTomTomDelaySeconds },
            setStashedParallelTomTomDelaySeconds = { stashedParallelTomTomDelaySeconds = it },
            getPendingNavTrafficPhrase = { pendingNavTrafficPhrase },
            setPendingNavTrafficPhrase = { pendingNavTrafficPhrase = it },
            getNavTrafficPrefetchJob = { navTrafficPrefetchJob },
            setNavTrafficPrefetchJob = { navTrafficPrefetchJob = it },
            getNavStartTrafficEligible = { navStartTrafficEligible },
            setNavStartTrafficEligible = { navStartTrafficEligible = it },
            getPoiRefreshJob = { poiRefreshJob },
            setPoiRefreshJob = { poiRefreshJob = it },
            getLastKnownLocation = { lastKnownLocation },
            setLastKnownLocation = { lastKnownLocation = it },
            resolveMapViewOrigin = ::resolveMapViewOrigin,
            beginLocationAcquisition = ::beginLocationAcquisition,
            readLastKnownLocation = ::readLastKnownLocation,
            hasLocationPermission = ::hasLocationPermission,
            updateLocationEngineInterval = ::updateLocationEngineInterval,
            clearForcedPreviewPoi = poiOverlayCoordinator::clearForcedPreviewPoi,
            clearPoiLayer = poiOverlayCoordinator::clearPoiLayer,
            clearCustomPin = { poiSelectionController.clearCustomPin() },
            clearRoutePreviewState = ::clearRoutePreviewState,
            hideNativeVectorPoiLayers = poiOverlayCoordinator::hideNativeVectorPoiLayers,
            drawRoute = ::drawRoute,
            showRouteThenDive = routeOverviewController::showRouteThenDive,
            enterNavigationCamera = ::enterNavigationCamera,
        )
    }

    private val mapInteractionController by lazy {
        MapInteractionController(
            uiState = { _uiState.value },
            updateUiState = { transform -> _uiState.update(transform) },
            mapLibreMap = { mapLibreMap },
            mapView = { mapView },
            mapReleased = { mapReleased },
            engineScope = engineScope,
            getPoiRefreshJob = { poiRefreshJob },
            setPoiRefreshJob = { poiRefreshJob = it },
            focusOnPoi = ::focusOnPoi,
            switchToLighterTrafficAlternate = ::switchToLighterTrafficAlternate,
            placeCustomPin = poiSelectionController::placeCustomPin,
            animateTopDownCamera = ::animateTopDownCamera,
            clearPoiOverlay = poiOverlayCoordinator::clearPoiOverlay,
            mergeIntoPoiCache = poiOverlayCoordinator::mergeIntoPoiCache,
            trimPoiCacheToMax = poiOverlayCoordinator::trimPoiCacheToMax,
            refreshPoiOverlay = poiOverlayCoordinator::refreshPoiOverlay,
            poiQueryCoordinator = poiQueryCoordinator,
            encounteredPlacesSampler = encounteredPlacesSampler,
            shouldQueryPhoton = poiOverlayCoordinator::shouldQueryPhoton,
            setLastPhotonQueryCenter = poiOverlayCoordinator::setLastPhotonQueryCenter,
            findSavedPlaceAt = poiSelectionController::findSavedPlaceAt,
            coordinatesNear = poiSelectionController::coordinatesNear,
            formatLatLng = poiSelectionController::formatLatLng,
            customPinController = customPinController,
            localPlaces = { localPlaces },
            useVectorTiles = { useVectorTiles },
            lastKnownLocation = { lastKnownLocation },
            poiCache = { poiOverlayCoordinator.poiCacheMap() },
            mapTapDismissHandler = { mapTapDismissHandler },
            routeTomtomLayerId = ROUTE_TOMTOM_LAYER_ID,
            savedPlacesLayerId = SAVED_PLACES_LAYER_ID,
            customPinLayerId = CUSTOM_PIN_LAYER_ID,
            poiLayerId = POI_LAYER_ID,
            vectorPoiLayerIds = VECTOR_POI_LAYER_IDS,
            minPoiZoom = MIN_POI_ZOOM,
            maxPoiPins = MAX_POI_PINS,
            poiDebounceMs = POI_DEBOUNCE_MS,
            bboxPaddingFactor = BBOX_PADDING_FACTOR,
            poiPreviewZoom = POI_PREVIEW_ZOOM,
        )
    }

    private fun rerouteNavigation(
        origin: LatLng,
        destLat: Double,
        destLng: Double,
        originBearingDeg: Float?,
    ) {
        navigationSessionCoordinator.startNavigation(
            origin,
            destLat,
            destLng,
            isReroute = true,
            originBearingDeg = originBearingDeg,
        )
    }

    private fun onNavigationTrackingEngaged() {
        val trafficPhrase = navigationSessionCoordinator.consumeNavStartTrafficPhrase()
        fullRouteSteps.firstOrNull()?.toNavStepPhrase()?.let { firstStep ->
            navigationVoice?.onNavigationDrivingStarted(firstStep, trafficPhrase)
        }
        updateLocationEngineInterval()
    }

    private fun updateLocationEngineInterval() {
        // Interval is owned by LocationComponent's single subscription through the engine chain.
    }

    init {
        var poiCoordRef: PoiOverlayCoordinator? = null
        var poiQueryRef: PoiQueryCoordinator? = null
        poiCoordRef = PoiOverlayCoordinator(
            mapLibreMap = { mapLibreMap },
            mapReleased = { mapReleased },
            uiState = { _uiState.value },
            useVectorTiles = { useVectorTiles },
            savedPlacesKeys = { savedPlacesKeys },
            poiOverlayRenderer = poiOverlayRenderer,
            poiQueryCoordinator = { poiQueryRef!! },
            resolveMapOverlayAnchorLayerId = ::resolveMapOverlayAnchorLayerId,
            poiLayerId = POI_LAYER_ID,
            poiLabelLayerId = POI_LABEL_LAYER_ID,
            savedPlacesLayerId = SAVED_PLACES_LAYER_ID,
            previewPoiSourceId = PREVIEW_POI_SOURCE_ID,
            previewPoiLayerId = PREVIEW_POI_LAYER_ID,
            previewPoiLabelLayerId = PREVIEW_POI_LABEL_LAYER_ID,
            vectorPoiLayerIds = VECTOR_POI_LAYER_IDS,
            emptyPoiGeoJson = EMPTY_POI_GEOJSON,
            maxPoiPins = MAX_POI_PINS,
            bboxPaddingFactor = BBOX_PADDING_FACTOR,
            photonMoveThresholdM = PHOTON_MOVE_THRESHOLD_M,
            dedupThresholdM = DEDUP_THRESHOLD_M,
            mapTapNearestPoiMaxM = MAP_TAP_NEAREST_POI_MAX_M,
        )
        poiQueryRef = PoiQueryCoordinator(
            poiCache = { poiCoordRef!!.poiCacheMap() },
            savedPlacesKeys = { savedPlacesKeys },
            mapLibreMap = { mapLibreMap },
            mapView = { mapView },
            useVectorTiles = { useVectorTiles },
            lastKnownLocation = { lastKnownLocation },
            localPlaces = { localPlaces },
            encounteredPlaces = { encounteredPlaces },
            rememberEncounteredPlaces = { rememberEncounteredPlaces },
            mergeIntoPoiCache = { poiCoordRef!!.mergeIntoPoiCache(it) },
            trimPoiCacheToMax = { poiCoordRef!!.trimPoiCacheToMax(it) },
            resolveSearchOrigin = searchOriginResolver::resolveSearchOrigin,
            isValidSearchOrigin = searchOriginResolver::isValidSearchOrigin,
        )
        poiOverlayCoordinator = poiCoordRef!!
        poiQueryCoordinator = poiQueryRef!!

        var routeOverviewRef: RouteOverviewController? = null
        var locRef: LocationTrackingController? = null
        var navRef: NavigationCameraController? = null
        routeOverviewRef = RouteOverviewController(
            engineScope = engineScope,
            mapView = { mapView },
            mapLibreMap = { mapLibreMap },
            appContext = { appContext },
            uiState = { _uiState.value },
            updateUiState = { transform -> _uiState.update(transform) },
            routeGeometryPoints = { routeGeometryPoints },
            lastKnownLocation = { lastKnownLocation },
            invalidateDrivingPaddingCache = { navRef!!.invalidateDrivingPaddingCache() },
            resetSmoothingMotion = { locRef!!.resetSmoothingMotion() },
            clearNavigationCameraTransitionActive = { navRef!!.clearNavigationCameraTransitionActive() },
            setNavigationCameraTransitionActive = { navRef!!.setNavigationCameraTransitionActive(it) },
            enterNavigationCamera = { navRef!!.enterNavigationCamera() },
            clearRoutePreviewState = ::clearRoutePreviewState,
            cancelDrivingTilePrefetch = { drivingTilePrefetcher?.cancel() },
            routeOverviewAnimationMs = ROUTE_OVERVIEW_ANIMATION_MS,
            routeOverviewHoldMs = ROUTE_OVERVIEW_HOLD_MS,
        )
        navRef = NavigationCameraController(
            mapView = { mapView },
            mapLibreMap = { mapLibreMap },
            appContext = { appContext },
            uiState = { _uiState.value },
            updateUiState = { transform -> _uiState.update(transform) },
            freeDriveZoom = { freeDriveZoom },
            navZoom = { navZoom },
            freeDriveTilt = { freeDriveTilt },
            navTilt = { navTilt },
            puckHorizontalOffset = { puckHorizontalOffset },
            puckVerticalOffset = { puckVerticalOffset },
            useVectorTiles = { useVectorTiles },
            lastKnownLocation = { lastKnownLocation },
            setLastKnownLocation = { lastKnownLocation = it },
            hasSnappedCameraToGps = { hasSnappedCameraToGps },
            setHasSnappedCameraToGps = { hasSnappedCameraToGps = it },
            isRouteOverviewActive = { routeOverviewRef!!.isRouteOverviewActive() },
            lastDrivingSpeedMps = { locRef!!.lastDrivingSpeedMps() },
            readLastKnownLocation = { ctx -> locRef!!.readLastKnownLocation(ctx) },
            shouldSmoothPuckMotion = { locRef!!.shouldSmoothPuckMotion() },
            forceLocationUpdateForImmediateRender = { map, bypass, allow ->
                locRef!!.forceLocationUpdateForImmediateRender(map, bypass, allow)
            },
            resetSmoothingMotion = { locRef!!.resetSmoothingMotion() },
            stopDeadReckoning = ::stopDeadReckoning,
            showNativeVectorPoiLayers = poiOverlayCoordinator::showNativeVectorPoiLayers,
            clearPoiOverlay = poiOverlayCoordinator::clearPoiOverlay,
            fitRouteOverviewCamera = { origin, destination, animate ->
                routeOverviewRef!!.fitRouteOverviewCamera(origin, destination, animate)
            },
            routeOverviewOrigin = { routeOverviewRef!!.routeOverviewOrigin() },
            routeOverviewDestination = { routeOverviewRef!!.routeOverviewDestination() },
            lastRouteOverviewLayoutWidth = { routeOverviewRef!!.lastRouteOverviewLayoutWidth() },
            lastRouteOverviewLayoutHeight = { routeOverviewRef!!.lastRouteOverviewLayoutHeight() },
            setLastRouteOverviewLayoutSize = { w, h ->
                routeOverviewRef!!.setLastRouteOverviewLayoutSize(w, h)
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
            isRouteOverviewActive = { routeOverviewRef!!.isRouteOverviewActive() },
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
            updateRouteProgress = { location ->
                if (!mapReleased && _uiState.value.isNavigating) {
                    routeRenderer.updateRouteProgress(location, routeGeometryPoints, mapLibreMap)
                }
            },
            onReroute = { origin, destLat, destLng ->
                lastKnownLocation = origin
                rerouteNavigation(origin, destLat, destLng, null)
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
            updateNavigationZoomForDistance = { distanceM ->
                navRef!!.updateNavigationZoomForDistance(distanceM)
            },
        )
        routeOverviewController = routeOverviewRef!!
        navigationCamera = navRef!!
        locationTracking = locRef!!

        poiSelectionController = PoiSelectionController(
            uiState = { _uiState.value },
            updateUiState = { transform -> _uiState.update(transform) },
            mapLibreMap = { mapLibreMap },
            navigationCamera = { navigationCamera },
            poiOverlayCoordinator = { poiOverlayCoordinator },
            customPinController = customPinController,
            encounteredPlaces = { encounteredPlaces },
            getNearbyPois = ::getNearbyPois,
            getSavedPlacesCache = { savedPlacesCache },
            getSavedPlacesKeys = { savedPlacesKeys },
            setSavedPlacesCache = { savedPlacesCache = it },
            setSavedPlacesKeys = { savedPlacesKeys = it },
            lastKnownLocation = { lastKnownLocation },
            animateTopDownCamera = ::animateTopDownCamera,
            emptyCustomPinGeoJson = EMPTY_CUSTOM_PIN_GEOJSON,
            nearbyPinDedupThresholdM = NEARBY_PIN_DEDUP_THRESHOLD_M,
            poiPreviewZoom = POI_PREVIEW_ZOOM,
        )
        freeDriveSessionCoordinator = FreeDriveSessionCoordinator(
            mapReleased = { mapReleased },
            mapLibreMap = { mapLibreMap },
            mapView = { mapView },
            updateUiState = { transform -> _uiState.update(transform) },
            navigationCamera = { navigationCamera },
            locationTracking = { locationTracking },
            poiOverlayCoordinator = { poiOverlayCoordinator },
            routeOverviewController = { routeOverviewController },
            navigationSessionCoordinator = { navigationSessionCoordinator },
            routeRenderer = { routeRenderer },
            encounteredPlacesSampler = encounteredPlacesSampler,
            navigationVoice = { navigationVoice },
            lastKnownLocation = { lastKnownLocation },
            setHasSnappedCameraToGps = { hasSnappedCameraToGps = it },
            getPoiRefreshJob = { poiRefreshJob },
            setPoiRefreshJob = { poiRefreshJob = it },
            resetNavSessionFields = {
                fullRouteSteps = emptyList()
                currentStepIndex = 0
                destinationLatLng = null
                navigationArrivalTriggered = false
                routeGeometryPoints = emptyList()
                routeRenderer.resetRouteProgress(routeGeometryPoints)
                offRouteDetector.reset()
                hasSnappedCameraToGps = false
                navigationSessionCoordinator.clearLighterTrafficAlternate()
            },
            clearCustomPin = { poiSelectionController.clearCustomPin() },
            clearRoutePreviewState = ::clearRoutePreviewState,
            stopDeadReckoning = ::stopDeadReckoning,
            updateLocationEngineInterval = ::updateLocationEngineInterval,
            withMapStyle = { block -> withMapStyle(block) },
            enterNavigationCamera = ::enterNavigationCamera,
            isNavigating = { _uiState.value.isNavigating },
        )
    }

    override fun createMapView(context: Context): View {
        mapView?.let { existing ->
            mapReleased = false
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
            mapReleased = false
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
                        withMapStyle { poiOverlayCoordinator.syncPoiOverlayVisibility(it) }
                    }
                }
                mapInteractionController.registerPoiInteractions(map)
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
        routeOverviewController.clearRouteOverviewState()
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
        val map = mapLibreMap ?: return
        if (_uiState.value.isNavigating) {
            navigationCamera.onNavZoomCeilingChanged()
        } else {
            map.animateCamera(CameraUpdateFactory.zoomTo(freeDriveZoom))
        }
    }

    override fun setDrivingTilt(tilt: Double) {
        freeDriveTilt = tilt
        navTilt = tilt + NAV_TILT_OFFSET
        val map = mapLibreMap ?: return
        val state = _uiState.value
        if (state.isNavigating) {
            navigationCamera.onDrivingTiltChanged()
        } else if (!state.isCameraDetached && !state.isInTopDownView) {
            val current = map.cameraPosition
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(current.target)
                        .zoom(current.zoom)
                        .tilt(freeDriveTilt)
                        .bearing(current.bearing)
                        .build(),
                ),
            )
        }
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
            Style.Builder().fromJson(MapStyleConstants.OSM_STYLE_JSON)
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
            poiOverlayCoordinator.updateSavedPlacesLayer(savedPlacesCache)
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
        mapReleased = true
        navigationCamera.invalidateCameraSession()
        stopDeadReckoning()
        poiRefreshJob?.cancel()
        poiRefreshJob = null
        encounteredPlacesSampler.cancel()
        navigationCamera.cancelPoiPreviewRetries()
        navigationCamera.cancelTopDownViewportSync()
        poiOverlayCoordinator.resetLastPhotonQueryCenter()
        poiOverlayCoordinator.clearPoiCache()
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
    ): List<SearchResultPlace> = destinationSearchCoordinator.searchDestination(
        query,
        currentLat,
        currentLng,
        limitDistance,
        onLocalResults,
    )

    override suspend fun seedSearchFromMapViewport() {
        val map = mapLibreMap ?: return
        withContext(Dispatchers.Main) {
            poiQueryCoordinator.seedViewportPoisIntoCache(map)
        }
    }

    override fun getNearbyPois(lat: Double, lng: Double, limit: Int): List<SearchResultPlace> =
        poiQueryCoordinator.getNearbyPois(lat, lng, limit)

    override fun hasOfflinePlacesDatabase(): Boolean =
        localPlaces?.hasInstalledDatabase == true

    override fun setRememberEncounteredPlaces(enabled: Boolean) {
        rememberEncounteredPlaces = enabled
    }

    override fun clearEncounteredPlaces() {
        encounteredPlaces?.clearAll()
    }

    override fun resolveSearchOrigin(): Pair<Double, Double> =
        searchOriginResolver.resolveSearchOrigin()

    override fun hasReliableSearchOrigin(): Boolean =
        searchOriginResolver.hasReliableSearchOrigin()

    override fun refreshSearchOrigin() = searchOriginResolver.refreshSearchOrigin()

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

    override fun recenterCamera() = freeDriveSessionCoordinator.recenterCamera()

    override fun startFreeDrive() = freeDriveSessionCoordinator.startFreeDrive()

    override fun dismissSelectedPoi() = poiSelectionController.dismissSelectedPoi()

    override fun focusOnLocation(lat: Double, lng: Double) =
        poiSelectionController.focusOnLocation(lat, lng)

    override fun focusOnPoi(place: SearchResultPlace, moveCamera: Boolean) =
        poiSelectionController.focusOnPoi(place, moveCamera)

    override fun onMapHostLayoutChanged() {
        val map = mapLibreMap ?: return
        val view = mapView ?: return
        if (view.width <= 0 || view.height <= 0) return
        view.post { navigationCamera.handleMapLayoutChange(map) }
    }

    override fun enterTopDownView() {
        navigationCamera.enterTopDownExploreView()
    }

    override fun setSavedPlaces(places: List<SearchResultPlace>) =
        poiSelectionController.setSavedPlaces(places)

    private fun resetRouteProgress() {
        routeRenderer.resetRouteProgress(routeGeometryPoints)
    }

    private fun drawRoute() {
        val map = mapLibreMap ?: return
        routeRenderer.drawRoute(map, routeGeometryPoints)
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

    override fun navigateToCoordinates(lat: Double, lng: Double) {
        navigationSessionCoordinator.navigateToCoordinates(lat, lng)
    }

    override fun switchToLighterTrafficAlternate() {
        navigationSessionCoordinator.switchToLighterTrafficAlternate()
    }

    private fun clearLighterTrafficAlternate() {
        navigationSessionCoordinator.clearLighterTrafficAlternate()
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

    private fun isMapAlive(): Boolean = !mapReleased && mapLibreMap != null

    private inline fun runIfMapAlive(block: () -> Unit) {
        if (!isMapAlive()) return
        block()
    }

    private inline fun withMapStyle(crossinline block: (Style) -> Unit) {
        val map = mapLibreMap ?: return
        if (mapReleased) return
        map.getStyle { style ->
            if (mapReleased || !style.isFullyLoaded) return@getStyle
            block(style)
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
        drivingTilePrefetcher?.maybePrefetchWhileDriving(
            displayLocation = displayLocation,
            map = map,
            useVectorTiles = useVectorTiles,
            isCameraDetached = _uiState.value.isCameraDetached,
            isInTopDownView = _uiState.value.isInTopDownView,
            isRouteOverviewActive = routeOverviewController.isRouteOverviewActive(),
            bearingEnricher = bearingEnricher,
        )
    }

    private fun resolveInitialLocation(context: Context): ResolvedLocation {
        if (!hasLocationPermission(context)) {
            Log.d(TAG, "Location permission not granted; using Philippines fallback")
            return ResolvedLocation(
                latLng = SEARCH_DEFAULT_LOCATION,
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
                latLng = SEARCH_DEFAULT_LOCATION,
                zoom = DEFAULT_ZOOM_FALLBACK,
                fromGps = false,
            )
        }
    }

    private fun animateTopDownCamera(lat: Double, lng: Double, zoom: Double, exploreMode: Boolean = false) {
        navigationCamera.animateTopDownCamera(lat, lng, zoom, exploreMode)
    }

    private fun clearRoutePreviewState() {
        navigationCamera.clearPoiPreviewState()
        navigationCamera.cancelTopDownViewportSync()
    }

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
        private const val NAV_CAMERA_DURATION_MS = 2500
        /**
         * Live owner: nav-vs-freeDrive pitch delta. Formula:
         * `navTilt = freeDriveTilt + NAV_TILT_OFFSET` (wired in [setDrivingTilt] and init).
         * Default free-drive 40° → nav 55°. Do not duplicate in [NavigationCameraController]
         * (it only applies the injected [navTilt] callback).
         */
        private const val NAV_TILT_OFFSET = 15.0
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
    }
}
