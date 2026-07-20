package com.kyuusanq3.mixauto.data.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.kyuusanq3.mixauto.BuildConfig
import com.kyuusanq3.mixauto.data.navigation.NavTickContext
import com.kyuusanq3.mixauto.data.navigation.NavigationVoiceController
import com.kyuusanq3.mixauto.domain.map.MapUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
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
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

private const val TAG = "LocationTrackingController"
private const val TRAFFIC_LAYER_ID = "mix-traffic-layer"
private const val FRESH_LOCATION_MIN_TIME_MS = 500L
private const val LOCATION_FIX_DEDUP_TIME_MS = 50L
private const val PUCK_PUSH_MIN_DIST_M = 3f
private const val FORCE_PUCK_RENDER_MIN_MS = 400L
private const val UI_STATE_COORD_THROTTLE_MS = 1000L
private const val UI_STATE_COORD_MIN_MOVE_M = 20f
private const val LOCATION_POLL_INTERVAL_MS = 1000L
private const val LOCATION_POLL_ATTEMPTS = 15
private val LOCATION_RETRY_DELAYS_MS = longArrayOf(1_000L, 3_000L, 8_000L)
private const val LOCATION_ENGINE_INTERVAL_MS = 750L
private const val LOCATION_ENGINE_FASTEST_INTERVAL_MS = 500L
private const val DRIVING_ANIMATION_FPS = 60
private const val STEP_ADVANCE_THRESHOLD_M = 25f
private const val ARRIVAL_THRESHOLD_M = 15f
private const val OFF_ROUTE_GRACE_AFTER_MANEUVER_MS = 8_000L
private const val ARRIVAL_FREE_DRIVE_DELAY_MS = 5_000L

private val PUCK_LAYER_IDS = setOf(
    LocationComponentConstants.SHADOW_LAYER,
    LocationComponentConstants.BACKGROUND_LAYER,
    LocationComponentConstants.FOREGROUND_LAYER,
    LocationComponentConstants.BEARING_LAYER,
    LocationComponentConstants.ACCURACY_LAYER,
    LocationComponentConstants.PULSING_CIRCLE_LAYER,
)

/**
 * Location pipeline, puck rendering helpers, and navigation step/off-route evaluation,
 * extracted from [MapLibreEngineImpl]. Owns the LocationEngine chain, dedup/throttle state,
 * and GPS acquisition listeners.
 *
 * Camera follow, padding, and top-down mode transitions are delegated via injected callbacks
 * into [NavigationCameraController]; cross-cutting nav/route flags stay on the engine.
 */
internal class LocationTrackingController(
    private val engineScope: CoroutineScope,
    private val mapLibreMap: () -> MapLibreMap?,
    private val appContext: () -> Context?,
    private val uiState: () -> MapUiState,
    private val updateUiState: ((MapUiState) -> MapUiState) -> Unit,
    private val puckScale: () -> Float,
    private val useVectorTiles: () -> Boolean,
    private val hasSnappedCameraToGps: () -> Boolean,
    private val setHasSnappedCameraToGps: (Boolean) -> Unit,
    private val lastKnownLocation: () -> LatLng?,
    private val setLastKnownLocation: (LatLng?) -> Unit,
    private val isRouteOverviewActive: () -> Boolean,
    private val navigationCameraTransitionActive: () -> Boolean,
    private val fullRouteSteps: () -> List<LegStep>,
    private val currentStepIndex: () -> Int,
    private val setCurrentStepIndex: (Int) -> Unit,
    private val destinationLatLng: () -> LatLng?,
    private val navigationArrivalTriggered: () -> Boolean,
    private val setNavigationArrivalTriggered: (Boolean) -> Unit,
    private val routeGeometryPoints: () -> List<LatLng>,
    private val offRouteDetector: OffRouteDetector,
    private val bearingEnricher: BearingEnricher,
    private val navigationVoice: () -> NavigationVoiceController?,
    private val offlineMapRepository: () -> OfflineMapRepository?,
    private val drivingTilePrefetcher: () -> DrivingTilePrefetcher?,
    private val encounteredPlacesSampler: EncounteredPlacesSampler,
    private val clearTickProjectionCache: () -> Unit,
    private val updateRouteProgress: (Location) -> Unit,
    private val onReroute: (origin: LatLng, destLat: Double, destLng: Double) -> Unit,
    private val startFreeDrive: () -> Unit,
    private val invalidateDrivingPaddingCache: () -> Unit,
    private val applyDrivingTrackingPadding: (MapLibreMap) -> Unit,
    private val maybeApplyDrivingPaddingForSpeedChange: (MapLibreMap) -> Unit,
    private val updateLookaheadPaddingState: (Float) -> Unit,
    private val snapCameraToGpsIfNeeded: (LatLng) -> Unit,
    private val activateFreeDriveTrackingMode: (MapLibreMap) -> Unit,
    private val ensureTopDownCameraDetached: (MapLibreMap) -> Unit,
    private val maybePrefetchDrivingTiles: (Location) -> Unit,
) {
    private var locationEngine: LocationEngine? = null
    private var rawLocationEngine: LocationEngine? = null
    private var smoothingLocationEngine: SmoothingLocationEngine? = null
    private var freshLocationListener: LocationListener? = null
    private var pendingLocationFix: Location? = null
    private var locationPollJob: Job? = null
    private var locationRetryJob: Job? = null
    private var lastLocationFixForDedup: Location? = null
    private var lastUiStateCoordUpdateMs: Long = 0L
    private var lastUiStateCoordLat: Double? = null
    private var lastUiStateCoordLng: Double? = null
    private var lastPuckPushLocation: Location? = null
    private var lastForcePuckRenderMs: Long = 0L
    private var lastDrivingSpeedMps: Float = 0f

    fun lastDrivingSpeedMps(): Float = lastDrivingSpeedMps

    fun resetLastDrivingSpeedMps() {
        lastDrivingSpeedMps = 0f
    }

    fun resolvePuckLayerAnchorId(style: Style): String? {
        if (style.getLayer(ROUTE_REMAINING_LAYER_ID) != null) {
            return ROUTE_REMAINING_LAYER_ID
        }
        if (style.getLayer(TRAFFIC_LAYER_ID) != null) {
            return TRAFFIC_LAYER_ID
        }
        return null
    }

    fun buildLocationComponentOptions(context: Context, style: Style): LocationComponentOptions {
        val builder = LocationComponentOptions.builder(context)
            .maxZoomIconScale(puckScale())
            .minZoomIconScale(puckScale())
        resolvePuckLayerAnchorId(style)?.let { anchorLayerId ->
            builder.layerAbove(anchorLayerId)
        }
        return builder.build()
    }

    fun ensurePuckAboveOverlays() {
        val map = mapLibreMap() ?: return
        val ctx = appContext() ?: return
        val component = map.locationComponent
        if (!component.isLocationComponentActivated) return
        map.getStyle { style ->
            component.applyStyle(buildLocationComponentOptions(ctx, style))
        }
    }

    fun findPuckAnchorLayerId(style: Style): String? {
        return style.layers.firstOrNull { it.id in PUCK_LAYER_IDS }?.id
    }

    fun pushPuckLocationIfNeeded(location: Location, force: Boolean = false) {
        if (!force) {
            val prev = lastPuckPushLocation
            if (prev != null && prev.distanceTo(location) < PUCK_PUSH_MIN_DIST_M) return
        }
        val map = mapLibreMap() ?: return
        val component = map.locationComponent
        if (!component.isLocationComponentActivated || !component.isLocationComponentEnabled) return
        lastPuckPushLocation = Location(location)
        component.forceLocationUpdate(location)
    }

    fun forceLocationUpdateForImmediateRender(
        map: MapLibreMap,
        bypassThrottle: Boolean = false,
        allowDuringSmoothing: Boolean = false,
    ) {
        if (!allowDuringSmoothing && shouldSmoothPuckMotion()) return
        val now = System.currentTimeMillis()
        if (!bypassThrottle && now - lastForcePuckRenderMs < FORCE_PUCK_RENDER_MIN_MS) return
        val component = map.locationComponent
        if (!component.isLocationComponentActivated || !component.isLocationComponentEnabled) return
        val location = smoothingLocationEngine?.currentDisplayLocation()
            ?: component.lastKnownLocation
            ?: lastKnownLocation()?.let { ll ->
                Location("forced").apply {
                    latitude = ll.latitude
                    longitude = ll.longitude
                }
            }
            ?: return
        lastForcePuckRenderMs = now
        pushPuckLocationIfNeeded(location, force = true)
    }

    fun resetSmoothingMotion() {
        smoothingLocationEngine?.reset()
    }

    fun onDestroy() {
        locationPollJob?.cancel()
        locationPollJob = null
        locationRetryJob?.cancel()
        locationRetryJob = null
        removeFreshLocationListener()
        smoothingLocationEngine?.reset()
        smoothingLocationEngine = null
        locationEngine = null
        rawLocationEngine = null
        pendingLocationFix = null
    }

    fun activateLocationTracking(map: MapLibreMap, style: Style) {
        val ctx = appContext() ?: return
        if (!hasLocationPermission(ctx)) {
            Log.w(TAG, "Location permission not granted; skipping LocationComponent activation")
            return
        }

        runCatching {
            val locationComponent = map.locationComponent
            val alreadyActivated = locationComponent.isLocationComponentActivated
            val locationEngine = if (alreadyActivated) {
                this.locationEngine ?: createLocationEngine(ctx)
            } else {
                createLocationEngine(ctx)
            }
            this.locationEngine = locationEngine
            val componentOptions = buildLocationComponentOptions(ctx, style)
            val engineRequest = buildDrivingLocationEngineRequest()
            if (!alreadyActivated) {
                val options = LocationComponentActivationOptions.builder(ctx, style)
                    .locationEngine(locationEngine)
                    .locationComponentOptions(componentOptions)
                    .locationEngineRequest(engineRequest)
                    .build()
                locationComponent.activateLocationComponent(options)
            } else {
                locationComponent.applyStyle(componentOptions)
            }
            locationComponent.isLocationComponentEnabled = true
            locationComponent.renderMode = RenderMode.GPS
            locationComponent.locationEngineRequest = engineRequest
            locationComponent.setMaxAnimationFps(DRIVING_ANIMATION_FPS)
            invalidateDrivingPaddingCache()
            applyDrivingTrackingPadding(map)

            flushPendingLocationFix()
            beginLocationAcquisition(ctx)
            scheduleLocationRetries(ctx)

            rawLocationEngine?.getLastLocation(object : LocationEngineCallback<LocationEngineResult> {
                override fun onSuccess(result: LocationEngineResult) {
                    result.lastLocation?.let { location ->
                        onLocationFixForAppLogic(location)
                    }
                }

                override fun onFailure(exception: Exception) {
                    Log.w(TAG, "Initial location fetch failed: ${exception.message}")
                }
            })
        }.onFailure { error ->
            Log.w(TAG, "Failed to activate LocationComponent: ${error.message}", error)
        }
    }

    fun onLocationFixForAppLogic(location: Location) {
        applyAndroidLocation(location, snapCamera = !hasSnappedCameraToGps())
        val speedKmh = if (location.hasSpeed()) {
            (location.speed * 3.6f).toInt().coerceAtLeast(0)
        } else {
            0
        }
        updateUiState { it.copy(currentSpeed = speedKmh) }
    }

    private fun createLocationEngine(context: Context): LocationEngine {
        Log.i(TAG, "Using MapLibre fused location engine (no Google Services)")
        val raw = LocationEngineProxy(MapLibreFusedLocationEngineImpl(context))
        rawLocationEngine = raw
        val enriched = BearingEnrichedLocationEngine(
            delegate = raw,
            enrich = { location ->
                val enriched = getOrEnrichLocation(location)
                if (uiState().isNavigating) {
                    blendSnapToRoute(enriched) ?: enriched
                } else {
                    enriched
                }
            },
            onRawFix = ::onLocationFixForAppLogic,
        )
        val smoothing = SmoothingLocationEngine(
            delegate = enriched,
            shouldSmooth = ::shouldSmoothPuckMotion,
        )
        smoothingLocationEngine = smoothing
        return smoothing
    }

    fun shouldSmoothPuckMotion(): Boolean {
        if (uiState().isCameraDetached || uiState().isInTopDownView) return false
        if (isRouteOverviewActive() || navigationCameraTransitionActive()) return false
        if (uiState().isRouteSelecting) return false
        val component = mapLibreMap()?.locationComponent ?: return false
        return component.isLocationComponentActivated &&
            component.isLocationComponentEnabled &&
            component.cameraMode == CameraMode.TRACKING_GPS
    }

    private fun blendSnapToRoute(location: Location): Location? {
        return offRouteDetector.snapLocationToRoute(location)
    }

    fun scheduleLocationRetries(context: Context) {
        locationRetryJob?.cancel()
        locationRetryJob = engineScope.launch {
            for (delayMs in LOCATION_RETRY_DELAYS_MS) {
                delay(delayMs)
                if (hasSnappedCameraToGps() && lastKnownLocation() != null) return@launch
                Log.i(TAG, "Scheduled location retry after ${delayMs}ms")
                refreshLocationOnly(context)
            }
        }
    }

    fun flushPendingLocationFix() {
        val location = pendingLocationFix ?: return
        val component = mapLibreMap()?.locationComponent ?: return
        if (!component.isLocationComponentActivated || !component.isLocationComponentEnabled) return
        pushPuckLocationIfNeeded(location, force = true)
        pendingLocationFix = null
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Applied queued location fix to LocationComponent")
        }
    }

    fun refreshLocationFromSystem(context: Context): LatLng? {
        val location = readLastKnownLocation(context) ?: return null
        applySystemLocation(location, snapCamera = !hasSnappedCameraToGps())
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

    private fun getOrEnrichLocation(location: Location): Location =
        bearingEnricher.getOrEnrichLocation(location)

    private fun isDuplicateLocationFix(location: Location): Boolean {
        val prev = lastLocationFixForDedup ?: return false
        val timeDelta = location.time - prev.time
        if (timeDelta in 0..LOCATION_FIX_DEDUP_TIME_MS) return true
        if (timeDelta < 500L && prev.distanceTo(location) < LOCATION_FIX_DEDUP_DIST_M) return true
        return false
    }

    private fun maybeUpdateUiStateCoords(lat: Double, lng: Double) {
        val now = System.currentTimeMillis()
        val shouldUpdate = when {
            lastUiStateCoordLat == null || lastUiStateCoordLng == null -> true
            now - lastUiStateCoordUpdateMs >= UI_STATE_COORD_THROTTLE_MS -> true
            else -> {
                val results = FloatArray(1)
                Location.distanceBetween(
                    lastUiStateCoordLat!!,
                    lastUiStateCoordLng!!,
                    lat,
                    lng,
                    results,
                )
                results[0] >= UI_STATE_COORD_MIN_MOVE_M
            }
        }
        if (!shouldUpdate) return
        lastUiStateCoordUpdateMs = now
        lastUiStateCoordLat = lat
        lastUiStateCoordLng = lng
        val connectivityLabel = resolveMapConnectivityLabel(lat, lng)
        updateUiState {
            it.copy(
                currentLat = lat,
                currentLng = lng,
                mapConnectivityLabel = connectivityLabel,
            )
        }
    }

    private fun resolveMapConnectivityLabel(lat: Double, lng: Double): String? {
        val context = appContext() ?: return null
        if (isNetworkAvailable(context)) return null
        val repo = offlineMapRepository() ?: return null
        return if (repo.hasCompleteRegionCovering(lat, lng)) {
            "Offline map cached"
        } else {
            null
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun applyAndroidLocation(location: Location, snapCamera: Boolean) {
        if (isDuplicateLocationFix(location)) return
        lastLocationFixForDedup = Location(location)
        clearTickProjectionCache()

        val locationWithBearing = getOrEnrichLocation(location)

        val displayLocation = if (uiState().isNavigating) {
            snapLocationToRoute(locationWithBearing) ?: locationWithBearing
        } else {
            locationWithBearing
        }

        val latLng = LatLng(displayLocation.latitude, displayLocation.longitude)
        setLastKnownLocation(latLng)
        maybeUpdateUiStateCoords(displayLocation.latitude, displayLocation.longitude)

        val newSpeedMps = if (displayLocation.hasSpeed()) displayLocation.speed else lastDrivingSpeedMps
        updateLookaheadPaddingState(newSpeedMps)
        lastDrivingSpeedMps = newSpeedMps
        mapLibreMap()?.let { map ->
            if (!uiState().isCameraDetached) {
                maybeApplyDrivingPaddingForSpeedChange(map)
            }
        }

        val component = mapLibreMap()?.locationComponent
        val componentReady = component != null &&
            component.isLocationComponentActivated &&
            component.isLocationComponentEnabled
        val isNavigating = uiState().isNavigating
        when {
            !componentReady -> pendingLocationFix = displayLocation
            else -> pendingLocationFix = null
        }

        if (isNavigating) {
            updateRouteProgress(displayLocation)
        }

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "Location fix ${displayLocation.latitude}, ${displayLocation.longitude} " +
                    "(provider=${displayLocation.provider}, bearing=${displayLocation.bearing}, " +
                    "roadSnapped=${displayLocation !== locationWithBearing}, " +
                    "snapCamera=$snapCamera, zoom=${mapLibreMap()?.cameraPosition?.zoom})",
            )
        }
        when {
            isNavigating -> evaluateStepAdvancement(locationWithBearing)
            uiState().isInTopDownView -> mapLibreMap()?.let { ensureTopDownCameraDetached(it) }
            !hasSnappedCameraToGps() -> snapCameraToGpsIfNeeded(latLng)
            else -> {
                if (!uiState().isCameraDetached) {
                    mapLibreMap()?.let { map ->
                        val liveComponent = map.locationComponent
                        val alreadyTracking = liveComponent.isLocationComponentActivated &&
                            liveComponent.isLocationComponentEnabled &&
                            liveComponent.cameraMode == CameraMode.TRACKING_GPS &&
                            liveComponent.renderMode == RenderMode.GPS
                        if (!alreadyTracking) {
                            activateFreeDriveTrackingMode(map)
                        }
                    }
                }
            }
        }
        maybePrefetchDrivingTiles(displayLocation)
        encounteredPlacesSampler.maybeSample(locationWithBearing)
    }

    private fun evaluateStepAdvancement(currentLocation: Location) {
        if (navigationArrivalTriggered()) return

        val steps = fullRouteSteps()
        if (steps.isEmpty()) return

        val dest = destinationLatLng()
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

        val nextIdx = currentStepIndex() + 1
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

        updateUiState {
            it.copy(distanceToNextTurn = NavigationRouteFetcher.formatDistance(distToManeuver.toDouble()))
        }

        val speedMps = if (currentLocation.hasSpeed()) currentLocation.speed else 0f
        navigationVoice()?.onNavTick(
            NavTickContext(
                currentStepIndex = currentStepIndex(),
                steps = steps.map { it.toNavStepPhrase() },
                distToNextManeuverM = distToManeuver,
                speedMps = speedMps,
                isRouteOverviewActive = isRouteOverviewActive() ||
                    navigationCameraTransitionActive() ||
                    uiState().isRouteSelecting,
                isRerouteInProgress = offRouteDetector.isRerouteInProgress,
            ),
        )

        if (distToManeuver < STEP_ADVANCE_THRESHOLD_M) {
            setCurrentStepIndex(nextIdx)
            offRouteDetector.offRouteGraceUntilMs = System.currentTimeMillis() + OFF_ROUTE_GRACE_AFTER_MANEUVER_MS
            val advanced = steps[currentStepIndex()]
            navigationVoice()?.onStepAdvanced(currentStepIndex(), advanced.toNavStepPhrase())
            updateUiState {
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
        offRouteDetector.checkOffRoute(
            currentLocation,
            routeGeometryPoints(),
            destinationLatLng(),
            navigationArrivalTriggered(),
        )
    }

    private fun snapLocationToRoute(location: Location): Location? {
        return blendSnapToRoute(location)
    }

    private fun triggerArrival() {
        if (navigationArrivalTriggered()) return
        setNavigationArrivalTriggered(true)
        updateUiState { it.copy(streetName = "Arrived at destination") }
        navigationVoice()?.onArrival {
            engineScope.launch {
                if (navigationArrivalTriggered()) {
                    startFreeDrive()
                }
            }
        } ?: engineScope.launch {
            delay(ARRIVAL_FREE_DRIVE_DELAY_MS)
            startFreeDrive()
        }
    }

    fun beginLocationAcquisition(context: Context) {
        if (!hasLocationPermission(context)) {
            Log.w(TAG, "beginLocationAcquisition skipped: permission not granted")
            return
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        Log.i(
            TAG,
            "beginLocationAcquisition: providers=${locationManager?.allProviders}, " +
                "enabled=${isLocationEnabled(context)}",
        )
        logPermissionState(context)

        refreshLocationFromSystem(context)
        ensureLocationListeners(context)
        startLocationPolling(context)
    }

    fun refreshLocationOnly(context: Context) {
        if (!hasLocationPermission(context)) return
        refreshLocationFromSystem(context)
        flushPendingLocationFix()
    }

    private fun ensureLocationListeners(context: Context) {
        if (freshLocationListener != null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "GPS location listener already active; skipping re-register")
            }
            return
        }
        ensureGpsLocationListener(context)
    }

    private fun ensureGpsLocationListener(context: Context) {
        if (freshLocationListener != null) return
        if (rawLocationEngine != null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Skipping direct GPS listener; using LocationEngine")
            }
            return
        }
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return

        val listener = LocationListener { location ->
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "GPS update: ${location.latitude}, ${location.longitude} from ${location.provider}",
                )
            }
            applyAndroidLocation(location, snapCamera = !hasSnappedCameraToGps())
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
                    applySystemLocation(latLng, snapCamera = !hasSnappedCameraToGps())
                    if (hasSnappedCameraToGps()) return@launch
                }
                delay(LOCATION_POLL_INTERVAL_MS)
            }
            if (lastKnownLocation() == null) {
                updateUiState { state ->
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

    private fun buildDrivingLocationEngineRequest(): LocationEngineRequest {
        return LocationEngineRequest.Builder(LOCATION_ENGINE_INTERVAL_MS)
            .setFastestInterval(LOCATION_ENGINE_FASTEST_INTERVAL_MS)
            .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
            .build()
    }

    fun readLastKnownLocation(context: Context): LatLng? {
        if (!hasLocationPermission(context)) {
            return null
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val bestLocation = locationManager.allProviders
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }

        return bestLocation?.let { LatLng(it.latitude, it.longitude) }
    }

    fun hasLocationPermission(context: Context): Boolean {
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

    private fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun removeFreshLocationListener() {
        val context = appContext() ?: return
        val listener = freshLocationListener ?: return
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return
        runCatching { locationManager.removeUpdates(listener) }
        freshLocationListener = null
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
}
