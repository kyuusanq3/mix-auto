package com.kyuusanq3.mixauto.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyuusanq3.mixauto.ui.settings.AppUpdateState
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.DarkSurface
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import java.io.File
import kotlin.math.roundToInt

@Composable
fun AppUpdatePrompts(
    uiState: AppUpdateState,
    showDownloadOffer: Boolean,
    showInstallOffer: Boolean,
    onDownloadUpdate: () -> Unit,
    onDismissDownloadOffer: () -> Unit,
    onInstallApk: (File) -> Unit,
    onDismissInstallOffer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var checkingSnackbarShown by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        when (uiState) {
            AppUpdateState.Checking -> {
                if (!checkingSnackbarShown) {
                    checkingSnackbarShown = true
                    snackbarHostState.showSnackbar(
                        message = "Checking for updates…",
                        duration = SnackbarDuration.Indefinite,
                    )
                }
            }
            is AppUpdateState.Downloading -> {
                snackbarHostState.currentSnackbarData?.dismiss()
            }
            AppUpdateState.UpToDate,
            is AppUpdateState.Available,
            is AppUpdateState.ReadyToInstall,
            is AppUpdateState.Error,
            -> {
                checkingSnackbarShown = false
                snackbarHostState.currentSnackbarData?.dismiss()
            }
            else -> Unit
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val showDownloadBanner = uiState is AppUpdateState.Available && showDownloadOffer
        val showInstallBanner = uiState is AppUpdateState.ReadyToInstall && showInstallOffer

        if (showDownloadBanner) {
            val version = (uiState as AppUpdateState.Available).versionName
            AppUpdateTopBanner(
                message = "Mix Auto v$version is available",
                primaryLabel = "Download",
                onPrimary = onDownloadUpdate,
                onLater = onDismissDownloadOffer,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
            )
        } else if (showInstallBanner) {
            AppUpdateTopBanner(
                message = "Update ready to install",
                primaryLabel = "Install",
                onPrimary = {
                    onInstallApk((uiState as AppUpdateState.ReadyToInstall).apkFile)
                    onDismissInstallOffer()
                },
                onLater = onDismissInstallOffer,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
            )
        }

        if (uiState is AppUpdateState.Downloading) {
            AppUpdateDownloadBanner(
                progress = uiState.progress,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 88.dp),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = DarkSurface,
                contentColor = ElectricCyan,
            )
        }
    }
}

@Composable
private fun AppUpdateDownloadBanner(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = DarkSurface,
        contentColor = ElectricCyan,
    ) {
        CarBodyText(
            text = "Downloading update… ${(progress * 100).roundToInt()}%",
            modifier = Modifier.padding(
                horizontal = CarDimensions.PaneGap * 2,
                vertical = CarDimensions.PaneGap,
            ),
        )
    }
}

@Composable
private fun AppUpdateTopBanner(
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = DarkSurface,
        contentColor = ElectricCyan,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = CarDimensions.PaneGap * 2,
                    vertical = CarDimensions.PaneGap,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
        ) {
            CarBodyText(
                text = message,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onLater) {
                CarLabelText(text = "Later")
            }
            Button(
                onClick = onPrimary,
                modifier = Modifier.height(CarDimensions.MinTapTarget),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = OledBlack,
                ),
            ) {
                CarBodyText(text = primaryLabel)
            }
        }
    }
}
