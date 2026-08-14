package com.kyuusanq3.mixauto.data.map

import android.content.Context
import android.util.Log
import com.kyuusanq3.mixauto.data.navigation.NavTtsPhrases
import com.kyuusanq3.mixauto.data.navigation.NavigationVoiceController
import com.kyuusanq3.mixauto.domain.map.MapUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

/**
 * Navigation session lifecycle: route fetch, conventional OSRM selection, lighter-traffic alternate.
 * Extracted from [MapLibreEngineImpl] via callback injection.
 */
internal class NavigationSessionCoordinator(
    private val uiState: () -> MapUiState,
    private val updateUiState: ((MapUiState) -> MapUiState) -> Unit,
    private val engineScope: CoroutineScope,
    private val appContext: () -> Context?,
    private val mapLibreMap: () -> MapLibreMap?,
    private val routeRenderer: () -> RouteRenderer,
    private val tomTomApiKey: () -> String,
    private val offRouteDetector: () -> OffRouteDetector,
    private val navigationVoice: () -> NavigationVoiceController?,
    private val getRouteGeometryPoints: () -> List<LatLng>,
    private val setRouteGeometryPoints: (List<LatLng>) -> Unit,
    private val setRouteTrafficSections: (List<TomTomTrafficSection>) -> Unit,
    private val getFullRouteSteps: () -> List<LegStep>,
    private val setFullRouteSteps: (List<LegStep>) -> Unit,
    private val getCurrentStepIndex: () -> Int,
    private val setCurrentStepIndex: (Int) -> Unit,
    private val getDestinationLatLng: () -> LatLng?,
    private val setDestinationLatLng: (LatLng?) -> Unit,
    private val getNavigationArrivalTriggered: () -> Boolean,
    private val setNavigationArrivalTriggered: (Boolean) -> Unit,
    private val getStashedLighterTrafficRoute: () -> RouteResult?,
    private val setStashedLighterTrafficRoute: (RouteResult?) -> Unit,
    private val getStashedParallelTomTomDelaySeconds: () -> Int,
    private val setStashedParallelTomTomDelaySeconds: (Int) -> Unit,
    private val getPendingNavTrafficPhrase: () -> String?,
    private val setPendingNavTrafficPhrase: (String?) -> Unit,
    private val getNavTrafficPrefetchJob: () -> Job?,
    private val setNavTrafficPrefetchJob: (Job?) -> Unit,
    private val getNavStartTrafficEligible: () -> Boolean,
    private val setNavStartTrafficEligible: (Boolean) -> Unit,
    private val getPoiRefreshJob: () -> Job?,
    private val setPoiRefreshJob: (Job?) -> Unit,
    private val getLastKnownLocation: () -> LatLng?,
    private val setLastKnownLocation: (LatLng?) -> Unit,
    private val resolveMapViewOrigin: () -> LatLng?,
    private val beginLocationAcquisition: (Context) -> Unit,
    private val readLastKnownLocation: (Context) -> LatLng?,
    private val hasLocationPermission: (Context) -> Boolean,
    private val updateLocationEngineInterval: () -> Unit,
    private val clearForcedPreviewPoi: () -> Unit,
    private val clearPoiLayer: () -> Unit,
    private val clearCustomPin: () -> Unit,
    private val clearRoutePreviewState: () -> Unit,
    private val hideNativeVectorPoiLayers: (Style) -> Unit,
    private val drawRoute: () -> Unit,
    private val showRouteThenDive: (LatLng, LatLng) -> Unit,
    private val enterNavigationCamera: () -> Unit,
    private val refreshTrafficOverlay: () -> Unit,
) {

    private val maneuverAlts = ManeuverAlternateCoordinator(
        engineScope = engineScope,
        mapLibreMap = mapLibreMap,
        routeRenderer = routeRenderer,
        getCurrentStepIndex = getCurrentStepIndex,
        getDestinationLatLng = getDestinationLatLng,
        fetchOsrmFromBranch = { latA, lngA, latB, lngB ->
            fetchOsrmRoutesWithAlternatives(lngA, latA, lngB, latB)
        },
    )

    fun navigateToCoordinates(lat: Double, lng: Double) {
        val ctx = appContext()
        var origin = getLastKnownLocation() ?: ctx?.let { readLastKnownLocation(it) }

        if (origin == null && ctx != null && hasLocationPermission(ctx)) {
            resolveMapViewOrigin()?.let { mapOrigin ->
                Log.i(TAG, "Routing from map view at zoom ${mapLibreMap()?.cameraPosition?.zoom}")
                setLastKnownLocation(mapOrigin)
                startNavigation(mapOrigin, lat, lng)
                return
            }

            updateUiState { it.copy(streetName = "Acquiring location...") }
            beginLocationAcquisition(ctx)
            engineScope.launch {
                val deadline = System.currentTimeMillis() + LOCATION_ACQUIRE_TIMEOUT_MS
                while (System.currentTimeMillis() < deadline) {
                    val resolvedOrigin = getLastKnownLocation() ?: readLastKnownLocation(ctx)
                    if (resolvedOrigin != null) {
                        setLastKnownLocation(resolvedOrigin)
                        startNavigation(resolvedOrigin, lat, lng)
                        return@launch
                    }
                    resolveMapViewOrigin()?.let { mapOrigin ->
                        Log.i(TAG, "GPS unavailable; routing from map view")
                        setLastKnownLocation(mapOrigin)
                        startNavigation(mapOrigin, lat, lng)
                        return@launch
                    }
                    delay(LOCATION_POLL_INTERVAL_MS)
                }
                Log.w(TAG, "Navigation aborted: no location after ${LOCATION_ACQUIRE_TIMEOUT_MS}ms")
                updateUiState {
                    it.copy(streetName = "Zoom map to your area, then retry")
                }
            }
            return
        }

        if (origin == null) {
            resolveMapViewOrigin()?.let { mapOrigin ->
                Log.i(TAG, "Routing from map view (no permission path)")
                setLastKnownLocation(mapOrigin)
                startNavigation(mapOrigin, lat, lng)
                return
            }
            Log.w(TAG, "No known location; cannot route")
            updateUiState { it.copy(streetName = "Zoom map to your area") }
            return
        }

        val resolvedOrigin = origin
        setLastKnownLocation(resolvedOrigin)
        startNavigation(resolvedOrigin, lat, lng)
    }

    fun startNavigation(
        origin: LatLng,
        lat: Double,
        lng: Double,
        isReroute: Boolean = false,
        originBearingDeg: Float? = null,
    ) {
        if (isReroute) {
            navigationVoice()?.onRerouteStarted()
        }
        if (!isReroute) {
            getPoiRefreshJob()?.cancel()
            setPoiRefreshJob(null)
            clearForcedPreviewPoi()
            clearPoiLayer()
            clearCustomPin()
            clearRoutePreviewState()
            mapLibreMap()?.getStyle { hideNativeVectorPoiLayers(it) }
        }
        updateLocationEngineInterval()
        updateUiState {
            it.copy(
                isNavigating = true,
                streetName = if (isReroute) "Re-routing..." else "Calculating route...",
                selectedPoi = null,
                nearbyPois = emptyList(),
                isInTopDownView = if (isReroute) it.isInTopDownView else false,
            )
        }
        refreshTrafficOverlay()

        engineScope.launch {
            try {
                if (isReroute) {
                    clearLighterTrafficAlternate()
                    clearManeuverAlternates()
                    val originRadius = REROUTE_ORIGIN_RADIUS_M
                    val osrmRoutes = withContext(Dispatchers.IO) {
                        fetchOsrmRoutesWithAlternatives(
                            origin.longitude,
                            origin.latitude,
                            lng,
                            lat,
                            originRadiusM = originRadius,
                            originBearingDeg = originBearingDeg,
                        )
                    }
                    val route = selectConventionalOsrmRoute(osrmRoutes)
                        ?: withContext(Dispatchers.IO) {
                            fetchOsrmRoute(
                                origin.longitude,
                                origin.latitude,
                                lng,
                                lat,
                                originRadiusM = originRadius,
                                originBearingDeg = originBearingDeg,
                            )
                        }
                        ?: withContext(Dispatchers.IO) {
                            fetchOsrmRoute(origin.longitude, origin.latitude, lng, lat)
                        }
                    if (route != null) {
                        applyActiveRoute(route)
                        setDestinationLatLng(LatLng(lat, lng))
                        setNavigationArrivalTriggered(false)
                        offRouteDetector().offRouteCount = 0
                        updateUiState {
                            it.copy(
                                isNavigating = true,
                                lighterTrafficAlternateActive = false,
                                streetName = route.streetName,
                                turnInstruction = route.instruction,
                                distanceToNextTurn = route.distance,
                            )
                        }
                        offRouteDetector().isRerouteInProgress = false
                        enterNavigationCamera()
                    } else {
                        offRouteDetector().isRerouteInProgress = false
                        updateUiState { it.copy(isNavigating = false, streetName = "Route not found") }
                        refreshTrafficOverlay()
                    }
                    return@launch
                }

                val osrmRoutesDeferred = async(Dispatchers.IO) {
                    fetchOsrmRoutesWithAlternatives(origin.longitude, origin.latitude, lng, lat)
                }
                val tomtomDeferred = async(Dispatchers.IO) {
                    if (tomTomApiKey().isBlank()) {
                        null
                    } else {
                        TomTomRoutingClient.fetchRoute(
                            origin.latitude,
                            origin.longitude,
                            lat,
                            lng,
                            tomTomApiKey(),
                        )
                    }
                }
                val osrmRoutes = osrmRoutesDeferred.await()
                val tomtomRoute = tomtomDeferred.await()

                val conventional = selectConventionalOsrmRoute(osrmRoutes)
                    ?: tomtomRoute?.let { LighterTrafficHelper.tomTomToRouteResult(it) }

                if (conventional == null) {
                    updateUiState { it.copy(isNavigating = false, streetName = "Route not found") }
                    refreshTrafficOverlay()
                    return@launch
                }

                setDestinationLatLng(LatLng(lat, lng))
                setNavigationArrivalTriggered(false)
                offRouteDetector().offRouteCount = 0
                setStashedParallelTomTomDelaySeconds(tomtomRoute?.trafficDelaySeconds ?: 0)
                setNavStartTrafficEligible(true)

                applyActiveRoute(conventional)
                maybeOfferLighterTrafficAlternate(conventional, tomtomRoute)
                updateUiState {
                    it.copy(
                        isNavigating = true,
                        streetName = conventional.streetName,
                        turnInstruction = conventional.instruction,
                        distanceToNextTurn = conventional.distance,
                    )
                }
                showRouteThenDive(origin, LatLng(lat, lng))
            } catch (e: Exception) {
                offRouteDetector().isRerouteInProgress = false
                Log.w(TAG, "Route fetch failed: ${e.message}", e)
                updateUiState { it.copy(isNavigating = false, streetName = "Routing failed") }
                refreshTrafficOverlay()
            }
        }
    }

    fun switchToLighterTrafficAlternate() {
        val alternate = getStashedLighterTrafficRoute() ?: return
        applyActiveRoute(alternate)
        clearLighterTrafficAlternate()
        updateUiState {
            it.copy(
                streetName = alternate.streetName,
                turnInstruction = alternate.instruction,
                distanceToNextTurn = alternate.distance,
            )
        }
    }

    fun clearLighterTrafficAlternate() {
        setStashedLighterTrafficRoute(null)
        val map = mapLibreMap()
        if (map != null) {
            map.getStyle { style -> routeRenderer().clearLighterTrafficAlternate(style) }
        }
        if (uiState().lighterTrafficAlternateActive) {
            updateUiState { it.copy(lighterTrafficAlternateActive = false) }
        }
    }

    fun clearNavTrafficPrefetchState() {
        getNavTrafficPrefetchJob()?.cancel()
        setNavTrafficPrefetchJob(null)
        setPendingNavTrafficPhrase(null)
        setStashedParallelTomTomDelaySeconds(0)
        setNavStartTrafficEligible(false)
    }

    /** Consumes nav-start traffic eligibility and returns any prefetched phrase. */
    fun consumeNavStartTrafficPhrase(): String? {
        if (!getNavStartTrafficEligible()) return null
        setNavStartTrafficEligible(false)
        val phrase = getPendingNavTrafficPhrase()
        setPendingNavTrafficPhrase(null)
        return phrase
    }

    private fun selectConventionalOsrmRoute(routes: List<RouteResult>): RouteResult? {
        if (routes.isEmpty()) return null
        if (routes.size == 1) return routes[0]
        val candidates = routes.map { it.toConventionalCandidate() }
        val index = ConventionalRouteSelector.selectConventionalRoute(candidates)
        return routes[index.coerceIn(routes.indices)]
    }

    private fun maybeOfferLighterTrafficAlternate(
        conventional: RouteResult,
        tomtomRoute: TomTomRouteResult?,
    ) {
        clearLighterTrafficAlternate()
        val tt = tomtomRoute ?: return
        if (!LighterTrafficHelper.isLighterTrafficAlternate(conventional, tt)) return
        val alternate = LighterTrafficHelper.tomTomToRouteResult(tt)
        setStashedLighterTrafficRoute(alternate)
        val map = mapLibreMap() ?: return
        map.getStyle { style ->
            routeRenderer().showLighterTrafficAlternate(style, alternate.geometryPoints)
        }
        updateUiState { it.copy(lighterTrafficAlternateActive = true) }
    }

    private fun applyActiveRoute(route: RouteResult) {
        setRouteGeometryPoints(route.geometryPoints)
        setRouteTrafficSections(route.trafficSections)
        setFullRouteSteps(route.steps)
        setCurrentStepIndex(0)
        drawRoute()
        prefetchNavTrafficHint(route)
        maneuverAlts.prefetch(route)
    }

    private fun prefetchNavTrafficHint(route: RouteResult) {
        getNavTrafficPrefetchJob()?.cancel()
        setPendingNavTrafficPhrase(null)
        if (!getNavStartTrafficEligible() || tomTomApiKey().isBlank()) return

        val routeLatLng = route.geometryPoints.map { Pair(it.latitude, it.longitude) }
        val routeDelay = route.trafficDelaySeconds
        val parallelDelay = getStashedParallelTomTomDelaySeconds()
        val apiKey = tomTomApiKey()

        setNavTrafficPrefetchJob(
            engineScope.launch {
                val phrase = withContext(Dispatchers.IO) {
                    resolveNavStartTrafficPhrase(routeLatLng, apiKey, routeDelay, parallelDelay)
                }
                if (!phrase.isNullOrBlank()) {
                    setPendingNavTrafficPhrase(phrase)
                }
            },
        )
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

    private fun fetchOsrmRoutesWithAlternatives(
        lngA: Double,
        latA: Double,
        lngB: Double,
        latB: Double,
        originRadiusM: Double? = null,
        originBearingDeg: Float? = null,
    ): List<RouteResult> = NavigationRouteFetcher.fetchOsrmRoutesWithAlternatives(
        lngA,
        latA,
        lngB,
        latB,
        originRadiusM,
        originBearingDeg,
    )

    private fun fetchOsrmRoute(
        lngA: Double,
        latA: Double,
        lngB: Double,
        latB: Double,
        originRadiusM: Double? = null,
        originBearingDeg: Float? = null,
    ): RouteResult? = NavigationRouteFetcher.fetchOsrmRoute(
        lngA,
        latA,
        lngB,
        latB,
        originRadiusM,
        originBearingDeg,
    )

    fun visibleManeuverAlternateGeometry(): List<LatLng> = maneuverAlts.visibleGeometry()

    fun onManeuverStepChanged(stepIndex: Int) {
        maneuverAlts.refreshVisible(stepIndex)
    }

    fun adoptVisibleManeuverAlternate() {
        val route = maneuverAlts.visibleRoute() ?: return
        offRouteDetector().offRouteCount = 0
        offRouteDetector().offRouteGraceUntilMs = System.currentTimeMillis() + 8_000L
        clearLighterTrafficAlternate()
        applyActiveRoute(route)
        updateUiState {
            it.copy(
                streetName = route.streetName,
                turnInstruction = route.instruction,
                distanceToNextTurn = route.distance,
            )
        }
    }

    fun clearManeuverAlternates() {
        maneuverAlts.clear()
    }

    companion object {
        private const val TAG = "NavigationSessionCoordinator"
        private const val LOCATION_POLL_INTERVAL_MS = 1000L
        private const val LOCATION_ACQUIRE_TIMEOUT_MS = 8000L
    }
}
