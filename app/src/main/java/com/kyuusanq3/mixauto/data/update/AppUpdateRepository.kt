package com.kyuusanq3.mixauto.data.update

import android.content.Context
import android.util.Log
import com.kyuusanq3.mixauto.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
)

class AppUpdateRepository(context: Context) {

    private val appContext = context.applicationContext

    fun checkForUpdate(): Result<UpdateInfo?> {
        return try {
            val connection = URL(VERSION_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.instanceFollowRedirects = true

            try {
                if (connection.responseCode !in 200..299) {
                    return Result.failure(
                        Exception("Update check failed: HTTP ${connection.responseCode}"),
                    )
                }

                val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonText)
                val remoteVersionCode = json.getInt("version_code")
                val remoteVersionName = json.getString("version_name")

                if (remoteVersionCode > BuildConfig.VERSION_CODE) {
                    Result.success(
                        UpdateInfo(
                            versionCode = remoteVersionCode,
                            versionName = remoteVersionName,
                        ),
                    )
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

    companion object {
        private const val TAG = "AppUpdateRepository"
        private const val APP_REPO = "kyuusanq3/mix-auto"
        private const val RELEASE_BASE_URL =
            "https://github.com/$APP_REPO/releases/latest/download"
        private const val VERSION_URL = "$RELEASE_BASE_URL/version.json"
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
