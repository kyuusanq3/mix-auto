package com.kyuusanq3.mixauto.ui.dashboard

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/** Bottom-of-player meter: a zigzag that fills with track progress, or the spectrum bars. */
enum class AudioMeterStyle(val label: String) {
    PROGRESS_LINE("Sine progress line"),
    BARS("Visualizer bars"),
    ;

    companion object {
        fun fromPreference(name: String): AudioMeterStyle =
            entries.firstOrNull { it.name == name } ?: PROGRESS_LINE
    }
}

private const val WAVE_CYCLES = 7
private const val WAVE_SAMPLES = 168
private const val WAVE_AMPLITUDE_FRACTION = 0.16f

/**
 * Track progress drawn as a low sine wave. Played length is solid cyan; the rest stays dim.
 * The session sample is the anchor. While playing, the playhead keeps advancing from that
 * sample until the next one arrives. Do not re-anchor on every frame.
 */
@Composable
internal fun PlaybackProgressLine(
    isPlaying: Boolean,
    playbackPositionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 4.dp,
) {
    val positionState = rememberUpdatedState(playbackPositionMs)
    val playingState = rememberUpdatedState(isPlaying)
    var positionAnchorMs by remember { mutableLongStateOf(playbackPositionMs) }
    var realtimeAnchorMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var drawnPositionMs by remember { mutableLongStateOf(playbackPositionMs) }

    LaunchedEffect(playbackPositionMs, isPlaying) {
        positionAnchorMs = playbackPositionMs
        realtimeAnchorMs = SystemClock.elapsedRealtime()
        if (!isPlaying) {
            drawnPositionMs = playbackPositionMs
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis {
                val position = if (playingState.value) {
                    positionAnchorMs + (SystemClock.elapsedRealtime() - realtimeAnchorMs)
                } else {
                    positionState.value
                }
                if (position != drawnPositionMs) {
                    drawnPositionMs = position
                }
            }
        }
    }

    val progress = if (durationMs > 0L) {
        (drawnPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Canvas(modifier = modifier) {
        drawZigzagProgress(progress, strokeWidth.toPx())
    }
}

private fun DrawScope.drawZigzagProgress(progress: Float, strokeWidth: Float) {
    val amplitude = size.height * WAVE_AMPLITUDE_FRACTION
    val centerY = size.height * 0.5f
    val inset = strokeWidth
    val span = (size.width - inset * 2f).coerceAtLeast(1f)
    val points = ArrayList<Offset>(WAVE_SAMPLES + 1)
    for (index in 0..WAVE_SAMPLES) {
        val t = index / WAVE_SAMPLES.toFloat()
        points.add(
            Offset(
                x = inset + t * span,
                y = centerY + sineWave(t * WAVE_CYCLES) * amplitude,
            ),
        )
    }
    val split = progress * WAVE_SAMPLES
    val splitIndex = floor(split).toInt().coerceIn(0, WAVE_SAMPLES)
    val splitT = split - splitIndex
    val splitPoint = if (splitIndex >= WAVE_SAMPLES) {
        points[WAVE_SAMPLES]
    } else {
        lerp(points[splitIndex], points[splitIndex + 1], splitT)
    }
    val played = pathThrough(points, 0, splitIndex, splitPoint, includeEnd = progress > 0f)
    val remainingStart = if (progress <= 0f) points[0] else splitPoint
    val remaining = pathThrough(
        points = points,
        fromIndex = splitIndex + 1,
        toIndex = WAVE_SAMPLES,
        endPoint = remainingStart,
        includeEnd = progress < 1f,
        prepend = remainingStart,
    )
    val stroke = Stroke(
        width = strokeWidth,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )
    if (progress < 1f) {
        drawPath(
            path = remaining,
            color = ElectricCyan.copy(alpha = 0.28f),
            style = stroke,
        )
    }
    if (progress > 0f) {
        drawPath(
            path = played,
            color = ElectricCyan,
            style = stroke,
        )
        drawCircle(
            color = ElectricCyan,
            radius = strokeWidth * 1.35f,
            center = splitPoint,
        )
    }
}

private fun sineWave(phase: Float): Float {
    return sin(phase * 2.0 * PI).toFloat()
}

private fun lerp(start: Offset, end: Offset, fraction: Float): Offset {
    return Offset(
        x = start.x + (end.x - start.x) * fraction,
        y = start.y + (end.y - start.y) * fraction,
    )
}

private fun pathThrough(
    points: List<Offset>,
    fromIndex: Int,
    toIndex: Int,
    endPoint: Offset,
    includeEnd: Boolean,
    prepend: Offset? = null,
): Path {
    val path = Path()
    if (!includeEnd && prepend == null) return path
    var started = false
    if (prepend != null) {
        path.moveTo(prepend.x, prepend.y)
        started = true
    }
    val start = fromIndex.coerceAtLeast(0)
    val end = toIndex.coerceAtMost(points.lastIndex)
    if (prepend == null) {
        for (index in start..end) {
            val point = points[index]
            if (!started) {
                path.moveTo(point.x, point.y)
                started = true
            } else {
                path.lineTo(point.x, point.y)
            }
        }
        if (includeEnd && started) {
            path.lineTo(endPoint.x, endPoint.y)
        }
        return path
    }
    for (index in start..end) {
        val point = points[index]
        path.lineTo(point.x, point.y)
    }
    return path
}
