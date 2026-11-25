package com.kyuusanq3.mixauto.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyuusanq3.mixauto.data.media.MediaSessionRepository
import com.kyuusanq3.mixauto.ui.dashboard.AudioMeterStyle
import com.kyuusanq3.mixauto.ui.dashboard.GestureHintDialog
import com.kyuusanq3.mixauto.ui.settings.LauncherViewModel
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack

@Composable
fun AudioSettingsPanelContent(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val launcherViewModel: LauncherViewModel = viewModel()
    val context = LocalContext.current
    var pendingDefaultApp by remember { mutableStateOf<AudioPlayerApp?>(null) }
    var showGestureHint by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            MediaSessionRepository.getInstance(context).attemptResumeNow(
                launcherViewModel.defaultAudioPackage.takeIf { it.isNotBlank() },
                launcherViewModel.audioFallbackResumeLink.takeIf { it.isNotBlank() },
            )
        }
    }

    val scrollState = rememberScrollState()
    Surface(
        modifier = modifier.carScrollbar(scrollState),
        color = OledBlack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(
                    horizontal = CarDimensions.PaneGap * 2,
                    vertical = CarDimensions.PaneGap / 2,
                ),
        ) {
            PanelHeaderRow(
                title = "Audio Settings",
                onClose = onDismiss,
                closeContentDescription = "Close audio settings",
                compact = true,
                largeActions = true,
                trailingContent = {
                    PanelHeaderIconButton(
                        onClick = { showGestureHint = true },
                        contentDescription = "Playback gesture help",
                        icon = Icons.Filled.Info,
                        large = true,
                    )
                },
            )

            AudioPlayerListContent(
                defaultAudioPackage = launcherViewModel.defaultAudioPackage,
                headerTitle = null,
                headerMessage = "Default audio source",
                showCloseButton = false,
                showLongPressHint = true,
                onDismiss = {},
                onAppClick = { app -> launchAppByPackage(context, app.packageName) },
                onRequestSetDefault = { app -> pendingDefaultApp = app },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = CarDimensions.PaneGap),
                fillAvailableHeight = false,
            )

            AudioFallbackLinkSection(
                value = launcherViewModel.audioFallbackResumeLink,
                onValueChange = launcherViewModel::updateAudioFallbackResumeLink,
            )

            SettingsSwitchRow(
                label = "Resume playback on startup",
                checked = launcherViewModel.resumeAudioOnStartup,
                onCheckedChange = launcherViewModel::updateResumeAudioOnStartup,
            )
            CarLabelText(
                text = "Wakes your default source (or fallback link) if nothing resumes automatically.",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = CarDimensions.PaneGap / 2),
            )

            CarBodyText(
                text = "Now playing display",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = CarDimensions.PaneGap / 2),
            )
            AudioMeterStyle.entries.forEach { style ->
                AudioMeterStyleOption(
                    label = style.label,
                    selected = launcherViewModel.audioMeterStyle == style,
                    onSelect = { launcherViewModel.updateAudioMeterStyle(style) },
                )
            }
            CarLabelText(
                text = "The progress line shows how far the track has played. Bars are the animated spectrum.",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = CarDimensions.PaneGap / 2),
            )

            SettingsSwitchRow(
                label = "Show playback buttons on album art",
                checked = launcherViewModel.showAlbumArtControls,
                onCheckedChange = launcherViewModel::updateShowAlbumArtControls,
            )
            CarLabelText(
                text = "Off by default. Tap album art to open the player, double-tap to play or pause, swipe to skip or like.",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }

    pendingDefaultApp?.let { app ->
        SetDefaultAudioConfirmDialog(
            app = app,
            onConfirm = {
                launcherViewModel.updateDefaultAudioPackage(app.packageName)
                pendingDefaultApp = null
            },
            onDismiss = { pendingDefaultApp = null },
        )
    }

    if (showGestureHint) {
        GestureHintDialog(
            supportsLike = true,
            onDismiss = { showGestureHint = false },
        )
    }
}

@Composable
private fun AudioMeterStyleOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CarDimensions.MinTapTarget)
            .clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
        )
        CarBodyText(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun AudioFallbackLinkSection(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CarBodyText(
            text = "Fallback resume link",
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(CarDimensions.PrimaryTapTarget + CarDimensions.PaneGap),
            placeholder = {
                CarBodyText(
                    text = "Playlist or station link to play if nothing resumes",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            singleLine = true,
            trailingIcon = {
                IconButton(
                    onClick = {
                        val uri = value.takeIf { it.isNotBlank() }?.let { link ->
                            runCatching { Uri.parse(link) }.getOrNull()
                        }
                        if (uri != null) {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, uri).addFlags(
                                        Intent.FLAG_ACTIVITY_NEW_TASK,
                                    ),
                                )
                            }
                        }
                    },
                    modifier = Modifier.size(CarDimensions.PanelHeaderTapTarget),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Test fallback link",
                        modifier = Modifier.size(CarDimensions.PanelHeaderIconSize),
                        tint = ElectricCyan,
                    )
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = OledBlack,
                unfocusedContainerColor = OledBlack,
                focusedTextColor = ElectricCyan,
                unfocusedTextColor = ElectricCyan,
            ),
        )
        CarLabelText(
            text = "Played automatically if nothing resumes when the app starts.",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
