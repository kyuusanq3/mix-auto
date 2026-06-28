package com.kyuusanq3.mixauto.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kyuusanq3.mixauto.ui.theme.CarDimensions

@Composable
fun OverlayCloseButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val tapTarget = if (compact) {
        CarDimensions.PanelCompactHeaderTapTarget
    } else {
        CarDimensions.PanelHeaderTapTarget
    }
    val iconSize = if (compact) {
        CarDimensions.PanelCompactHeaderIconSize
    } else {
        CarDimensions.PanelHeaderIconSize
    }
    IconButton(
        onClick = onClick,
        modifier = modifier.size(tapTarget),
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
