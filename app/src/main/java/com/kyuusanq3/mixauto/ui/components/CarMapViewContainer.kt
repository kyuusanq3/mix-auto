package com.kyuusanq3.mixauto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarHeadlineText
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack

@Composable
fun CarMapViewContainer(
    engine: CarMapEngine,
    onToggleSearch: () -> Unit,
    isSearchOpen: Boolean,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapUiState by engine.uiState.collectAsState()

    DisposableEffect(lifecycleOwner, engine) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> engine.onStart()
                Lifecycle.Event.ON_RESUME -> engine.onResume()
                Lifecycle.Event.ON_PAUSE -> engine.onPause()
                Lifecycle.Event.ON_STOP -> engine.onStop()
                Lifecycle.Event.ON_DESTROY -> engine.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = modifier
            .padding(CarDimensions.PaneGap)
            .clip(MaterialTheme.shapes.medium),
    ) {
        AndroidView(
            factory = { context -> engine.createMapView(context) },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(CarDimensions.PaneGap)
                .background(OledBlack.copy(alpha = 0.72f))
                .padding(CarDimensions.PaneGap),
        ) {
            CarHeadlineText(
                text = "${mapUiState.currentSpeed} km/h",
                style = MaterialTheme.typography.headlineMedium,
            )
            CarBodyText(
                text = mapUiState.streetName,
                modifier = Modifier.padding(top = CarDimensions.PaneGap / 2),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (mapUiState.isNavigating) {
                mapUiState.turnInstruction?.let { instruction ->
                    CarLabelText(
                        text = instruction,
                        modifier = Modifier.padding(top = CarDimensions.PaneGap / 2),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                mapUiState.distanceToNextTurn?.let { distance ->
                    CarLabelText(
                        text = distance,
                        modifier = Modifier.padding(top = CarDimensions.PaneGap / 4),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(CarDimensions.PaneGap),
            horizontalAlignment = Alignment.End,
        ) {
            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier
                    .size(CarDimensions.MinTapTarget)
                    .background(OledBlack.copy(alpha = 0.72f), MaterialTheme.shapes.small),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = if (isSearchOpen) {
                        "Hide destination search"
                    } else {
                        "Show destination search"
                    },
                    tint = if (isSearchOpen) {
                        ElectricCyan
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }

            if (mapUiState.isNavigating) {
                Spacer(modifier = Modifier.height(CarDimensions.PaneGap))
                IconButton(
                    onClick = { engine.startFreeDrive() },
                    modifier = Modifier
                        .size(CarDimensions.MinTapTarget)
                        .background(Color(0xBBCC2200), MaterialTheme.shapes.small),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "End navigation",
                        tint = Color.White,
                    )
                }
            }

            if (mapUiState.isCameraDetached && !mapUiState.isNavigating) {
                if (!mapUiState.isInTopDownView) {
                    Spacer(modifier = Modifier.height(CarDimensions.PaneGap))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TooltipBubble(text = "Enter Top-View Mode\nto place pins")
                        IconButton(
                            onClick = { engine.enterTopDownView() },
                            modifier = Modifier
                                .size(CarDimensions.MinTapTarget)
                                .background(OledBlack.copy(alpha = 0.72f), MaterialTheme.shapes.small),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CropFree,
                                contentDescription = "Top-down view",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(CarDimensions.PaneGap))
                IconButton(
                    onClick = { engine.recenterCamera() },
                    modifier = Modifier
                        .size(CarDimensions.MinTapTarget)
                        .background(OledBlack.copy(alpha = 0.72f), MaterialTheme.shapes.small),
                ) {
                    Icon(
                        imageVector = Icons.Filled.GpsFixed,
                        contentDescription = "Recenter map",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (mapUiState.routeOverviewProgress > 0f) {
            LinearProgressIndicator(
                progress = { mapUiState.routeOverviewProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(6.dp),
                color = ElectricCyan,
                trackColor = OledBlack.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun TooltipBubble(
    text: String,
    modifier: Modifier = Modifier,
) {
    val bubbleColor = OledBlack.copy(alpha = 0.72f)
    val cornerRadius = 8.dp
    val tailWidth = 12.dp
    val tailHeight = 8.dp

    Text(
        text = text,
        modifier = modifier
            .drawBehind {
                val tailW = tailWidth.toPx()
                val tailH = tailHeight.toPx()
                val cornerPx = cornerRadius.toPx()
                val bodyWidth = size.width - tailH
                val tailMidY = size.height / 2f

                drawRoundRect(
                    color = bubbleColor,
                    size = Size(bodyWidth, size.height),
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                )

                val tailPath = Path().apply {
                    moveTo(bodyWidth, tailMidY - tailW / 2f)
                    lineTo(bodyWidth, tailMidY + tailW / 2f)
                    lineTo(size.width, tailMidY)
                    close()
                }
                drawPath(tailPath, bubbleColor)
            }
            .padding(end = tailHeight)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium.copy(color = ElectricCyan),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
