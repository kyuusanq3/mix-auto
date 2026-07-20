package com.kyuusanq3.mixauto.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kyuusanq3.mixauto.data.map.OfflineRegionDefinition
import com.kyuusanq3.mixauto.data.map.OfflineRegionInstallState
import com.kyuusanq3.mixauto.data.map.formatOfflineMbProgressLabel
import com.kyuusanq3.mixauto.data.map.formatOfflineStorageMb
import com.kyuusanq3.mixauto.data.map.parseSizeEstimateUpperMb
import com.kyuusanq3.mixauto.ui.settings.MapDataUiState
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.DeepCharcoal
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val InstalledGreen = Color(0xFF4CAF50)

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

@Composable
internal fun OfflineDownloadStatusHints(
    isPreparing: Boolean,
    completedResourceCount: Long,
    requiredResourceCount: Long,
    completedResourceSize: Long = 0L,
    resourceProgress: Float = 0f,
    sizeEstimateMb: String? = null,
    modifier: Modifier = Modifier,
) {
    var showPreparingHint by remember { mutableStateOf(false) }
    var showStallHint by remember { mutableStateOf(false) }
    var showSlowResourceHint by remember { mutableStateOf(false) }

    LaunchedEffect(isPreparing) {
        showPreparingHint = false
        if (isPreparing) {
            delay(30_000)
            showPreparingHint = true
        }
    }

    LaunchedEffect(isPreparing, completedResourceCount, requiredResourceCount, completedResourceSize) {
        showStallHint = false
        if (isPreparing || requiredResourceCount == 0L) return@LaunchedEffect
        val countSnapshot = completedResourceCount
        val bytesSnapshot = completedResourceSize
        delay(2 * 60 * 1000L)
        if (!isPreparing && requiredResourceCount > 0L &&
            completedResourceCount == countSnapshot &&
            completedResourceSize == bytesSnapshot
        ) {
            showStallHint = true
        }
    }

    LaunchedEffect(isPreparing, resourceProgress, completedResourceSize) {
        showSlowResourceHint = false
        if (isPreparing || completedResourceSize <= 0L) return@LaunchedEffect
        if (resourceProgress < 0.10f) {
            showSlowResourceHint = true
        }
    }

    Column(modifier = modifier) {
        if (!isPreparing && requiredResourceCount > 0L) {
            CarLabelText(
                text = formatOfflineDownloadDetail(
                    completedResourceCount,
                    requiredResourceCount,
                    completedResourceSize,
                    sizeEstimateMb,
                ),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (showPreparingHint && isPreparing) {
            CarLabelText(
                text = "Building tile list from OpenFreeMap — can take 1–2 minutes on first download.",
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (showSlowResourceHint && !isPreparing && !showStallHint) {
            CarLabelText(
                text = "Downloading map tiles — progress may look slow at first.",
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (showStallHint) {
            CarLabelText(
                text = "Stuck? Tap ✕ to cancel, delete the region, and retry on Wi‑Fi.",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
internal fun PackRowActions(
    isDownloading: Boolean,
    isPreparing: Boolean = false,
    downloadProgress: Float,
    completedBytes: Long = 0L,
    sizeEstimateMb: String? = null,
    isInstalled: Boolean,
    needsDetailUpgrade: Boolean = false,
    transferInProgress: Boolean,
    itemName: String,
    onDownload: (() -> Unit)?,
    onUpdate: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onCancelDownload: (() -> Unit)? = null,
    showDelete: Boolean = isInstalled,
) {
    when {
        isDownloading -> {
            CircularProgressIndicator(
                modifier = Modifier.size(CarDimensions.DockHorizontalIconSize),
                color = ElectricCyan,
                strokeWidth = 3.dp,
            )
            CarLabelText(
                text = when {
                    isPreparing -> "Preparing…"
                    else -> {
                        formatOfflineMbProgressLabel(completedBytes, sizeEstimateMb)
                            ?: "${(downloadProgress * 100).roundToInt()}%"
                    }
                },
                style = MaterialTheme.typography.labelMedium,
            )
            if (onCancelDownload != null) {
                IconButton(
                    onClick = onCancelDownload,
                    modifier = Modifier.size(CarDimensions.MinTapTarget),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cancel download",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        isInstalled -> {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Installed",
                tint = InstalledGreen,
                modifier = Modifier.size(CarDimensions.DockHorizontalIconSize),
            )
            CarLabelText(
                text = "Installed",
                style = MaterialTheme.typography.labelMedium.copy(color = InstalledGreen),
            )
            if (showDelete) {
                IconButton(
                    onClick = onDelete,
                    enabled = !transferInProgress,
                    modifier = Modifier.size(CarDimensions.MinTapTarget),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete $itemName",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        needsDetailUpgrade -> {
            IconButton(
                onClick = { onUpdate?.invoke() },
                enabled = !transferInProgress && onUpdate != null,
                modifier = Modifier.size(CarDimensions.MinTapTarget),
            ) {
                Icon(
                    imageVector = Icons.Filled.SystemUpdate,
                    contentDescription = "Update $itemName",
                    tint = ElectricCyan,
                )
            }
            CarLabelText(
                text = "Update",
                style = MaterialTheme.typography.labelMedium.copy(color = ElectricCyan),
            )
            if (showDelete) {
                IconButton(
                    onClick = onDelete,
                    enabled = !transferInProgress,
                    modifier = Modifier.size(CarDimensions.MinTapTarget),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete $itemName",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        else -> {
            IconButton(
                onClick = { onDownload?.invoke() },
                enabled = !transferInProgress && onDownload != null,
                modifier = Modifier.size(CarDimensions.MinTapTarget),
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudDownload,
                    contentDescription = "Download $itemName",
                    tint = ElectricCyan,
                )
            }
        }
    }
}

@Composable
internal fun ActionRow(
    text: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CarDimensions.MinTapTarget)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = CarDimensions.PaneGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) ElectricCyan else ElectricCyan.copy(alpha = 0.4f),
        )
        CarBodyText(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = if (enabled) ElectricCyan else ElectricCyan.copy(alpha = 0.4f),
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

private fun formatOfflineDownloadDetail(
    completedCount: Long,
    requiredCount: Long,
    completedBytes: Long,
    sizeEstimateMb: String? = null,
): String = buildString {
    append(completedCount)
    append(" / ")
    append(requiredCount)
    append(" resources")
    if (completedBytes > 0L) {
        append(" · ~")
        append(formatOfflineStorageMb(completedBytes))
        sizeEstimateMb?.let { estimate ->
            parseSizeEstimateUpperMb(estimate)?.let { upperMb ->
                append(" of ~")
                append(upperMb)
                append(" MB est.")
            }
        }
    }
}
