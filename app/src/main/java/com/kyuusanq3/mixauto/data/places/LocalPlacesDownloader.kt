package com.kyuusanq3.mixauto.data.places

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * HTTP / asset / URI install of Overture country packs, extracted from
 * [LocalPlacesRepository]. SQLite open/search stays on the repository;
 * this class only copies bytes onto disk then asks the repo to open/read meta.
 */
internal class LocalPlacesDownloader(
    private val appContext: Context,
    private val placesDir: File,
    private val dbLock: Any,
    private val databaseFile: (isoCode: String) -> File,
    private val prepareDatabaseFileReplacement: (isoCode: String) -> Unit,
    private val openDatabase: (isoCode: String) -> Boolean,
    private val readMeta: (isoCode: String) -> LocalDbMeta?,
    private val deleteDatabase: (isoCode: String) -> Unit,
) {
    fun downloadDatabaseFromUrl(
        isoCode: String,
        url: String,
        onProgress: (Float) -> Unit,
    ): Result<LocalDbMeta> {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = DOWNLOAD_CONNECT_TIMEOUT_MS
            connection.readTimeout = DOWNLOAD_READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", DOWNLOAD_USER_AGENT)
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

                val rawStream = connection.inputStream
                val inputStream = if (url.endsWith(".gz", ignoreCase = true)) {
                    GZIPInputStream(rawStream)
                } else {
                    rawStream
                }

                inputStream.use { input ->
                    copyToDatabaseFile(isoCode, input, totalBytes, onProgress)
                }

                val meta = readMeta(isoCode)
                    ?: return Result.failure(Exception("Downloaded file is not a valid places database"))

                if (!openDatabase(meta.countryIso)) {
                    return Result.failure(Exception("Downloaded database could not be opened"))
                }

                Result.success(meta)
            } finally {
                connection.disconnect()
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to download database from $url", exception)
            deleteDatabase(isoCode)
            Result.failure(exception)
        }
    }

    fun installFromAsset(assetPath: String, isoCode: String): Boolean {
        return try {
            appContext.assets.open(assetPath).use { input ->
                copyToDatabaseFile(isoCode, input)
            }
            openDatabase(isoCode)
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to install asset $assetPath for $isoCode", exception)
            databaseFile(isoCode).delete()
            false
        }
    }

    fun importDatabaseFromUri(
        defaultIsoCode: String,
        uri: Uri,
        onProgress: (Float) -> Unit,
    ): Result<LocalDbMeta> {
        val resolver = appContext.contentResolver
        val totalBytes = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0) cursor.getLong(index) else -1L
                } else {
                    -1L
                }
            } ?: -1L

        return try {
            resolver.openInputStream(uri)?.use { input ->
                copyToDatabaseFile(defaultIsoCode, input, totalBytes, onProgress)
            } ?: return Result.failure(Exception("Could not read selected file"))

            val meta = readMeta(defaultIsoCode)
                ?: return Result.failure(Exception("Imported file is not a valid places database"))

            if (!openDatabase(meta.countryIso)) {
                return Result.failure(Exception("Imported database could not be opened"))
            }

            Result.success(meta)
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to import database from $uri", exception)
            deleteDatabase(defaultIsoCode)
            Result.failure(exception)
        }
    }

    private fun copyToDatabaseFile(
        isoCode: String,
        input: InputStream,
        totalBytes: Long = -1L,
        onProgress: ((Float) -> Unit)? = null,
    ) {
        val targetFile = databaseFile(isoCode)
        val tempFile = File(placesDir, "${isoCode.lowercase()}.db.tmp")
        targetFile.parentFile?.mkdirs()
        tempFile.delete()

        tempFile.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var copied = 0L
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                copied += read
                if (totalBytes > 0L && onProgress != null) {
                    onProgress((copied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f))
                }
            }
        }

        synchronized(dbLock) {
            prepareDatabaseFileReplacement(isoCode)
            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
        }
        onProgress?.invoke(1f)
    }

    companion object {
        private const val TAG = "LocalPlacesDownloader"
        private const val DOWNLOAD_USER_AGENT = "MixAutoCarLauncher/1.0"
        private const val DOWNLOAD_CONNECT_TIMEOUT_MS = 30_000
        private const val DOWNLOAD_READ_TIMEOUT_MS = 300_000
    }
}
