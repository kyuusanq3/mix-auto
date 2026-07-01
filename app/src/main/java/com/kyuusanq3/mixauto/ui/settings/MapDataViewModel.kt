package com.kyuusanq3.mixauto.ui.settings

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kyuusanq3.mixauto.data.map.MapDownloadNetworkGate
import com.kyuusanq3.mixauto.data.map.OfflineCountryCatalog
import com.kyuusanq3.mixauto.data.map.OfflineMapRepository
import com.kyuusanq3.mixauto.data.map.OfflineRegionDefinition
import com.kyuusanq3.mixauto.data.map.OfflineRegionInstallState
import com.kyuusanq3.mixauto.data.places.LocalDbMeta
import com.kyuusanq3.mixauto.service.OfflineMapDownloadService
import com.kyuusanq3.mixauto.data.places.LocalPlacesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class RemoteCountryPack(
    val iso: String,
    val name: String,
    val downloadUrl: String,
    val compressedMb: Int,
    val isInstalled: Boolean,
)

sealed class MapDataUiState {
    data object Idle : MapDataUiState()
    data object LoadingCatalog : MapDataUiState()
    data class Catalog(val packs: List<RemoteCountryPack>) : MapDataUiState()
    data class Downloading(
        val iso: String,
        val name: String,
        val progress: Float,
        val packs: List<RemoteCountryPack>,
    ) : MapDataUiState()
    data class DownloadingOfflineMap(
        val regionId: String,
        val regionName: String,
        val progress: Float,
        val isPreparing: Boolean = false,
        val packs: List<RemoteCountryPack>,
    ) : MapDataUiState()
    data class Importing(val progress: Float, val label: String) : MapDataUiState()
    data class Error(val message: String) : MapDataUiState()
}

class MapDataViewModel(
    application: Application,
    private val localPlacesRepository: LocalPlacesRepository,
    private val offlineMapRepository: OfflineMapRepository,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<MapDataUiState>(MapDataUiState.LoadingCatalog)
    val uiState: StateFlow<MapDataUiState> = _uiState.asStateFlow()

    val offlineCatalog: List<OfflineCountryCatalog> = offlineMapRepository.catalogCountries()
    val offlineInstallStates: StateFlow<Map<String, OfflineRegionInstallState>> =
        offlineMapRepository.installStates

    var expandedCountryIso by mutableStateOf<String?>(offlineCatalog.firstOrNull()?.iso)
        private set

    var suggestedRegionId by mutableStateOf<String?>(null)
        private set

    private var catalogPacks: List<RemoteCountryPack> = emptyList()

    private val launcherPreferences = LauncherPreferences(application)

    private fun mapDownloadBlockReason(): String? =
        MapDownloadNetworkGate.blockReason(
            getApplication(),
            launcherPreferences.allowMapDownloadOnMobileData,
        )

    init {
        loadCatalog()
        refreshOfflineRegions()
        viewModelScope.launch {
            offlineMapRepository.installStates.collect { states ->
                val failed = states.values.firstOrNull {
                    !it.errorMessage.isNullOrBlank() && !it.isComplete && !it.isDownloading
                }
                if (failed != null) {
                    _uiState.value = MapDataUiState.Error(
                        failed.errorMessage ?: "Offline map download failed",
                    )
                }
                val downloading = states.values.firstOrNull { it.isDownloading }
                val current = _uiState.value
                if (downloading != null && current !is MapDataUiState.DownloadingOfflineMap) {
                    val definition = offlineMapRepository.regionDefinition(downloading.regionId)
                    _uiState.value = MapDataUiState.DownloadingOfflineMap(
                        regionId = downloading.regionId,
                        regionName = definition?.name ?: downloading.regionId,
                        progress = downloading.downloadProgress,
                        isPreparing = downloading.requiredResourceCount == 0L,
                        packs = catalogPacks,
                    )
                } else if (downloading != null && current is MapDataUiState.DownloadingOfflineMap) {
                    _uiState.value = current.copy(
                        progress = downloading.downloadProgress,
                        isPreparing = downloading.requiredResourceCount == 0L,
                    )
                } else if (downloading == null && current is MapDataUiState.DownloadingOfflineMap) {
                    refreshCatalogAfterChange()
                }
            }
        }
    }

    fun loadCatalog() {
        viewModelScope.launch {
            _uiState.value = MapDataUiState.LoadingCatalog
            val result = withContext(Dispatchers.IO) { fetchCatalog() }
            result.fold(
                onSuccess = { packs ->
                    catalogPacks = packs
                    _uiState.value = MapDataUiState.Catalog(packs)
                },
                onFailure = { error ->
                    _uiState.value = MapDataUiState.Error(
                        error.message ?: "Could not load country catalog",
                    )
                },
            )
        }
    }

    fun refreshOfflineRegions() {
        offlineMapRepository.refreshInstallStates()
    }

    fun updateSuggestedRegion(lat: Double?, lng: Double?) {
        suggestedRegionId = offlineMapRepository.suggestRegionId(lat, lng)
    }

    fun toggleCountryExpanded(iso: String) {
        expandedCountryIso = if (expandedCountryIso == iso) null else iso
    }

    fun placesStorageBytes(): Long {
        val placesDir = File(getApplication<Application>().filesDir, "places")
        return placesDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".db", ignoreCase = true) }
            ?.sumOf { it.length() }
            ?: 0L
    }

    fun offlineMapsStorageBytes(): Long = offlineMapRepository.totalInstalledBytes()

    fun downloadOfflineRegion(regionId: String, pixelRatio: Float) {
        if (isTransferInProgress()) return

        mapDownloadBlockReason()?.let { reason ->
            _uiState.value = MapDataUiState.Error(reason)
            return
        }

        val definition = offlineMapRepository.regionDefinition(regionId) ?: return
        _uiState.value = MapDataUiState.DownloadingOfflineMap(
            regionId = regionId,
            regionName = definition.name,
            progress = 0f,
            packs = catalogPacks,
        )

        viewModelScope.launch {
            OfflineMapDownloadService.startDownload(
                context = getApplication(),
                regionId = regionId,
                pixelRatio = pixelRatio,
            )
        }
    }

    fun deleteOfflineRegion(regionId: String) {
        OfflineMapDownloadService.stop(getApplication())
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { offlineMapRepository.deleteRegion(regionId) }
            }
            result.fold(
                onSuccess = {
                    offlineMapRepository.refreshInstallStates()
                    refreshCatalogAfterChange()
                },
                onFailure = { error ->
                    _uiState.value = MapDataUiState.Error(
                        error.message ?: "Could not delete offline map",
                    )
                },
            )
        }
    }

    fun downloadCountryData(pack: RemoteCountryPack) {
        if (isTransferInProgress()) return

        mapDownloadBlockReason()?.let { reason ->
            _uiState.value = MapDataUiState.Error(reason)
            return
        }

        viewModelScope.launch {
            _uiState.value = MapDataUiState.Downloading(
                iso = pack.iso,
                name = pack.name,
                progress = 0f,
                packs = catalogPacks,
            )

            val result = withContext(Dispatchers.IO) {
                localPlacesRepository.downloadDatabaseFromUrl(
                    isoCode = pack.iso,
                    url = pack.downloadUrl,
                    onProgress = { progress ->
                        _uiState.value = MapDataUiState.Downloading(
                            iso = pack.iso,
                            name = pack.name,
                            progress = progress,
                            packs = catalogPacks,
                        )
                    },
                )
            }

            result.fold(
                onSuccess = { refreshCatalogAfterChange() },
                onFailure = { error ->
                    _uiState.value = MapDataUiState.Error(
                        error.message ?: "Download failed",
                    )
                },
            )
        }
    }

    fun deleteDatabase(iso: String) {
        if (isTransferInProgress()) return

        localPlacesRepository.deleteDatabase(iso)
        refreshCatalogAfterChange()
    }

    fun importPhilippinesDatabase(uri: Uri) {
        if (isTransferInProgress()) return

        viewModelScope.launch {
            _uiState.value = MapDataUiState.Importing(
                progress = 0f,
                label = "Philippines",
            )

            val result = withContext(Dispatchers.IO) {
                localPlacesRepository.importDatabaseFromUri(
                    defaultIsoCode = PH_ISO,
                    uri = uri,
                    onProgress = { progress ->
                        _uiState.value = MapDataUiState.Importing(
                            progress = progress,
                            label = "Philippines",
                        )
                    },
                )
            }

            result.fold(
                onSuccess = { refreshCatalogAfterChange() },
                onFailure = { error ->
                    _uiState.value = MapDataUiState.Error(
                        error.message ?: "Import failed",
                    )
                },
            )
        }
    }

    fun installSamplePhilippinesData() {
        if (isTransferInProgress()) return

        viewModelScope.launch {
            _uiState.value = MapDataUiState.Importing(
                progress = 0f,
                label = "Philippines (sample)",
            )

            val result = withContext(Dispatchers.IO) {
                val installed = localPlacesRepository.installFromAsset(
                    assetPath = SAMPLE_PH_ASSET,
                    isoCode = PH_ISO,
                )
                if (!installed) {
                    return@withContext Result.failure<LocalDbMeta>(
                        Exception("Could not install bundled sample database"),
                    )
                }
                localPlacesRepository.readMeta(PH_ISO)?.let { Result.success(it) }
                    ?: Result.failure(Exception("Sample database has invalid metadata"))
            }

            result.fold(
                onSuccess = { refreshCatalogAfterChange() },
                onFailure = { error ->
                    _uiState.value = MapDataUiState.Error(
                        error.message ?: "Sample install failed",
                    )
                },
            )
        }
    }

    fun regionForCountry(iso: String): List<OfflineRegionDefinition> =
        offlineCatalog.firstOrNull { it.iso.equals(iso, ignoreCase = true) }?.regions.orEmpty()

    private fun isTransferInProgress(): Boolean = when (_uiState.value) {
        is MapDataUiState.Downloading,
        is MapDataUiState.DownloadingOfflineMap,
        is MapDataUiState.Importing,
        -> true
        else -> false
    }

    private fun refreshCatalogAfterChange() {
        viewModelScope.launch {
            offlineMapRepository.refreshInstallStates()
            val result = withContext(Dispatchers.IO) { fetchCatalog() }
            result.fold(
                onSuccess = { packs ->
                    catalogPacks = packs
                    _uiState.value = MapDataUiState.Catalog(packs)
                },
                onFailure = {
                    _uiState.value = MapDataUiState.Catalog(
                        catalogPacks.map { pack ->
                            pack.copy(
                                isInstalled = localPlacesRepository.isDatabaseInstalled(pack.iso),
                            )
                        },
                    )
                },
            )
        }
    }

    private fun fetchCatalog(): Result<List<RemoteCountryPack>> {
        return try {
            val connection = URL(CATALOG_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = CATALOG_CONNECT_TIMEOUT_MS
            connection.readTimeout = CATALOG_READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", CATALOG_USER_AGENT)
            connection.instanceFollowRedirects = true

            try {
                if (connection.responseCode !in 200..299) {
                    return Result.failure(
                        Exception("Catalog fetch failed: HTTP ${connection.responseCode}"),
                    )
                }

                val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(jsonText)
                val packs = buildList {
                    for (index in 0 until array.length()) {
                        val entry = array.getJSONObject(index)
                        val iso = entry.getString("iso").uppercase()
                        val asset = entry.getString("asset")
                        add(
                            RemoteCountryPack(
                                iso = iso,
                                name = entry.getString("name"),
                                downloadUrl = "$RELEASE_BASE_URL/$asset",
                                compressedMb = entry.optInt("size_compressed_mb", 0),
                                isInstalled = localPlacesRepository.isDatabaseInstalled(iso),
                            ),
                        )
                    }
                }
                Result.success(packs)
            } finally {
                connection.disconnect()
            }
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    companion object {
        const val PH_ISO = "PH"
        private const val SAMPLE_PH_ASSET = "places/ph_sample.db"
        private const val CATALOG_USER_AGENT = "MixAutoCarLauncher/1.0"
        private const val CATALOG_CONNECT_TIMEOUT_MS = 30_000
        private const val CATALOG_READ_TIMEOUT_MS = 60_000
        private const val DATA_REPO = "kyuusanq3/mix-auto-overture-maps"
        private const val CATALOG_URL =
            "https://github.com/$DATA_REPO/releases/latest/download/countries.json"
        private const val RELEASE_BASE_URL =
            "https://github.com/$DATA_REPO/releases/latest/download"
    }
}
