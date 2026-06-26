package com.kyuusanq3.mixauto.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
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
import com.kyuusanq3.mixauto.ui.components.loadAudioPlayerPackageNames
import com.kyuusanq3.mixauto.ui.components.rememberAppIcon
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.DeepCharcoal
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan

private const val VOICE_SEARCH_KEY = "voice_search"
private const val DOCK_OVERFLOW_MENU_KEY = "dock_overflow_menu"
private const val APP_DRAWER_KEY = "app_drawer"
private val DOCK_MENU_ESTIMATED_HEIGHT = 112.dp
private val DOCK_APP_MENU_ESTIMATED_HEIGHT = 168.dp

private fun dockPinnedKey(packageName: String) = "dock_pinned_$packageName"

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

@Composable
fun ShortcutDock(
    isHorizontal: Boolean,
    isLargeIcons: Boolean = false,
    isLeftHandDrive: Boolean = true,
    activePanel: ActivePanel,
    voiceSearchAvailable: Boolean = true,
    defaultAudioPackage: String = "",
    dockPinnedPackages: List<String> = emptyList(),
    onToggleDockPin: (String) -> Unit = {},
    onSelectAudioSource: (String) -> Unit = {},
    onTogglePanel: (ActivePanel) -> Unit,
    onVoiceSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val audioPlayerPackages = remember(context) { loadAudioPlayerPackageNames(context) }
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
    val itemSpacing = CarDimensions.DockItemSpacing

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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(tapTarget)
                        .padding(
                            start = if (isLeftHandDrive) CarDimensions.PaneGap else 0.dp,
                            end = if (isLeftHandDrive) 0.dp else CarDimensions.PaneGap,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isLeftHandDrive) {
                        DriverSideCluster(
                            isHorizontal = true,
                            voiceSearchAvailable = voiceSearchAvailable,
                            activePanel = activePanel,
                            tapTarget = tapTarget,
                            iconSize = iconSize,
                            itemSpacing = itemSpacing,
                            activeIndicatorPlacement = activeIndicatorPlacement,
                            onTogglePanel = onTogglePanel,
                            onVoiceSearch = onVoiceSearch,
                        )
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            CenterDockCluster(
                                isHorizontal = true,
                                dockPinnedPackages = dockPinnedPackages,
                                audioPlayerPackages = audioPlayerPackages,
                                defaultAudioPackage = defaultAudioPackage,
                                tapTarget = tapTarget,
                                iconSize = iconSize,
                                itemSpacing = itemSpacing,
                                isLeftHandDrive = isLeftHandDrive,
                                onToggleDockPin = onToggleDockPin,
                                onSelectAudioSource = onSelectAudioSource,
                            )
                        }
                        key(DOCK_OVERFLOW_MENU_KEY) {
                            DockOverflowMenuItem(
                                isActive = activePanel == ActivePanel.SETTINGS,
                                isHorizontal = true,
                                isLeftHandDrive = isLeftHandDrive,
                                tapTarget = tapTarget,
                                iconSize = iconSize,
                                activeIndicatorPlacement = activeIndicatorPlacement,
                                edgePadding = 2.dp,
                                onOpenMusicPlayer = { onTogglePanel(ActivePanel.MEDIA) },
                                onOpenLauncherSettings = { onTogglePanel(ActivePanel.SETTINGS) },
                            )
                        }
                    } else {
                        key(DOCK_OVERFLOW_MENU_KEY) {
                            DockOverflowMenuItem(
                                isActive = activePanel == ActivePanel.SETTINGS,
                                isHorizontal = true,
                                isLeftHandDrive = isLeftHandDrive,
                                tapTarget = tapTarget,
                                iconSize = iconSize,
                                activeIndicatorPlacement = activeIndicatorPlacement,
                                edgePadding = 2.dp,
                                onOpenMusicPlayer = { onTogglePanel(ActivePanel.MEDIA) },
                                onOpenLauncherSettings = { onTogglePanel(ActivePanel.SETTINGS) },
                            )
                        }
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            CenterDockCluster(
                                isHorizontal = true,
                                dockPinnedPackages = dockPinnedPackages,
                                audioPlayerPackages = audioPlayerPackages,
                                defaultAudioPackage = defaultAudioPackage,
                                tapTarget = tapTarget,
                                iconSize = iconSize,
                                itemSpacing = itemSpacing,
                                isLeftHandDrive = isLeftHandDrive,
                                onToggleDockPin = onToggleDockPin,
                                onSelectAudioSource = onSelectAudioSource,
                            )
                        }
                        DriverSideCluster(
                            isHorizontal = true,
                            voiceSearchAvailable = voiceSearchAvailable,
                            activePanel = activePanel,
                            tapTarget = tapTarget,
                            iconSize = iconSize,
                            itemSpacing = itemSpacing,
                            activeIndicatorPlacement = activeIndicatorPlacement,
                            onTogglePanel = onTogglePanel,
                            onVoiceSearch = onVoiceSearch,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .wrapContentWidth()
                        .fillMaxHeight()
                        .padding(vertical = itemSpacing),
                    verticalArrangement = Arrangement.spacedBy(itemSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DriverSideCluster(
                        isHorizontal = false,
                        voiceSearchAvailable = voiceSearchAvailable,
                        activePanel = activePanel,
                        tapTarget = tapTarget,
                        iconSize = iconSize,
                        itemSpacing = itemSpacing,
                        activeIndicatorPlacement = activeIndicatorPlacement,
                        onTogglePanel = onTogglePanel,
                        onVoiceSearch = onVoiceSearch,
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CenterDockCluster(
                            isHorizontal = false,
                            dockPinnedPackages = dockPinnedPackages,
                            audioPlayerPackages = audioPlayerPackages,
                            defaultAudioPackage = defaultAudioPackage,
                            tapTarget = tapTarget,
                            iconSize = iconSize,
                            itemSpacing = itemSpacing,
                            isLeftHandDrive = isLeftHandDrive,
                            onToggleDockPin = onToggleDockPin,
                            onSelectAudioSource = onSelectAudioSource,
                        )
                    }
                    key(DOCK_OVERFLOW_MENU_KEY) {
                        DockOverflowMenuItem(
                            isActive = activePanel == ActivePanel.SETTINGS,
                            isHorizontal = false,
                            isLeftHandDrive = isLeftHandDrive,
                            tapTarget = tapTarget,
                            iconSize = iconSize,
                            activeIndicatorPlacement = activeIndicatorPlacement,
                            edgePadding = 2.dp,
                            onOpenMusicPlayer = { onTogglePanel(ActivePanel.MEDIA) },
                            onOpenLauncherSettings = { onTogglePanel(ActivePanel.SETTINGS) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DriverSideCluster(
    isHorizontal: Boolean,
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
            appDrawer()
            voiceSearch()
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            appDrawer()
            voiceSearch()
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
private fun CenterDockCluster(
    isHorizontal: Boolean,
    dockPinnedPackages: List<String>,
    audioPlayerPackages: Set<String>,
    defaultAudioPackage: String,
    tapTarget: Dp,
    iconSize: Dp,
    itemSpacing: Dp,
    isLeftHandDrive: Boolean,
    onToggleDockPin: (String) -> Unit,
    onSelectAudioSource: (String) -> Unit,
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
                        isAudioPlayer = packageName in audioPlayerPackages,
                        isDefaultAudioSource = packageName == defaultAudioPackage,
                        isHorizontal = true,
                        isLeftHandDrive = isLeftHandDrive,
                        tapTarget = tapTarget,
                        iconSize = iconSize,
                        isPinnedToDock = true,
                        canAddToDock = true,
                        onToggleDockPin = { onToggleDockPin(packageName) },
                        onSelectAudioSource = { onSelectAudioSource(packageName) },
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
                        isAudioPlayer = packageName in audioPlayerPackages,
                        isDefaultAudioSource = packageName == defaultAudioPackage,
                        isHorizontal = false,
                        isLeftHandDrive = isLeftHandDrive,
                        tapTarget = tapTarget,
                        iconSize = iconSize,
                        isPinnedToDock = true,
                        canAddToDock = true,
                        onToggleDockPin = { onToggleDockPin(packageName) },
                        onSelectAudioSource = { onSelectAudioSource(packageName) },
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
    isAudioPlayer: Boolean,
    isDefaultAudioSource: Boolean,
    isHorizontal: Boolean,
    isLeftHandDrive: Boolean,
    tapTarget: Dp,
    iconSize: Dp,
    isPinnedToDock: Boolean,
    canAddToDock: Boolean,
    onToggleDockPin: () -> Unit,
    onSelectAudioSource: () -> Unit,
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val appIcon = rememberAppIcon(packageName)
    val badgeSize = iconSize * 0.55f
    val badgeIconSize = iconSize * 0.35f
    val badgeTint = if (isDefaultAudioSource) ElectricCyan else MaterialTheme.colorScheme.primary
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
                    onClick = {
                        if (isAudioPlayer) onSelectAudioSource() else launchAppByPackage(context, packageName)
                    },
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
            if (isAudioPlayer) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .size(badgeSize)
                        .clip(CircleShape)
                        .background(DeepCharcoal, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = "Audio source",
                        modifier = Modifier.size(badgeIconSize),
                        tint = badgeTint,
                    )
                }
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

@Composable
private fun DockOverflowMenuItem(
    isActive: Boolean,
    isHorizontal: Boolean,
    isLeftHandDrive: Boolean,
    iconSize: Dp,
    onOpenMusicPlayer: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    tapTarget: Dp = CarDimensions.DockHorizontalTapTarget,
    activeIndicatorPlacement: DockActiveIndicatorPlacement = DockActiveIndicatorPlacement.Bottom,
    edgePadding: Dp = 0.dp,
) {
    var expanded by remember { mutableStateOf(false) }
    val iconTint = if (isActive) ElectricCyan else MaterialTheme.colorScheme.primary
    val menuUpOffset = -(tapTarget + DOCK_MENU_ESTIMATED_HEIGHT)
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
        modifier = Modifier
            .padding(
                start = if (!isHorizontal || !isLeftHandDrive) edgePadding else 0.dp,
                end = if (isHorizontal && isLeftHandDrive) edgePadding else 0.dp,
                bottom = if (!isHorizontal) edgePadding else 0.dp,
            )
            .wrapContentSize(edgeAlignment, unbounded = true),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(tapTarget)
                .clickable { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "Dock menu",
                modifier = Modifier.size(iconSize),
                tint = iconTint,
            )
            DockActiveIndicator(isActive, activeIndicatorPlacement)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = dropdownOffset,
        ) {
            DropdownMenuItem(
                text = { CarLabelText("Music player") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                onClick = {
                    expanded = false
                    onOpenMusicPlayer()
                },
            )
            DropdownMenuItem(
                text = { CarLabelText("Launcher settings") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                onClick = {
                    expanded = false
                    onOpenLauncherSettings()
                },
            )
        }
    }
}
