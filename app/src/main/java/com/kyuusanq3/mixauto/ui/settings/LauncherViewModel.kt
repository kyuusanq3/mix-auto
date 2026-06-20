package com.kyuusanq3.mixauto.ui.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

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
}
