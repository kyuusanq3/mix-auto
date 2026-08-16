package com.kyuusanq3.mixauto.ui.map

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kyuusanq3.mixauto.data.map.GoogleMapsShareParser
import com.kyuusanq3.mixauto.data.map.MapLibreEngineImpl
import com.kyuusanq3.mixauto.data.map.OfflineMapRepository
import com.kyuusanq3.mixauto.data.map.PhotonSearchClient
import com.kyuusanq3.mixauto.data.map.WebViewCoordinateResolver
import com.kyuusanq3.mixauto.data.navigation.NavigationVoiceController
import com.kyuusanq3.mixauto.data.places.EncounteredPlacesRepository
import com.kyuusanq3.mixauto.data.places.LocalPlacesRepository
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import com.kyuusanq3.mixauto.service.OfflineMapDownloadService
import com.kyuusanq3.mixauto.ui.settings.LauncherPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity-scoped holder for the map engine and its dependencies.
 * Survives configuration changes so navigation, camera, and POI state persist across rotation.
 */
class MapHostViewModel(application: Application) : AndroidViewModel(application) {

    val localPlacesRepository = LocalPlacesRepository(application)

    val encounteredPlacesRepository = EncounteredPlacesRepository(application)

    val offlineMapRepository = OfflineMapRepository(application)

    val navigationVoiceController: NavigationVoiceController

    val mapEngine: CarMapEngine

    private val _pendingSharedPlace = MutableStateFlow<SearchResultPlace?>(null)
    val pendingSharedPlace: StateFlow<SearchResultPlace?> = _pendingSharedPlace.asStateFlow()

    private val _shareError = MutableStateFlow<String?>(null)
    val shareError: StateFlow<String?> = _shareError.asStateFlow()

    init {
        val prefs = LauncherPreferences(application)
        navigationVoiceController = NavigationVoiceController(application).apply {
            enabled = prefs.navigationVoiceEnabled
            volume = prefs.navigationVoiceVolume
            boostEnabled = prefs.navigationVoiceBoost
        }
        mapEngine = MapLibreEngineImpl(
            localPlaces = localPlacesRepository,
            encounteredPlaces = encounteredPlacesRepository,
            navigationVoice = navigationVoiceController,
            offlineMapRepository = offlineMapRepository,
            initialUseVectorTiles = prefs.useVectorTiles,
            initialShow3dBuildings = prefs.show3dBuildings,
            initialDrivingZoom = prefs.drivingZoom.toDouble(),
            initialDrivingTilt = prefs.drivingTilt.toDouble(),
            initialPuckHOffset = prefs.puckHorizontalOffset,
            initialPuckVOffset = prefs.puckVerticalOffset,
            initialPuckScale = prefs.puckScale,
            initialRememberEncounteredPlaces = prefs.rememberEncounteredPlaces,
        ).also { engine ->
            engine.setTrafficEnabled(prefs.showTraffic, prefs.tomTomApiKey)
        }
        OfflineMapDownloadService.resumeIfNeeded(getApplication())
    }

    /** Called from onCreate (cold start) and onNewIntent (already running, singleTask). */
    fun handleSharedIntent(intent: Intent) {
        Log.d(TAG, "handleSharedIntent: action=${intent.action} type=${intent.type}")
        if (intent.action != Intent.ACTION_SEND) {
            Log.d(TAG, "handleSharedIntent: ignoring, not ACTION_SEND")
            return
        }
        val mimeType = intent.type
        if (mimeType == null) {
            Log.w(TAG, "handleSharedIntent: ignoring, intent.type is null")
            return
        }
        if (!mimeType.startsWith("text/")) {
            Log.w(TAG, "handleSharedIntent: ignoring, mimeType=$mimeType is not text/*")
            return
        }
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (sharedText == null) {
            Log.w(TAG, "handleSharedIntent: ignoring, no EXTRA_TEXT in intent")
            return
        }
        Log.d(TAG, "handleSharedIntent: EXTRA_TEXT=\"${sharedText.take(300)}\"")
        if (!GoogleMapsShareParser.isShareableText(sharedText)) {
            Log.w(TAG, "handleSharedIntent: no URL or geo: URI detected in shared text")
            _shareError.value = SHARE_ERROR_MESSAGE
            return
        }

        viewModelScope.launch {
            val parsedShare = withContext(Dispatchers.IO) {
                runCatching { GoogleMapsShareParser.parseSharedText(sharedText) }
                    .onFailure { e -> Log.e(TAG, "handleSharedIntent: parseSharedText threw", e) }
                    .getOrNull()
            }
            val place = when (parsedShare) {
                is GoogleMapsShareParser.ParsedShare.Resolved -> parsedShare.place
                is GoogleMapsShareParser.ParsedShare.NeedsGeocode ->
                    resolveViaWebViewThenGeocode(parsedShare.url, parsedShare.nameHint)
                null ->
                    GoogleMapsShareParser.extractNameHint(sharedText)?.let { geocodeNameHint(it) }
            }
            if (place != null) {
                Log.d(TAG, "handleSharedIntent: resolved place=$place")
                _pendingSharedPlace.value = place
            } else {
                Log.w(TAG, "handleSharedIntent: could not resolve a place from the shared text")
                _shareError.value = SHARE_ERROR_MESSAGE
            }
        }
    }

    /**
     * Business/POI shares that encode the place purely as a Google feature ID have no
     * coordinates anywhere in the URL or a plain HTTP response -- Google only resolves the ID
     * to a `@lat,lng` via client-side JS. Try that (headless WebView, JS enabled) before falling
     * back to name-based geocoding, which can only find places that also exist in OpenStreetMap.
     */
    private suspend fun resolveViaWebViewThenGeocode(url: String?, nameHint: String): SearchResultPlace? {
        if (url != null) {
            val coordinates = withContext(Dispatchers.Main) {
                runCatching { WebViewCoordinateResolver.resolveCoordinates(getApplication(), url) }
                    .onFailure { e -> Log.e(TAG, "resolveViaWebViewThenGeocode: WebView resolve threw", e) }
                    .getOrNull()
            }
            if (coordinates != null) {
                val (lat, lng) = coordinates
                Log.d(TAG, "resolveViaWebViewThenGeocode: WebView resolved lat=$lat lng=$lng")
                return SearchResultPlace(
                    name = nameHint.takeIf { it.isNotBlank() } ?: "Shared Location",
                    subTitle = nameHint,
                    latitude = lat,
                    longitude = lng,
                    isDroppedPin = true,
                )
            }
            Log.w(TAG, "resolveViaWebViewThenGeocode: WebView found no coordinates, falling back to geocoding")
        }
        return geocodeNameHint(nameHint)
    }

    /**
     * Fallback for shared links with no embedded coordinates (e.g. business/POI shares that
     * encode the place purely as a Google feature ID) -- geocode a place-name hint via the same
     * Photon (OpenStreetMap) search already used for destination search. The hint may come from
     * the URL's own `/maps/place/<name>/` path segment (often a full comma-joined address like
     * "Business Name, Building, City, Province, Country"), or from text Google Maps put above a
     * bare share link. Try the clean business-name segment first (Photon full-text search does
     * better with a short name than the whole address string), then fall back to the full hint.
     *
     * Note: this can only find places that are actually mapped in OpenStreetMap. Small local
     * businesses that exist only in Google's proprietary Places database (which is what a bare
     * feature-ID link like `!1s0x...:0x...` points into) cannot be resolved this way -- doing so
     * would require a paid Google Places API key, which this app intentionally does not use.
     */
    private suspend fun geocodeNameHint(nameHint: String): SearchResultPlace? {
        val primaryName = nameHint.substringBefore(',').trim().takeIf { it.isNotBlank() } ?: nameHint
        if (primaryName != nameHint) {
            fetchPhotonResult(primaryName)?.let { return it }
        }
        return fetchPhotonResult(nameHint)
    }

    private suspend fun fetchPhotonResult(query: String): SearchResultPlace? {
        Log.d(TAG, "geocodeNameHint: geocoding query=\"$query\" via Photon")
        val (lat, lng) = mapEngine.resolveSearchOrigin()
        val results = withContext(Dispatchers.IO) {
            runCatching { PhotonSearchClient.fetchPhoton(query, lat, lng) }
                .onFailure { e -> Log.e(TAG, "geocodeNameHint: Photon fetch threw", e) }
                .getOrNull()
        }
        Log.d(TAG, "geocodeNameHint: Photon returned ${results?.size ?: 0} result(s) for \"$query\"")
        return results?.firstOrNull()
    }

    fun consumeSharedPlace() {
        _pendingSharedPlace.value = null
    }

    fun consumeShareError() {
        _shareError.value = null
    }

    override fun onCleared() {
        mapEngine.onDestroy()
        navigationVoiceController.shutdown()
        super.onCleared()
    }

    companion object {
        private const val TAG = "MixAutoShare"
        private const val SHARE_ERROR_MESSAGE = "Couldn't read that shared location"
    }
}
