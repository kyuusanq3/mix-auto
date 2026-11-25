package com.kyuusanq3.mixauto.ui.dashboard

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Single-pointer album-art gestures. Do not pair [androidx.compose.foundation.gestures.detectTapGestures]
 * with a second [awaitEachGesture] on the same [PointerInputScope] — the drag loop's
 * [awaitFirstDown] consumes the second tap and turns a double-tap into open-app.
 */
internal suspend fun PointerInputScope.detectPlaybackArtGestures(
    swipeThresholdPx: Float,
    hasActiveSession: () -> Boolean,
    supportsLike: () -> Boolean,
    onOpenApp: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleLike: () -> Unit,
    onLongPress: (Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown()
        when (val first = awaitTapDragOrLongPress(down.id, swipeThresholdPx)) {
            is PointerFate.Drag -> dispatchSwipe(
                total = first.total,
                hasActiveSession = hasActiveSession,
                supportsLike = supportsLike,
                onSkipPrevious = onSkipPrevious,
                onSkipNext = onSkipNext,
                onToggleLike = onToggleLike,
            )
            PointerFate.LongPress -> {
                onLongPress(down.position)
                waitForUpOrCancellation()
            }
            PointerFate.Cancel -> Unit
            is PointerFate.Tap -> {
                val minSecondDownUptime = first.upUptimeMillis + viewConfiguration.doubleTapMinTimeMillis
                val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis.toLong()) {
                    var next = awaitFirstDown()
                    while (next.uptimeMillis < minSecondDownUptime) {
                        next = awaitFirstDown()
                    }
                    next
                }
                if (secondDown == null) {
                    if (hasActiveSession()) onOpenApp()
                    return@awaitEachGesture
                }
                when (val second = awaitTapDragOrLongPress(secondDown.id, swipeThresholdPx)) {
                    is PointerFate.Drag -> dispatchSwipe(
                        total = second.total,
                        hasActiveSession = hasActiveSession,
                        supportsLike = supportsLike,
                        onSkipPrevious = onSkipPrevious,
                        onSkipNext = onSkipNext,
                        onToggleLike = onToggleLike,
                    )
                    is PointerFate.Tap -> {
                        if (hasActiveSession()) onPlayPause()
                    }
                    PointerFate.LongPress -> {
                        onLongPress(secondDown.position)
                        waitForUpOrCancellation()
                    }
                    PointerFate.Cancel -> Unit
                }
            }
        }
    }
}

private sealed class PointerFate {
    data class Tap(val upUptimeMillis: Long) : PointerFate()
    data class Drag(val total: Offset) : PointerFate()
    data object LongPress : PointerFate()
    data object Cancel : PointerFate()
}

private suspend fun AwaitPointerEventScope.awaitTapDragOrLongPress(
    pointerId: PointerId,
    swipeThresholdPx: Float,
): PointerFate {
    var drag = Offset.Zero
    var stillDown = true
    val firstPhase = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis.toLong()) {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: return@withTimeoutOrNull PointerFate.Cancel
            drag += change.positionChange()
            stillDown = change.pressed
            if (drag.getDistance() > swipeThresholdPx) {
                change.consume()
                return@withTimeoutOrNull PointerFate.Drag(drag)
            }
            if (!change.pressed) {
                return@withTimeoutOrNull PointerFate.Tap(change.uptimeMillis)
            }
        }
        @Suppress("UNREACHABLE_CODE")
        PointerFate.Cancel
    }
    return when (val fate = firstPhase) {
        is PointerFate.Drag -> {
            while (stillDown) {
                val dragEvent = awaitPointerEvent()
                val dragChange = dragEvent.changes.firstOrNull { it.id == pointerId } ?: break
                drag += dragChange.positionChange()
                dragChange.consume()
                stillDown = dragChange.pressed
            }
            PointerFate.Drag(drag)
        }
        null -> PointerFate.LongPress
        else -> fate
    }
}

private fun dispatchSwipe(
    total: Offset,
    hasActiveSession: () -> Boolean,
    supportsLike: () -> Boolean,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleLike: () -> Unit,
) {
    if (!hasActiveSession()) return
    val absX = abs(total.x)
    val absY = abs(total.y)
    when {
        absY > absX && total.y < 0f && supportsLike() -> onToggleLike()
        absX > absY && total.x < 0f -> onSkipPrevious()
        absX > absY && total.x > 0f -> onSkipNext()
    }
}
