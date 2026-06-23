package com.kyuusanq3.mixauto.data.update

import android.content.Context
import android.util.Log
import com.kyuusanq3.mixauto.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
)

class AppUpdateRepository(context: Context) {

    private val appContext = context.applicationContext

    fun checkForUpdate(): Result<UpdateInfo?> {
        return try {
            val connection = URL(RELEASES_API_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.instanceFollowRedirects = true

            try {
                if (connection.responseCode !in 200..299) {
                    return Result.failure(
                        Exception("Update check failed: HTTP ${connection.responseCode}"),
                    )
                }

                val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonText)
                val remoteVersionName = json.getString("tag_name")
                val localVersionName = BuildConfig.VERSION_NAME

                if (isNewerVersion(remoteVersionName, localVersionName)) {
                    Result.success(UpdateInfo(versionName = remoteVersionName))
                } else {
                    Result.success(null)
                }
            } finally {
                connection.disconnect()
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to check for update", exception)
            Result.failure(exception)
        }
    }

    fun downloadApk(onProgress: (Float) -> Unit): Result<File> {
        return try {
            val connection = URL(APK_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = DOWNLOAD_READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.instanceFollowRedirects = true

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
                val tempFile = File(updatesDir, APK_TEMP_NAME)
                val targetFile = File(updatesDir, APK_NAME)
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

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val rParts = remote.trimStart('v').split(".").map { it.toIntOrNull() ?: 0 }
        val lParts = local.trimStart('v').split(".").map { it.toIntOrNull() ?: 0 }
        val len = maxOf(rParts.size, lParts.size)
        for (i in 0 until len) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    companion object {
        private const val TAG = "AppUpdateRepository"
        private const val APP_REPO = "kyuusanq3/mix-auto"
        private const val RELEASES_API_URL =
            "https://api.github.com/repos/$APP_REPO/releases/latest"
        private const val RELEASE_BASE_URL =
            "https://github.com/$APP_REPO/releases/latest/download"
        private const val APK_URL = "$RELEASE_BASE_URL/mix-auto.apk"
        private const val USER_AGENT = "MixAutoCarLauncher/1.0"
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val DOWNLOAD_READ_TIMEOUT_MS = 300_000
        private const val UPDATES_DIR = "updates"
        private const val APK_NAME = "mix-auto.apk"
        private const val APK_TEMP_NAME = "mix-auto.apk.tmp"
    }
}
