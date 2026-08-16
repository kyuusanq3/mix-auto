package com.kyuusanq3.mixauto.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kyuusanq3.mixauto.data.map.OfflineRegionDefinition
import com.kyuusanq3.mixauto.data.map.OfflineRegionInstallState
import com.kyuusanq3.mixauto.ui.settings.MapDataUiState
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.DeepCharcoal
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack

@Composable
internal fun OfflineRegionRow(
    region: OfflineRegionDefinition,
    installState: OfflineRegionInstallState?,
    isSuggested: Boolean,
    uiState: MapDataUiState,
    pixelRatio: Float,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showUpdateConfirm by remember { mutableStateOf(false) }
    val isDownloading = uiState is MapDataUiState.DownloadingOfflineMap &&
        uiState.regionId == region.id
    val isPreparing = (isDownloading || installState?.isDownloading == true) &&
        (installState?.requiredResourceCount ?: 0L) == 0L
    val downloadProgress = when {
        isDownloading -> (uiState as MapDataUiState.DownloadingOfflineMap).progress
        installState?.isDownloading == true -> installState.displayProgress
        else -> 0f
    }
    val completedBytes = installState?.completedResourceSize ?: 0L
    val resourceProgress = installState?.downloadProgress ?: 0f
    val needsDetailUpgrade = installState?.needsDetailUpgrade == true
    val isInstalledCurrent = installState?.isCurrentDetail == true
    val transferInProgress = uiState is MapDataUiState.Downloading ||
        uiState is MapDataUiState.DownloadingOfflineMap ||
        uiState is MapDataUiState.Importing

    if (showUpdateConfirm) {
        AlertDialog(
            onDismissRequest = { showUpdateConfirm = false },
            title = { CarBodyText(text = "Update ${region.name}?") },
            text = {
                CarBodyText(
                    text = "Replaces the installed z${installState?.installedMaxZoom ?: "?"} pack with " +
                        "street detail to zoom ${region.maxZoom.toInt()} (~${region.sizeEstimateMb} MB). " +
                        "The old map tiles will be removed first.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUpdateConfirm = false
                        onDownload()
                    },
                ) {
                    CarLabelText(text = "Update", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateConfirm = false }) {
                    CarLabelText(text = "Cancel", style = MaterialTheme.typography.labelLarge)
                }
            },
            containerColor = DeepCharcoal,
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { CarBodyText(text = "Delete ${region.name}?") },
            text = {
                CarBodyText(
                    text = "Removes cached map tiles for this region. Search data is not affected.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                ) {
                    CarLabelText(text = "Delete", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    CarLabelText(text = "Cancel", style = MaterialTheme.typography.labelLarge)
                }
            },
            containerColor = DeepCharcoal,
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = OledBlack,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CarDimensions.MinTapTarget)
                    .padding(horizontal = CarDimensions.PaneGap / 2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
            ) {
                if (isSuggested) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Near you",
                        tint = ElectricCyan,
                        modifier = Modifier.size(CarDimensions.PanelHeaderIconSize),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    CarBodyText(
                        text = if (isSuggested) "Near you — ${region.name}" else region.name,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    CarLabelText(
                        text = buildString {
                            if (isSuggested) append("Suggested from GPS · ")
                            if (needsDetailUpgrade) {
                                append("Installed z${installState?.installedMaxZoom ?: "?"} · ")
                                append("Update to z${region.maxZoom.toInt()} (~${region.sizeEstimateMb} MB)")
                            } else {
                                append("z${region.minZoom.toInt()}–${region.maxZoom.toInt()}")
                                append(" · est. ${region.sizeEstimateMb} MB")
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                PackRowActions(
                    isDownloading = isDownloading || installState?.isDownloading == true,
                    isPreparing = isPreparing,
                    downloadProgress = downloadProgress,
                    completedBytes = completedBytes,
                    sizeEstimateMb = region.sizeEstimateMb,
                    isInstalled = isInstalledCurrent,
                    needsDetailUpgrade = needsDetailUpgrade,
                    transferInProgress = transferInProgress,
                    itemName = region.name,
                    onDownload = onDownload,
                    onUpdate = { showUpdateConfirm = true },
                    onDelete = { showDeleteConfirm = true },
                    onCancelDownload = if (isDownloading || installState?.isDownloading == true) {
                        { showDeleteConfirm = true }
                    } else {
                        null
                    },
                    showDelete = isInstalledCurrent || needsDetailUpgrade ||
                        (installState?.completedResourceCount ?: 0L) > 0L,
                )
            }
            if (isDownloading || installState?.isDownloading == true) {
                OfflineDownloadStatusHints(
                    isPreparing = isPreparing,
                    completedResourceCount = installState?.completedResourceCount ?: 0L,
                    requiredResourceCount = installState?.requiredResourceCount ?: 0L,
                    completedResourceSize = installState?.completedResourceSize ?: 0L,
                    resourceProgress = resourceProgress,
                    sizeEstimateMb = region.sizeEstimateMb,
                    modifier = Modifier.padding(
                        start = CarDimensions.PaneGap / 2,
                        end = CarDimensions.PaneGap / 2,
                        bottom = CarDimensions.PaneGap / 2,
                    ),
                )
            }
        }
    }
}
