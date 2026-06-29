package com.kyuusanq3.mixauto.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.kyuusanq3.mixauto.data.map.MapLibreEngineImpl
import com.kyuusanq3.mixauto.data.map.OfflineMapRepository
import com.kyuusanq3.mixauto.data.navigation.NavigationVoiceController
import com.kyuusanq3.mixauto.data.places.EncounteredPlacesRepository
import com.kyuusanq3.mixauto.data.places.LocalPlacesRepository
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.ui.settings.LauncherPreferences

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
            initialPuckHOffset = prefs.puckHorizontalOffset,
            initialPuckVOffset = prefs.puckVerticalOffset,
            initialPuckScale = prefs.puckScale,
            initialRememberEncounteredPlaces = prefs.rememberEncounteredPlaces,
        ).also { engine ->
            engine.setTrafficEnabled(prefs.showTraffic, prefs.tomTomApiKey)
        }
    }

    override fun onCleared() {
        mapEngine.onDestroy()
        navigationVoiceController.shutdown()
        super.onCleared()
    }
}
