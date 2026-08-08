package com.kyuusanq3.mixauto.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kyuusanq3.mixauto.ui.components.canLaunchApp
import com.kyuusanq3.mixauto.ui.settings.DeveloperSettings
import com.kyuusanq3.mixauto.ui.components.launchAppByPackage
import com.kyuusanq3.mixauto.ui.components.rememberAppIcon
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarHeadlineText
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack

@Composable
fun GestureHintDialog(
    supportsLike: Boolean,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OledBlack,
        title = {
            CarHeadlineText(
                text = "Playback gestures",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CarBodyText(text = "Double-tap album art \u2192 Play / Pause", style = MaterialTheme.typography.bodyMedium)
                CarBodyText(text = "Swipe left \u2192 Previous track", style = MaterialTheme.typography.bodyMedium)
                CarBodyText(text = "Swipe right \u2192 Next track", style = MaterialTheme.typography.bodyMedium)
                if (supportsLike) {
                    CarBodyText(text = "Swipe up \u2192 Like", style = MaterialTheme.typography.bodyMedium)
                }
                CarBodyText(
                    text = if (DeveloperSettings.USE_LEGACY_MEDIA_PLAYER_LAYOUT) {
                        "Long-press \u2192 Change album art style"
                    } else {
                        "Long-press \u2192 Audio Settings"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                CarBodyText(
                    text = "Turn these back into on-screen buttons anytime from Audio Settings (\u22ee).",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                CarLabelText(text = "Got it", style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SourceAppButton(
    sourcePackage: String,
    defaultAudioPackage: String,
    hasActiveSession: Boolean,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val displayPackage = if (hasActiveSession) sourcePackage else defaultAudioPackage
    val appIcon = rememberAppIcon(displayPackage)
    val canLaunch = remember(displayPackage) { canLaunchApp(context, displayPackage) }
    val canLaunchSource = hasActiveSession && canLaunch
    val canOpenPicker = !hasActiveSession
    val showDefaultIcon = !hasActiveSession && defaultAudioPackage.isNotBlank() && appIcon != null
    val iconSize = CarDimensions.AppIconSize - 8.dp

    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .requiredSize(CarDimensions.MinTapTarget)
                .combinedClickable(
                    onClick = {
                        when {
                            canLaunchSource -> launchAppByPackage(context, sourcePackage)
                            canOpenPicker -> onOpenPicker()
                        }
                    },
                    onLongClick = onOpenPicker,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (appIcon != null && (canLaunchSource || showDefaultIcon)) {
                Image(
                    bitmap = appIcon,
                    contentDescription = "Open audio source",
                    modifier = Modifier.size(iconSize),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = if (canOpenPicker) "Choose audio player" else "Audio source",
                    modifier = Modifier.requiredSize(iconSize),
                    tint = when {
                        canOpenPicker -> ElectricCyan
                        canLaunchSource -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                )
            }
        }
    }
}

@Composable
internal fun MediaControlButton(
    onClick: () -> Unit,
    contentDescription: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    active: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .requiredSize(CarDimensions.MinTapTarget)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.requiredSize(
                    if (emphasized) CarDimensions.AppIconSize else CarDimensions.AppIconSize - 8.dp,
                ),
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    active -> ElectricCyan
                    else -> MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}
