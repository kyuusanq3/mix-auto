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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyuusanq3.mixauto.data.map.OfflineRegionDefinition
import com.kyuusanq3.mixauto.data.map.OfflineRegionInstallState
import com.kyuusanq3.mixauto.ui.settings.LauncherViewModel
import com.kyuusanq3.mixauto.ui.settings.MapDataUiState
import com.kyuusanq3.mixauto.ui.settings.MapDataViewModel
import com.kyuusanq3.mixauto.ui.settings.RemoteCountryPack
import com.kyuusanq3.mixauto.ui.settings.TomTomKeyCheckState
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.DeepCharcoal
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val InstalledGreen = Color(0xFF4CAF50)

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
                LinearProgressIndicator(
                    progress = { if (state.isPreparing) 0f else state.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                CarLabelText(
                    text = if (state.isPreparing) {
                        "Preparing ${state.regionName}…"
                    } else {
                        "Downloading ${state.regionName}… ${(state.progress * 100).roundToInt()}%"
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
                OfflineDownloadStatusHints(
                    isPreparing = state.isPreparing,
                    completedResourceCount = offlineInstall?.completedResourceCount ?: 0L,
                    requiredResourceCount = offlineInstall?.requiredResourceCount ?: 0L,
                )
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

@Composable
private fun TomTomApiKeySection(
    tomTomApiKey: String,
    onTomTomApiKeyChange: (String) -> Unit,
    showTraffic: Boolean,
    onToggleTraffic: (() -> Unit)?,
) {
    val launcherViewModel: LauncherViewModel = viewModel()
    val tomTomKeyCheckState = launcherViewModel.tomTomKeyCheckState
    val isChecking = tomTomKeyCheckState is TomTomKeyCheckState.Checking

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
            trailingIcon = {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(CarDimensions.PanelHeaderIconSize),
                        color = ElectricCyan,
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(
                        onClick = launcherViewModel::checkTomTomApiKey,
                        modifier = Modifier.size(CarDimensions.PanelHeaderTapTarget),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NetworkCheck,
                            contentDescription = "Test TomTom API Key",
                            modifier = Modifier.size(CarDimensions.PanelHeaderIconSize),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
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
            text = "Free tier covers Philippines. Required for traffic overlay.",
            style = MaterialTheme.typography.labelMedium,
        )
        if (onToggleTraffic != null) {
            SettingsSwitchRow(
                label = "Traffic Overlay",
                checked = showTraffic,
                onCheckedChange = { checked ->
                    if (checked != showTraffic) {
                        onToggleTraffic()
                    }
                },
            )
        }
        when (tomTomKeyCheckState) {
            TomTomKeyCheckState.Idle, TomTomKeyCheckState.Checking -> Unit
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
    }
}

@Composable
private fun CountryCatalogList(
    modifier: Modifier = Modifier,
    packs: List<RemoteCountryPack>,
    uiState: MapDataUiState,
    offlineCatalog: List<com.kyuusanq3.mixauto.data.map.OfflineCountryCatalog>,
    offlineStates: Map<String, OfflineRegionInstallState>,
    expandedCountryIso: String?,
    suggestedRegionId: String?,
    useVectorTiles: Boolean,
    pixelRatio: Float,
    onToggleCountry: (String) -> Unit,
    onDownloadPoi: (RemoteCountryPack) -> Unit,
    onDeletePoi: (String) -> Unit,
    onDownloadRegion: (String, Float) -> Unit,
    onDeleteRegion: (String) -> Unit,
    regionDefinitions: (String) -> List<OfflineRegionDefinition>,
    useInternalScroll: Boolean = true,
) {
    val scrollState = rememberScrollState()
    val columnContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CarDimensions.DockItemSpacing / 2),
        ) {
            if (packs.isEmpty() && offlineCatalog.isEmpty()) {
                CarBodyText(
                    text = "No countries in catalog.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                val packByIso = packs.associateBy { it.iso.uppercase() }
                val countries = offlineCatalog.ifEmpty {
                    packs.map { com.kyuusanq3.mixauto.data.map.OfflineCountryCatalog(it.iso, it.name, emptyList()) }
                }
                countries.forEach { country ->
                    val poiPack = packByIso[country.iso.uppercase()]
                    CountryGroupCard(
                        countryIso = country.iso,
                        countryName = country.name,
                        poiPack = poiPack,
                        regions = regionDefinitions(country.iso).ifEmpty { country.regions },
                        offlineStates = offlineStates,
                        isExpanded = expandedCountryIso == country.iso,
                        suggestedRegionId = suggestedRegionId,
                        useVectorTiles = useVectorTiles,
                        pixelRatio = pixelRatio,
                        uiState = uiState,
                        onToggleCountry = { onToggleCountry(country.iso) },
                        onDownloadPoi = poiPack?.let { { onDownloadPoi(it) } },
                        onDeletePoi = { onDeletePoi(country.iso) },
                        onDownloadRegion = onDownloadRegion,
                        onDeleteRegion = onDeleteRegion,
                    )
                }
            }
        }
    }

    if (useInternalScroll) {
        Box(modifier = modifier.carScrollbar(scrollState)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
            ) {
                columnContent()
            }
        }
    } else {
        Box(modifier = modifier) {
            columnContent()
        }
    }
}

@Composable
private fun CountryGroupCard(
    countryIso: String,
    countryName: String,
    poiPack: RemoteCountryPack?,
    regions: List<OfflineRegionDefinition>,
    offlineStates: Map<String, OfflineRegionInstallState>,
    isExpanded: Boolean,
    suggestedRegionId: String?,
    useVectorTiles: Boolean,
    pixelRatio: Float,
    uiState: MapDataUiState,
    onToggleCountry: () -> Unit,
    onDownloadPoi: (() -> Unit)?,
    onDeletePoi: () -> Unit,
    onDownloadRegion: (String, Float) -> Unit,
    onDeleteRegion: (String) -> Unit,
) {
    val installedMapCount = regions.count { offlineStates[it.id]?.isComplete == true }
    val poiInstalled = poiPack?.isInstalled == true

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = DeepCharcoal,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CarDimensions.MinTapTarget)
                    .clickable(onClick = onToggleCountry)
                    .padding(horizontal = CarDimensions.PaneGap),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = ElectricCyan,
                    modifier = Modifier.size(CarDimensions.DockHorizontalIconSize),
                )
                Column(modifier = Modifier.weight(1f)) {
                    CarBodyText(
                        text = countryName,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    CarLabelText(
                        text = buildString {
                            append("Search: ")
                            append(if (poiInstalled) "Installed" else "Not installed")
                            append(" · Maps: ")
                            append(installedMapCount)
                            append(" region")
                            if (installedMapCount != 1) append('s')
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = CarDimensions.PaneGap,
                            end = CarDimensions.PaneGap,
                            bottom = CarDimensions.PaneGap,
                        ),
                    verticalArrangement = Arrangement.spacedBy(CarDimensions.DockItemSpacing / 2),
                ) {
                    if (poiPack != null) {
                        CarLabelText(
                            text = "Search & places",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        PoiPackRow(
                            pack = poiPack,
                            uiState = uiState,
                            onDownload = onDownloadPoi,
                            onDelete = onDeletePoi,
                        )
                    }

                    CarLabelText(
                        text = "Offline maps (per region)",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    CarLabelText(
                        text = if (useVectorTiles) {
                            "Street detail to zoom 17."
                        } else {
                            "Offline map regions require vector tiles (enable in Map Settings above)."
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )

                    if (useVectorTiles) {
                        val sortedRegions = regions.sortedWith(
                            compareBy<OfflineRegionDefinition> { it.id != suggestedRegionId }
                                .thenBy { it.name },
                        )
                        sortedRegions.forEach { region ->
                            val isSuggested = region.id == suggestedRegionId
                            OfflineRegionRow(
                                region = region,
                                installState = offlineStates[region.id],
                                isSuggested = isSuggested,
                                uiState = uiState,
                                pixelRatio = pixelRatio,
                                onDownload = { onDownloadRegion(region.id, pixelRatio) },
                                onDelete = { onDeleteRegion(region.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PoiPackRow(
    pack: RemoteCountryPack,
    uiState: MapDataUiState,
    onDownload: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    val isDownloading = uiState is MapDataUiState.Downloading && uiState.iso == pack.iso
    val downloadProgress = (uiState as? MapDataUiState.Downloading)?.progress ?: 0f
    val transferInProgress = uiState is MapDataUiState.Downloading ||
        uiState is MapDataUiState.DownloadingOfflineMap ||
        uiState is MapDataUiState.Importing

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = OledBlack,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(CarDimensions.MinTapTarget)
                .padding(horizontal = CarDimensions.PaneGap / 2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CarBodyText(
                    text = "${pack.name} POI pack",
                    style = MaterialTheme.typography.bodyMedium,
                )
                CarLabelText(
                    text = "Offline search, Nearby, passed places" +
                        if (pack.compressedMb > 0) " · ~${pack.compressedMb} MB download" else "",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            PackRowActions(
                isDownloading = isDownloading,
                downloadProgress = downloadProgress,
                isInstalled = pack.isInstalled,
                transferInProgress = transferInProgress,
                itemName = pack.name,
                onDownload = onDownload,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun OfflineRegionRow(
    region: OfflineRegionDefinition,
    installState: OfflineRegionInstallState?,
    isSuggested: Boolean,
    uiState: MapDataUiState,
    pixelRatio: Float,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showUpdateConfirm by remember { mutableStateOf(false) }
    val isDownloading = uiState is MapDataUiState.DownloadingOfflineMap &&
        uiState.regionId == region.id
    val isPreparing = (isDownloading || installState?.isDownloading == true) &&
        (installState?.requiredResourceCount ?: 0L) == 0L
    val downloadProgress = when {
        isDownloading -> (uiState as MapDataUiState.DownloadingOfflineMap).progress
        installState?.isDownloading == true -> installState.downloadProgress
        else -> 0f
    }
    val needsDetailUpgrade = installState?.needsDetailUpgrade == true
    val isInstalledCurrent = installState?.isCurrentDetail == true
    val transferInProgress = uiState is MapDataUiState.Downloading ||
        uiState is MapDataUiState.DownloadingOfflineMap ||
        uiState is MapDataUiState.Importing

    if (showUpdateConfirm) {
        AlertDialog(
            onDismissRequest = { showUpdateConfirm = false },
            title = { CarBodyText(text = "Update ${region.name}?") },
            text = {
                CarBodyText(
                    text = "Replaces the installed z${installState?.installedMaxZoom ?: "?"} pack with " +
                        "street detail to zoom ${region.maxZoom.toInt()} (~${region.sizeEstimateMb} MB). " +
                        "The old map tiles will be removed first.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUpdateConfirm = false
                        onDownload()
                    },
                ) {
                    CarLabelText(text = "Update", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateConfirm = false }) {
                    CarLabelText(text = "Cancel", style = MaterialTheme.typography.labelLarge)
                }
            },
            containerColor = DeepCharcoal,
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { CarBodyText(text = "Delete ${region.name}?") },
            text = {
                CarBodyText(
                    text = "Removes cached map tiles for this region. Search data is not affected.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                ) {
                    CarLabelText(text = "Delete", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    CarLabelText(text = "Cancel", style = MaterialTheme.typography.labelLarge)
                }
            },
            containerColor = DeepCharcoal,
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = OledBlack,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CarDimensions.MinTapTarget)
                    .padding(horizontal = CarDimensions.PaneGap / 2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
            ) {
                if (isSuggested) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Near you",
                        tint = ElectricCyan,
                        modifier = Modifier.size(CarDimensions.PanelHeaderIconSize),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    CarBodyText(
                        text = if (isSuggested) "Near you — ${region.name}" else region.name,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    CarLabelText(
                        text = buildString {
                            if (isSuggested) append("Suggested from GPS · ")
                            if (needsDetailUpgrade) {
                                append("Installed z${installState?.installedMaxZoom ?: "?"} · ")
                                append("Update to z${region.maxZoom.toInt()} (~${region.sizeEstimateMb} MB)")
                            } else {
                                append("z${region.minZoom.toInt()}–${region.maxZoom.toInt()}")
                                append(" · est. ${region.sizeEstimateMb} MB")
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                PackRowActions(
                    isDownloading = isDownloading || installState?.isDownloading == true,
                    isPreparing = isPreparing,
                    downloadProgress = downloadProgress,
                    isInstalled = isInstalledCurrent,
                    needsDetailUpgrade = needsDetailUpgrade,
                    transferInProgress = transferInProgress,
                    itemName = region.name,
                    onDownload = onDownload,
                    onUpdate = { showUpdateConfirm = true },
                    onDelete = { showDeleteConfirm = true },
                    onCancelDownload = if (isDownloading || installState?.isDownloading == true) {
                        { showDeleteConfirm = true }
                    } else {
                        null
                    },
                    showDelete = isInstalledCurrent || needsDetailUpgrade ||
                        (installState?.completedResourceCount ?: 0L) > 0L,
                )
            }
            if (isDownloading || installState?.isDownloading == true) {
                OfflineDownloadStatusHints(
                    isPreparing = isPreparing,
                    completedResourceCount = installState?.completedResourceCount ?: 0L,
                    requiredResourceCount = installState?.requiredResourceCount ?: 0L,
                    modifier = Modifier.padding(
                        start = CarDimensions.PaneGap / 2,
                        end = CarDimensions.PaneGap / 2,
                        bottom = CarDimensions.PaneGap / 2,
                    ),
                )
            }
        }
    }
}

@Composable
private fun OfflineDownloadStatusHints(
    isPreparing: Boolean,
    completedResourceCount: Long,
    requiredResourceCount: Long,
    modifier: Modifier = Modifier,
) {
    var showPreparingHint by remember { mutableStateOf(false) }
    var showStallHint by remember { mutableStateOf(false) }

    LaunchedEffect(isPreparing) {
        showPreparingHint = false
        if (isPreparing) {
            delay(30_000)
            showPreparingHint = true
        }
    }

    LaunchedEffect(isPreparing, completedResourceCount, requiredResourceCount) {
        showStallHint = false
        if (isPreparing || requiredResourceCount == 0L) return@LaunchedEffect
        val snapshot = completedResourceCount
        delay(5 * 60 * 1000L)
        if (!isPreparing && requiredResourceCount > 0L && completedResourceCount == snapshot) {
            showStallHint = true
        }
    }

    Column(modifier = modifier) {
        if (showPreparingHint && isPreparing) {
            CarLabelText(
                text = "Building tile list from OpenFreeMap — can take 1–2 minutes on first download.",
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (showStallHint) {
            CarLabelText(
                text = "Stuck? Tap ✕ to cancel, delete the region, and retry on Wi‑Fi.",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun PackRowActions(
    isDownloading: Boolean,
    isPreparing: Boolean = false,
    downloadProgress: Float,
    isInstalled: Boolean,
    needsDetailUpgrade: Boolean = false,
    transferInProgress: Boolean,
    itemName: String,
    onDownload: (() -> Unit)?,
    onUpdate: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onCancelDownload: (() -> Unit)? = null,
    showDelete: Boolean = isInstalled,
) {
    when {
        isDownloading -> {
            CircularProgressIndicator(
                modifier = Modifier.size(CarDimensions.DockHorizontalIconSize),
                color = ElectricCyan,
                strokeWidth = 3.dp,
            )
            CarLabelText(
                text = if (isPreparing) {
                    "Preparing…"
                } else {
                    "${(downloadProgress * 100).roundToInt()}%"
                },
                style = MaterialTheme.typography.labelMedium,
            )
            if (onCancelDownload != null) {
                IconButton(
                    onClick = onCancelDownload,
                    modifier = Modifier.size(CarDimensions.MinTapTarget),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cancel download",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        isInstalled -> {
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
            if (showDelete) {
                IconButton(
                    onClick = onDelete,
                    enabled = !transferInProgress,
                    modifier = Modifier.size(CarDimensions.MinTapTarget),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete $itemName",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        needsDetailUpgrade -> {
            IconButton(
                onClick = { onUpdate?.invoke() },
                enabled = !transferInProgress && onUpdate != null,
                modifier = Modifier.size(CarDimensions.MinTapTarget),
            ) {
                Icon(
                    imageVector = Icons.Filled.SystemUpdate,
                    contentDescription = "Update $itemName",
                    tint = ElectricCyan,
                )
            }
            CarLabelText(
                text = "Update",
                style = MaterialTheme.typography.labelMedium.copy(color = ElectricCyan),
            )
            if (showDelete) {
                IconButton(
                    onClick = onDelete,
                    enabled = !transferInProgress,
                    modifier = Modifier.size(CarDimensions.MinTapTarget),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete $itemName",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        else -> {
            IconButton(
                onClick = { onDownload?.invoke() },
                enabled = !transferInProgress && onDownload != null,
                modifier = Modifier.size(CarDimensions.MinTapTarget),
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudDownload,
                    contentDescription = "Download $itemName",
                    tint = ElectricCyan,
                )
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

private fun formatStorageMb(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb < 10.0) {
        String.format("%.1f MB", mb)
    } else {
        "${mb.roundToInt()} MB"
    }
}
