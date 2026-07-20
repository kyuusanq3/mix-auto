package com.kyuusanq3.mixauto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import kotlin.math.roundToInt

@Composable
internal fun MapMediaDividerHandle(
    isVertical: Boolean,
    containerSizePx: Float,
    mapMediaRatio: Float,
    onMapMediaRatioChange: (Float) -> Unit,
    invertDrag: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val currentRatio by rememberUpdatedState(mapMediaRatio)
    val crossAxisModifier = if (isVertical) {
        Modifier.fillMaxHeight()
    } else {
        Modifier.fillMaxWidth()
    }

    Box(
        modifier = modifier
            .then(crossAxisModifier)
            .seamTouchTarget(isVertical = isVertical)
            .zIndex(1f)
            .pointerInput(containerSizePx, invertDrag, isVertical) {
                var dragStartRatio = mapMediaRatio
                var totalDragPx = 0f
                if (isVertical) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragStartRatio = currentRatio
                            totalDragPx = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (containerSizePx > 0f) {
                                totalDragPx += if (invertDrag) -dragAmount else dragAmount
                                val newRatio = (dragStartRatio + totalDragPx / containerSizePx)
                                    .coerceIn(0.3f, 0.8f)
                                onMapMediaRatioChange(newRatio)
                            }
                        },
                    )
                } else {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragStartRatio = currentRatio
                            totalDragPx = 0f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            if (containerSizePx > 0f) {
                                totalDragPx += dragAmount
                                val newRatio = (dragStartRatio + totalDragPx / containerSizePx)
                                    .coerceIn(0.3f, 0.8f)
                                onMapMediaRatioChange(newRatio)
                            }
                        },
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isVertical) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(ElectricCyan, CircleShape),
                    )
                }
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(ElectricCyan, CircleShape),
                    )
                }
            }
        }
    }
}

private fun Modifier.seamTouchTarget(
    isVertical: Boolean,
    layoutSeam: Dp = 0.dp,
    touchSize: Dp = CarDimensions.MinTapTarget,
): Modifier = layout { measurable, constraints ->
    val layoutPx = layoutSeam.roundToPx()
    val touchPx = touchSize.roundToPx()
    val placeable = measurable.measure(
        if (isVertical) {
            Constraints.fixed(
                width = touchPx,
                height = constraints.maxHeight,
            )
        } else {
            Constraints.fixed(
                width = constraints.maxWidth,
                height = touchPx,
            )
        },
    )
    if (isVertical) {
        layout(layoutPx, placeable.height) {
            placeable.place(-((touchPx - layoutPx) / 2f).roundToInt(), 0)
        }
    } else {
        layout(placeable.width, layoutPx) {
            placeable.place(0, -((touchPx - layoutPx) / 2f).roundToInt())
        }
    }
}
