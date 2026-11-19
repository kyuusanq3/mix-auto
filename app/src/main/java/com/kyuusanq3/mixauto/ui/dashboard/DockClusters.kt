package com.kyuusanq3.mixauto.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyuusanq3.mixauto.ui.components.AppContextDropdownMenu
import com.kyuusanq3.mixauto.ui.components.launchAppByPackage
import com.kyuusanq3.mixauto.ui.components.rememberAppIcon
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan

private const val VOICE_SEARCH_KEY = "voice_search"
private const val APP_DRAWER_KEY = "app_drawer"
private val DOCK_APP_MENU_ESTIMATED_HEIGHT = 168.dp

private fun dockPinnedKey(packageName: String) = "dock_pinned_$packageName"

@Composable
internal fun DriverSideCluster(
    isHorizontal: Boolean,
    isLeftHandDrive: Boolean,
    voiceSearchAvailable: Boolean,
    activePanel: ActivePanel,
    tapTarget: Dp,
    iconSize: Dp,
    itemSpacing: Dp,
    activeIndicatorPlacement: DockActiveIndicatorPlacement,
    onTogglePanel: (ActivePanel) -> Unit,
    onVoiceSearch: () -> Unit,
) {
    val appDrawer = @Composable {
        key(APP_DRAWER_KEY) {
            AppDrawerDockItem(
                isActive = activePanel == ActivePanel.APP_DRAWER,
                tapTarget = tapTarget,
                iconSize = iconSize,
                activeIndicatorPlacement = activeIndicatorPlacement,
                onClick = { onTogglePanel(ActivePanel.APP_DRAWER) },
            )
        }
    }
    val voiceSearch = @Composable {
        if (voiceSearchAvailable) {
            key(VOICE_SEARCH_KEY) {
                VoiceSearchDockItem(
                    tapTarget = tapTarget,
                    iconSize = iconSize,
                    onClick = onVoiceSearch,
                )
            }
        }
    }
    if (isHorizontal) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLeftHandDrive) {
                appDrawer()
                voiceSearch()
            } else {
                voiceSearch()
                appDrawer()
            }
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isLeftHandDrive) {
                appDrawer()
                voiceSearch()
            } else {
                voiceSearch()
                appDrawer()
            }
        }
    }
}

@Composable
internal fun CenterDockCluster(
    isHorizontal: Boolean,
    dockPinnedPackages: List<String>,
    tapTarget: Dp,
    iconSize: Dp,
    itemSpacing: Dp,
    isLeftHandDrive: Boolean,
    onToggleDockPin: (String) -> Unit,
) {
    if (isHorizontal) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(itemSpacing, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dockPinnedPackages.forEach { packageName ->
                key(dockPinnedKey(packageName)) {
                    PinnedDockAppItem(
                        packageName = packageName,
                        isHorizontal = true,
                        isLeftHandDrive = isLeftHandDrive,
                        tapTarget = tapTarget,
                        iconSize = iconSize,
                        isPinnedToDock = true,
                        canAddToDock = true,
                        onToggleDockPin = { onToggleDockPin(packageName) },
                    )
                }
            }
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(itemSpacing, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            dockPinnedPackages.forEach { packageName ->
                key(dockPinnedKey(packageName)) {
                    PinnedDockAppItem(
                        packageName = packageName,
                        isHorizontal = false,
                        isLeftHandDrive = isLeftHandDrive,
                        tapTarget = tapTarget,
                        iconSize = iconSize,
                        isPinnedToDock = true,
                        canAddToDock = true,
                        onToggleDockPin = { onToggleDockPin(packageName) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedDockAppItem(
    packageName: String,
    isHorizontal: Boolean,
    isLeftHandDrive: Boolean,
    tapTarget: Dp,
    iconSize: Dp,
    isPinnedToDock: Boolean,
    canAddToDock: Boolean,
    onToggleDockPin: () -> Unit,
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val appIcon = rememberAppIcon(packageName)
    val menuUpOffset = -(tapTarget + DOCK_APP_MENU_ESTIMATED_HEIGHT)
    val dropdownOffset = when {
        isHorizontal -> DpOffset(0.dp, menuUpOffset)
        isLeftHandDrive -> DpOffset(tapTarget, menuUpOffset)
        else -> DpOffset(-tapTarget, menuUpOffset)
    }
    val edgeAlignment = when {
        isHorizontal && isLeftHandDrive -> Alignment.CenterEnd
        isHorizontal -> Alignment.CenterStart
        else -> Alignment.Center
    }

    Box(
        modifier = Modifier.wrapContentSize(edgeAlignment, unbounded = true),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(tapTarget)
                .combinedClickable(
                    onClick = { launchAppByPackage(context, packageName) },
                    onLongClick = { showMenu = true },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (appIcon != null) {
                Image(
                    bitmap = appIcon,
                    contentDescription = packageName,
                    modifier = Modifier
                        .size(iconSize)
                        .clip(CircleShape),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Android,
                    contentDescription = packageName,
                    modifier = Modifier.size(iconSize),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        AppContextDropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            packageName = packageName,
            isPinnedToDock = isPinnedToDock,
            canAddToDock = canAddToDock,
            onToggleDockPin = onToggleDockPin,
            offset = dropdownOffset,
        )
    }
}

@Composable
private fun VoiceSearchDockItem(
    iconSize: Dp,
    onClick: () -> Unit,
    tapTarget: Dp = CarDimensions.DockHorizontalTapTarget,
) {
    Box(
        modifier = Modifier
            .size(tapTarget)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = "Voice destination search",
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.primary,
        )
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
