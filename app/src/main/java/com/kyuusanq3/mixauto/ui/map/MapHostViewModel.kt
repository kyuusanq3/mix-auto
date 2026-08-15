package com.kyuusanq3.mixauto.ui.map

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kyuusanq3.mixauto.data.map.GoogleMapsShareParser
import com.kyuusanq3.mixauto.data.map.MapLibreEngineImpl
import com.kyuusanq3.mixauto.data.map.OfflineMapRepository
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
        if (intent.action != Intent.ACTION_SEND) return
        val mimeType = intent.type ?: return
        if (!mimeType.startsWith("text/")) return
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        if (!GoogleMapsShareParser.isShareableText(sharedText)) return

        viewModelScope.launch {
            val place = withContext(Dispatchers.IO) {
                runCatching { GoogleMapsShareParser.parseSharedText(sharedText) }.getOrNull()
            }
            if (place != null) {
                _pendingSharedPlace.value = place
            }
        }
    }

    fun consumeSharedPlace() {
        _pendingSharedPlace.value = null
    }

    override fun onCleared() {
        mapEngine.onDestroy()
        navigationVoiceController.shutdown()
        super.onCleared()
    }
}
