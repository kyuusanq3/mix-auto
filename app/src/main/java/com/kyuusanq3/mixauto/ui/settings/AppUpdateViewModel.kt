package com.kyuusanq3.mixauto.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kyuusanq3.mixauto.data.update.AppUpdateRepository
import com.kyuusanq3.mixauto.data.update.UpdateInfo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class AppUpdateState {
    data object Idle : AppUpdateState()
    data object Checking : AppUpdateState()
    data object UpToDate : AppUpdateState()
    data class Available(val versionName: String) : AppUpdateState()
    data class Downloading(val progress: Float) : AppUpdateState()
    data class ReadyToInstall(val apkFile: File) : AppUpdateState()
    data class Error(val message: String) : AppUpdateState()
}

class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppUpdateRepository(application)

    private val _uiState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val uiState: StateFlow<AppUpdateState> = _uiState.asStateFlow()

    private val _showDownloadOffer = MutableStateFlow(false)
    val showDownloadOffer: StateFlow<Boolean> = _showDownloadOffer.asStateFlow()

    private val _showInstallOffer = MutableStateFlow(false)
    val showInstallOffer: StateFlow<Boolean> = _showInstallOffer.asStateFlow()

    private var availableUpdate: UpdateInfo? = null
    private var pendingAutoPrompt = false
    private var bootAutoCheckDone = false

    fun checkForUpdate(autoPrompt: Boolean = false) {
        if (autoPrompt && bootAutoCheckDone) return

        if (_uiState.value is AppUpdateState.Checking ||
            _uiState.value is AppUpdateState.Downloading
        ) {
            return
        }

        if (autoPrompt) {
            bootAutoCheckDone = true
        }
        pendingAutoPrompt = autoPrompt

        viewModelScope.launch {
            _uiState.value = AppUpdateState.Checking
            val result = withContext(Dispatchers.IO) { repository.checkForUpdate() }
            result.fold(
                onSuccess = { update ->
                    if (update != null) {
                        availableUpdate = update
                        _uiState.value = AppUpdateState.Available(update.versionName)
                        if (pendingAutoPrompt) {
                            _showDownloadOffer.value = true
                        }
                    } else {
                        availableUpdate = null
                        _uiState.value = AppUpdateState.UpToDate
                    }
                    pendingAutoPrompt = false
                },
                onFailure = { error ->
                    pendingAutoPrompt = false
                    _uiState.value = AppUpdateState.Error(
                        error.message ?: "Could not check for updates",
                    )
                },
            )
        }
    }

    fun downloadUpdate() {
        if (_uiState.value is AppUpdateState.Downloading) return
        if (availableUpdate == null && _uiState.value !is AppUpdateState.Available) return

        _showDownloadOffer.value = false

        viewModelScope.launch {
            _uiState.value = AppUpdateState.Downloading(progress = 0f)
            var lastReportedPercent = -1
            val result = withContext(Dispatchers.IO) {
                repository.downloadApk { progress ->
                    val percent = (progress * 100).roundToInt()
                    if (progress < 1f && percent == lastReportedPercent) return@downloadApk
                    lastReportedPercent = percent
                    _uiState.value = AppUpdateState.Downloading(progress = progress)
                }
            }
            result.fold(
                onSuccess = { apkFile ->
                    _uiState.value = AppUpdateState.ReadyToInstall(apkFile)
                    _showInstallOffer.value = true
                },
                onFailure = { error ->
                    _uiState.value = AppUpdateState.Error(
                        error.message ?: "Download failed",
                    )
                },
            )
        }
    }

    fun dismissDownloadOffer() {
        _showDownloadOffer.value = false
    }

    fun dismissInstallOffer() {
        _showInstallOffer.value = false
    }

    fun clearAutoPrompts() {
        _showDownloadOffer.value = false
        _showInstallOffer.value = false
    }
}
