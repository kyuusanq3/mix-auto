package com.kyuusanq3.mixauto

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyuusanq3.mixauto.data.map.MapLibreEngineImpl
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.ui.dashboard.DashboardScreen
import com.kyuusanq3.mixauto.ui.settings.LauncherViewModel
import com.kyuusanq3.mixauto.ui.theme.MixAutoTheme

class MainActivity : ComponentActivity() {

    private val mapEngine: CarMapEngine = MapLibreEngineImpl()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.any { it }) {
            mapEngine.retryLocationActivation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MixAutoTheme {
                val viewModel: LauncherViewModel = viewModel()
                DashboardScreen(
                    mapEngine = mapEngine,
                    isLeftHandDrive = viewModel.isLeftHandDrive,
                    isShortcutsHorizontal = viewModel.isShortcutsHorizontal,
                    onToggleLhd = viewModel::toggleLeftHandDrive,
                    onToggleShortcutsHorizontal = viewModel::toggleShortcutsHorizontal,
                )
            }
        }
        requestLocationPermissionIfNeeded()
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
}
