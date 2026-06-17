package com.kyuusanq3.mixauto.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MixAutoShapes = Shapes(
    extraSmall = RoundedCornerShape(CarDimensions.CardCornerRadius / 2),
    small = RoundedCornerShape(CarDimensions.CardCornerRadius / 2),
    medium = RoundedCornerShape(CarDimensions.CardCornerRadius),
    large = RoundedCornerShape(CarDimensions.CardCornerRadius),
    extraLarge = RoundedCornerShape(CarDimensions.CardCornerRadius * 1.5f),
)

@Composable
fun MixAutoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MixAutoDarkColors,
        typography = MixAutoTypography,
        shapes = MixAutoShapes,
        content = content,
    )
}
