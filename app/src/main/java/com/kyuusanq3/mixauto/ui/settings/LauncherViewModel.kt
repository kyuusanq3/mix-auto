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

    fun toggleLeftHandDrive() {
        isLeftHandDrive = !isLeftHandDrive
        preferences.isLeftHandDrive = isLeftHandDrive
    }

    fun toggleShortcutsHorizontal() {
        isShortcutsHorizontal = !isShortcutsHorizontal
        preferences.isShortcutsHorizontal = isShortcutsHorizontal
    }
}
