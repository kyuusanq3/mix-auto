package com.kyuusanq3.mixauto.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan

@Composable
fun Modifier.carScrollbar(state: ScrollState): Modifier {
    val thumbColor = ElectricCyan.copy(alpha = 0.6f)
    val thumbWidthPx = with(LocalDensity.current) { 4.dp.toPx() }
    val thumbMinH = with(LocalDensity.current) { 48.dp.toPx() }
    val insetPx = with(LocalDensity.current) { 2.dp.toPx() }
    return drawWithContent {
        drawContent()
        val range = state.maxValue.toFloat()
        if (range > 0f) {
            val vH = size.height
            val thumbH = (vH * vH / (vH + range)).coerceAtLeast(thumbMinH)
            val thumbY = (state.value / range) * (vH - thumbH)
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(size.width - thumbWidthPx - insetPx, thumbY),
                size = Size(thumbWidthPx, thumbH),
                cornerRadius = CornerRadius(thumbWidthPx / 2),
            )
        }
    }
}

@Composable
fun Modifier.carLazyScrollbar(state: LazyListState): Modifier {
    val thumbColor = ElectricCyan.copy(alpha = 0.6f)
    val thumbWidthPx = with(LocalDensity.current) { 4.dp.toPx() }
    val thumbMinH = with(LocalDensity.current) { 48.dp.toPx() }
    val insetPx = with(LocalDensity.current) { 2.dp.toPx() }
    return drawWithContent {
        drawContent()
        val layoutInfo = state.layoutInfo
        val totalItems = layoutInfo.totalItemsCount
        if (totalItems == 0) return@drawWithContent

        val viewportHeight = layoutInfo.viewportSize.height.toFloat()
        if (viewportHeight <= 0f) return@drawWithContent

        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return@drawWithContent

        val canScroll = state.canScrollForward || state.canScrollBackward
        if (!canScroll && visibleItems.size >= totalItems) return@drawWithContent

        val averageItemSize = visibleItems.sumOf { it.size } / visibleItems.size.toFloat()
        val totalContentHeight = averageItemSize * totalItems
        if (totalContentHeight <= viewportHeight) return@drawWithContent

        val scrollRange = totalContentHeight - viewportHeight
        val scrollOffset = state.firstVisibleItemIndex * averageItemSize + state.firstVisibleItemScrollOffset
        val thumbH = (viewportHeight * viewportHeight / totalContentHeight).coerceAtLeast(thumbMinH)
        val thumbY = (scrollOffset / scrollRange).coerceIn(0f, 1f) * (viewportHeight - thumbH)
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(size.width - thumbWidthPx - insetPx, thumbY),
            size = Size(thumbWidthPx, thumbH),
            cornerRadius = CornerRadius(thumbWidthPx / 2),
        )
    }
}
