package com.kyuusanq3.mixauto.ui.dashboard

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import kotlin.math.abs
import kotlin.math.roundToInt

enum class AlbumArtMode {
    PLAIN,
    VINYL,
    VISUALIZER;

    companion object {
        fun fromPreference(name: String): AlbumArtMode =
            entries.firstOrNull { it.name == name } ?: PLAIN
    }
}

@Composable
fun AlbumArtModeContent(
    mode: AlbumArtMode,
    albumArt: Bitmap?,
    isPlaying: Boolean,
    playbackPositionMs: Long = 0L,
    modifier: Modifier = Modifier,
) {
    when (mode) {
        AlbumArtMode.PLAIN -> PlainAlbumArt(
            albumArt = albumArt,
            modifier = modifier,
        )
        AlbumArtMode.VINYL -> VinylAlbumArt(
            albumArt = albumArt,
            isPlaying = isPlaying,
            modifier = modifier,
        )
        AlbumArtMode.VISUALIZER -> VisualizerAlbumArt(
            albumArt = albumArt,
            isPlaying = isPlaying,
            playbackPositionMs = playbackPositionMs,
            modifier = modifier,
        )
    }
}

@Composable
fun PlainAlbumArt(
    albumArt: Bitmap?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (albumArt != null) {
            Image(
                bitmap = albumArt.asImageBitmap(),
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CarLabelText(
                text = "Album Art",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
fun AlbumArtModePicker(
    albumArt: Bitmap?,
    isPlaying: Boolean,
    playbackPositionMs: Long = 0L,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    onConfirm: (AlbumArtMode) -> Unit,
    tileSize: Dp,
    modifier: Modifier = Modifier,
) {
    val modes = AlbumArtMode.entries
    val spacing = tileSize * 0.68f
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val spacingPx = with(density) { spacing.toPx() }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(selectedIndex) {
                detectTapGestures(
                    onTap = { onConfirm(modes[selectedIndex]) },
                    onLongPress = { onConfirm(modes[selectedIndex]) },
                )
            }
            .pointerInput(selectedIndex) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        dragOffset += dragAmount
                    },
                    onDragEnd = {
                        val threshold = spacingPx * 0.2f
                        when {
                            dragOffset < -threshold -> {
                                onSelectedIndexChange((selectedIndex + 1) % modes.size)
                            }
                            dragOffset > threshold -> {
                                onSelectedIndexChange((selectedIndex + modes.size - 1) % modes.size)
                            }
                        }
                        dragOffset = 0f
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        modes.forEachIndexed { index, mode ->
            val relative = index - selectedIndex
            val xPx = relative * spacingPx + dragOffset
            val distance = abs(xPx / spacingPx)
            val scale = (0.75f - distance * 0.12f).coerceIn(0.55f, 0.75f)
            val alpha = (1f - distance * 0.35f).coerceIn(0.45f, 1f)
            val animatePlayback = isPlaying && index == selectedIndex

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(xPx.roundToInt(), 0) }
                    .size(tileSize)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AlbumArtModeContent(
                    mode = mode,
                    albumArt = albumArt,
                    isPlaying = animatePlayback,
                    playbackPositionMs = if (animatePlayback) playbackPositionMs else 0L,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
