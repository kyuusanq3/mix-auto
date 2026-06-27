package com.kyuusanq3.mixauto.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyuusanq3.mixauto.domain.media.MediaPlaybackState
import com.kyuusanq3.mixauto.ui.components.AppContextDropdownMenu
import com.kyuusanq3.mixauto.ui.components.launchAppByPackage
import com.kyuusanq3.mixauto.ui.components.loadAudioPlayerPackageNames
import com.kyuusanq3.mixauto.ui.components.rememberAppIcon
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.DeepCharcoal
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan

private const val VOICE_SEARCH_KEY = "voice_search"
private const val DOCK_MUSIC_CONTROL_KEY = "dock_music_control"
private const val APP_DRAWER_KEY = "app_drawer"
private val DOCK_MUSIC_SLOT_WIDTH = 280.dp
private const val DOCK_TITLE_VISUALIZER_ALPHA = 0.22f
private const val DOCK_TITLE_VISUALIZER_BAR_COUNT = 32
private val DOCK_APP_MENU_ESTIMATED_HEIGHT = 168.dp

private val dockMusicIdleTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.titleLarge.copy(
        lineHeight = 20.sp,
        textAlign = TextAlign.End,
    )

private val dockMusicTitleTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.headlineLarge.copy(
        lineHeight = 24.sp,
        textAlign = TextAlign.End,
    )

private val dockMusicArtistTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyLarge.copy(
        lineHeight = 18.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
    )

private fun dockPinnedKey(packageName: String) = "dock_pinned_$packageName"

enum class DockShortcutIconSize {
    SMALL,
    MEDIUM,
    LARGE,
    ;

    companion object {
        fun fromOrdinal(ordinal: Int): DockShortcutIconSize =
            entries.getOrElse(ordinal.coerceIn(0, entries.lastIndex)) { LARGE }
    }

    val label: String
        get() = when (this) {
            SMALL -> "Small"
            MEDIUM -> "Medium"
            LARGE -> "Large"
        }
}

private fun DockShortcutIconSize.scaleFactor(): Float = when (this) {
    DockShortcutIconSize.SMALL -> 1f
    DockShortcutIconSize.MEDIUM -> 1.5f
    DockShortcutIconSize.LARGE -> 2f
}

private fun dockTapTargetFor(size: DockShortcutIconSize): Dp =
    CarDimensions.DockHorizontalTapTarget * size.scaleFactor()

private fun dockIconSizeFor(size: DockShortcutIconSize): Dp =
    CarDimensions.DockHorizontalIconSize * size.scaleFactor()

enum class ActivePanel {
    MEDIA,
    SETTINGS,
    MAP_DATA,
    APP_DRAWER,
    SEARCH,
    POI_DETAIL,
    ROUTE_PICKER,
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
    shortcutIconSize: DockShortcutIconSize = DockShortcutIconSize.SMALL,
    isLeftHandDrive: Boolean = true,
    activePanel: ActivePanel,
    mediaState: MediaPlaybackState,
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
    val tapTarget = dockTapTargetFor(shortcutIconSize)
    val iconSize = dockIconSizeFor(shortcutIconSize)
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
                // Pinned icons align to the full dock width; side clusters overlay the edges.
                // A weighted middle zone would skew left because the music slot is 280 dp
                // while the driver cluster is only two icons wide.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(tapTarget)
                        .padding(
                            start = if (isLeftHandDrive) CarDimensions.PaneGap else 0.dp,
                            end = if (isLeftHandDrive) 0.dp else CarDimensions.PaneGap,
                        ),
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
                    if (isLeftHandDrive) {
                        Box(modifier = Modifier.align(Alignment.CenterStart)) {
                            DriverSideCluster(
                                isHorizontal = true,
                                isLeftHandDrive = true,
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
                        key(DOCK_MUSIC_CONTROL_KEY) {
                            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                                DockMusicSideControl(
                                    activePanel = activePanel,
                                    mediaState = mediaState,
                                    isHorizontal = true,
                                    isLeftHandDrive = isLeftHandDrive,
                                    tapTarget = tapTarget,
                                    iconSize = iconSize,
                                    activeIndicatorPlacement = activeIndicatorPlacement,
                                    edgePadding = 2.dp,
                                    onToggleMusicPane = { onTogglePanel(ActivePanel.MEDIA) },
                                )
                            }
                        }
                    } else {
                        key(DOCK_MUSIC_CONTROL_KEY) {
                            Box(modifier = Modifier.align(Alignment.CenterStart)) {
                                DockMusicSideControl(
                                    activePanel = activePanel,
                                    mediaState = mediaState,
                                    isHorizontal = true,
                                    isLeftHandDrive = isLeftHandDrive,
                                    tapTarget = tapTarget,
                                    iconSize = iconSize,
                                    activeIndicatorPlacement = activeIndicatorPlacement,
                                    edgePadding = 2.dp,
                                    onToggleMusicPane = { onTogglePanel(ActivePanel.MEDIA) },
                                )
                            }
                        }
                        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                            DriverSideCluster(
                                isHorizontal = true,
                                isLeftHandDrive = false,
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
                        isLeftHandDrive = isLeftHandDrive,
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
                    key(DOCK_MUSIC_CONTROL_KEY) {
                        DockMusicSideControl(
                            activePanel = activePanel,
                            mediaState = mediaState,
                            isHorizontal = false,
                            isLeftHandDrive = isLeftHandDrive,
                            tapTarget = tapTarget,
                            iconSize = iconSize,
                            activeIndicatorPlacement = activeIndicatorPlacement,
                            edgePadding = 2.dp,
                            onToggleMusicPane = { onTogglePanel(ActivePanel.MEDIA) },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockMarqueeTextLine(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = style,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .fillMaxWidth()
            .basicMarquee(
                iterations = Int.MAX_VALUE,
                initialDelayMillis = 1_500,
                delayMillis = 2_000,
            ),
    )
}

@Composable
private fun DockMusicSideControl(
    activePanel: ActivePanel,
    mediaState: MediaPlaybackState,
    isHorizontal: Boolean,
    isLeftHandDrive: Boolean,
    iconSize: Dp,
    onToggleMusicPane: () -> Unit,
    tapTarget: Dp = CarDimensions.DockHorizontalTapTarget,
    activeIndicatorPlacement: DockActiveIndicatorPlacement = DockActiveIndicatorPlacement.Bottom,
    edgePadding: Dp = 0.dp,
) {
    val isMusicPaneVisible =
        activePanel == ActivePanel.MEDIA || activePanel == ActivePanel.APP_DRAWER
    val edgeAlignment = when {
        isHorizontal && isLeftHandDrive -> Alignment.CenterEnd
        isHorizontal -> Alignment.CenterStart
        else -> Alignment.Center
    }
    val horizontalContentAlignment = if (isLeftHandDrive) Alignment.CenterEnd else Alignment.CenterStart
    val horizontalTextAlign = if (isLeftHandDrive) TextAlign.End else TextAlign.Start
    val horizontalColumnAlignment = if (isLeftHandDrive) Alignment.End else Alignment.Start

    Box(
        modifier = Modifier
            .then(
                if (isHorizontal) {
                    Modifier.width(DOCK_MUSIC_SLOT_WIDTH)
                } else {
                    Modifier.wrapContentSize(edgeAlignment, unbounded = true)
                },
            )
            .padding(
                start = if (!isHorizontal || !isLeftHandDrive) edgePadding else 0.dp,
                end = if (isHorizontal && isLeftHandDrive) edgePadding else 0.dp,
                bottom = if (!isHorizontal) edgePadding else 0.dp,
            ),
        contentAlignment = if (isHorizontal) horizontalContentAlignment else Alignment.Center,
    ) {
        if (isMusicPaneVisible) {
            Box(
                modifier = Modifier
                    .then(
                        if (isHorizontal) {
                            Modifier
                                .width(DOCK_MUSIC_SLOT_WIDTH)
                                .height(tapTarget)
                        } else {
                            Modifier.size(tapTarget)
                        },
                    )
                    .clickable(onClick = onToggleMusicPane),
                contentAlignment = if (isHorizontal) horizontalContentAlignment else Alignment.Center,
            ) {
                DockMiniVisualizer(
                    isPlaying = mediaState.isPlaying,
                    playbackPositionMs = mediaState.playbackPositionMs,
                    modifier = Modifier.size(iconSize),
                )
                if (!isHorizontal) {
                    DockActiveIndicator(isActive = true, activeIndicatorPlacement)
                }
            }
        } else if (isHorizontal) {
            Box(
                modifier = Modifier
                    .width(DOCK_MUSIC_SLOT_WIDTH)
                    .height(tapTarget)
                    .clickable(onClick = onToggleMusicPane)
                    .padding(horizontal = 4.dp),
                contentAlignment = horizontalContentAlignment,
            ) {
                DockMiniVisualizer(
                    isPlaying = mediaState.isPlaying,
                    playbackPositionMs = mediaState.playbackPositionMs,
                    modifier = Modifier.fillMaxSize(),
                    barAlpha = DOCK_TITLE_VISUALIZER_ALPHA,
                    barCount = DOCK_TITLE_VISUALIZER_BAR_COUNT,
                    wide = true,
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = horizontalColumnAlignment,
                ) {
                    if (!mediaState.hasActiveSession) {
                        DockMarqueeTextLine(
                            text = "No music is playing",
                            style = dockMusicIdleTextStyle.copy(textAlign = horizontalTextAlign),
                        )
                    } else {
                        DockMarqueeTextLine(
                            text = mediaState.title.ifBlank { "Unknown track" },
                            style = dockMusicTitleTextStyle.copy(textAlign = horizontalTextAlign),
                        )
                        if (mediaState.artist.isNotBlank()) {
                            DockMarqueeTextLine(
                                text = mediaState.artist,
                                style = dockMusicArtistTextStyle.copy(textAlign = horizontalTextAlign),
                            )
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(tapTarget)
                    .clickable(onClick = onToggleMusicPane),
                contentAlignment = Alignment.Center,
            ) {
                DockMiniVisualizer(
                    isPlaying = mediaState.isPlaying,
                    playbackPositionMs = mediaState.playbackPositionMs,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}
