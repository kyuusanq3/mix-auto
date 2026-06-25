package com.kyuusanq3.mixauto.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    onDismiss: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CarHeadlineText(
                    text = "Audio Players",
                    modifier = Modifier.weight(1f),
                )
                OverlayCloseButton(
                    onClick = onDismiss,
                    contentDescription = "Close audio player list",
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
                                onClick = {
                                    launchAppByPackage(context, app.packageName)
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioPlayerRow(
    app: AudioPlayerApp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CarDimensions.MinTapTarget)
            .clickable(onClick = onClick)
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
            text = app.label,
            modifier = Modifier.weight(1f),
        )
    }
}
