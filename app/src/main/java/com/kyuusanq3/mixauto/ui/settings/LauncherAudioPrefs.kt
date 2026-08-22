package com.kyuusanq3.mixauto.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kyuusanq3.mixauto.ui.components.canLaunchApp
import com.kyuusanq3.mixauto.ui.dashboard.AlbumArtMode

/**
 * Default player / resume / album-art prefs extracted from [LauncherViewModel].
 * Boot/resume actions stay on [com.kyuusanq3.mixauto.data.media.MediaSessionRepository].
 */
internal class LauncherAudioPrefs(
    private val appContext: Context,
    private val preferences: LauncherPreferences,
) {
    var defaultAudioPackage by mutableStateOf(loadValidatedDefaultAudioPackage())
        private set

    var audioFallbackResumeLink by mutableStateOf(preferences.audioFallbackResumeLink)
        private set

    var showAlbumArtControls by mutableStateOf(preferences.showAlbumArtControls)
        private set

    var resumeAudioOnStartup by mutableStateOf(preferences.resumeAudioOnStartup)
        private set

    var albumArtMode by mutableStateOf(AlbumArtMode.fromPreference(preferences.albumArtMode))
        private set

    fun updateDefaultAudioPackage(packageName: String) {
        defaultAudioPackage = packageName
        preferences.defaultAudioPackage = packageName
    }

    fun updateAudioFallbackResumeLink(link: String) {
        audioFallbackResumeLink = link
        preferences.audioFallbackResumeLink = link
    }

    fun updateShowAlbumArtControls(enabled: Boolean) {
        showAlbumArtControls = enabled
        preferences.showAlbumArtControls = enabled
    }

    fun updateResumeAudioOnStartup(enabled: Boolean) {
        resumeAudioOnStartup = enabled
        preferences.resumeAudioOnStartup = enabled
    }

    fun updateAlbumArtMode(mode: AlbumArtMode) {
        albumArtMode = mode
        preferences.albumArtMode = mode.name
    }

    private fun loadValidatedDefaultAudioPackage(): String {
        val stored = preferences.defaultAudioPackage
        if (stored.isBlank()) return ""
        if (!canLaunchApp(appContext, stored)) {
            preferences.defaultAudioPackage = ""
            return ""
        }
        return stored
    }
}
