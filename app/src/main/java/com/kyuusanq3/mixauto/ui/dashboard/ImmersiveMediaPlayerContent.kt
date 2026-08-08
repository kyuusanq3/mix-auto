package com.kyuusanq3.mixauto.ui.dashboard

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyuusanq3.mixauto.domain.media.MediaPlaybackState
import com.kyuusanq3.mixauto.ui.components.AudioPlayerListContent
import com.kyuusanq3.mixauto.ui.components.PanelHeaderIconButton
import com.kyuusanq3.mixauto.ui.components.launchAppByPackage
import com.kyuusanq3.mixauto.ui.settings.LauncherViewModel
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarHeadlineText
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.DarkSurface
import kotlin.math.abs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val ImmersiveSwipeThreshold = 40.dp
private val ImmersiveVisualizerHeight = 112.dp
private const val IMMERSIVE_VISUALIZER_BAR_COUNT = 24

/**
 * Immersive now-playing layout: opaque album-art background, title/artist at top,
 * thin bar visualizer at bottom. Long-press opens Audio Settings (not art-mode picker).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImmersiveMediaPlayerContent(
    mediaState: MediaPlaybackState,
    defaultAudioPackage: String,
    onSetDefaultAudioPackage: (String) -> Unit,
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
    val swipeThresholdPx = with(LocalDensity.current) { ImmersiveSwipeThreshold.toPx() }
    val onPlayPauseState = rememberUpdatedState(onPlayPause)
    val onSkipPreviousState = rememberUpdatedState(onSkipPrevious)
    val onSkipNextState = rememberUpdatedState(onSkipNext)
    val onToggleLikeState = rememberUpdatedState(onToggleLike)
    val supportsLikeState = rememberUpdatedState(mediaState.supportsLike)
    val hasActiveSessionState = rememberUpdatedState(mediaState.hasActiveSession)
    val onOpenAudioSettingsState = rememberUpdatedState(onOpenAudioSettings)
    val fallbackResumeLink = launcherViewModel.audioFallbackResumeLink.takeIf { it.isNotBlank() }
    val showIdleLinkFallback = !mediaState.hasActiveSession && fallbackResumeLink != null
    val showIdleDefaultPlayerFallback = !mediaState.hasActiveSession &&
        fallbackResumeLink == null &&
        defaultAudioPackage.isNotBlank()

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
            } else if (mediaState.needsNotificationAccess) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(CarDimensions.PaneGap),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
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
                }
            } else {
                val gestureModifier = Modifier.pointerInput(
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
                                onLongPress = { onOpenAudioSettingsState.value() },
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
                val artInteractionModifier = when {
                    showIdleLinkFallback -> Modifier.combinedClickable(
                        onClick = {
                            launchManualFallbackLink(
                                context = context,
                                resumeLink = fallbackResumeLink.orEmpty(),
                                preferredPackage = defaultAudioPackage.takeIf { it.isNotBlank() },
                            )
                        },
                        onLongClick = onOpenAudioSettings,
                    )
                    showIdleDefaultPlayerFallback -> Modifier.combinedClickable(
                        onClick = { launchAppByPackage(context, defaultAudioPackage) },
                        onLongClick = onOpenAudioSettings,
                    )
                    else -> gestureModifier
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(artInteractionModifier),
                ) {
                    if (mediaState.albumArt != null) {
                        Image(
                            bitmap = mediaState.albumArt.asImageBitmap(),
                            contentDescription = "Album art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.55f),
                                        Color.Black.copy(alpha = 0.25f),
                                        Color.Black.copy(alpha = 0.55f),
                                    ),
                                ),
                            ),
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(CarDimensions.PaneGap),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = CarDimensions.PanelHeaderTapTarget),
                        ) {
                            when {
                                mediaState.hasActiveSession -> {
                                    CarHeadlineText(
                                        text = mediaState.displayTitle,
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
                                showIdleLinkFallback -> {
                                    IdleManualLinkArt(modifier = Modifier.fillMaxWidth())
                                }
                                showIdleDefaultPlayerFallback -> {
                                    CarBodyText(
                                        text = "Start music on the player manually",
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 2,
                                    )
                                }
                                else -> {
                                    CarHeadlineText(
                                        text = mediaState.displayTitle,
                                        style = MaterialTheme.typography.headlineMedium,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        AnimatedSpectrumVisualizer(
                            barCount = IMMERSIVE_VISUALIZER_BAR_COUNT,
                            isPlaying = mediaState.isPlaying,
                            playbackPositionMs = mediaState.playbackPositionMs,
                            maxHeightFraction = 0.92f,
                            barAlpha = 1f,
                            barLayout = VisualizerBarLayout.THIN,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ImmersiveVisualizerHeight),
                        )

                        if (launcherViewModel.showAlbumArtControls) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = CarDimensions.PaneGap / 2)
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
                                    icon = if (mediaState.isLiked == true) {
                                        Icons.Filled.Favorite
                                    } else {
                                        Icons.Outlined.FavoriteBorder
                                    },
                                    enabled = mediaState.hasActiveSession && mediaState.supportsLike,
                                    active = mediaState.isLiked == true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            if (!needsDefaultSetup) {
                PanelHeaderIconButton(
                    onClick = onOpenAudioSettings,
                    contentDescription = "Audio settings",
                    icon = Icons.Filled.MoreVert,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
    }
}