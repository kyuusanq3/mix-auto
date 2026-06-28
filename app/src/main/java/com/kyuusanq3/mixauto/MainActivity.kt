package com.kyuusanq3.mixauto

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyuusanq3.mixauto.data.map.MapLibreEngineImpl
import com.kyuusanq3.mixauto.data.media.MediaSessionRepository
import com.kyuusanq3.mixauto.data.navigation.NavTtsPhrases
import com.kyuusanq3.mixauto.data.navigation.NavigationVoiceController
import com.kyuusanq3.mixauto.data.places.LocalPlacesRepository
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.ui.dashboard.DashboardScreen
import com.kyuusanq3.mixauto.ui.media.MediaPlayerViewModel
import com.kyuusanq3.mixauto.ui.onboarding.CURRENT_ONBOARDING_VERSION
import com.kyuusanq3.mixauto.ui.onboarding.OnboardingStep
import com.kyuusanq3.mixauto.ui.onboarding.OnboardingWizard
import com.kyuusanq3.mixauto.ui.onboarding.pendingOnboardingSteps
import com.kyuusanq3.mixauto.ui.settings.AppUpdateViewModel
import com.kyuusanq3.mixauto.ui.settings.LauncherPreferences
import com.kyuusanq3.mixauto.ui.settings.LauncherViewModel
import com.kyuusanq3.mixauto.ui.settings.MapDataViewModel
import com.kyuusanq3.mixauto.ui.settings.MapDataViewModelFactory
import com.kyuusanq3.mixauto.ui.theme.MixAutoTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var localPlacesRepository: LocalPlacesRepository
    private lateinit var navigationVoiceController: NavigationVoiceController
    private lateinit var mapEngine: CarMapEngine
    private lateinit var launcherViewModel: LauncherViewModel
    private lateinit var launcherPreferences: LauncherPreferences
    private lateinit var appUpdateViewModel: AppUpdateViewModel

    private var showOnboarding by mutableStateOf(false)
    private var pendingOnboardingSteps = emptyList<OnboardingStep>()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.any { it }) {
            mapEngine.retryLocationActivation()
        }
    }

    private val homeRoleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* Role dialog result; alias stays enabled until user changes setting */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launcherPreferences = LauncherPreferences(this)
        applyLauncherMode(launcherPreferences.isLauncherMode)
        localPlacesRepository = LocalPlacesRepository(this)
        navigationVoiceController = NavigationVoiceController(applicationContext).apply {
            enabled = launcherPreferences.navigationVoiceEnabled
            volume = launcherPreferences.navigationVoiceVolume
        }
        mapEngine = MapLibreEngineImpl(
            localPlaces = localPlacesRepository,
            navigationVoice = navigationVoiceController,
            initialUseVectorTiles = launcherPreferences.useVectorTiles,
            initialShow3dBuildings = launcherPreferences.show3dBuildings,
            initialDrivingZoom = launcherPreferences.drivingZoom.toDouble(),
            initialPuckHOffset = launcherPreferences.puckHorizontalOffset,
            initialPuckVOffset = launcherPreferences.puckVerticalOffset,
            initialPuckScale = launcherPreferences.puckScale,
        )
        mapEngine.setTrafficEnabled(
            launcherPreferences.showTraffic,
            launcherPreferences.tomTomApiKey,
        )

        MediaSessionRepository.getInstance(this)
        launcherViewModel = ViewModelProvider(this)[LauncherViewModel::class.java]
        appUpdateViewModel = ViewModelProvider(this)[AppUpdateViewModel::class.java]
        pendingOnboardingSteps = pendingOnboardingSteps(launcherPreferences.onboardingVersion)
        val shouldShowOnboarding = launcherPreferences.onboardingVersion < CURRENT_ONBOARDING_VERSION &&
            pendingOnboardingSteps.isNotEmpty()
        showOnboarding = shouldShowOnboarding
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applySystemBarVisibility(launcherPreferences.showSystemStatusBar)
        setContent {
            MixAutoTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    val mediaViewModel: MediaPlayerViewModel = viewModel()
                    val mapDataViewModel: MapDataViewModel = viewModel(
                        factory = MapDataViewModelFactory(
                            application = application,
                            localPlacesRepository = localPlacesRepository,
                        ),
                    )
                    val mediaState by mediaViewModel.mediaState.collectAsStateWithLifecycle()

                    SideEffect {
                        applySystemBarVisibility(launcherViewModel.showSystemStatusBar)
                    }

                    DashboardScreen(
                    mapEngine = mapEngine,
                    mapDataViewModel = mapDataViewModel,
                    mediaState = mediaState,
                    defaultAudioPackage = launcherViewModel.defaultAudioPackage,
                    onSetDefaultAudioPackage = launcherViewModel::updateDefaultAudioPackage,
                    onSelectAudioSource = { packageName ->
                        launcherViewModel.updateDefaultAudioPackage(packageName)
                        mediaViewModel.selectAudioSource(packageName)
                    },
                    onMediaPlayPause = mediaViewModel::playPause,
                    onMediaSkipPrevious = mediaViewModel::skipToPrevious,
                    onMediaSkipNext = mediaViewModel::skipToNext,
                    onMediaToggleLike = mediaViewModel::toggleLike,
                    albumArtMode = launcherViewModel.albumArtMode,
                    onAlbumArtModeChange = launcherViewModel::updateAlbumArtMode,
                    isLeftHandDrive = launcherViewModel.isLeftHandDrive,
                    isShortcutsHorizontal = launcherViewModel.isShortcutsHorizontal,
                    mapMediaRatio = launcherViewModel.mapMediaRatio,
                    limitSearchDistance = launcherViewModel.limitSearchDistance,
                    recentDestinations = launcherViewModel.recentDestinations,
                    savedPlaces = launcherViewModel.savedPlaces,
                    onDestinationSelected = launcherViewModel::addRecentDestination,
                    onToggleSavedPlace = launcherViewModel::toggleSavedPlace,
                    onUpdateSavedPlace = launcherViewModel::updateSavedPlace,
                    useVectorTiles = launcherViewModel.useVectorTiles,
                    show3dBuildings = launcherViewModel.show3dBuildings,
                    showTraffic = launcherViewModel.showTraffic,
                    navigationVoiceEnabled = launcherViewModel.navigationVoiceEnabled,
                    navigationVoiceVolume = launcherViewModel.navigationVoiceVolume,
                    tomTomApiKey = launcherViewModel.tomTomApiKey,
                    isLauncherMode = launcherViewModel.isLauncherMode,
                    shortcutIconSize = launcherViewModel.dockShortcutIconSize,
                    dockPinnedPackages = launcherViewModel.dockPinnedPackages,
                    onToggleDockPin = launcherViewModel::toggleDockPinnedPackage,
                    launchableApps = launcherViewModel.launchableApps,
                    audioPlayerPackages = launcherViewModel.audioPlayerPackages,
                    isAppDrawerLoading = launcherViewModel.isAppDrawerLoading,
                    onEnsureLaunchableAppsLoaded = launcherViewModel::ensureLaunchableAppsLoaded,
                    drivingZoom = launcherViewModel.drivingZoom,
                    puckHorizontalOffset = launcherViewModel.puckHorizontalOffset,
                    puckVerticalOffset = launcherViewModel.puckVerticalOffset,
                    puckScale = launcherViewModel.puckScale,
                    onToggleLhd = launcherViewModel::toggleLeftHandDrive,
                    onToggleShortcutsHorizontal = launcherViewModel::toggleShortcutsHorizontal,
                    onMapMediaRatioChange = launcherViewModel::updateMapMediaRatio,
                    onToggleLimitSearchDistance = launcherViewModel::toggleLimitSearchDistance,
                    onToggleVectorTiles = {
                        launcherViewModel.toggleVectorTiles()
                        mapEngine.setMapStyle(launcherViewModel.useVectorTiles)
                    },
                    onToggleShow3dBuildings = {
                        launcherViewModel.toggleShow3dBuildings()
                        mapEngine.setShow3dBuildings(launcherViewModel.show3dBuildings)
                    },
                    onToggleTraffic = {
                        launcherViewModel.toggleTraffic()
                        mapEngine.setTrafficEnabled(
                            launcherViewModel.showTraffic,
                            launcherViewModel.tomTomApiKey,
                        )
                    },
                    onToggleNavigationVoice = {
                        launcherViewModel.toggleNavigationVoice()
                        mapEngine.setNavigationVoiceEnabled(launcherViewModel.navigationVoiceEnabled)
                    },
                    onNavigationVoiceVolumeChange = { value ->
                        launcherViewModel.updateNavigationVoiceVolume(value)
                        navigationVoiceController.volume = launcherViewModel.navigationVoiceVolume
                    },
                    onTestNavigationVoice = {
                        navigationVoiceController.speakPreview(
                            NavTtsPhrases.buildAheadCue("turn right", "Main Street", "turn"),
                        )
                    },
                    onTomTomApiKeyChange = { key ->
                        launcherViewModel.updateTomTomApiKey(key)
                        mapEngine.setTrafficEnabled(
                            launcherViewModel.showTraffic,
                            launcherViewModel.tomTomApiKey,
                        )
                    },
                    onToggleLauncherMode = {
                        launcherViewModel.toggleLauncherMode()
                        applyLauncherMode(launcherViewModel.isLauncherMode)
                        if (launcherViewModel.isLauncherMode) {
                            promptSetAsDefaultHome()
                        }
                    },
                    onShortcutIconSizeChange = launcherViewModel::updateDockShortcutIconSize,
                    onDrivingZoomChange = { value ->
                        launcherViewModel.updateDrivingZoom(value)
                        mapEngine.setDrivingZoom(value.toDouble())
                    },
                    onPuckHorizontalOffsetChange = { value ->
                        launcherViewModel.updatePuckHorizontalOffset(value)
                        mapEngine.setViewportPadding(
                            launcherViewModel.puckHorizontalOffset,
                            launcherViewModel.puckVerticalOffset,
                        )
                    },
                    onPuckVerticalOffsetChange = { value ->
                        launcherViewModel.updatePuckVerticalOffset(value)
                        mapEngine.setViewportPadding(
                            launcherViewModel.puckHorizontalOffset,
                            launcherViewModel.puckVerticalOffset,
                        )
                    },
                    onPuckScaleChange = { value ->
                        launcherViewModel.updatePuckScale(value)
                        mapEngine.setPuckScale(value)
                    },
                    showStatusStrip = launcherViewModel.showStatusStrip,
                    showSystemStatusBar = launcherViewModel.showSystemStatusBar,
                    onToggleShowStatusStrip = launcherViewModel::toggleShowStatusStrip,
                    onToggleShowSystemStatusBar = launcherViewModel::toggleShowSystemStatusBar,
                    onInstallApk = ::launchApkInstall,
                )

                    if (showOnboarding) {
                        OnboardingWizard(
                            pendingSteps = pendingOnboardingSteps,
                            onComplete = {
                                launcherPreferences.onboardingVersion = CURRENT_ONBOARDING_VERSION
                                showOnboarding = false
                                requestLocationPermissionIfNeeded()
                                refreshMediaSessionsAndBootAudio()
                                appUpdateViewModel.checkForUpdate(autoPrompt = true)
                            },
                        )
                    }
                }
            }
        }
        if (!shouldShowOnboarding) {
            appUpdateViewModel.checkForUpdate(autoPrompt = true)
            requestLocationPermissionIfNeeded()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::launcherPreferences.isInitialized) {
            applySystemBarVisibility(launcherPreferences.showSystemStatusBar)
        }
        refreshMediaSessionsAndBootAudio()
        if (::mapEngine.isInitialized && hasLocationPermission()) {
            mapEngine.retryLocationActivation()
        }
    }

    override fun onDestroy() {
        if (::navigationVoiceController.isInitialized) {
            navigationVoiceController.shutdown()
        }
        super.onDestroy()
    }

    private fun refreshMediaSessionsAndBootAudio() {
        val repository = MediaSessionRepository.getInstance(this)
        repository.refreshSessions()
        if (::launcherViewModel.isInitialized) {
            repository.ensureDefaultPlayerIfNeeded(
                launcherViewModel.defaultAudioPackage.takeIf { it.isNotBlank() },
            )
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOICE_ASSIST || keyCode == KeyEvent.KEYCODE_SEARCH) &&
            ::launcherViewModel.isInitialized &&
            launcherViewModel.isDestinationSearchOpen
        ) {
            launcherViewModel.triggerVoiceSearch()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun requestLocationPermissionIfNeeded() {
        if (hasLocationPermission()) {
            mapEngine.retryLocationActivation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    private fun applySystemBarVisibility(show: Boolean) {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            if (show) {
                show(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            } else {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    private fun applyLauncherMode(enabled: Boolean) {
        val alias = ComponentName(this, "com.kyuusanq3.mixauto.LauncherModeAlias")
        packageManager.setComponentEnabledSetting(
            alias,
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun promptSetAsDefaultHome() {
        // Wait for PackageManager to register the enabled alias before prompting.
        window.decorView.postDelayed({
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                    !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
                ) {
                    homeRoleRequestLauncher.launch(
                        roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME),
                    )
                    return@postDelayed
                }
            }
            openHomeAppSettings()
        }, LAUNCHER_ALIAS_REGISTER_DELAY_MS)
    }

    private fun openHomeAppSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            // No system UI for default home on this device; alias is still enabled.
        }
    }

    companion object {
        private const val LAUNCHER_ALIAS_REGISTER_DELAY_MS = 200L
    }

    private fun launchApkInstall(apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    },
                )
            } catch (_: ActivityNotFoundException) {
                // Fall through to install intent; system may still prompt.
            }
            return
        }
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            apkFile,
        )
        startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
