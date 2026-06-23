package com.kyuusanq3.mixauto

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyuusanq3.mixauto.data.map.MapLibreEngineImpl
import com.kyuusanq3.mixauto.data.media.MediaSessionRepository
import com.kyuusanq3.mixauto.data.places.LocalPlacesRepository
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.ui.dashboard.DashboardScreen
import com.kyuusanq3.mixauto.ui.media.MediaPlayerViewModel
import com.kyuusanq3.mixauto.ui.settings.AppUpdateViewModel
import com.kyuusanq3.mixauto.ui.settings.LauncherPreferences
import com.kyuusanq3.mixauto.ui.settings.LauncherViewModel
import com.kyuusanq3.mixauto.ui.settings.MapDataViewModel
import com.kyuusanq3.mixauto.ui.settings.MapDataViewModelFactory
import com.kyuusanq3.mixauto.ui.theme.MixAutoTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var localPlacesRepository: LocalPlacesRepository
    private lateinit var mapEngine: CarMapEngine

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.any { it }) {
            mapEngine.retryLocationActivation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcherPreferences = LauncherPreferences(this)
        applyLauncherMode(launcherPreferences.isLauncherMode)
        localPlacesRepository = LocalPlacesRepository(this)
        mapEngine = MapLibreEngineImpl(
            localPlaces = localPlacesRepository,
            initialUseVectorTiles = launcherPreferences.useVectorTiles,
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MixAutoTheme {
                val launcherViewModel: LauncherViewModel = viewModel()
                val mediaViewModel: MediaPlayerViewModel = viewModel()
                val mapDataViewModel: MapDataViewModel = viewModel(
                    factory = MapDataViewModelFactory(
                        application = application,
                        localPlacesRepository = localPlacesRepository,
                    ),
                )
                val mediaState by mediaViewModel.mediaState.collectAsStateWithLifecycle()

                DashboardScreen(
                    mapEngine = mapEngine,
                    mapDataViewModel = mapDataViewModel,
                    mediaState = mediaState,
                    onMediaPlayPause = mediaViewModel::playPause,
                    onMediaSkipPrevious = mediaViewModel::skipToPrevious,
                    onMediaSkipNext = mediaViewModel::skipToNext,
                    isLeftHandDrive = launcherViewModel.isLeftHandDrive,
                    isShortcutsHorizontal = launcherViewModel.isShortcutsHorizontal,
                    mapMediaRatio = launcherViewModel.mapMediaRatio,
                    limitSearchDistance = launcherViewModel.limitSearchDistance,
                    recentDestinations = launcherViewModel.recentDestinations,
                    onDestinationSelected = launcherViewModel::addRecentDestination,
                    useVectorTiles = launcherViewModel.useVectorTiles,
                    showTraffic = launcherViewModel.showTraffic,
                    tomTomApiKey = launcherViewModel.tomTomApiKey,
                    isLauncherMode = launcherViewModel.isLauncherMode,
                    isLargeShortcutIcons = launcherViewModel.isLargeShortcutIcons,
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
                    onToggleTraffic = {
                        launcherViewModel.toggleTraffic()
                        mapEngine.setTrafficEnabled(
                            launcherViewModel.showTraffic,
                            launcherViewModel.tomTomApiKey,
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
                    },
                    onToggleLargeShortcutIcons = launcherViewModel::toggleLargeShortcutIcons,
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
                    onInstallApk = ::launchApkInstall,
                )
            }
        }
        ViewModelProvider(this)[AppUpdateViewModel::class.java].checkForUpdate()
        requestLocationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        MediaSessionRepository.getInstance(this).refreshSessions()
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

    private fun launchApkInstall(apkFile: File) {
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
