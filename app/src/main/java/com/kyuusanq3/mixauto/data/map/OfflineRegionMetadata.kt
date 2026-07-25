package com.kyuusanq3.mixauto.data.map

import android.util.Log
import java.nio.charset.StandardCharsets
import org.json.JSONObject
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

internal const val STYLE_TRANSPORT_LOOPBACK = "loopback-v1"

internal const val MAX_OFFLINE_PIXEL_RATIO = 2f
internal const val DEFAULT_OFFLINE_PIXEL_RATIO = 2f

internal fun parseRegionId(region: OfflineRegion): String? {
    return try {
        val metadata = region.metadata ?: return null
        val json = JSONObject(String(metadata, StandardCharsets.UTF_8))
        json.optString("id").takeIf { it.isNotBlank() }
    } catch (exception: Exception) {
        Log.w(TAG, "Failed to parse offline region metadata", exception)
        null
    }
}

internal fun parseInstalledMaxZoom(region: OfflineRegion): Int? {
    return try {
        val definition = region.definition
        if (definition is OfflineTilePyramidRegionDefinition) {
            definition.maxZoom.toInt()
        } else {
            null
        }
    } catch (exception: Exception) {
        Log.w(TAG, "Failed to parse installed maxZoom", exception)
        null
    }
}

internal fun metadataBytes(
    regionId: String,
    name: String,
    pixelRatio: Float,
    catalogMaxZoom: Double,
): ByteArray {
    val json = JSONObject()
        .put("id", regionId)
        .put("name", name)
        .put("pixelRatio", pixelRatio.toDouble())
        .put("catalogMaxZoom", catalogMaxZoom.toInt())
        .put("styleTransport", STYLE_TRANSPORT_LOOPBACK)
        .toString()
    return json.toByteArray(StandardCharsets.UTF_8)
}

internal fun parsePixelRatio(region: OfflineRegion): Float {
    return try {
        val metadata = region.metadata ?: return DEFAULT_OFFLINE_PIXEL_RATIO
        val json = JSONObject(String(metadata, StandardCharsets.UTF_8))
        json.optDouble("pixelRatio", DEFAULT_OFFLINE_PIXEL_RATIO.toDouble()).toFloat()
            .coerceIn(1f, MAX_OFFLINE_PIXEL_RATIO)
    } catch (exception: Exception) {
        DEFAULT_OFFLINE_PIXEL_RATIO
    }
}

internal fun isResumable(region: OfflineRegion, status: OfflineRegionStatus): Boolean {
    if (status.isComplete) return false
    if (status.requiredResourceCount == 1L && status.completedResourceCount == 0L) return false
    if (!hasLoopbackStyleTransport(region)) {
        Log.w(TAG, "Region ${parseRegionId(region)} uses legacy style transport — not resumable")
        return false
    }
    val styleUrl = parseRegionStyleUrl(region)
    if (styleUrl != null && isLegacyStyleUrl(styleUrl)) {
        Log.w(TAG, "Region ${parseRegionId(region)} uses legacy style URL $styleUrl — not resumable")
        return false
    }
    return true
}

internal fun hasLoopbackStyleTransport(region: OfflineRegion): Boolean {
    return try {
        val metadata = region.metadata ?: return false
        val json = JSONObject(String(metadata, StandardCharsets.UTF_8))
        json.optString("styleTransport") == STYLE_TRANSPORT_LOOPBACK
    } catch (exception: Exception) {
        false
    }
}

internal fun parseRegionStyleUrl(region: OfflineRegion): String? {
    return try {
        val definition = region.definition
        if (definition is OfflineTilePyramidRegionDefinition) {
            definition.styleURL
        } else {
            null
        }
    } catch (exception: Exception) {
        Log.w(TAG, "Failed to parse region style URL", exception)
        null
    }
}

internal fun isLegacyStyleUrl(styleUrl: String): Boolean {
    val lower = styleUrl.lowercase()
    return lower.startsWith("file://") || lower.startsWith("asset://")
}

internal fun OfflineRegionStatus.toInstallState(
    regionId: String,
    installedMaxZoom: Int? = null,
    sizeEstimateMb: String?,
    catalogMaxZoom: Int?,
): OfflineRegionInstallState {
    val required = requiredResourceCount
    val completed = completedResourceCount
    val progress = if (required > 0) {
        (completed.toFloat() / required.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val displayProgress = computeOfflineDisplayProgress(
        completedResourceCount = completed,
        requiredResourceCount = required,
        completedResourceSize = completedResourceSize,
        sizeEstimateMb = sizeEstimateMb,
        isComplete = isComplete,
    )
    return OfflineRegionInstallState(
        regionId = regionId,
        isComplete = isComplete,
        completedResourceCount = completed,
        requiredResourceCount = required,
        completedResourceSize = completedResourceSize,
        downloadProgress = progress,
        displayProgress = displayProgress,
        isDownloading = downloadState == OfflineRegion.STATE_ACTIVE && !isComplete,
        installedMaxZoom = installedMaxZoom,
        catalogMaxZoom = catalogMaxZoom,
    )
}

private const val TAG = "OfflineRegionMetadata"
