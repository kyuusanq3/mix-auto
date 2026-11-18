package com.kyuusanq3.mixauto.data.update

import android.content.Context
import android.util.Log
import com.kyuusanq3.mixauto.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val apkUrl: String,
)

class AppUpdateRepository(context: Context) {

    private val appContext = context.applicationContext

    fun checkForUpdate(): Result<UpdateInfo?> {
        return try {
            val prefix = BuildConfig.UPDATE_TAG_PREFIX
            if (prefix.isEmpty()) {
                checkLatestRelease()
            } else {
                checkPrefixedRelease(prefix)
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to check for update", exception)
            Result.failure(exception)
        }
    }

    fun downloadApk(apkUrl: String, onProgress: (Float) -> Unit): Result<File> {
        return try {
            val connection = open(apkUrl, DOWNLOAD_READ_TIMEOUT_MS)
            try {
                if (connection.responseCode !in 200..299) {
                    return Result.failure(
                        Exception("Download failed: HTTP ${connection.responseCode}"),
                    )
                }

                val totalBytes = connection.contentLengthLong.takeIf { it > 0L }
                    ?: connection.getHeaderField("Content-Length")?.toLongOrNull()
                    ?: -1L

                val updatesDir = File(appContext.cacheDir, UPDATES_DIR).apply { mkdirs() }
                val apkName = BuildConfig.UPDATE_ASSET_NAME
                val tempFile = File(updatesDir, "$apkName.tmp")
                val targetFile = File(updatesDir, apkName)
                tempFile.delete()

                connection.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (totalBytes > 0L) {
                                onProgress(
                                    (copied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f),
                                )
                            }
                        }
                    }
                }

                if (targetFile.exists()) {
                    targetFile.delete()
                }
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                onProgress(1f)
                Result.success(targetFile)
            } finally {
                connection.disconnect()
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to download APK", exception)
            Result.failure(exception)
        }
    }

    private fun checkLatestRelease(): Result<UpdateInfo?> {
        val connection = open(RELEASES_LATEST_URL, READ_TIMEOUT_MS)
        return try {
            val json = readJsonObject(connection) ?: return Result.success(null)
            val tag = json.optString("tag_name")
            if (tag.isEmpty() || !isNewerRelease(tag, BuildConfig.VERSION_NAME, "")) {
                Result.success(null)
            } else {
                val version = normalizeReleaseVersion(tag, "")
                val url = "$LATEST_DOWNLOAD_BASE/${BuildConfig.UPDATE_ASSET_NAME}"
                Result.success(UpdateInfo(versionName = version, apkUrl = url))
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun checkPrefixedRelease(prefix: String): Result<UpdateInfo?> {
        val connection = open(RELEASES_LIST_URL, READ_TIMEOUT_MS)
        return try {
            val array = readJsonArray(connection) ?: return Result.success(null)
            val tag = newestMatchingTag(releaseCandidates(array), prefix, BuildConfig.UPDATE_ASSET_NAME)
            if (tag == null || !isNewerRelease(tag, BuildConfig.VERSION_NAME, prefix)) {
                Result.success(null)
            } else {
                val version = normalizeReleaseVersion(tag, prefix)
                val url = "$TAG_DOWNLOAD_BASE/$tag/${BuildConfig.UPDATE_ASSET_NAME}"
                Result.success(UpdateInfo(versionName = version, apkUrl = url))
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, readTimeoutMs: Int): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = readTimeoutMs
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.instanceFollowRedirects = true
        return connection
    }

    private fun readJsonObject(connection: HttpURLConnection): JSONObject? {
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("Update check failed: HTTP ${connection.responseCode}")
        }
        val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
        return JSONObject(jsonText)
    }

    private fun readJsonArray(connection: HttpURLConnection): JSONArray? {
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("Update check failed: HTTP ${connection.responseCode}")
        }
        val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
        return JSONArray(jsonText)
    }

    companion object {
        private const val TAG = "AppUpdateRepository"
        private const val APP_REPO = "kyuusanq3/mix-auto"
        private const val RELEASES_LATEST_URL =
            "https://api.github.com/repos/$APP_REPO/releases/latest"
        private const val RELEASES_LIST_URL =
            "https://api.github.com/repos/$APP_REPO/releases?per_page=100"
        private const val LATEST_DOWNLOAD_BASE =
            "https://github.com/$APP_REPO/releases/latest/download"
        private const val TAG_DOWNLOAD_BASE =
            "https://github.com/$APP_REPO/releases/download"
        private const val USER_AGENT = "MixAutoCarLauncher/1.0"
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val DOWNLOAD_READ_TIMEOUT_MS = 300_000
        private const val UPDATES_DIR = "updates"
    }
}

internal data class ReleaseCandidate(
    val tag: String,
    val draft: Boolean,
    val assetNames: List<String>,
)

internal fun releaseCandidates(releases: JSONArray): List<ReleaseCandidate> {
    val candidates = ArrayList<ReleaseCandidate>(releases.length())
    for (index in 0 until releases.length()) {
        val release = releases.optJSONObject(index) ?: continue
        val assets = release.optJSONArray("assets")
        val names = ArrayList<String>(assets?.length() ?: 0)
        if (assets != null) {
            for (assetIndex in 0 until assets.length()) {
                val name = assets.optJSONObject(assetIndex)?.optString("name").orEmpty()
                if (name.isNotEmpty()) names.add(name)
            }
        }
        candidates.add(
            ReleaseCandidate(
                tag = release.optString("tag_name"),
                draft = release.optBoolean("draft"),
                assetNames = names,
            ),
        )
    }
    return candidates
}

internal fun newestMatchingTag(
    releases: List<ReleaseCandidate>,
    tagPrefix: String,
    assetName: String,
): String? {
    var best: String? = null
    for (release in releases) {
        if (release.draft) continue
        val tag = release.tag
        if (tagPrefix.isNotEmpty() && !tag.startsWith(tagPrefix)) continue
        if (assetName !in release.assetNames) continue
        if (best == null || isNewerRelease(tag, best, tagPrefix)) {
            best = tag
        }
    }
    return best
}

internal fun isNewerRelease(remote: String, local: String, tagPrefix: String): Boolean {
    val remoteParts = versionParts(normalizeReleaseVersion(remote, tagPrefix))
    val localParts = versionParts(normalizeReleaseVersion(local, tagPrefix))
    val length = maxOf(remoteParts.size, localParts.size)
    for (index in 0 until length) {
        val remotePart = remoteParts.getOrElse(index) { 0 }
        val localPart = localParts.getOrElse(index) { 0 }
        if (remotePart > localPart) return true
        if (remotePart < localPart) return false
    }
    return false
}

internal fun normalizeReleaseVersion(raw: String, tagPrefix: String): String {
    var version = raw.trim()
    if (tagPrefix.isNotEmpty() && version.startsWith(tagPrefix)) {
        version = version.removePrefix(tagPrefix)
    }
    version = version.trimStart('v', 'V')
    val dash = version.indexOf('-')
    if (dash >= 0) {
        version = version.substring(0, dash)
    }
    return version
}

private fun versionParts(version: String): List<Int> =
    version.split('.').map { it.toIntOrNull() ?: 0 }
