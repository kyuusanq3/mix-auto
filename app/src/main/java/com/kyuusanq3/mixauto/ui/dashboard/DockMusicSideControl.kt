package com.kyuusanq3.mixauto.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyuusanq3.mixauto.domain.media.MediaPlaybackState
import com.kyuusanq3.mixauto.ui.theme.CarDimensions

private val DOCK_MUSIC_SLOT_WIDTH = 280.dp
private const val DOCK_TITLE_VISUALIZER_ALPHA = 0.22f
private const val DOCK_TITLE_VISUALIZER_BAR_COUNT = 32

private val dockMusicIdleTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.titleLarge.copy(
        lineHeight = 20.sp,
        textAlign = TextAlign.End,
    )

private val dockMusicTitleTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.headlineLarge.copy(
        lineHeight = 24.sp,
        textAlign = TextAlign.End,
    )

private val dockMusicArtistTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyLarge.copy(
        lineHeight = 18.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
    )

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockMarqueeTextLine(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = style,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .fillMaxWidth()
            .basicMarquee(
                iterations = Int.MAX_VALUE,
                initialDelayMillis = 1_500,
                delayMillis = 2_000,
            ),
    )
}

@Composable
internal fun DockMusicSideControl(
    activePanel: ActivePanel,
    mediaState: MediaPlaybackState,
    isHorizontal: Boolean,
    isLeftHandDrive: Boolean,
    iconSize: Dp,
    onToggleMusicPane: () -> Unit,
    tapTarget: Dp = CarDimensions.DockHorizontalTapTarget,
    activeIndicatorPlacement: DockActiveIndicatorPlacement = DockActiveIndicatorPlacement.Bottom,
    edgePadding: Dp = 0.dp,
) {
    val isMusicPaneVisible =
        activePanel == ActivePanel.MEDIA || activePanel == ActivePanel.APP_DRAWER
    val edgeAlignment = when {
        isHorizontal && isLeftHandDrive -> Alignment.CenterEnd
        isHorizontal -> Alignment.CenterStart
        else -> Alignment.Center
    }
    val horizontalContentAlignment = if (isLeftHandDrive) Alignment.CenterEnd else Alignment.CenterStart
    val horizontalTextAlign = if (isLeftHandDrive) TextAlign.End else TextAlign.Start
    val horizontalColumnAlignment = if (isLeftHandDrive) Alignment.End else Alignment.Start

    Box(
        modifier = Modifier
            .then(
                if (isHorizontal) {
                    Modifier.width(DOCK_MUSIC_SLOT_WIDTH)
                } else {
                    Modifier.wrapContentSize(edgeAlignment, unbounded = true)
                },
            )
            .padding(
                start = if (!isHorizontal || !isLeftHandDrive) edgePadding else 0.dp,
                end = if (isHorizontal && isLeftHandDrive) edgePadding else 0.dp,
                bottom = if (!isHorizontal) edgePadding else 0.dp,
            ),
        contentAlignment = if (isHorizontal) horizontalContentAlignment else Alignment.Center,
    ) {
        if (isMusicPaneVisible) {
            Box(
                modifier = Modifier
                    .then(
                        if (isHorizontal) {
                            Modifier
                                .width(DOCK_MUSIC_SLOT_WIDTH)
                                .height(tapTarget)
                        } else {
                            Modifier.size(tapTarget)
                        },
                    )
                    .clickable(onClick = onToggleMusicPane),
                contentAlignment = if (isHorizontal) horizontalContentAlignment else Alignment.Center,
            ) {
                DockMiniVisualizer(
                    isPlaying = mediaState.isPlaying,
                    playbackPositionMs = mediaState.playbackPositionMs,
                    modifier = Modifier.size(iconSize),
                )
                if (!isHorizontal) {
                    DockActiveIndicator(isActive = true, activeIndicatorPlacement)
                }
            }
        } else if (isHorizontal) {
            Box(
                modifier = Modifier
                    .width(DOCK_MUSIC_SLOT_WIDTH)
                    .height(tapTarget)
                    .clickable(onClick = onToggleMusicPane)
                    .padding(horizontal = 4.dp),
                contentAlignment = horizontalContentAlignment,
            ) {
                DockMiniVisualizer(
                    isPlaying = mediaState.isPlaying,
                    playbackPositionMs = mediaState.playbackPositionMs,
                    modifier = Modifier.fillMaxSize(),
                    barAlpha = DOCK_TITLE_VISUALIZER_ALPHA,
                    barCount = DOCK_TITLE_VISUALIZER_BAR_COUNT,
                    wide = true,
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = horizontalColumnAlignment,
                ) {
                    if (!mediaState.hasActiveSession) {
                        DockMarqueeTextLine(
                            text = "No music is playing",
                            style = dockMusicIdleTextStyle.copy(textAlign = horizontalTextAlign),
                        )
                    } else {
                        DockMarqueeTextLine(
                            text = mediaState.title.ifBlank { "Unknown track" },
                            style = dockMusicTitleTextStyle.copy(textAlign = horizontalTextAlign),
                        )
                        if (mediaState.artist.isNotBlank()) {
                            DockMarqueeTextLine(
                                text = mediaState.artist,
                                style = dockMusicArtistTextStyle.copy(textAlign = horizontalTextAlign),
                            )
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(tapTarget)
                    .clickable(onClick = onToggleMusicPane),
                contentAlignment = Alignment.Center,
            ) {
                DockMiniVisualizer(
                    isPlaying = mediaState.isPlaying,
                    playbackPositionMs = mediaState.playbackPositionMs,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}
