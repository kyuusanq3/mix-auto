package com.kyuusanq3.mixauto.ui.dashboard

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyuusanq3.mixauto.domain.media.MediaPlaybackState
import com.kyuusanq3.mixauto.ui.components.AudioPlayerListContent
import com.kyuusanq3.mixauto.ui.components.PanelHeaderIconButton
import com.kyuusanq3.mixauto.ui.components.canLaunchApp
import com.kyuusanq3.mixauto.ui.components.launchAppByPackage
import com.kyuusanq3.mixauto.ui.components.rememberAppIcon
import com.kyuusanq3.mixauto.ui.settings.LauncherViewModel
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarHeadlineText
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.DarkSurface
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import kotlin.math.abs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val AlbumArtSwipeThreshold = 40.dp

@Composable
fun MediaPlayerPane(
    mediaState: MediaPlaybackState,
    defaultAudioPackage: String,
    onSetDefaultAudioPackage: (String) -> Unit,
    albumArtMode: AlbumArtMode,
    onAlbumArtModeChange: (AlbumArtMode) -> Unit,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleLike: () -> Unit,
    reduceTopInset: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val launcherViewModel: LauncherViewModel = viewModel()
    val onOpenAudioSettings = { launcherViewModel.setActivePanel(ActivePanel.AUDIO_SETTINGS) }
    val needsDefaultSetup = defaultAudioPackage.isBlank() &&
        !mediaState.hasActiveSession &&
        !mediaState.needsNotificationAccess
    var isPickerOpen by remember { mutableStateOf(false) }
    var pickerIndex by remember { mutableIntStateOf(0) }
    var showGestureHint by remember { mutableStateOf(false) }
    val swipeThresholdPx = with(LocalDensity.current) { AlbumArtSwipeThreshold.toPx() }
    val onPlayPauseState = rememberUpdatedState(onPlayPause)
    val onSkipPreviousState = rememberUpdatedState(onSkipPrevious)
    val onSkipNextState = rememberUpdatedState(onSkipNext)
    val onToggleLikeState = rememberUpdatedState(onToggleLike)
    val supportsLikeState = rememberUpdatedState(mediaState.supportsLike)
    val hasActiveSessionState = rememberUpdatedState(mediaState.hasActiveSession)
    // pointerInput below is keyed on (hasActiveSession, supportsLike, swipeThresholdPx) only —
    // albumArtMode must go through rememberUpdatedState too, or a long-press after switching
    // modes reopens the picker pre-selecting the stale mode from when the gesture was last set up.
    val albumArtModeState = rememberUpdatedState(albumArtMode)

    ElevatedCard(
        modifier = modifier.padding(
            start = CarDimensions.PaneGap,
            end = CarDimensions.PaneGap,
            bottom = CarDimensions.PaneGap,
            top = if (reduceTopInset) {
                CarDimensions.StatusStripAdjacentGap
            } else {
                CarDimensions.PaneGap
            },
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = CarDimensions.CardElevation),
        colors = CardDefaults.elevatedCardColors(
            containerColor = DarkSurface,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
        if (needsDefaultSetup) {
            AudioPlayerListContent(
                defaultAudioPackage = defaultAudioPackage,
                headerTitle = null,
                headerMessage = "Choose the default audio source",
                showCloseButton = false,
                showLongPressHint = false,
                onDismiss = {},
                onAppClick = { app ->
                    onSetDefaultAudioPackage(app.packageName)
                    launchAppByPackage(context, app.packageName)
                },
                onRequestSetDefault = {},
                modifier = Modifier.fillMaxSize(),
            )
        } else {
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
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            val artSize = (if (maxWidth < maxHeight) maxWidth else maxHeight) * 0.92f
                            val gestureModifier = if (!isPickerOpen) {
                                Modifier.pointerInput(
                                    mediaState.hasActiveSession,
                                    mediaState.supportsLike,
                                    swipeThresholdPx,
                                ) {
                                    coroutineScope {
                                        launch {
                                            detectTapGestures(
                                                onDoubleTap = {
                                                    if (hasActiveSessionState.value) {
                                                        onPlayPauseState.value()
                                                    }
                                                },
                                                onLongPress = {
                                                    pickerIndex = albumArtModeState.value.ordinal
                                                    isPickerOpen = true
                                                },
                                            )
                                        }
                                        launch {
                                            awaitEachGesture {
                                                if (!hasActiveSessionState.value) return@awaitEachGesture
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                var drag = Offset.Zero
                                                var isDrag = false
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull { it.id == down.id }
                                                        ?: break
                                                    drag += change.positionChange()
                                                    if (!change.pressed) break
                                                    if (drag.getDistance() > swipeThresholdPx) {
                                                        isDrag = true
                                                    }
                                                    if (isDrag) {
                                                        change.consume()
                                                    }
                                                }
                                                if (!isDrag) return@awaitEachGesture
                                                val absX = abs(drag.x)
                                                val absY = abs(drag.y)
                                                when {
                                                    absY > absX && drag.y < 0f && supportsLikeState.value -> {
                                                        onToggleLikeState.value()
                                                    }
                                                    absX > absY && drag.x < 0f -> {
                                                        onSkipPreviousState.value()
                                                    }
                                                    absX > absY && drag.x > 0f -> {
                                                        onSkipNextState.value()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Modifier
                            }

                            if (isPickerOpen) {
                                AlbumArtModePicker(
                                    albumArt = mediaState.albumArt,
                                    isPlaying = mediaState.isPlaying,
                                    playbackPositionMs = mediaState.playbackPositionMs,
                                    selectedIndex = pickerIndex,
                                    onSelectedIndexChange = { pickerIndex = it },
                                    onConfirm = { mode ->
                                        onAlbumArtModeChange(mode)
                                        isPickerOpen = false
                                    },
                                    tileSize = artSize,
                                    modifier = Modifier
                                        .size(artSize)
                                        .fillMaxWidth(),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(artSize)
                                        .then(gestureModifier)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(12.dp),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AlbumArtModeContent(
                                        mode = albumArtMode,
                                        albumArt = mediaState.albumArt,
                                        isPlaying = mediaState.isPlaying,
                                        playbackPositionMs = mediaState.playbackPositionMs,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
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

            if (!mediaState.needsNotificationAccess && launcherViewModel.showAlbumArtControls) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CarDimensions.MinTapTarget),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SourceAppButton(
                        sourcePackage = mediaState.sourcePackage,
                        defaultAudioPackage = defaultAudioPackage,
                        hasActiveSession = mediaState.hasActiveSession,
                        onOpenPicker = onOpenAudioSettings,
                        modifier = Modifier.weight(1f),
                    )
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
                    MediaControlButton(
                        onClick = onToggleLike,
                        contentDescription = if (mediaState.isLiked == true) "Unlike" else "Like",
                        icon = if (mediaState.isLiked == true) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        enabled = mediaState.hasActiveSession && mediaState.supportsLike,
                        active = mediaState.isLiked == true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

            Row(modifier = Modifier.align(Alignment.TopEnd)) {
                if (!mediaState.needsNotificationAccess && !launcherViewModel.showAlbumArtControls) {
                    PanelHeaderIconButton(
                        onClick = { showGestureHint = true },
                        contentDescription = "Playback gesture help",
                        icon = Icons.Filled.Info,
                    )
                }
                PanelHeaderIconButton(
                    onClick = onOpenAudioSettings,
                    contentDescription = "Audio settings",
                    icon = Icons.Filled.MoreVert,
                )
            }

            if (showGestureHint) {
                GestureHintDialog(
                    supportsLike = mediaState.supportsLike,
                    onDismiss = { showGestureHint = false },
                )
            }
        }
        }
    }
}

@Composable
private fun GestureHintDialog(
    supportsLike: Boolean,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OledBlack,
        title = {
            CarHeadlineText(
                text = "Playback gestures",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CarBodyText(text = "Double-tap album art \u2192 Play / Pause", style = MaterialTheme.typography.bodyMedium)
                CarBodyText(text = "Swipe left \u2192 Previous track", style = MaterialTheme.typography.bodyMedium)
                CarBodyText(text = "Swipe right \u2192 Next track", style = MaterialTheme.typography.bodyMedium)
                if (supportsLike) {
                    CarBodyText(text = "Swipe up \u2192 Like", style = MaterialTheme.typography.bodyMedium)
                }
                CarBodyText(text = "Long-press \u2192 Change album art style", style = MaterialTheme.typography.bodyMedium)
                CarBodyText(
                    text = "Turn these back into on-screen buttons anytime from Audio Settings (\u22ee).",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                CarLabelText(text = "Got it", style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SourceAppButton(
    sourcePackage: String,
    defaultAudioPackage: String,
    hasActiveSession: Boolean,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val displayPackage = if (hasActiveSession) sourcePackage else defaultAudioPackage
    val appIcon = rememberAppIcon(displayPackage)
    val canLaunch = remember(displayPackage) { canLaunchApp(context, displayPackage) }
    val canLaunchSource = hasActiveSession && canLaunch
    val canOpenPicker = !hasActiveSession
    val showDefaultIcon = !hasActiveSession && defaultAudioPackage.isNotBlank() && appIcon != null
    val iconSize = CarDimensions.AppIconSize - 8.dp

    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .requiredSize(CarDimensions.MinTapTarget)
                .combinedClickable(
                    onClick = {
                        when {
                            canLaunchSource -> launchAppByPackage(context, sourcePackage)
                            canOpenPicker -> onOpenPicker()
                        }
                    },
                    onLongClick = onOpenPicker,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (appIcon != null && (canLaunchSource || showDefaultIcon)) {
                Image(
                    bitmap = appIcon,
                    contentDescription = "Open audio source",
                    modifier = Modifier.size(iconSize),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = if (canOpenPicker) "Choose audio player" else "Audio source",
                    modifier = Modifier.requiredSize(iconSize),
                    tint = when {
                        canOpenPicker -> ElectricCyan
                        canLaunchSource -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                )
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
    active: Boolean = false,
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
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    active -> ElectricCyan
                    else -> MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}
