package com.kyuusanq3.mixauto.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kyuusanq3.mixauto.data.places.LocalDbMeta
import com.kyuusanq3.mixauto.data.places.LocalPlacesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
    data class Importing(val progress: Float, val label: String) : MapDataUiState()
    data class Error(val message: String) : MapDataUiState()
}

class MapDataViewModel(
    application: Application,
    private val localPlacesRepository: LocalPlacesRepository,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<MapDataUiState>(MapDataUiState.LoadingCatalog)
    val uiState: StateFlow<MapDataUiState> = _uiState.asStateFlow()

    private var catalogPacks: List<RemoteCountryPack> = emptyList()

    init {
        loadCatalog()
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

    fun downloadCountryData(pack: RemoteCountryPack) {
        if (_uiState.value is MapDataUiState.Downloading ||
            _uiState.value is MapDataUiState.Importing
        ) {
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
        if (_uiState.value is MapDataUiState.Downloading ||
            _uiState.value is MapDataUiState.Importing
        ) {
            return
        }

        localPlacesRepository.deleteDatabase(iso)
        refreshCatalogAfterChange()
    }

    fun importPhilippinesDatabase(uri: Uri) {
        if (_uiState.value is MapDataUiState.Downloading ||
            _uiState.value is MapDataUiState.Importing
        ) {
            return
        }

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
        if (_uiState.value is MapDataUiState.Downloading ||
            _uiState.value is MapDataUiState.Importing
        ) {
            return
        }

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

    private fun refreshCatalogAfterChange() {
        viewModelScope.launch {
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
