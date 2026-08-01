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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.DeepCharcoal
import kotlin.math.min

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
