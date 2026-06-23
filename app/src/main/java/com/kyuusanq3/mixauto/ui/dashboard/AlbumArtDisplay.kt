package com.kyuusanq3.mixauto.ui.dashboard

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.DeepCharcoal
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class AlbumArtMode {
    PLAIN,
    VINYL,
    VISUALIZER,
}

@Composable
fun AlbumArtModeContent(
    mode: AlbumArtMode,
    albumArt: Bitmap?,
    isPlaying: Boolean,
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
fun VinylAlbumArt(
    albumArt: Bitmap?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    var frozenRotation by remember { mutableFloatStateOf(0f) }
    val infiniteTransition = rememberInfiniteTransition(label = "vinylSpin")
    val animatedRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8_000, easing = LinearEasing),
        ),
        label = "vinylRotation",
    )

    if (isPlaying) {
        frozenRotation = animatedRotation
    }
    val rotation = if (isPlaying) animatedRotation else frozenRotation

    Box(
        modifier = modifier
            .clip(CircleShape)
            .graphicsLayer { rotationZ = rotation },
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                CarLabelText(
                    text = "Album Art",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = min(size.width, size.height) / 2f
            val outerRadius = maxRadius * 0.5f
            val innerRadius = maxRadius * 0.28f
            val groovePath = Path().apply {
                addOval(
                    Rect(
                        left = center.x - outerRadius,
                        top = center.y - outerRadius,
                        right = center.x + outerRadius,
                        bottom = center.y + outerRadius,
                    ),
                )
                addOval(
                    Rect(
                        left = center.x - innerRadius,
                        top = center.y - innerRadius,
                        right = center.x + innerRadius,
                        bottom = center.y + innerRadius,
                    ),
                )
                fillType = PathFillType.EvenOdd
            }
            drawPath(
                path = groovePath,
                color = Color.Black.copy(alpha = 0.88f),
                style = Fill,
            )
            drawCircle(
                color = DeepCharcoal,
                radius = maxRadius * 0.05f,
                center = center,
            )
        }
    }
}

@Composable
fun VisualizerAlbumArt(
    albumArt: Bitmap?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val barCount = 16
    val periods = remember {
        FloatArray(barCount) { index -> 280f + index * 37f }
    }
    val phases = remember {
        FloatArray(barCount) { index -> index * 0.9f }
    }
    var timeMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            withFrameMillis { frameMs ->
                timeMs = frameMs
            }
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (albumArt != null) {
            Image(
                bitmap = albumArt.asImageBitmap(),
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                alpha = 0.35f,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                CarLabelText(
                    text = "Album Art",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val barWidth = size.width / (barCount * 2f)
            val gap = barWidth * 0.45f
            val maxBarHeight = size.height * 0.72f
            val baseY = size.height * 0.88f

            for (index in 0 until barCount) {
                val normalized = if (isPlaying) {
                    0.15f + 0.6f * abs(
                        sin(timeMs / periods[index] + phases[index]).toFloat(),
                    )
                } else {
                    0.15f
                }
                val barHeight = maxBarHeight * normalized
                val x = gap + index * (barWidth + gap)
                drawRect(
                    color = ElectricCyan,
                    topLeft = Offset(x, baseY - barHeight),
                    size = Size(barWidth, barHeight),
                )
            }
        }
    }
}

@Composable
fun AlbumArtModePicker(
    albumArt: Bitmap?,
    isPlaying: Boolean,
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
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
