package com.kyuusanq3.mixauto.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kyuusanq3.mixauto.data.map.TomTomKeyCheckResult
import com.kyuusanq3.mixauto.data.map.TomTomTrafficClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class TomTomKeyCheckState {
    data object Idle : TomTomKeyCheckState()
    data object Checking : TomTomKeyCheckState()
    data class Success(val message: String) : TomTomKeyCheckState()
    data class Error(val message: String) : TomTomKeyCheckState()
}

/**
 * Map / traffic / driving-camera / nav-voice prefs extracted from [LauncherViewModel].
 * Panel session and layout split stay on the ViewModel.
 */
internal class LauncherMapPrefs(
    private val preferences: LauncherPreferences,
    private val scope: CoroutineScope,
) {
    var limitSearchDistance by mutableStateOf(preferences.limitSearchDistance)
        private set

    var useVectorTiles by mutableStateOf(preferences.useVectorTiles)
        private set

    var show3dBuildings by mutableStateOf(preferences.show3dBuildings)
        private set

    var drivingZoom by mutableStateOf(preferences.drivingZoom)
        private set

    var drivingTilt by mutableStateOf(preferences.drivingTilt)
        private set

    var puckHorizontalOffset by mutableStateOf(preferences.puckHorizontalOffset)
        private set

    var puckVerticalOffset by mutableStateOf(preferences.puckVerticalOffset)
        private set

    var puckScale by mutableStateOf(preferences.puckScale)
        private set

    var showTraffic by mutableStateOf(preferences.showTraffic)
        private set

    var navigationVoiceEnabled by mutableStateOf(preferences.navigationVoiceEnabled)
        private set

    var navigationVoiceVolume by mutableStateOf(preferences.navigationVoiceVolume)
        private set

    var navigationVoiceBoost by mutableStateOf(preferences.navigationVoiceBoost)
        private set

    var tomTomApiKey by mutableStateOf(preferences.tomTomApiKey)
        private set

    var rememberEncounteredPlaces by mutableStateOf(preferences.rememberEncounteredPlaces)
        private set

    var allowMapDownloadOnMobileData by mutableStateOf(preferences.allowMapDownloadOnMobileData)
        private set

    var offlineDetailUpgradeBannerDismissed by mutableStateOf(
        preferences.offlineDetailUpgradeBannerDismissed,
    )
        private set

    var tomTomKeyCheckState by mutableStateOf<TomTomKeyCheckState>(TomTomKeyCheckState.Idle)
        private set

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

    fun toggleTraffic() {
        showTraffic = !showTraffic
        preferences.showTraffic = showTraffic
    }

    fun toggleNavigationVoice() {
        navigationVoiceEnabled = !navigationVoiceEnabled
        preferences.navigationVoiceEnabled = navigationVoiceEnabled
    }

    fun updateDrivingZoom(value: Float) {
        drivingZoom = value
        preferences.drivingZoom = value
    }

    fun updateDrivingTilt(value: Float) {
        drivingTilt = value.coerceIn(
            LauncherPreferences.MIN_DRIVING_TILT,
            LauncherPreferences.MAX_DRIVING_TILT,
        )
        preferences.drivingTilt = drivingTilt
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

    fun updateNavigationVoiceVolume(value: Float) {
        navigationVoiceVolume = value.coerceIn(
            LauncherPreferences.MIN_NAVIGATION_VOICE_VOLUME,
            LauncherPreferences.MAX_NAVIGATION_VOICE_VOLUME,
        )
        preferences.navigationVoiceVolume = navigationVoiceVolume
    }

    fun updateNavigationVoiceBoost(enabled: Boolean) {
        navigationVoiceBoost = enabled
        preferences.navigationVoiceBoost = enabled
    }

    fun updateTomTomApiKey(key: String) {
        tomTomApiKey = key
        preferences.tomTomApiKey = key
        tomTomKeyCheckState = TomTomKeyCheckState.Idle
    }

    fun updateRememberEncounteredPlaces(enabled: Boolean) {
        rememberEncounteredPlaces = enabled
        preferences.rememberEncounteredPlaces = enabled
    }

    fun toggleAllowMapDownloadOnMobileData() {
        allowMapDownloadOnMobileData = !allowMapDownloadOnMobileData
        preferences.allowMapDownloadOnMobileData = allowMapDownloadOnMobileData
    }

    fun dismissOfflineDetailUpgradeBanner() {
        offlineDetailUpgradeBannerDismissed = true
        preferences.offlineDetailUpgradeBannerDismissed = true
    }

    fun checkTomTomApiKey() {
        if (tomTomKeyCheckState is TomTomKeyCheckState.Checking) return

        scope.launch {
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
}
