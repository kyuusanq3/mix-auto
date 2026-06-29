package com.kyuusanq3.mixauto.data.map

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OfflineRegionDefinition(
    val id: String,
    val name: String,
    val countryIso: String,
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
    val minZoom: Double,
    val maxZoom: Double,
    val sizeEstimateMb: String,
) {
    fun contains(lat: Double, lng: Double): Boolean =
        lat in south..north && lng in west..east

    fun toBounds(): LatLngBounds = LatLngBounds.Builder()
        .include(LatLng(south, west))
        .include(LatLng(north, east))
        .build()
}

data class OfflineCountryCatalog(
    val iso: String,
    val name: String,
    val regions: List<OfflineRegionDefinition>,
)

data class OfflineRegionInstallState(
    val regionId: String,
    val isComplete: Boolean,
    val completedResourceCount: Long = 0,
    val requiredResourceCount: Long = 0,
    val completedResourceSize: Long = 0,
    val downloadProgress: Float = 0f,
    val isDownloading: Boolean = false,
    val errorMessage: String? = null,
)

class OfflineMapRepository(context: Context) {

    private val appContext = context.applicationContext

    private val catalog: List<OfflineCountryCatalog> = loadCatalog()
    private val regionById: Map<String, OfflineRegionDefinition> = catalog
        .flatMap { country -> country.regions.map { it to country.iso } }
        .associate { (region, iso) -> region.id to region.copy(countryIso = iso) }

    private val _installStates = MutableStateFlow<Map<String, OfflineRegionInstallState>>(emptyMap())
    val installStates: StateFlow<Map<String, OfflineRegionInstallState>> = _installStates.asStateFlow()

    private val offlineManager: OfflineManager

    init {
        MapLibreAppBootstrap.ensureInitialized(appContext)
        offlineManager = OfflineManager.getInstance(appContext)
        offlineManager.setOfflineMapboxTileCountLimit(OFFLINE_TILE_COUNT_LIMIT)
        OfflineMapRepositoryHolder.instance = this
        refreshInstallStates()
    }

    fun catalogCountries(): List<OfflineCountryCatalog> = catalog

    fun regionDefinition(regionId: String): OfflineRegionDefinition? = regionById[regionId]

    fun suggestRegionId(lat: Double?, lng: Double?): String? {
        if (lat == null || lng == null) return null
        return catalog
            .flatMap { it.regions }
            .filter { it.contains(lat, lng) && it.id != "ph_overview" }
            .minByOrNull { (it.east - it.west) * (it.north - it.south) }
            ?.id
    }

    fun hasCompleteRegionCovering(lat: Double, lng: Double): Boolean {
        val states = _installStates.value
        return regionById.values.any { def ->
            def.contains(lat, lng) && states[def.id]?.isComplete == true
        }
    }

    fun totalInstalledBytes(): Long =
        _installStates.value.values.sumOf { it.completedResourceSize }

    fun refreshInstallStates() {
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val base = regionById.keys.associateWith { regionId ->
                    OfflineRegionInstallState(regionId = regionId, isComplete = false)
                }.toMutableMap()

                val regions = offlineRegions?.toList().orEmpty()
                if (regions.isEmpty()) {
                    _installStates.value = base
                    return
                }

                var pending = 0
                for (sdkRegion in regions) {
                    val regionId = parseRegionId(sdkRegion) ?: continue
                    pending++
                    sdkRegion.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) {
                            if (status == null) {
                                pending--
                                if (pending <= 0) {
                                    _installStates.value = base.toMap()
                                }
                                return
                            }
                            base[regionId] = status.toInstallState(regionId)
                            pending--
                            if (pending <= 0) {
                                _installStates.value = base.toMap()
                            }
                        }

                        override fun onError(error: String?) {
                            Log.w(TAG, "getStatus failed for $regionId: $error")
                            pending--
                            if (pending <= 0) {
                                _installStates.value = base.toMap()
                            }
                        }
                    })
                }

                if (pending == 0) {
                    _installStates.value = base.toMap()
                }
            }

            override fun onError(error: String) {
                Log.w(TAG, "listOfflineRegions failed: $error")
            }
        })
    }

    suspend fun reportRegionDownloadError(regionId: String, message: String) {
        withContext(Dispatchers.Main) {
            _installStates.value = _installStates.value.toMutableMap().apply {
                put(
                    regionId,
                    OfflineRegionInstallState(
                        regionId = regionId,
                        isComplete = false,
                        isDownloading = false,
                        errorMessage = message,
                    ),
                )
            }
        }
    }

    suspend fun resumeIncompleteDownloads(): Boolean {
        val pending = findFirstResumableIncompleteRegion() ?: return false
        Log.i(TAG, "Resuming incomplete download for ${pending.regionId}")
        downloadRegion(pending.regionId, pending.pixelRatio)
        return true
    }

    suspend fun pauseDownload(regionId: String) {
        withContext(Dispatchers.Main) {
            val sdkRegion = findSdkRegion(regionId) ?: return@withContext
            sdkRegion.setObserver(null)
            sdkRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
            val status = sdkRegion.awaitStatus()
            updateStateFromStatus(regionId, status, isDownloading = false)
        }
    }

    suspend fun downloadRegion(regionId: String, pixelRatio: Float) {
        val styleUri = withContext(Dispatchers.IO) {
            MapStyleAssetResolver.prepareOfflineRegionStyleUri(appContext)
        }
        withContext(Dispatchers.Main) {
            withTimeout(DOWNLOAD_TIMEOUT_MS) {
                downloadRegionOnMain(regionId, pixelRatio, styleUri)
            }
        }
    }

    private suspend fun downloadRegionOnMain(
        regionId: String,
        pixelRatio: Float,
        styleUri: String,
    ) {
        val definition = regionById[regionId]
            ?: throw IllegalArgumentException("Unknown offline region: $regionId")

        markRegionDownloading(regionId)

        val effectivePixelRatio = pixelRatio.coerceIn(1f, MAX_OFFLINE_PIXEL_RATIO)

        val existing = findSdkRegion(regionId)
        if (existing != null) {
            val status = existing.awaitStatus()
            if (status.isComplete) {
                updateStateFromStatus(regionId, status, isDownloading = false)
                return
            }
            if (isResumable(status)) {
                Log.i(TAG, "Resuming offline region $regionId at ${status.completedResourceCount}/${status.requiredResourceCount}")
                observeAndActivate(existing, regionId)
                return
            }
            Log.w(TAG, "Replacing broken incomplete offline region $regionId")
            deleteSdkRegion(existing)
        }

        val metadata = metadataBytes(regionId, definition.name, effectivePixelRatio)
        val regionDefinition = OfflineTilePyramidRegionDefinition(
            styleUri,
            definition.toBounds(),
            definition.minZoom,
            definition.maxZoom,
            effectivePixelRatio,
        )

        Log.i(
            TAG,
            "Creating offline region $regionId style=$styleUri zoom=${definition.minZoom}-${definition.maxZoom} " +
                "pixelRatio=$effectivePixelRatio",
        )

        val created = suspendCancellableCoroutine<OfflineRegion> { cont ->
            offlineManager.createOfflineRegion(
                regionDefinition,
                metadata,
                object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(offlineRegion: OfflineRegion) {
                        if (cont.isActive) cont.resume(offlineRegion)
                    }

                    override fun onError(error: String) {
                        if (cont.isActive) {
                            cont.resumeWithException(Exception("Create offline region failed: $error"))
                        }
                    }
                },
            )
        }

        observeAndActivate(created, regionId)
    }

    suspend fun deleteRegion(regionId: String) {
        val sdkRegion = findSdkRegion(regionId)
            ?: throw IllegalArgumentException("Region not downloaded: $regionId")

        deleteSdkRegion(sdkRegion)
        _installStates.value = _installStates.value.toMutableMap().apply {
            put(regionId, OfflineRegionInstallState(regionId = regionId, isComplete = false))
        }
    }

    private suspend fun deleteSdkRegion(region: OfflineRegion) {
        suspendCancellableCoroutine { cont ->
            region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                override fun onDelete() {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onError(error: String) {
                    if (cont.isActive) {
                        cont.resumeWithException(Exception("Delete offline region failed: $error"))
                    }
                }
            })
        }
    }

    private suspend fun findSdkRegion(regionId: String): OfflineRegion? {
        val regions = suspendCancellableCoroutine<List<OfflineRegion>> { cont ->
            offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    if (cont.isActive) cont.resume(offlineRegions?.toList().orEmpty())
                }

                override fun onError(error: String) {
                    if (cont.isActive) {
                        cont.resumeWithException(Exception("listOfflineRegions failed: $error"))
                    }
                }
            })
        }
        return regions.firstOrNull { parseRegionId(it) == regionId }
    }

    private suspend fun observeAndActivate(region: OfflineRegion, regionId: String) {
        // MapLibre requires the observer before STATE_ACTIVE or progress can stall at 0% on device.
        withTimeout(PREPARE_TIMEOUT_MS) {
            waitForResourceQueue(region, regionId)
        }
        observeUntilComplete(region, regionId)
    }

    private suspend fun waitForResourceQueue(region: OfflineRegion, regionId: String) {
        suspendCancellableCoroutine { cont ->
            region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                override fun onStatusChanged(status: OfflineRegionStatus) {
                    logStatus(regionId, status, "prepare")
                    updateStateFromStatus(regionId, status, isDownloading = !status.isComplete)
                    if (status.requiredResourceCount > 0L || status.isComplete) {
                        region.setObserver(null)
                        if (cont.isActive) cont.resume(Unit)
                    }
                }

                override fun onError(error: OfflineRegionError) {
                    region.setObserver(null)
                    if (cont.isActive) {
                        cont.resumeWithException(Exception(error.message))
                    }
                }

                override fun mapboxTileCountLimitExceeded(limit: Long) {
                    region.setObserver(null)
                    if (cont.isActive) {
                        cont.resumeWithException(Exception("Offline tile limit exceeded ($limit)"))
                    }
                }
            })
            region.setDownloadState(OfflineRegion.STATE_ACTIVE)
            cont.invokeOnCancellation {
                region.setObserver(null)
            }
        }
    }

    private suspend fun observeUntilComplete(region: OfflineRegion, regionId: String) {
        suspendCancellableCoroutine { cont ->
            region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                override fun onStatusChanged(status: OfflineRegionStatus) {
                    logStatus(regionId, status, "download")
                    updateStateFromStatus(regionId, status, isDownloading = !status.isComplete)
                    if (status.isComplete && cont.isActive) {
                        cont.resume(Unit)
                    }
                }

                override fun onError(error: OfflineRegionError) {
                    Log.e(TAG, "Offline $regionId error: ${error.message} type=${error.reason}")
                    _installStates.value = _installStates.value.toMutableMap().apply {
                        put(
                            regionId,
                            OfflineRegionInstallState(
                                regionId = regionId,
                                isComplete = false,
                                isDownloading = false,
                                errorMessage = error.message,
                            ),
                        )
                    }
                    if (cont.isActive) {
                        cont.resumeWithException(Exception(error.message))
                    }
                }

                override fun mapboxTileCountLimitExceeded(limit: Long) {
                    val message = "Offline tile limit exceeded ($limit)"
                    _installStates.value = _installStates.value.toMutableMap().apply {
                        put(
                            regionId,
                            OfflineRegionInstallState(
                                regionId = regionId,
                                isComplete = false,
                                isDownloading = false,
                                errorMessage = message,
                            ),
                        )
                    }
                    if (cont.isActive) {
                        cont.resumeWithException(Exception(message))
                    }
                }
            })
            region.setDownloadState(OfflineRegion.STATE_ACTIVE)
            region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                override fun onStatus(status: OfflineRegionStatus?) {
                    if (status == null) return
                    logStatus(regionId, status, "download-snapshot")
                    updateStateFromStatus(regionId, status, isDownloading = !status.isComplete)
                    if (status.isComplete && cont.isActive) {
                        cont.resume(Unit)
                    }
                }

                override fun onError(error: String?) = Unit
            })
            cont.invokeOnCancellation {
                region.setObserver(null)
            }
        }
    }

    private fun isResumable(status: OfflineRegionStatus): Boolean {
        if (status.isComplete) return false
        // Legacy file:// style regions stall as a single unfetchable style resource.
        if (status.requiredResourceCount == 1L && status.completedResourceCount == 0L) return false
        return true
    }

    suspend fun findPendingResumeRegion(): PendingOfflineResume? = withContext(Dispatchers.Main) {
        findFirstResumableIncompleteRegion()
    }

    private suspend fun findFirstResumableIncompleteRegion(): PendingOfflineResume? {
        val regions = suspendCancellableCoroutine<List<OfflineRegion>> { cont ->
            offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    if (cont.isActive) cont.resume(offlineRegions?.toList().orEmpty())
                }

                override fun onError(error: String) {
                    if (cont.isActive) {
                        cont.resumeWithException(Exception("listOfflineRegions failed: $error"))
                    }
                }
            })
        }
        for (sdkRegion in regions) {
            val regionId = parseRegionId(sdkRegion) ?: continue
            val status = sdkRegion.awaitStatus()
            if (status.isComplete || !isResumable(status)) continue
            return PendingOfflineResume(
                regionId = regionId,
                pixelRatio = parsePixelRatio(sdkRegion),
            )
        }
        return null
    }

data class PendingOfflineResume(val regionId: String, val pixelRatio: Float)

    private fun logStatus(regionId: String, status: OfflineRegionStatus, phase: String) {
        Log.i(
            TAG,
            "Offline $regionId [$phase]: ${status.completedResourceCount}/${status.requiredResourceCount} " +
                "bytes=${status.completedResourceSize} complete=${status.isComplete} " +
                "state=${status.downloadState}",
        )
    }

    private fun OfflineRegionStatus.toInstallState(regionId: String): OfflineRegionInstallState {
        val required = requiredResourceCount
        val completed = completedResourceCount
        val progress = if (required > 0) {
            (completed.toFloat() / required.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        return OfflineRegionInstallState(
            regionId = regionId,
            isComplete = isComplete,
            completedResourceCount = completed,
            requiredResourceCount = required,
            completedResourceSize = completedResourceSize,
            downloadProgress = progress,
            isDownloading = downloadState == OfflineRegion.STATE_ACTIVE && !isComplete,
        )
    }

    private fun markRegionDownloading(regionId: String) {
        _installStates.value = _installStates.value.toMutableMap().apply {
            val previous = get(regionId)
            put(
                regionId,
                OfflineRegionInstallState(
                    regionId = regionId,
                    isComplete = false,
                    completedResourceCount = previous?.completedResourceCount ?: 0L,
                    requiredResourceCount = previous?.requiredResourceCount ?: 0L,
                    completedResourceSize = previous?.completedResourceSize ?: 0L,
                    downloadProgress = previous?.downloadProgress ?: 0f,
                    isDownloading = true,
                ),
            )
        }
    }

    private fun updateStateFromStatus(
        regionId: String,
        status: OfflineRegionStatus,
        isDownloading: Boolean,
    ) {
        val required = status.requiredResourceCount
        val completed = status.completedResourceCount
        val progress = if (required > 0) {
            (completed.toFloat() / required.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        _installStates.value = _installStates.value.toMutableMap().apply {
            put(
                regionId,
                OfflineRegionInstallState(
                    regionId = regionId,
                    isComplete = status.isComplete,
                    completedResourceCount = completed,
                    requiredResourceCount = required,
                    completedResourceSize = status.completedResourceSize,
                    downloadProgress = progress,
                    isDownloading = isDownloading && !status.isComplete,
                ),
            )
        }
    }

    private fun metadataBytes(regionId: String, name: String, pixelRatio: Float): ByteArray {
        val json = JSONObject()
            .put("id", regionId)
            .put("name", name)
            .put("pixelRatio", pixelRatio.toDouble())
            .put("styleTransport", STYLE_TRANSPORT_LOOPBACK)
            .toString()
        return json.toByteArray(StandardCharsets.UTF_8)
    }

    private fun parsePixelRatio(region: OfflineRegion): Float {
        return try {
            val metadata = region.metadata ?: return DEFAULT_PIXEL_RATIO
            val json = JSONObject(String(metadata, StandardCharsets.UTF_8))
            json.optDouble("pixelRatio", DEFAULT_PIXEL_RATIO.toDouble()).toFloat()
                .coerceIn(1f, MAX_OFFLINE_PIXEL_RATIO)
        } catch (exception: Exception) {
            DEFAULT_PIXEL_RATIO
        }
    }

    private suspend fun OfflineRegion.awaitStatus(): OfflineRegionStatus =
        suspendCancellableCoroutine { cont ->
            getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                override fun onStatus(status: OfflineRegionStatus?) {
                    if (status != null && cont.isActive) cont.resume(status)
                }

                override fun onError(error: String?) {
                    if (cont.isActive) {
                        cont.resumeWithException(
                            Exception("getStatus failed: ${error ?: "unknown"}"),
                        )
                    }
                }
            })
        }

    private fun loadCatalog(): List<OfflineCountryCatalog> {
        return try {
            val jsonText = appContext.assets.open(CATALOG_ASSET).bufferedReader().use { it.readText() }
            val root = JSONObject(jsonText)
            val countriesArray = root.getJSONArray("countries")
            buildList {
                for (index in 0 until countriesArray.length()) {
                    val country = countriesArray.getJSONObject(index)
                    val iso = country.getString("iso").uppercase()
                    val name = country.getString("name")
                    val regionsArray = country.getJSONArray("regions")
                    val regions = buildList {
                        for (regionIndex in 0 until regionsArray.length()) {
                            val region = regionsArray.getJSONObject(regionIndex)
                            add(
                                OfflineRegionDefinition(
                                    id = region.getString("id"),
                                    name = region.getString("name"),
                                    countryIso = iso,
                                    south = region.getDouble("south"),
                                    west = region.getDouble("west"),
                                    north = region.getDouble("north"),
                                    east = region.getDouble("east"),
                                    minZoom = region.getDouble("minZoom"),
                                    maxZoom = region.getDouble("maxZoom"),
                                    sizeEstimateMb = region.getString("sizeEstimateMb"),
                                ),
                            )
                        }
                    }
                    add(OfflineCountryCatalog(iso = iso, name = name, regions = regions))
                }
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to load offline region catalog", exception)
            emptyList()
        }
    }

    private fun parseRegionId(region: OfflineRegion): String? {
        return try {
            val metadata = region.metadata ?: return null
            val json = JSONObject(String(metadata, StandardCharsets.UTF_8))
            json.optString("id").takeIf { it.isNotBlank() }
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to parse offline region metadata", exception)
            null
        }
    }

    companion object {
        private const val TAG = "OfflineMapRepository"
        private const val CATALOG_ASSET = "map/offline_regions.json"
        private const val OFFLINE_TILE_COUNT_LIMIT = 1_000_000L
        private const val PREPARE_TIMEOUT_MS = 120_000L
        private const val DOWNLOAD_TIMEOUT_MS = 45L * 60L * 1000L
        private const val MAX_OFFLINE_PIXEL_RATIO = 2f
        private const val DEFAULT_PIXEL_RATIO = 2f
        private const val STYLE_TRANSPORT_LOOPBACK = "loopback-v1"
    }
}
