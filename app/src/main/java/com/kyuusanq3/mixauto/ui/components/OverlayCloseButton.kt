package com.kyuusanq3.mixauto.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyuusanq3.mixauto.ui.theme.CarDimensions

@Composable
fun OverlayCloseButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(CarDimensions.MinTapTarget),
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = contentDescription,
            modifier = Modifier.size(CarDimensions.AppIconSize - 8.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
