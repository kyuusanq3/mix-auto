package com.kyuusanq3.mixauto.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarHeadlineText
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.OledBlack

@Composable
fun AudioPlayerPickerOverlay(
    defaultAudioPackage: String,
    onSetDefaultAudioPackage: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pendingDefaultApp by remember { mutableStateOf<AudioPlayerApp?>(null) }

    Box(modifier = modifier) {
        AudioPlayerListContent(
            defaultAudioPackage = defaultAudioPackage,
            headerTitle = "Audio Players",
            headerMessage = null,
            showCloseButton = true,
            showLongPressHint = true,
            onDismiss = onDismiss,
            onAppClick = { app ->
                launchAppByPackage(context, app.packageName)
                onDismiss()
            },
            onRequestSetDefault = { app -> pendingDefaultApp = app },
        )

        pendingDefaultApp?.let { app ->
            SetDefaultAudioConfirmDialog(
                app = app,
                onConfirm = {
                    onSetDefaultAudioPackage(app.packageName)
                    pendingDefaultApp = null
                },
                onDismiss = { pendingDefaultApp = null },
            )
        }
    }
}

@Composable
fun AudioPlayerListContent(
    defaultAudioPackage: String,
    headerTitle: String?,
    headerMessage: String?,
    showCloseButton: Boolean,
    showLongPressHint: Boolean,
    onDismiss: () -> Unit,
    onAppClick: (AudioPlayerApp) -> Unit,
    onRequestSetDefault: (AudioPlayerApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val audioApps = remember(context) { loadAudioPlayerApps(context) }
    val listState = rememberLazyListState()

    Surface(
        modifier = modifier,
        color = OledBlack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(CarDimensions.PaneGap),
        ) {
            if (headerTitle != null || showCloseButton) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (headerTitle != null) {
                        CarHeadlineText(
                            text = headerTitle,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f))
                    }
                    if (showCloseButton) {
                        OverlayCloseButton(
                            onClick = onDismiss,
                            contentDescription = "Close audio player list",
                        )
                    }
                }
            }

            if (!headerMessage.isNullOrBlank()) {
                CarBodyText(
                    text = headerMessage,
                    modifier = Modifier.padding(top = CarDimensions.PaneGap),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                )
            }

            if (audioApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CarBodyText(
                        text = "No music apps found on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .carLazyScrollbar(listState),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = audioApps,
                            key = { it.packageName },
                        ) { app ->
                            AudioPlayerRow(
                                app = app,
                                isDefault = app.packageName == defaultAudioPackage,
                                onClick = { onAppClick(app) },
                                onLongClick = { onRequestSetDefault(app) },
                            )
                        }
                    }
                }
            }

            if (showLongPressHint && audioApps.isNotEmpty()) {
                CarBodyText(
                    text = "Long press to set as default",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = CarDimensions.PaneGap),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SetDefaultAudioConfirmDialog(
    app: AudioPlayerApp,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OledBlack,
        title = {
            CarHeadlineText(
                text = "Set as default?",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            CarBodyText(
                text = "Use ${app.label} as the default audio source when nothing is playing.",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                CarLabelText(
                    text = "Set default",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                CarLabelText(
                    text = "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioPlayerRow(
    app: AudioPlayerApp,
    isDefault: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (isDefault) "${app.label} (Default)" else app.label
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CarDimensions.MinTapTarget)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = CarDimensions.PaneGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = Modifier.size(CarDimensions.AppIconSize),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = app.label,
                modifier = Modifier.size(CarDimensions.AppIconSize),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        CarLabelText(
            text = label,
            modifier = Modifier.weight(1f),
        )
    }
}
