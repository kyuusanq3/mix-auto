package com.kyuusanq3.mixauto.ui.settings

import android.app.Application
import android.location.Location
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kyuusanq3.mixauto.data.map.TomTomKeyCheckResult
import com.kyuusanq3.mixauto.data.map.TomTomTrafficClient
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import com.kyuusanq3.mixauto.BuildConfig
import com.kyuusanq3.mixauto.ui.components.canLaunchApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class TomTomKeyCheckState {
    data object Idle : TomTomKeyCheckState()
    data object Checking : TomTomKeyCheckState()
    data class Success(val message: String) : TomTomKeyCheckState()
    data class Error(val message: String) : TomTomKeyCheckState()
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = LauncherPreferences(application)

    var defaultAudioPackage by mutableStateOf(loadValidatedDefaultAudioPackage())
        private set

    var dockPinnedPackages by mutableStateOf(loadValidatedDockPinnedPackages())
        private set

    var isLeftHandDrive by mutableStateOf(preferences.isLeftHandDrive)
        private set

    var isShortcutsHorizontal by mutableStateOf(preferences.isShortcutsHorizontal)
        private set

    var mapMediaRatio by mutableStateOf(preferences.mapMediaRatio)
        private set

    var limitSearchDistance by mutableStateOf(preferences.limitSearchDistance)
        private set

    var useVectorTiles by mutableStateOf(preferences.useVectorTiles)
        private set

    var show3dBuildings by mutableStateOf(preferences.show3dBuildings)
        private set

    var isLauncherMode by mutableStateOf(preferences.isLauncherMode)
        private set

    var isLargeShortcutIcons by mutableStateOf(preferences.isLargeShortcutIcons)
        private set

    var drivingZoom by mutableStateOf(preferences.drivingZoom)
        private set

    var puckHorizontalOffset by mutableStateOf(preferences.puckHorizontalOffset)
        private set

    var puckVerticalOffset by mutableStateOf(preferences.puckVerticalOffset)
        private set

    var puckScale by mutableStateOf(preferences.puckScale)
        private set

    var showTraffic by mutableStateOf(preferences.showTraffic)
        private set

    var tomTomApiKey by mutableStateOf(preferences.tomTomApiKey)
        private set

    var recentDestinations by mutableStateOf(preferences.recentDestinations)
        private set

    var savedPlaces by mutableStateOf(preferences.savedPlaces)
        private set

    var tomTomKeyCheckState by mutableStateOf<TomTomKeyCheckState>(TomTomKeyCheckState.Idle)
        private set

    var isDestinationSearchOpen by mutableStateOf(false)
        internal set

    private val _voiceSearchTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val voiceSearchTrigger = _voiceSearchTrigger.asSharedFlow()

    private var startVoiceOnSearchOpen = false

    fun triggerVoiceSearch() {
        _voiceSearchTrigger.tryEmit(Unit)
    }

    fun setStartVoiceOnSearchOpen() {
        startVoiceOnSearchOpen = true
    }

    fun consumeStartVoiceOnSearchOpen(): Boolean {
        if (!startVoiceOnSearchOpen) return false
        startVoiceOnSearchOpen = false
        return true
    }

    fun toggleLeftHandDrive() {
        isLeftHandDrive = !isLeftHandDrive
        preferences.isLeftHandDrive = isLeftHandDrive
    }

    fun toggleShortcutsHorizontal() {
        isShortcutsHorizontal = !isShortcutsHorizontal
        preferences.isShortcutsHorizontal = isShortcutsHorizontal
    }

    fun updateMapMediaRatio(value: Float) {
        mapMediaRatio = value
        preferences.mapMediaRatio = value
    }

    fun toggleLimitSearchDistance() {
        limitSearchDistance = !limitSearchDistance
        preferences.limitSearchDistance = limitSearchDistance
    }

    fun toggleVectorTiles() {
        useVectorTiles = !useVectorTiles
        preferences.useVectorTiles = useVectorTiles
    }

    fun toggleShow3dBuildings() {
        show3dBuildings = !show3dBuildings
        preferences.show3dBuildings = show3dBuildings
    }

    fun toggleLauncherMode() {
        isLauncherMode = !isLauncherMode
        preferences.isLauncherMode = isLauncherMode
    }

    fun toggleLargeShortcutIcons() {
        isLargeShortcutIcons = !isLargeShortcutIcons
        preferences.isLargeShortcutIcons = isLargeShortcutIcons
    }

    fun updateDrivingZoom(value: Float) {
        drivingZoom = value
        preferences.drivingZoom = value
    }

    fun updatePuckHorizontalOffset(value: Float) {
        puckHorizontalOffset = value
        preferences.puckHorizontalOffset = value
    }

    fun updatePuckVerticalOffset(value: Float) {
        puckVerticalOffset = value
        preferences.puckVerticalOffset = value
    }

    fun updatePuckScale(value: Float) {
        puckScale = value
        preferences.puckScale = value
    }

    fun toggleTraffic() {
        showTraffic = !showTraffic
        preferences.showTraffic = showTraffic
    }

    fun updateTomTomApiKey(key: String) {
        tomTomApiKey = key
        preferences.tomTomApiKey = key
        tomTomKeyCheckState = TomTomKeyCheckState.Idle
    }

    fun updateDefaultAudioPackage(packageName: String) {
        defaultAudioPackage = packageName
        preferences.defaultAudioPackage = packageName
    }

    fun isDockPinned(packageName: String): Boolean {
        return dockPinnedPackages.contains(packageName)
    }

    fun toggleDockPinnedPackage(packageName: String) {
        if (packageName.isBlank() || packageName == BuildConfig.APPLICATION_ID) return
        dockPinnedPackages = if (packageName in dockPinnedPackages) {
            dockPinnedPackages.filterNot { it == packageName }
        } else if (dockPinnedPackages.size < LauncherPreferences.MAX_DOCK_PINNED_APPS) {
            dockPinnedPackages + packageName
        } else {
            return
        }
        preferences.dockPinnedPackages = dockPinnedPackages
    }

    fun addRecentDestination(place: SearchResultPlace) {
        val filtered = recentDestinations.filterNot { existing ->
            isWithinDedupThreshold(existing, place)
        }
        val updated = listOf(place) + filtered
        recentDestinations = updated.take(LauncherPreferences.MAX_RECENT_DESTINATIONS)
        preferences.recentDestinations = recentDestinations
    }

    fun toggleSavedPlace(place: SearchResultPlace) {
        val existing = savedPlaces.find { isWithinDedupThreshold(it, place) }
        savedPlaces = if (existing != null) {
            savedPlaces.filterNot { isWithinDedupThreshold(it, place) }
        } else {
            (listOf(place) + savedPlaces).take(LauncherPreferences.MAX_SAVED_PLACES)
        }
        preferences.savedPlaces = savedPlaces
    }

    fun updateSavedPlace(place: SearchResultPlace) {
        val index = savedPlaces.indexOfFirst { isWithinDedupThreshold(it, place) }
        if (index < 0) return
        savedPlaces = savedPlaces.toMutableList().also { it[index] = place }
        preferences.savedPlaces = savedPlaces
    }

    fun isPlaceSaved(place: SearchResultPlace): Boolean {
        return savedPlaces.any { isWithinDedupThreshold(it, place) }
    }

    fun checkTomTomApiKey() {
        if (tomTomKeyCheckState is TomTomKeyCheckState.Checking) return

        viewModelScope.launch {
            tomTomKeyCheckState = TomTomKeyCheckState.Checking
            val result = withContext(Dispatchers.IO) {
                TomTomTrafficClient.verifyApiKey(tomTomApiKey)
            }
            tomTomKeyCheckState = when (result) {
                is TomTomKeyCheckResult.Success -> TomTomKeyCheckState.Success(result.message)
                is TomTomKeyCheckResult.Failure -> TomTomKeyCheckState.Error(result.message)
            }
        }
    }

    private fun loadValidatedDefaultAudioPackage(): String {
        val stored = preferences.defaultAudioPackage
        if (stored.isBlank()) return ""
        if (!canLaunchApp(getApplication(), stored)) {
            preferences.defaultAudioPackage = ""
            return ""
        }
        return stored
    }

    private fun loadValidatedDockPinnedPackages(): List<String> {
        val app = getApplication<Application>()
        val validated = preferences.dockPinnedPackages
            .distinct()
            .filter { pkg -> canLaunchApp(app, pkg) }
            .take(LauncherPreferences.MAX_DOCK_PINNED_APPS)
        if (validated != preferences.dockPinnedPackages) {
            preferences.dockPinnedPackages = validated
        }
        return validated
    }

    private fun isWithinDedupThreshold(a: SearchResultPlace, b: SearchResultPlace): Boolean {
        val distanceResults = FloatArray(1)
        Location.distanceBetween(
            a.latitude,
            a.longitude,
            b.latitude,
            b.longitude,
            distanceResults,
        )
        return distanceResults[0] < DEDUP_THRESHOLD_M
    }

    companion object {
        private const val DEDUP_THRESHOLD_M = 50f
    }
}
