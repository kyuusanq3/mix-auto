package com.kyuusanq3.mixauto.ui.settings

import android.app.Application
import android.location.Location
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kyuusanq3.mixauto.BuildConfig
import com.kyuusanq3.mixauto.data.apps.LaunchableAppEntry
import com.kyuusanq3.mixauto.data.apps.LaunchableAppsRepository
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import com.kyuusanq3.mixauto.ui.components.canLaunchApp
import com.kyuusanq3.mixauto.ui.dashboard.ActivePanel
import com.kyuusanq3.mixauto.ui.dashboard.AlbumArtMode
import com.kyuusanq3.mixauto.ui.dashboard.DockShortcutIconSize
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = LauncherPreferences(application)
    private val launchableAppsRepository = LaunchableAppsRepository(application)
    private val mapPrefs = LauncherMapPrefs(preferences, viewModelScope)
    private val audioPrefs = LauncherAudioPrefs(application, preferences)

    val defaultAudioPackage: String get() = audioPrefs.defaultAudioPackage
    val audioFallbackResumeLink: String get() = audioPrefs.audioFallbackResumeLink
    val showAlbumArtControls: Boolean get() = audioPrefs.showAlbumArtControls
    val resumeAudioOnStartup: Boolean get() = audioPrefs.resumeAudioOnStartup
    val albumArtMode: AlbumArtMode get() = audioPrefs.albumArtMode

    val limitSearchDistance: Boolean get() = mapPrefs.limitSearchDistance
    val useVectorTiles: Boolean get() = mapPrefs.useVectorTiles
    val show3dBuildings: Boolean get() = mapPrefs.show3dBuildings
    val drivingZoom: Float get() = mapPrefs.drivingZoom
    val drivingTilt: Float get() = mapPrefs.drivingTilt
    val puckHorizontalOffset: Float get() = mapPrefs.puckHorizontalOffset
    val puckVerticalOffset: Float get() = mapPrefs.puckVerticalOffset
    val puckScale: Float get() = mapPrefs.puckScale
    val showTraffic: Boolean get() = mapPrefs.showTraffic
    val navigationVoiceEnabled: Boolean get() = mapPrefs.navigationVoiceEnabled
    val navigationVoiceVolume: Float get() = mapPrefs.navigationVoiceVolume
    val navigationVoiceBoost: Boolean get() = mapPrefs.navigationVoiceBoost
    val tomTomApiKey: String get() = mapPrefs.tomTomApiKey
    val rememberEncounteredPlaces: Boolean get() = mapPrefs.rememberEncounteredPlaces
    val allowMapDownloadOnMobileData: Boolean get() = mapPrefs.allowMapDownloadOnMobileData
    val offlineDetailUpgradeBannerDismissed: Boolean
        get() = mapPrefs.offlineDetailUpgradeBannerDismissed
    val tomTomKeyCheckState: TomTomKeyCheckState get() = mapPrefs.tomTomKeyCheckState

    var dockPinnedPackages by mutableStateOf(loadValidatedDockPinnedPackages())
        private set

    var isLeftHandDrive by mutableStateOf(preferences.isLeftHandDrive)
        private set

    var isShortcutsHorizontal by mutableStateOf(preferences.isShortcutsHorizontal)
        private set

    var mapMediaRatio by mutableStateOf(preferences.mapMediaRatio)
        private set

    var isLauncherMode by mutableStateOf(preferences.isLauncherMode)
        private set

    var dockShortcutIconSize by mutableStateOf(preferences.dockShortcutIconSize)
        private set

    var recentDestinations by mutableStateOf(preferences.recentDestinations)
        private set

    var savedPlaces by mutableStateOf(preferences.savedPlaces)
        private set

    var showStatusStrip by mutableStateOf(preferences.showStatusStrip)
        private set

    var showSystemStatusBar by mutableStateOf(preferences.showSystemStatusBar)
        private set

    var musicPaneEnabled by mutableStateOf(preferences.musicPaneEnabled)
        private set

    var isAudioPlayerMinimized by mutableStateOf(false)
        internal set

    var isDestinationSearchOpen by mutableStateOf(false)
        internal set

    var activePanel by mutableStateOf(
        if (preferences.musicPaneEnabled) ActivePanel.MEDIA else ActivePanel.HIDDEN,
    )
        internal set

    var poiReturnToSearch by mutableStateOf(false)
        internal set

    fun setActivePanel(panel: ActivePanel) {
        activePanel = panel
    }

    fun setPoiReturnToSearch(value: Boolean) {
        poiReturnToSearch = value
    }

    fun clearPoiReturnToSearch() {
        poiReturnToSearch = false
    }

    var destinationSearchState by mutableStateOf(DestinationSearchUiState())
        private set

    fun updateDestinationSearch(
        transform: (DestinationSearchUiState) -> DestinationSearchUiState,
    ) {
        destinationSearchState = transform(destinationSearchState)
    }

    fun clearDestinationSearchState() {
        destinationSearchState = DestinationSearchUiState()
    }

    var addPlaceLinkState by mutableStateOf(AddPlaceLinkUiState())
        private set

    fun updateAddPlaceLink(
        transform: (AddPlaceLinkUiState) -> AddPlaceLinkUiState,
    ) {
        addPlaceLinkState = transform(addPlaceLinkState)
    }

    fun clearAddPlaceLinkState() {
        addPlaceLinkState = AddPlaceLinkUiState()
    }

    var launchableApps by mutableStateOf<List<LaunchableAppEntry>>(emptyList())
        private set

    var audioPlayerPackages by mutableStateOf<Set<String>>(emptySet())
        private set

    var isAppDrawerLoading by mutableStateOf(false)
        private set

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

    fun ensureLaunchableAppsLoaded() {
        if (launchableApps.isNotEmpty() || isAppDrawerLoading) return
        viewModelScope.launch {
            isAppDrawerLoading = true
            val (apps, audioPackages) = launchableAppsRepository.loadAll()
            launchableApps = apps
            audioPlayerPackages = audioPackages
            isAppDrawerLoading = false
        }
    }

    init {
        ensureLaunchableAppsLoaded()
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
        if (value > 0.8f) {
            isAudioPlayerMinimized = true
            return
        }
        mapMediaRatio = value
        preferences.mapMediaRatio = value
    }

    fun updateMusicPaneEnabled(enabled: Boolean) {
        musicPaneEnabled = enabled
        preferences.musicPaneEnabled = enabled
    }

    fun toggleAudioPlayerMinimized() {
        isAudioPlayerMinimized = !isAudioPlayerMinimized
        if (!isAudioPlayerMinimized) {
            updateMapMediaRatio(0.8f)
        }
    }

    fun setAudioPlayerMinimized(value: Boolean) {
        isAudioPlayerMinimized = value
    }

    fun toggleLimitSearchDistance() = mapPrefs.toggleLimitSearchDistance()

    fun toggleVectorTiles() = mapPrefs.toggleVectorTiles()

    fun toggleShow3dBuildings() = mapPrefs.toggleShow3dBuildings()

    fun toggleLauncherMode() {
        isLauncherMode = !isLauncherMode
        preferences.isLauncherMode = isLauncherMode
    }

    fun updateDockShortcutIconSize(size: DockShortcutIconSize) {
        dockShortcutIconSize = size
        preferences.dockShortcutIconSize = size
    }

    fun updateDrivingZoom(value: Float) = mapPrefs.updateDrivingZoom(value)

    fun updateDrivingTilt(value: Float) = mapPrefs.updateDrivingTilt(value)

    fun updatePuckHorizontalOffset(value: Float) = mapPrefs.updatePuckHorizontalOffset(value)

    fun updatePuckVerticalOffset(value: Float) = mapPrefs.updatePuckVerticalOffset(value)

    fun updatePuckScale(value: Float) = mapPrefs.updatePuckScale(value)

    fun updateAlbumArtMode(mode: AlbumArtMode) = audioPrefs.updateAlbumArtMode(mode)

    fun toggleTraffic() = mapPrefs.toggleTraffic()

    fun toggleNavigationVoice() = mapPrefs.toggleNavigationVoice()

    fun updateNavigationVoiceVolume(value: Float) = mapPrefs.updateNavigationVoiceVolume(value)

    fun updateNavigationVoiceBoost(enabled: Boolean) = mapPrefs.updateNavigationVoiceBoost(enabled)

    fun updateRememberEncounteredPlaces(enabled: Boolean) =
        mapPrefs.updateRememberEncounteredPlaces(enabled)

    fun toggleAllowMapDownloadOnMobileData() = mapPrefs.toggleAllowMapDownloadOnMobileData()

    fun dismissOfflineDetailUpgradeBanner() = mapPrefs.dismissOfflineDetailUpgradeBanner()

    fun toggleShowStatusStrip() {
        showStatusStrip = !showStatusStrip
        preferences.showStatusStrip = showStatusStrip
    }

    fun toggleShowSystemStatusBar() {
        showSystemStatusBar = !showSystemStatusBar
        preferences.showSystemStatusBar = showSystemStatusBar
    }

    fun updateTomTomApiKey(key: String) = mapPrefs.updateTomTomApiKey(key)

    fun updateDefaultAudioPackage(packageName: String) =
        audioPrefs.updateDefaultAudioPackage(packageName)

    fun updateAudioFallbackResumeLink(link: String) =
        audioPrefs.updateAudioFallbackResumeLink(link)

    fun updateShowAlbumArtControls(enabled: Boolean) =
        audioPrefs.updateShowAlbumArtControls(enabled)

    fun updateResumeAudioOnStartup(enabled: Boolean) =
        audioPrefs.updateResumeAudioOnStartup(enabled)

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

    fun checkTomTomApiKey() = mapPrefs.checkTomTomApiKey()

    private fun loadValidatedDockPinnedPackages(): List<String> {
        val app = getApplication<Application>()
        val validated = preferences.dockPinnedPackages
            .distinct()
            .filter { pkg ->
                pkg != BuildConfig.APPLICATION_ID && canLaunchApp(app, pkg)
            }
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

/** Survives rotation while destination search is open; cleared when search panel dismisses. */
data class DestinationSearchUiState(
    val query: String = "",
    val results: List<SearchResultPlace> = emptyList(),
    val nearbyPois: List<SearchResultPlace> = emptyList(),
    val snapshotOriginLat: Double? = null,
    val snapshotOriginLng: Double? = null,
    val snapshotOriginReliable: Boolean = false,
    val hasSearched: Boolean = false,
    val savedFilterActive: Boolean = false,
    /** Debounce + local/cache phase before or while Photon runs. */
    val isSearching: Boolean = false,
    /** Photon geocoder still in flight. */
    val isLoadingRemote: Boolean = false,
)

/** Survives rotation while add-from-link panel is open; cleared when panel dismisses. */
data class AddPlaceLinkUiState(
    val linkText: String = "",
    val errorMessage: String? = null,
    val isResolving: Boolean = false,
)
