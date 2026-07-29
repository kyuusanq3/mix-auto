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
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionStatus
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OfflineMapRepository(context: Context) {

    private val appContext = context.applicationContext

    private val catalog: List<OfflineCountryCatalog> = OfflineRegionCatalog.loadCatalog(appContext)
    private val regionById: Map<String, OfflineRegionDefinition> = catalog
        .flatMap { country -> country.regions.map { it to country.iso } }
        .associate { (region, iso) -> region.id to region.copy(countryIso = iso) }

    private val _installStates = MutableStateFlow<Map<String, OfflineRegionInstallState>>(emptyMap())
    val installStates: StateFlow<Map<String, OfflineRegionInstallState>> = _installStates.asStateFlow()

    private val offlineManager: OfflineManager

    private val downloadSession: OfflineRegionDownloadSession

    init {
        MapLibreAppBootstrap.ensureInitialized(appContext)
        offlineManager = OfflineManager.getInstance(appContext)
        offlineManager.setOfflineMapboxTileCountLimit(OFFLINE_TILE_COUNT_LIMIT)
        downloadSession = OfflineRegionDownloadSession(
            offlineManager = offlineManager,
            regionById = regionById,
            markRegionDownloading = ::markRegionDownloading,
            updateStateFromStatus = ::updateStateFromStatus,
            reportDownloadError = ::reportDownloadError,
        )
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
            def.contains(lat, lng) && states[def.id]?.isCurrentDetail == true
        }
    }

    fun anyRegionNeedsDetailUpgrade(): Boolean =
        _installStates.value.values.any { it.needsDetailUpgrade }

    fun needsDetailUpgrade(regionId: String): Boolean =
        _installStates.value[regionId]?.needsDetailUpgrade == true

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
                            base[regionId] = status.toInstallState(
                                regionId = regionId,
                                installedMaxZoom = parseInstalledMaxZoom(sdkRegion),
                                sizeEstimateMb = regionById[regionId]?.sizeEstimateMb,
                                catalogMaxZoom = regionById[regionId]?.maxZoom?.toInt(),
                            )
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
            reportDownloadError(regionId, message)
        }
    }

    suspend fun resumeIncompleteDownloads(): Boolean {
        val pending = downloadSession.findFirstResumableIncompleteRegion() ?: return false
        Log.i(TAG, "Resuming incomplete download for ${pending.regionId}")
        downloadRegion(pending.regionId, pending.pixelRatio)
        return true
    }

    suspend fun pauseDownload(regionId: String) {
        withContext(Dispatchers.Main) {
            val sdkRegion = downloadSession.findSdkRegion(regionId) ?: return@withContext
            sdkRegion.setObserver(null)
            sdkRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
            val status = sdkRegion.awaitStatus()
            updateStateFromStatus(regionId, status, isDownloading = false, installedMaxZoom = null)
        }
    }

    suspend fun downloadRegion(regionId: String, pixelRatio: Float) {
        val styleUri = withContext(Dispatchers.IO) {
            MapStyleAssetResolver.prepareOfflineRegionStyleUri(appContext)
        }
        withContext(Dispatchers.Main) {
            withTimeout(DOWNLOAD_TIMEOUT_MS) {
                downloadSession.downloadRegionOnMain(regionId, pixelRatio, styleUri)
            }
        }
    }

    suspend fun deleteRegion(regionId: String) {
        val sdkRegion = downloadSession.findSdkRegion(regionId)
            ?: throw IllegalArgumentException("Region not downloaded: $regionId")

        downloadSession.deleteSdkRegion(sdkRegion)
        _installStates.value = _installStates.value.toMutableMap().apply {
            put(regionId, OfflineRegionInstallState(regionId = regionId, isComplete = false))
        }
    }

    suspend fun findPendingResumeRegion(): PendingOfflineResume? =
        withContext(Dispatchers.Main) {
            downloadSession.findFirstResumableIncompleteRegion()
        }

    private fun reportDownloadError(regionId: String, message: String) {
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
                    displayProgress = previous?.displayProgress ?: 0f,
                    isDownloading = true,
                ),
            )
        }
    }

    private fun updateStateFromStatus(
        regionId: String,
        status: OfflineRegionStatus,
        isDownloading: Boolean,
        installedMaxZoom: Int?,
    ) {
        val resolvedMaxZoom = installedMaxZoom ?: _installStates.value[regionId]?.installedMaxZoom
        _installStates.value = _installStates.value.toMutableMap().apply {
            put(
                regionId,
                status.toInstallState(
                    regionId = regionId,
                    installedMaxZoom = resolvedMaxZoom,
                    sizeEstimateMb = regionById[regionId]?.sizeEstimateMb,
                    catalogMaxZoom = regionById[regionId]?.maxZoom?.toInt(),
                ).copy(isDownloading = isDownloading && !status.isComplete),
            )
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

    companion object {
        private const val TAG = "OfflineMapRepository"
        private const val OFFLINE_TILE_COUNT_LIMIT = 1_000_000L
        private const val DOWNLOAD_TIMEOUT_MS = 45L * 60L * 1000L
    }
}
