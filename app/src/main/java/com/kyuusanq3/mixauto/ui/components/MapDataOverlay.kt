package com.kyuusanq3.mixauto.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyuusanq3.mixauto.ui.settings.LauncherViewModel
import com.kyuusanq3.mixauto.ui.settings.MapDataUiState
import com.kyuusanq3.mixauto.ui.settings.MapDataViewModel
import com.kyuusanq3.mixauto.ui.settings.RemoteCountryPack
import com.kyuusanq3.mixauto.ui.settings.TomTomKeyCheckState
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarHeadlineText
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.DeepCharcoal
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import kotlin.math.roundToInt

private val InstalledGreen = Color(0xFF4CAF50)

@Composable
fun MapDataOverlay(
    viewModel: MapDataViewModel,
    onDismiss: () -> Unit,
    tomTomApiKey: String = "",
    onTomTomApiKeyChange: (String) -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = OledBlack,
        ) {
            MapDataPanelContent(
                viewModel = viewModel,
                onDismiss = onDismiss,
                tomTomApiKey = tomTomApiKey,
                onTomTomApiKeyChange = onTomTomApiKeyChange,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun MapDataPanelContent(
    viewModel: MapDataViewModel,
    onDismiss: () -> Unit,
    tomTomApiKey: String,
    onTomTomApiKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .padding(CarDimensions.PaneGap * 2),
        verticalArrangement = Arrangement.spacedBy(CarDimensions.DockItemSpacing),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CarHeadlineText(
                text = "Map Data",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            OverlayCloseButton(
                onClick = onDismiss,
                contentDescription = "Close map data",
            )
        }

        CarBodyText(
            text = "Download offline Overture POI packs from GitHub (~50 MB compressed per country).",
            style = MaterialTheme.typography.bodyMedium,
        )

        when (val state = uiState) {
            MapDataUiState.LoadingCatalog -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = ElectricCyan)
                }
            }
            is MapDataUiState.Error -> {
                CarBodyText(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                )
                ActionRow(
                    text = "Retry loading catalog",
                    icon = Icons.Filled.CloudDownload,
                    enabled = true,
                    onClick = { viewModel.loadCatalog() },
                )
            }
            is MapDataUiState.Importing -> {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                CarLabelText(
                    text = "Importing ${state.label}… ${(state.progress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            is MapDataUiState.Catalog -> {
                CountryCatalogList(
                    modifier = Modifier.weight(1f),
                    packs = state.packs,
                    uiState = uiState,
                    onDownload = viewModel::downloadCountryData,
                    onDelete = viewModel::deleteDatabase,
                )
            }
            is MapDataUiState.Downloading -> {
                CountryCatalogList(
                    modifier = Modifier.weight(1f),
                    packs = state.packs,
                    uiState = uiState,
                    onDownload = viewModel::downloadCountryData,
                    onDelete = viewModel::deleteDatabase,
                )
            }
            MapDataUiState.Idle -> Unit
        }

        TomTomApiKeySection(
            tomTomApiKey = tomTomApiKey,
            onTomTomApiKeyChange = onTomTomApiKeyChange,
        )
    }
}

@Composable
private fun TomTomApiKeySection(
    tomTomApiKey: String,
    onTomTomApiKeyChange: (String) -> Unit,
) {
    val launcherViewModel: LauncherViewModel = viewModel()
    val tomTomKeyCheckState = launcherViewModel.tomTomKeyCheckState

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CarBodyText(
            text = "TomTom API Key (traffic)",
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedTextField(
            value = tomTomApiKey,
            onValueChange = onTomTomApiKeyChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(CarDimensions.PrimaryTapTarget + CarDimensions.PaneGap),
            placeholder = {
                CarBodyText(
                    text = "Paste key from developer.tomtom.com",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = OledBlack,
                unfocusedContainerColor = OledBlack,
                focusedTextColor = ElectricCyan,
                unfocusedTextColor = ElectricCyan,
            ),
        )
        CarLabelText(
            text = "Free tier covers Philippines. Required for traffic overlay.",
            style = MaterialTheme.typography.labelMedium,
        )
        when (tomTomKeyCheckState) {
            TomTomKeyCheckState.Idle -> Unit
            TomTomKeyCheckState.Checking -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CarDimensions.MinTapTarget),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(CarDimensions.MinTapTarget / 2),
                        color = ElectricCyan,
                    )
                    CarBodyText(text = "Testing API key...")
                }
            }
            is TomTomKeyCheckState.Success -> {
                CarLabelText(
                    text = tomTomKeyCheckState.message,
                    style = MaterialTheme.typography.labelMedium.copy(color = ElectricCyan),
                )
            }
            is TomTomKeyCheckState.Error -> {
                CarLabelText(
                    text = tomTomKeyCheckState.message,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.error,
                    ),
                )
            }
        }
        Button(
            onClick = launcherViewModel::checkTomTomApiKey,
            enabled = tomTomKeyCheckState !is TomTomKeyCheckState.Checking,
            modifier = Modifier
                .fillMaxWidth()
                .height(CarDimensions.MinTapTarget),
        ) {
            CarBodyText(text = "Test TomTom API Key")
        }
    }
}

@Composable
private fun CountryCatalogList(
    modifier: Modifier = Modifier,
    packs: List<RemoteCountryPack>,
    uiState: MapDataUiState,
    onDownload: (RemoteCountryPack) -> Unit,
    onDelete: (String) -> Unit,
) {
    val scrollState = rememberScrollState()
    Box(modifier = modifier.carScrollbar(scrollState)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(CarDimensions.DockItemSpacing / 2),
        ) {
            if (packs.isEmpty()) {
                CarBodyText(
                    text = "No countries in catalog.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                packs.forEach { pack ->
                    CountryPackRow(
                        pack = pack,
                        uiState = uiState,
                        onDownload = { onDownload(pack) },
                        onDelete = { onDelete(pack.iso) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CountryPackRow(
    pack: RemoteCountryPack,
    uiState: MapDataUiState,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val isDownloading = uiState is MapDataUiState.Downloading && uiState.iso == pack.iso
    val downloadProgress = (uiState as? MapDataUiState.Downloading)?.progress ?: 0f
    val transferInProgress = uiState is MapDataUiState.Downloading ||
        uiState is MapDataUiState.Importing

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = DeepCharcoal,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(CarDimensions.MinTapTarget)
                .padding(horizontal = CarDimensions.PaneGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CarBodyText(
                    text = pack.name,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (pack.compressedMb > 0) {
                    CarLabelText(
                        text = "~${pack.compressedMb} MB download",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            when {
                isDownloading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(CarDimensions.DockHorizontalIconSize),
                        color = ElectricCyan,
                        strokeWidth = 3.dp,
                    )
                    CarLabelText(
                        text = "${(downloadProgress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                pack.isInstalled -> {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Installed",
                        tint = InstalledGreen,
                        modifier = Modifier.size(CarDimensions.DockHorizontalIconSize),
                    )
                    CarLabelText(
                        text = "Installed",
                        style = MaterialTheme.typography.labelMedium.copy(color = InstalledGreen),
                    )
                    IconButton(
                        onClick = onDelete,
                        enabled = !transferInProgress,
                        modifier = Modifier.size(CarDimensions.MinTapTarget),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete ${pack.name} data",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                else -> {
                    IconButton(
                        onClick = onDownload,
                        enabled = !transferInProgress,
                        modifier = Modifier.size(CarDimensions.MinTapTarget),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudDownload,
                            contentDescription = "Download ${pack.name}",
                            tint = ElectricCyan,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    text: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CarDimensions.MinTapTarget)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = CarDimensions.PaneGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) ElectricCyan else ElectricCyan.copy(alpha = 0.4f),
        )
        CarBodyText(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = if (enabled) ElectricCyan else ElectricCyan.copy(alpha = 0.4f),
            ),
            modifier = Modifier.weight(1f),
        )
    }
}
