package com.kyuusanq3.mixauto.ui.dashboard

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kyuusanq3.mixauto.domain.media.MediaPlaybackState
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarHeadlineText
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.DarkSurface

@Composable
fun MediaPlayerPane(
    mediaState: MediaPlaybackState,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    ElevatedCard(
        modifier = modifier.padding(CarDimensions.PaneGap),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = CarDimensions.CardElevation),
        colors = CardDefaults.elevatedCardColors(
            containerColor = DarkSurface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(CarDimensions.PaneGap),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (mediaState.needsNotificationAccess) {
                    CarBodyText(
                        text = "Enable notification access to show now playing from YouTube Music and other apps.",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                    )
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                },
                            )
                        },
                        modifier = Modifier
                            .padding(top = CarDimensions.PaneGap)
                            .height(CarDimensions.MinTapTarget),
                    ) {
                        CarLabelText(
                            text = "Open Access Settings",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(CarDimensions.PrimaryTapTarget)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (mediaState.albumArt != null) {
                            Image(
                                bitmap = mediaState.albumArt.asImageBitmap(),
                                contentDescription = "Album art",
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            CarLabelText(
                                text = "Album Art",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }

                    CarHeadlineText(
                        text = mediaState.displayTitle,
                        modifier = Modifier.padding(top = CarDimensions.PaneGap),
                        style = MaterialTheme.typography.headlineMedium,
                    )

                    if (mediaState.displayArtist.isNotBlank()) {
                        CarBodyText(
                            text = mediaState.displayArtist,
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                        )
                    }
                }
            }

            if (!mediaState.needsNotificationAccess) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CarDimensions.MinTapTarget),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MediaControlButton(
                        onClick = onSkipPrevious,
                        contentDescription = "Previous",
                        icon = Icons.Filled.SkipPrevious,
                        enabled = mediaState.hasActiveSession,
                        modifier = Modifier.weight(1f),
                    )
                    MediaControlButton(
                        onClick = onPlayPause,
                        contentDescription = if (mediaState.isPlaying) "Pause" else "Play",
                        icon = if (mediaState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        emphasized = true,
                        enabled = mediaState.hasActiveSession,
                        modifier = Modifier.weight(1f),
                    )
                    MediaControlButton(
                        onClick = onSkipNext,
                        contentDescription = "Next",
                        icon = Icons.Filled.SkipNext,
                        enabled = mediaState.hasActiveSession,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaControlButton(
    onClick: () -> Unit,
    contentDescription: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .requiredSize(CarDimensions.MinTapTarget)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.requiredSize(
                    if (emphasized) CarDimensions.AppIconSize else CarDimensions.AppIconSize - 8.dp,
                ),
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
        }
    }
}
