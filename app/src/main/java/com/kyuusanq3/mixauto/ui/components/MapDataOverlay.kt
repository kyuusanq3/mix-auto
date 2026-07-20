package com.kyuusanq3.mixauto.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyuusanq3.mixauto.data.map.formatOfflineMbProgressLabel
import com.kyuusanq3.mixauto.ui.settings.LauncherViewModel
import com.kyuusanq3.mixauto.ui.settings.MapDataUiState
import com.kyuusanq3.mixauto.ui.settings.MapDataViewModel
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.DeepCharcoal
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import kotlin.math.roundToInt

@Composable
fun MapDataOverlay(
    viewModel: MapDataViewModel,
    onDismiss: () -> Unit,
    useVectorTiles: Boolean,
    currentLat: Double?,
    currentLng: Double?,
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
                useVectorTiles = useVectorTiles,
                currentLat = currentLat,
                currentLng = currentLng,
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
    useVectorTiles: Boolean,
    currentLat: Double?,
    currentLng: Double?,
    tomTomApiKey: String,
    onTomTomApiKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(
                horizontal = CarDimensions.PaneGap * 2,
                vertical = CarDimensions.PaneGap,
            ),
        verticalArrangement = Arrangement.spacedBy(CarDimensions.DockItemSpacing),
    ) {
        PanelHeaderRow(
            title = "Map Data",
            onClose = onDismiss,
            closeContentDescription = "Close map data",
        )

        MapDataSectionContent(
            viewModel = viewModel,
            useVectorTiles = useVectorTiles,
            currentLat = currentLat,
            currentLng = currentLng,
            tomTomApiKey = tomTomApiKey,
            onTomTomApiKeyChange = onTomTomApiKeyChange,
            catalogModifier = Modifier.weight(1f),
            useInternalCatalogScroll = true,
        )
    }
}

@Composable
fun MapDataSectionContent(
    viewModel: MapDataViewModel,
    useVectorTiles: Boolean,
    currentLat: Double?,
    currentLng: Double?,
    tomTomApiKey: String,
    onTomTomApiKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    catalogModifier: Modifier = Modifier.fillMaxWidth(),
    useInternalCatalogScroll: Boolean = false,
    showTraffic: Boolean = false,
    onToggleTraffic: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val offlineStates by viewModel.offlineInstallStates.collectAsState()
    val expandedCountryIso = viewModel.expandedCountryIso
    val suggestedRegionId = viewModel.suggestedRegionId
    val density = LocalDensity.current.density

    LaunchedEffect(currentLat, currentLng) {
        viewModel.updateSuggestedRegion(currentLat, currentLng)
    }

    val placesMb = formatStorageMb(viewModel.placesStorageBytes())
    val mapsMb = formatStorageMb(viewModel.offlineMapsStorageBytes())
    val launcherViewModel: LauncherViewModel = viewModel()
    val allowMapDownloadOnMobileData = launcherViewModel.allowMapDownloadOnMobileData
    val showDetailUpgradeBanner = offlineStates.values.any { it.needsDetailUpgrade } &&
        !launcherViewModel.offlineDetailUpgradeBannerDismissed

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CarDimensions.DockItemSpacing),
    ) {
        if (showDetailUpgradeBanner) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DeepCharcoal,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CarDimensions.PaneGap, vertical = CarDimensions.PaneGap / 2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(CarDimensions.PanelHeaderIconSize),
                    )
                    CarBodyText(
                        text = "Street-detail map update available — tap Update on your region.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { launcherViewModel.dismissOfflineDetailUpgradeBanner() },
                        modifier = Modifier.size(CarDimensions.PanelHeaderTapTarget),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        CarBodyText(
            text = "Download offline search and regional maps. Map packs are per region; POI search is nationwide.",
            style = MaterialTheme.typography.bodyMedium,
        )

        SettingsSwitchRow(
            label = "Download on mobile data",
            checked = allowMapDownloadOnMobileData,
            onCheckedChange = { checked ->
                if (checked != allowMapDownloadOnMobileData) {
                    launcherViewModel.toggleAllowMapDownloadOnMobileData()
                }
            },
        )

        CarLabelText(
            text = if (allowMapDownloadOnMobileData) {
                "POI and map packs may use cellular data. Large downloads can use a lot of data."
            } else {
                "POI and map packs use Wi‑Fi or Ethernet only unless mobile data is enabled above."
            },
            style = MaterialTheme.typography.labelMedium,
        )

        CarLabelText(
            text = "Storage on device: Places $placesMb · Maps $mapsMb",
            style = MaterialTheme.typography.labelMedium,
        )

        when (val state = uiState) {
            MapDataUiState.LoadingCatalog -> {
                Box(
                    modifier = catalogModifier,
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
                    modifier = catalogModifier,
                    packs = state.packs,
                    uiState = uiState,
                    offlineCatalog = viewModel.offlineCatalog,
                    offlineStates = offlineStates,
                    expandedCountryIso = expandedCountryIso,
                    suggestedRegionId = suggestedRegionId,
                    useVectorTiles = useVectorTiles,
                    pixelRatio = density,
                    onToggleCountry = viewModel::toggleCountryExpanded,
                    onDownloadPoi = viewModel::downloadCountryData,
                    onDeletePoi = viewModel::deleteDatabase,
                    onDownloadRegion = viewModel::downloadOfflineRegion,
                    onDeleteRegion = viewModel::deleteOfflineRegion,
                    regionDefinitions = viewModel::regionForCountry,
                    useInternalScroll = useInternalCatalogScroll,
                )
            }
            is MapDataUiState.Downloading -> {
                CountryCatalogList(
                    modifier = catalogModifier,
                    packs = state.packs,
                    uiState = uiState,
                    offlineCatalog = viewModel.offlineCatalog,
                    offlineStates = offlineStates,
                    expandedCountryIso = expandedCountryIso,
                    suggestedRegionId = suggestedRegionId,
                    useVectorTiles = useVectorTiles,
                    pixelRatio = density,
                    onToggleCountry = viewModel::toggleCountryExpanded,
                    onDownloadPoi = viewModel::downloadCountryData,
                    onDeletePoi = viewModel::deleteDatabase,
                    onDownloadRegion = viewModel::downloadOfflineRegion,
                    onDeleteRegion = viewModel::deleteOfflineRegion,
                    regionDefinitions = viewModel::regionForCountry,
                    useInternalScroll = useInternalCatalogScroll,
                )
            }
            is MapDataUiState.DownloadingOfflineMap -> {
                val offlineInstall = offlineStates[state.regionId]
                val sizeEstimateMb = viewModel.regionDefinition(state.regionId)?.sizeEstimateMb
                when {
                    state.isFinishing -> {
                        LinearProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        CarLabelText(
                            text = "Finishing installation…",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    state.isPreparing -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        CarLabelText(
                            text = "Preparing ${state.regionName}…",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        OfflineDownloadStatusHints(
                            isPreparing = true,
                            completedResourceCount = offlineInstall?.completedResourceCount ?: 0L,
                            requiredResourceCount = offlineInstall?.requiredResourceCount ?: 0L,
                            completedResourceSize = offlineInstall?.completedResourceSize ?: 0L,
                            resourceProgress = 0f,
                            sizeEstimateMb = sizeEstimateMb,
                        )
                    }
                    else -> {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        CarLabelText(
                            text = buildDownloadProgressLabel(
                                regionName = state.regionName,
                                progress = state.progress,
                                completedCount = offlineInstall?.completedResourceCount ?: 0L,
                                requiredCount = offlineInstall?.requiredResourceCount ?: 0L,
                                completedBytes = offlineInstall?.completedResourceSize ?: 0L,
                                sizeEstimateMb = sizeEstimateMb,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        OfflineDownloadStatusHints(
                            isPreparing = false,
                            completedResourceCount = offlineInstall?.completedResourceCount ?: 0L,
                            requiredResourceCount = offlineInstall?.requiredResourceCount ?: 0L,
                            completedResourceSize = offlineInstall?.completedResourceSize ?: 0L,
                            resourceProgress = offlineInstall?.downloadProgress ?: 0f,
                            sizeEstimateMb = sizeEstimateMb,
                        )
                    }
                }
                CountryCatalogList(
                    modifier = catalogModifier,
                    packs = state.packs,
                    uiState = uiState,
                    offlineCatalog = viewModel.offlineCatalog,
                    offlineStates = offlineStates,
                    expandedCountryIso = expandedCountryIso,
                    suggestedRegionId = suggestedRegionId,
                    useVectorTiles = useVectorTiles,
                    pixelRatio = density,
                    onToggleCountry = viewModel::toggleCountryExpanded,
                    onDownloadPoi = viewModel::downloadCountryData,
                    onDeletePoi = viewModel::deleteDatabase,
                    onDownloadRegion = viewModel::downloadOfflineRegion,
                    onDeleteRegion = viewModel::deleteOfflineRegion,
                    regionDefinitions = viewModel::regionForCountry,
                    useInternalScroll = useInternalCatalogScroll,
                )
            }
            MapDataUiState.Idle -> Unit
        }

        TomTomApiKeySection(
            tomTomApiKey = tomTomApiKey,
            onTomTomApiKeyChange = onTomTomApiKeyChange,
            showTraffic = showTraffic,
            onToggleTraffic = onToggleTraffic,
        )
    }
}

private fun formatStorageMb(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb < 10.0) {
        String.format("%.1f MB", mb)
    } else {
        "${mb.roundToInt()} MB"
    }
}

private fun buildDownloadProgressLabel(
    regionName: String,
    progress: Float,
    completedCount: Long,
    requiredCount: Long,
    completedBytes: Long,
    sizeEstimateMb: String?,
): String = buildString {
    append("Downloading ")
    append(regionName)
    append("… ")
    val mbLabel = formatOfflineMbProgressLabel(completedBytes, sizeEstimateMb)
    if (mbLabel != null) {
        append(mbLabel)
    } else {
        append((progress * 100f).roundToInt())
        append('%')
    }
    if (requiredCount > 0L) {
        append(" · ")
        append(completedCount)
        append(" / ")
        append(requiredCount)
        append(" resources")
    }
}
