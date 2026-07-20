package com.kyuusanq3.mixauto.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kyuusanq3.mixauto.data.map.OfflineRegionDefinition
import com.kyuusanq3.mixauto.data.map.OfflineRegionInstallState
import com.kyuusanq3.mixauto.ui.settings.MapDataUiState
import com.kyuusanq3.mixauto.ui.settings.RemoteCountryPack
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.DeepCharcoal
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack

@Composable
internal fun CountryCatalogList(
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
