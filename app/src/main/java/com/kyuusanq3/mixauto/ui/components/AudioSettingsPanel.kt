package com.kyuusanq3.mixauto.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyuusanq3.mixauto.data.media.MediaSessionRepository
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

    Surface(
        modifier = modifier,
        color = OledBlack,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                    trailingContent = {
                        PanelHeaderIconButton(
                            onClick = { showGestureHint = true },
                            contentDescription = "Playback gesture help",
                            icon = Icons.Filled.Info,
                        )
                    },
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = CarDimensions.PaneGap),
                ) {
                    AudioPlayerListContent(
                        defaultAudioPackage = launcherViewModel.defaultAudioPackage,
                        headerTitle = null,
                        headerMessage = "Default audio source",
                        showCloseButton = false,
                        showLongPressHint = true,
                        onDismiss = {},
                        onAppClick = { app -> launchAppByPackage(context, app.packageName) },
                        onRequestSetDefault = { app -> pendingDefaultApp = app },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

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

                SettingsSwitchRow(
                    label = "Show playback buttons on album art",
                    checked = launcherViewModel.showAlbumArtControls,
                    onCheckedChange = launcherViewModel::updateShowAlbumArtControls,
                )
                CarLabelText(
                    text = "Off by default — double-tap to play/pause, swipe to skip or like.",
                    style = MaterialTheme.typography.labelMedium,
                )
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
