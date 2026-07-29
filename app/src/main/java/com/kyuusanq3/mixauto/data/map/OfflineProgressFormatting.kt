package com.kyuusanq3.mixauto.data.map

import kotlin.math.max

/** Upper-bound MB from catalog strings like "150–400" or "40-80". */
fun parseSizeEstimateUpperMb(sizeEstimateMb: String): Int? {
    val normalized = sizeEstimateMb.replace('–', '-').replace('—', '-')
    val parts = normalized.split('-').map { part ->
        part.trim().filter { it.isDigit() }
    }
    val upper = parts.lastOrNull()?.toIntOrNull() ?: parts.firstOrNull()?.toIntOrNull()
    return upper?.takeIf { it > 0 }
}

fun parseSizeEstimateUpperBytes(sizeEstimateMb: String): Long? {
    val mb = parseSizeEstimateUpperMb(sizeEstimateMb) ?: return null
    return mb.toLong() * 1024L * 1024L
}

fun computeOfflineDisplayProgress(
    completedResourceCount: Long,
    requiredResourceCount: Long,
    completedResourceSize: Long,
    sizeEstimateMb: String?,
    isComplete: Boolean,
): Float {
    if (isComplete) return 1f
    val resourceProgress = if (requiredResourceCount > 0L) {
        (completedResourceCount.toFloat() / requiredResourceCount.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val estimatedMaxBytes = sizeEstimateMb?.let { parseSizeEstimateUpperBytes(it) }
    val byteProgress = if (estimatedMaxBytes != null && estimatedMaxBytes > 0L && completedResourceSize > 0L) {
        (completedResourceSize.toFloat() / estimatedMaxBytes.toFloat()).coerceIn(0f, 0.99f)
    } else {
        0f
    }
    return max(resourceProgress, byteProgress).coerceIn(0f, 0.99f)
}

/** Primary user-facing download label, e.g. "~120 MB of ~400 MB". */
fun formatOfflineMbProgressLabel(completedBytes: Long, sizeEstimateMb: String?): String? {
    if (completedBytes <= 0L) return null
    val received = formatOfflineStorageMb(completedBytes)
    val upperMb = sizeEstimateMb?.let { parseSizeEstimateUpperMb(it) }
    return if (upperMb != null) {
        "~$received of ~$upperMb MB"
    } else {
        "~$received"
    }
}

fun formatOfflineStorageMb(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb < 10.0) {
        String.format("%.1f MB", mb)
    } else {
        "${mb.toInt()} MB"
    }
}

data class OfflineRegionInstallState(
    val regionId: String,
    val isComplete: Boolean,
    val completedResourceCount: Long = 0,
    val requiredResourceCount: Long = 0,
    val completedResourceSize: Long = 0,
    val downloadProgress: Float = 0f,
    /** Blended resource-count + byte progress for UI (max of both, capped at 0.99 until complete). */
    val displayProgress: Float = 0f,
    val isDownloading: Boolean = false,
    val errorMessage: String? = null,
    val installedMaxZoom: Int? = null,
    val catalogMaxZoom: Int? = null,
) {
    val needsDetailUpgrade: Boolean
        get() = isComplete &&
            installedMaxZoom != null &&
            catalogMaxZoom != null &&
            installedMaxZoom < catalogMaxZoom

    val isCurrentDetail: Boolean
        get() = isComplete && !needsDetailUpgrade
}

data class PendingOfflineResume(val regionId: String, val pixelRatio: Float)
