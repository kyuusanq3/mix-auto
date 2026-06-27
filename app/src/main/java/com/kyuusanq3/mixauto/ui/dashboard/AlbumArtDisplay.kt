package com.kyuusanq3.mixauto.ui.dashboard

import android.graphics.Bitmap
import android.os.SystemClock
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class AlbumArtMode {
    PLAIN,
    VINYL,
    VISUALIZER;

    companion object {
        fun fromPreference(name: String): AlbumArtMode =
            entries.firstOrNull { it.name == name } ?: PLAIN
    }
}

private const val VISUALIZER_BAR_COUNT = 24
private const val DOCK_MINI_VISUALIZER_BAR_COUNT = 6
private const val VISUALIZER_MAX_HEIGHT_FRACTION = 0.58f
private const val DOCK_MINI_MAX_HEIGHT_FRACTION = 0.72f
private const val VISUALIZER_MIN_BAR_FRACTION = 0.1f
private const val VISUALIZER_SMOOTHING_FACTOR = 0.1f
private const val VISUALIZER_IDLE_SMOOTHING_FACTOR = 0.16f
private const val VISUALIZER_BAR_WIDTH_FRACTION = 0.42f

private enum class VisualizerBarLayout {
    THIN,
    DOCK,
    DOCK_WIDE,
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
    playbackPositionMs: Long = 0L,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter,
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

        AnimatedSpectrumVisualizer(
            barCount = VISUALIZER_BAR_COUNT,
            isPlaying = isPlaying,
            playbackPositionMs = playbackPositionMs,
            maxHeightFraction = VISUALIZER_MAX_HEIGHT_FRACTION,
            barAlpha = 1f,
            barLayout = VisualizerBarLayout.THIN,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(),
        )
    }
}

@Composable
private fun AnimatedSpectrumVisualizer(
    barCount: Int,
    isPlaying: Boolean,
    playbackPositionMs: Long,
    maxHeightFraction: Float,
    barAlpha: Float,
    barLayout: VisualizerBarLayout,
    modifier: Modifier = Modifier,
) {
    val periods = remember(barCount) {
        FloatArray(barCount) { index -> 720f + index * 65f }
    }
    val phases = remember(barCount) {
        FloatArray(barCount) { index -> index * 0.9f }
    }
    val playbackPositionState = rememberUpdatedState(playbackPositionMs)
    val isPlayingState = rememberUpdatedState(isPlaying)
    var positionAnchorMs by remember { mutableLongStateOf(playbackPositionMs) }
    var realtimeAnchorMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var animatedPositionMs by remember { mutableLongStateOf(playbackPositionMs) }
    var frameTimeMs by remember { mutableLongStateOf(0L) }
    var smoothedHeights by remember(barCount) {
        mutableStateOf(FloatArray(barCount) { VISUALIZER_MIN_BAR_FRACTION })
    }

    SideEffect {
        positionAnchorMs = playbackPositionState.value
        realtimeAnchorMs = SystemClock.elapsedRealtime()
        if (!isPlaying) {
            animatedPositionMs = playbackPositionState.value
        }
    }

    LaunchedEffect(barCount) {
        while (true) {
            withFrameMillis { frameMs ->
                frameTimeMs = frameMs
                if (isPlayingState.value) {
                    animatedPositionMs = positionAnchorMs +
                        (SystemClock.elapsedRealtime() - realtimeAnchorMs)
                }
                val positionMs = if (isPlayingState.value && playbackPositionState.value > 0L) {
                    animatedPositionMs
                } else {
                    0L
                }
                val smoothing = if (isPlayingState.value) {
                    VISUALIZER_SMOOTHING_FACTOR
                } else {
                    VISUALIZER_IDLE_SMOOTHING_FACTOR
                }
                smoothedHeights = smoothedHeights.copyOf().also { heights ->
                    for (index in 0 until barCount) {
                        val target = if (isPlayingState.value) {
                            computeVisualizerBarFraction(
                                index = index,
                                barCount = barCount,
                                isPlaying = true,
                                playbackPositionMs = positionMs,
                                frameTimeMs = frameTimeMs,
                                periods = periods,
                                phases = phases,
                            )
                        } else {
                            VISUALIZER_MIN_BAR_FRACTION
                        }
                        heights[index] += (target - heights[index]) * smoothing
                    }
                }
            }
        }
    }

    Canvas(modifier = modifier) {
        drawSpectrumVisualizerBars(
            barCount = barCount,
            smoothedHeights = smoothedHeights,
            maxHeightFraction = maxHeightFraction,
            barAlpha = barAlpha,
            barLayout = barLayout,
        )
    }
}

private fun DrawScope.drawSpectrumVisualizerBars(
    barCount: Int,
    smoothedHeights: FloatArray,
    maxHeightFraction: Float,
    barAlpha: Float,
    barLayout: VisualizerBarLayout,
) {
    val maxBarHeight = size.height * maxHeightFraction
    val baseY = size.height

    when (barLayout) {
        VisualizerBarLayout.THIN -> {
            val slotWidth = size.width / barCount
            val barWidth = slotWidth * VISUALIZER_BAR_WIDTH_FRACTION
            val gap = slotWidth - barWidth
            for (index in 0 until barCount) {
                val normalized = smoothedHeights[index]
                val barHeight = (maxBarHeight * normalized).coerceAtLeast(barWidth * 0.12f)
                val x = index * slotWidth + gap / 2f
                drawRoundRect(
                    color = ElectricCyan.copy(alpha = barAlpha),
                    topLeft = Offset(x, baseY - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth * 0.45f, barWidth * 0.45f),
                )
            }
        }
        VisualizerBarLayout.DOCK -> {
            val barWidth = size.width / (barCount * 2f)
            val gap = barWidth * 0.45f
            for (index in 0 until barCount) {
                val normalized = smoothedHeights[index]
                val barHeight = maxBarHeight * normalized
                val x = gap + index * (barWidth + gap)
                drawRect(
                    color = ElectricCyan.copy(alpha = barAlpha),
                    topLeft = Offset(x, baseY - barHeight),
                    size = Size(barWidth, barHeight),
                )
            }
        }
        VisualizerBarLayout.DOCK_WIDE -> {
            val slotWidth = size.width / barCount
            val barWidth = slotWidth * VISUALIZER_BAR_WIDTH_FRACTION
            val gap = slotWidth - barWidth
            for (index in 0 until barCount) {
                val normalized = smoothedHeights[index]
                val barHeight = maxBarHeight * normalized
                val x = index * slotWidth + gap / 2f
                drawRect(
                    color = ElectricCyan.copy(alpha = barAlpha),
                    topLeft = Offset(x, baseY - barHeight),
                    size = Size(barWidth, barHeight),
                )
            }
        }
    }
}

private fun computeVisualizerBarFraction(
    index: Int,
    barCount: Int,
    isPlaying: Boolean,
    playbackPositionMs: Long,
    frameTimeMs: Long,
    periods: FloatArray,
    phases: FloatArray,
): Float {
    if (!isPlaying) return VISUALIZER_MIN_BAR_FRACTION

    val bandPos = index.toFloat() / (barCount - 1).coerceAtLeast(1)
    val barSeed = index * 1.618f + 0.37f

    val signal = if (playbackPositionMs > 0L) {
        computeBandSpectrumFraction(bandPos, barSeed, playbackPositionMs)
    } else {
        computeFallbackSpectrumFraction(
            bandPos = bandPos,
            barSeed = barSeed,
            frameTimeMs = frameTimeMs,
            periodMs = periods[index],
            phase = phases[index],
        )
    }

    val heightBias = 0.78f + 0.22f * (1f - bandPos * 0.5f)
    return (VISUALIZER_MIN_BAR_FRACTION + (1f - VISUALIZER_MIN_BAR_FRACTION) * signal * heightBias)
        .coerceIn(VISUALIZER_MIN_BAR_FRACTION, 1f)
}

private fun computeBandSpectrumFraction(
    bandPos: Float,
    barSeed: Float,
    positionMs: Long,
): Float {
    fun wave(periodMs: Float, cycles: Float = 1f, offset: Float = 0f): Float {
        val raw = sin((positionMs / periodMs * cycles + offset) * PI.toFloat() * 2f)
        return ((raw + 1f) * 0.5f)
    }

    fun kick(periodMs: Long): Float {
        val phase = (positionMs % periodMs).toFloat() / periodMs.toFloat()
        return sqrt((1f - phase).coerceIn(0f, 1f))
    }

    val subBass = kick(620L) * 0.88f + wave(1400f, offset = barSeed * 0.08f) * 0.16f
    val bass = kick(620L) * 0.45f + wave(1100f + barSeed * 18f, offset = barSeed * 0.22f) * 0.5f
    val lowMid = wave(900f, offset = barSeed * 0.41f) * 0.7f + kick(620L) * 0.22f
    val mid = wave(720f, offset = barSeed * 0.58f) * 0.78f
    val highMid = wave(520f, offset = barSeed * 0.74f) * 0.8f +
        wave(980f, offset = barSeed * 1.05f) * 0.18f
    val treble = wave(360f + barSeed * 8f, offset = barSeed * 1.35f) * 0.82f +
        wave(420f, offset = barSeed * 0.95f) * 0.24f

    val wSub = gaussianBandWeight(bandPos, center = 0.05f, spread = 0.11f)
    val wBass = gaussianBandWeight(bandPos, center = 0.2f, spread = 0.15f)
    val wLowMid = gaussianBandWeight(bandPos, center = 0.38f, spread = 0.17f)
    val wMid = gaussianBandWeight(bandPos, center = 0.54f, spread = 0.15f)
    val wHighMid = gaussianBandWeight(bandPos, center = 0.72f, spread = 0.14f)
    val wTreble = gaussianBandWeight(bandPos, center = 0.91f, spread = 0.13f)

    val weightSum = wSub + wBass + wLowMid + wMid + wHighMid + wTreble
    val blended = if (weightSum > 0f) {
        (subBass * wSub + bass * wBass + lowMid * wLowMid + mid * wMid +
            highMid * wHighMid + treble * wTreble) / weightSum
    } else {
        mid
    }

    return blended.coerceIn(0f, 1f)
}

private fun computeFallbackSpectrumFraction(
    bandPos: Float,
    barSeed: Float,
    frameTimeMs: Long,
    periodMs: Float,
    phase: Float,
): Float {
    val bassWeight = (1f - bandPos * 1.35f).coerceIn(0f, 1f)
    val trebleWeight = ((bandPos - 0.5f) * 2f).coerceIn(0f, 1f)
    val midWeight = (1f - bassWeight * 0.45f - trebleWeight * 0.45f).coerceAtLeast(0.25f)

    val bassAnim = ((sin(frameTimeMs / (920f + barSeed * 24f) + phase) + 1f) * 0.5f)
    val midAnim = ((sin(frameTimeMs / periodMs + barSeed) + 1f) * 0.5f)
    val trebleAnim = ((sin(frameTimeMs / (340f + barSeed * 12f) + barSeed * 2.4f) + 1f) * 0.5f)

    return (bassAnim * bassWeight + midAnim * midWeight + trebleAnim * trebleWeight)
        .coerceIn(0f, 1f)
}

private fun gaussianBandWeight(bandPos: Float, center: Float, spread: Float): Float {
    val distance = (bandPos - center) / spread
    return exp(-(distance * distance)).toFloat()
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

@Composable
fun DockMiniVisualizer(
    isPlaying: Boolean,
    playbackPositionMs: Long = 0L,
    modifier: Modifier = Modifier,
    barAlpha: Float = 1f,
    barCount: Int = DOCK_MINI_VISUALIZER_BAR_COUNT,
    wide: Boolean = false,
) {
    AnimatedSpectrumVisualizer(
        barCount = barCount,
        isPlaying = isPlaying,
        playbackPositionMs = playbackPositionMs,
        maxHeightFraction = DOCK_MINI_MAX_HEIGHT_FRACTION,
        barAlpha = barAlpha,
        barLayout = if (wide) VisualizerBarLayout.DOCK_WIDE else VisualizerBarLayout.DOCK,
        modifier = modifier,
    )
}
