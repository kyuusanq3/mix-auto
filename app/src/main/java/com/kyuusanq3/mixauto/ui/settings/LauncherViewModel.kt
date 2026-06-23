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
import kotlinx.coroutines.Dispatchers
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

    var tomTomKeyCheckState by mutableStateOf<TomTomKeyCheckState>(TomTomKeyCheckState.Idle)
        private set

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

    fun addRecentDestination(place: SearchResultPlace) {
        val filtered = recentDestinations.filterNot { existing ->
            isWithinDedupThreshold(existing, place)
        }
        val updated = listOf(place) + filtered
        recentDestinations = updated.take(LauncherPreferences.MAX_RECENT_DESTINATIONS)
        preferences.recentDestinations = recentDestinations
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
