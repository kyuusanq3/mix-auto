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
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.kyuusanq3.mixauto.data.map.formatOfflineMbProgressLabel
import com.kyuusanq3.mixauto.data.map.formatOfflineStorageMb
import com.kyuusanq3.mixauto.data.map.parseSizeEstimateUpperMb
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val InstalledGreen = Color(0xFF4CAF50)

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
