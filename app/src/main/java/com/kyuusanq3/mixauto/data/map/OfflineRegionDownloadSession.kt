package com.kyuusanq3.mixauto.data.map

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class OfflineRegionDownloadSession(
    private val offlineManager: OfflineManager,
    private val regionById: Map<String, OfflineRegionDefinition>,
    private val markRegionDownloading: (regionId: String) -> Unit,
    private val updateStateFromStatus: (
        regionId: String,
        status: OfflineRegionStatus,
        isDownloading: Boolean,
        installedMaxZoom: Int?,
    ) -> Unit,
    private val reportDownloadError: (regionId: String, message: String) -> Unit,
) {
    suspend fun downloadRegionOnMain(
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
            val installedMax = parseInstalledMaxZoom(existing)
            val catalogMax = definition.maxZoom.toInt()
            val needsUpgrade = status.isComplete &&
                installedMax != null &&
                installedMax < catalogMax
            if (status.isComplete && !needsUpgrade) {
                updateStateFromStatus(
                    regionId,
                    status,
                    false,
                    installedMax,
                )
                return
            }
            if (!needsUpgrade && isResumable(existing, status)) {
                Log.i(TAG, "Resuming offline region $regionId at ${status.completedResourceCount}/${status.requiredResourceCount}")
                observeAndActivate(existing, regionId)
                return
            }
            if (needsUpgrade) {
                Log.i(TAG, "Replacing stale offline region $regionId for street-detail upgrade")
            } else {
                Log.w(TAG, "Replacing broken incomplete offline region $regionId")
            }
            deleteSdkRegion(existing)
        }

        val metadata = metadataBytes(regionId, definition.name, effectivePixelRatio, definition.maxZoom)
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

    suspend fun deleteSdkRegion(region: OfflineRegion) {
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

    suspend fun findSdkRegion(regionId: String): OfflineRegion? {
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

    suspend fun findFirstResumableIncompleteRegion(): PendingOfflineResume? {
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
            if (status.isComplete || !isResumable(sdkRegion, status)) continue
            return PendingOfflineResume(
                regionId = regionId,
                pixelRatio = parsePixelRatio(sdkRegion),
            )
        }
        return null
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
                    updateStateFromStatus(regionId, status, !status.isComplete, null)
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

    private suspend fun observeUntilComplete(region: OfflineRegion, regionId: String) = coroutineScope {
        val completeSignal = CompletableDeferred<Unit>()
        var lastProgressCount = -1L
        var lastProgressBytes = -1L
        var lastProgressMs = System.currentTimeMillis()
        var kickAttempted = false
        var kickAtMs = 0L

        fun noteProgress(status: OfflineRegionStatus) {
            val countChanged = status.completedResourceCount != lastProgressCount
            val bytesChanged = status.completedResourceSize != lastProgressBytes
            if (countChanged || bytesChanged) {
                lastProgressCount = status.completedResourceCount
                lastProgressBytes = status.completedResourceSize
                lastProgressMs = System.currentTimeMillis()
                kickAttempted = false
            }
        }

        fun handleStatus(status: OfflineRegionStatus, phase: String) {
            logStatus(regionId, status, phase)
            updateStateFromStatus(regionId, status, !status.isComplete, null)
            noteProgress(status)
            if (status.isComplete && !completeSignal.isCompleted) {
                completeSignal.complete(Unit)
            }
        }

        fun failDownload(message: String) {
            if (completeSignal.isCompleted) return
            reportDownloadError(regionId, message)
            completeSignal.completeExceptionally(Exception(message))
        }

        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                handleStatus(status, "download")
            }

            override fun onError(error: OfflineRegionError) {
                Log.e(TAG, "Offline $regionId error: ${error.message} type=${error.reason}")
                failDownload(error.message ?: "Offline map download failed")
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                failDownload("Offline tile limit exceeded ($limit)")
            }
        })
        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
        region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) {
                if (status == null) return
                handleStatus(status, "download-snapshot")
            }

            override fun onError(error: String?) = Unit
        })

        val stallJob = launch {
            while (isActive && !completeSignal.isCompleted) {
                delay(STALL_CHECK_INTERVAL_MS)
                if (completeSignal.isCompleted) return@launch
                val status = runCatching { region.awaitStatus() }.getOrNull() ?: continue
                handleStatus(status, "download-poll")
                if (status.isComplete) return@launch

                val stalledMs = System.currentTimeMillis() - lastProgressMs
                if (status.requiredResourceCount <= 0L || stalledMs < STALL_TIMEOUT_MS) continue

                if (!kickAttempted) {
                    kickAttempted = true
                    kickAtMs = System.currentTimeMillis()
                    Log.w(
                        TAG,
                        "Download stalled for $regionId at ${status.completedResourceCount}/" +
                            "${status.requiredResourceCount} state=${status.downloadState} — kicking",
                    )
                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                } else if (System.currentTimeMillis() - kickAtMs >= STALL_KICK_GRACE_MS) {
                    failDownload("Download stalled — check Wi-Fi to OpenFreeMap and retry")
                    return@launch
                }
            }
        }

        try {
            completeSignal.await()
        } finally {
            stallJob.cancel()
            region.setObserver(null)
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

    private fun logStatus(regionId: String, status: OfflineRegionStatus, phase: String) {
        Log.i(
            TAG,
            "Offline $regionId [$phase]: ${status.completedResourceCount}/${status.requiredResourceCount} " +
                "bytes=${status.completedResourceSize} complete=${status.isComplete} " +
                "state=${status.downloadState}",
        )
    }

    companion object {
        private const val TAG = "OfflineRegionDownloadSession"
        private const val PREPARE_TIMEOUT_MS = 120_000L
        private const val STALL_CHECK_INTERVAL_MS = 30_000L
        private const val STALL_TIMEOUT_MS = 180_000L
        private const val STALL_KICK_GRACE_MS = 60_000L
    }
}
