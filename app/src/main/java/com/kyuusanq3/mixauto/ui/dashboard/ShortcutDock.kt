package com.kyuusanq3.mixauto.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyuusanq3.mixauto.domain.media.MediaPlaybackState
import com.kyuusanq3.mixauto.ui.components.loadAudioPlayerPackageNames
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.DeepCharcoal
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan

private const val DOCK_MUSIC_CONTROL_KEY = "dock_music_control"

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
    ADD_PLACE,
    POI_DETAIL,
    ROUTE_PICKER,
    AUDIO_SETTINGS,
    HIDDEN,
}

internal enum class DockActiveIndicatorPlacement {
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
internal fun BoxScope.DockActiveIndicator(
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
