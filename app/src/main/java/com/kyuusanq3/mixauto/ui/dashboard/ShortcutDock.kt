package com.kyuusanq3.mixauto.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyuusanq3.mixauto.ui.components.rememberAppIcon
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.DeepCharcoal
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan

private const val MUSIC_KEY = "music_player"
private const val LAUNCHER_SETTINGS_KEY = "launcher_settings"
private const val MAP_DATA_KEY = "map_data"
private const val APP_DRAWER_KEY = "app_drawer"

enum class ActivePanel {
    MEDIA,
    SETTINGS,
    MAP_DATA,
    APP_DRAWER,
    SEARCH,
    POI_DETAIL,
    HIDDEN,
}

private enum class DockActiveIndicatorPlacement {
    Bottom,
    Start,
    End,
}

private enum class DockItem {
    AppDrawer,
    Music,
    MapData,
    Settings,
}

/** Vertical dock: app drawer first (top). Horizontal LHD: left; horizontal RHD: right. */
private fun dockItemOrder(isHorizontal: Boolean, isLeftHandDrive: Boolean): List<DockItem> {
    val core = listOf(DockItem.Music, DockItem.MapData, DockItem.Settings)
    return when {
        !isHorizontal -> listOf(DockItem.AppDrawer) + core
        isLeftHandDrive -> listOf(DockItem.AppDrawer) + core
        else -> core + DockItem.AppDrawer
    }
}

private fun LazyListScope.dockItems(
    order: List<DockItem>,
    activePanel: ActivePanel,
    sourcePackage: String,
    tapTarget: Dp,
    iconSize: Dp,
    activeIndicatorPlacement: DockActiveIndicatorPlacement,
    onTogglePanel: (ActivePanel) -> Unit,
) {
    order.forEach { dockItem ->
        when (dockItem) {
            DockItem.AppDrawer -> item(key = APP_DRAWER_KEY) {
                AppDrawerDockItem(
                    isActive = activePanel == ActivePanel.APP_DRAWER,
                    tapTarget = tapTarget,
                    iconSize = iconSize,
                    activeIndicatorPlacement = activeIndicatorPlacement,
                    onClick = { onTogglePanel(ActivePanel.APP_DRAWER) },
                )
            }
            DockItem.Music -> item(key = MUSIC_KEY) {
                MusicDockItem(
                    isActive = activePanel == ActivePanel.MEDIA,
                    sourcePackage = sourcePackage,
                    tapTarget = tapTarget,
                    iconSize = iconSize,
                    activeIndicatorPlacement = activeIndicatorPlacement,
                    onClick = { onTogglePanel(ActivePanel.MEDIA) },
                )
            }
            DockItem.MapData -> item(key = MAP_DATA_KEY) {
                MapDataDockItem(
                    isActive = activePanel == ActivePanel.MAP_DATA,
                    tapTarget = tapTarget,
                    iconSize = iconSize,
                    activeIndicatorPlacement = activeIndicatorPlacement,
                    onClick = { onTogglePanel(ActivePanel.MAP_DATA) },
                )
            }
            DockItem.Settings -> item(key = LAUNCHER_SETTINGS_KEY) {
                SettingsDockItem(
                    isActive = activePanel == ActivePanel.SETTINGS,
                    tapTarget = tapTarget,
                    iconSize = iconSize,
                    activeIndicatorPlacement = activeIndicatorPlacement,
                    onClick = { onTogglePanel(ActivePanel.SETTINGS) },
                )
            }
        }
    }
}

@Composable
fun ShortcutDock(
    isHorizontal: Boolean,
    isLargeIcons: Boolean = false,
    isLeftHandDrive: Boolean = true,
    activePanel: ActivePanel,
    sourcePackage: String = "",
    onTogglePanel: (ActivePanel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tapTarget = if (isLargeIcons) {
        CarDimensions.DockHorizontalTapTarget * 2
    } else {
        CarDimensions.DockHorizontalTapTarget
    }
    val iconSize = if (isLargeIcons) {
        CarDimensions.DockHorizontalIconSize * 2
    } else {
        CarDimensions.DockHorizontalIconSize
    }
    val activeIndicatorPlacement = if (isHorizontal) {
        DockActiveIndicatorPlacement.Bottom
    } else if (isLeftHandDrive) {
        DockActiveIndicatorPlacement.End
    } else {
        DockActiveIndicatorPlacement.Start
    }
    val itemOrder = dockItemOrder(isHorizontal, isLeftHandDrive)

    ElevatedCard(
        modifier = if (isHorizontal) {
            modifier
                .padding(horizontal = CarDimensions.PaneGap)
                .wrapContentHeight()
        } else {
            modifier
                .padding(vertical = CarDimensions.PaneGap)
                .wrapContentWidth()
        },
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = DeepCharcoal,
        ),
    ) {
        key(isHorizontal, isLeftHandDrive) {
            if (isHorizontal) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(tapTarget)
                        .padding(horizontal = CarDimensions.PaneGap),
                    horizontalArrangement = Arrangement.spacedBy(CarDimensions.DockItemSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dockItems(
                        order = itemOrder,
                        activePanel = activePanel,
                        sourcePackage = sourcePackage,
                        tapTarget = tapTarget,
                        iconSize = iconSize,
                        activeIndicatorPlacement = activeIndicatorPlacement,
                        onTogglePanel = onTogglePanel,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.wrapContentHeight(),
                    contentPadding = PaddingValues(vertical = CarDimensions.DockItemSpacing),
                    verticalArrangement = Arrangement.spacedBy(CarDimensions.DockItemSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    dockItems(
                        order = itemOrder,
                        activePanel = activePanel,
                        sourcePackage = sourcePackage,
                        tapTarget = tapTarget,
                        iconSize = iconSize,
                        activeIndicatorPlacement = activeIndicatorPlacement,
                        onTogglePanel = onTogglePanel,
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.DockActiveIndicator(
    isActive: Boolean,
    placement: DockActiveIndicatorPlacement = DockActiveIndicatorPlacement.Bottom,
) {
    if (!isActive) return
    when (placement) {
        DockActiveIndicatorPlacement.Bottom -> {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(8.dp)
                    .height(4.dp)
                    .background(
                        color = ElectricCyan,
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                    ),
            )
        }
        DockActiveIndicatorPlacement.Start -> {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(4.dp)
                    .height(8.dp)
                    .background(
                        color = ElectricCyan,
                        shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp),
                    ),
            )
        }
        DockActiveIndicatorPlacement.End -> {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(4.dp)
                    .height(8.dp)
                    .background(
                        color = ElectricCyan,
                        shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp),
                    ),
            )
        }
    }
}

@Composable
private fun MusicDockItem(
    isActive: Boolean,
    iconSize: Dp,
    onClick: () -> Unit,
    sourcePackage: String = "",
    tapTarget: Dp = CarDimensions.DockHorizontalTapTarget,
    activeIndicatorPlacement: DockActiveIndicatorPlacement = DockActiveIndicatorPlacement.Bottom,
) {
    val iconTint = if (isActive) ElectricCyan else MaterialTheme.colorScheme.primary
    val sourceIcon = rememberAppIcon(sourcePackage)
    val badgeSize = 20.dp
    Box(
        modifier = Modifier
            .size(tapTarget)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = "Music",
            modifier = Modifier.size(iconSize),
            tint = iconTint,
        )
        if (sourceIcon != null) {
            Image(
                bitmap = sourceIcon,
                contentDescription = "Audio source",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(DeepCharcoal, CircleShape),
            )
        }
        DockActiveIndicator(isActive, activeIndicatorPlacement)
    }
}

@Composable
private fun MapDataDockItem(
    isActive: Boolean,
    iconSize: Dp,
    onClick: () -> Unit,
    tapTarget: Dp = CarDimensions.DockHorizontalTapTarget,
    activeIndicatorPlacement: DockActiveIndicatorPlacement = DockActiveIndicatorPlacement.Bottom,
) {
    val iconTint = if (isActive) ElectricCyan else MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(tapTarget)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Map,
            contentDescription = "Map Data",
            modifier = Modifier.size(iconSize),
            tint = iconTint,
        )
        DockActiveIndicator(isActive, activeIndicatorPlacement)
    }
}

@Composable
private fun AppDrawerDockItem(
    isActive: Boolean,
    iconSize: Dp,
    onClick: () -> Unit,
    tapTarget: Dp = CarDimensions.DockHorizontalTapTarget,
    activeIndicatorPlacement: DockActiveIndicatorPlacement = DockActiveIndicatorPlacement.Bottom,
) {
    val iconTint = if (isActive) ElectricCyan else MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(tapTarget)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Apps,
            contentDescription = "App Drawer",
            modifier = Modifier.size(iconSize),
            tint = iconTint,
        )
        DockActiveIndicator(isActive, activeIndicatorPlacement)
    }
}

@Composable
private fun SettingsDockItem(
    isActive: Boolean = false,
    iconSize: Dp,
    onClick: () -> Unit,
    tapTarget: Dp = CarDimensions.DockHorizontalTapTarget,
    activeIndicatorPlacement: DockActiveIndicatorPlacement = DockActiveIndicatorPlacement.Bottom,
) {
    val iconTint = if (isActive) ElectricCyan else MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(tapTarget)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Tune,
            contentDescription = "Launcher",
            modifier = Modifier.size(iconSize),
            tint = iconTint,
        )
        DockActiveIndicator(isActive, activeIndicatorPlacement)
    }
}
