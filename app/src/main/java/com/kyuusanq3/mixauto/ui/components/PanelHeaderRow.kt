package com.kyuusanq3.mixauto.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarHeadlineText

@Composable
fun PanelHeaderRow(
    title: String,
    onClose: () -> Unit,
    closeContentDescription: String,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.headlineSmall,
    compact: Boolean = false,
    largeActions: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val tapTarget = if (largeActions) {
        CarDimensions.SettingsHeaderTapTarget
    } else if (compact) {
        CarDimensions.PanelCompactHeaderTapTarget
    } else {
        CarDimensions.PanelHeaderTapTarget
    }
    val resolvedTitleStyle = if (compact) {
        MaterialTheme.typography.titleMedium
    } else {
        titleStyle
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = tapTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        CarHeadlineText(
            text = title,
            style = resolvedTitleStyle,
            modifier = Modifier.weight(1f),
        )
        trailingContent()
        OverlayCloseButton(
            onClick = onClose,
            contentDescription = closeContentDescription,
            compact = compact,
            large = largeActions,
        )
    }
}

@Composable
fun PanelHeaderIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val tapTarget = if (large) {
        CarDimensions.SettingsHeaderTapTarget
    } else {
        CarDimensions.PanelHeaderTapTarget
    }
    val iconSize = if (large) {
        CarDimensions.SettingsHeaderIconSize
    } else {
        CarDimensions.PanelHeaderIconSize
    }
    IconButton(
        onClick = onClick,
        modifier = modifier.size(tapTarget),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
