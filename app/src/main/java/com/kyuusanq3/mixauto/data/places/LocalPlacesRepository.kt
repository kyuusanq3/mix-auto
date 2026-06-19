package com.kyuusanq3.mixauto.data.places

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.location.Location
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import java.io.File
import java.io.InputStream

class LocalPlacesRepository(context: Context) {

    private val appContext = context.applicationContext
    private val placesDir: File = File(appContext.filesDir, PLACES_DIR)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var database: SQLiteDatabase? = null
    private var activeIsoCode: String? = null
    private var fts5Available: Boolean = true

    init {
        placesDir.mkdirs()
        prefs.getString(KEY_ACTIVE_ISO, null)?.let { iso ->
            if (databaseFile(iso).exists()) {
                openDatabase(iso)
            }
        }
    }

    val isOpen: Boolean get() = database?.isOpen == true

    val activeCountryIso: String? get() = activeIsoCode

    fun databaseFile(isoCode: String): File = File(placesDir, "${isoCode.lowercase()}.db")

    fun isDatabaseInstalled(isoCode: String): Boolean = databaseFile(isoCode).exists()

    fun openDatabase(isoCode: String): Boolean {
        val file = databaseFile(isoCode)
        if (!file.exists()) {
            Log.w(TAG, "Places database missing for $isoCode")
            return false
        }
        closeDatabase()
        return try {
            database = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
            activeIsoCode = isoCode.uppercase()
            fts5Available = detectFts5()
            prefs.edit().putString(KEY_ACTIVE_ISO, activeIsoCode).apply()
            Log.i(TAG, "Opened places database for $activeIsoCode (fts5=$fts5Available)")
            true
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to open places database for $isoCode", exception)
            database = null
            activeIsoCode = null
            false
        }
    }

    fun closeDatabase() {
        database?.close()
        database = null
        activeIsoCode = null
    }

    fun deleteDatabase(isoCode: String) {
        if (activeIsoCode.equals(isoCode, ignoreCase = true)) {
            closeDatabase()
            prefs.edit().remove(KEY_ACTIVE_ISO).apply()
        }
        databaseFile(isoCode).delete()
        File(placesDir, "${isoCode.lowercase()}.db.tmp").delete()
    }

    fun downloadDatabaseFromUrl(
        isoCode: String,
        url: String,
        onProgress: (Float) -> Unit,
    ): Result<LocalDbMeta> {
        return try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
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
                    java.util.zip.GZIPInputStream(rawStream)
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

        if (targetFile.exists()) {
            targetFile.delete()
        }
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }
        onProgress?.invoke(1f)
    }

    fun readMeta(isoCode: String = activeIsoCode.orEmpty()): LocalDbMeta? {
        val file = databaseFile(isoCode)
        if (!file.exists()) return null

        return runCatching {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                fun metaValue(key: String): String? {
                    db.rawQuery(
                        "SELECT value FROM meta WHERE key = ? LIMIT 1",
                        arrayOf(key),
                    ).use { cursor ->
                        return if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                }

                val countryIso = metaValue("country_iso") ?: isoCode.uppercase()
                val countryName = metaValue("country_name") ?: countryIso
                val recordCount = metaValue("record_count")?.toIntOrNull() ?: 0
                val generatedDate = metaValue("generated_date") ?: "unknown"
                LocalDbMeta(
                    countryIso = countryIso,
                    countryName = countryName,
                    recordCount = recordCount,
                    generatedDate = generatedDate,
                )
            }
        }.getOrNull()
    }

    fun searchPlaces(
        query: String,
        currentLat: Double,
        currentLng: Double,
    ): List<SearchResultPlace> {
        val db = database ?: return emptyList()
        if (query.isBlank()) return emptyList()

        val minLat = currentLat - BBOX_DELTA
        val maxLat = currentLat + BBOX_DELTA
        val minLng = currentLng - BBOX_DELTA
        val maxLng = currentLng + BBOX_DELTA

        val results = if (fts5Available) {
            searchWithFts5(db, query, minLat, maxLat, minLng, maxLng)
        } else {
            searchWithLike(db, query, minLat, maxLat, minLng, maxLng)
        }

        return results
            .map { row ->
                val distanceResults = FloatArray(1)
                Location.distanceBetween(
                    currentLat,
                    currentLng,
                    row.latitude,
                    row.longitude,
                    distanceResults,
                )
                row.copy(distanceInMeters = distanceResults[0])
            }
            .sortedBy { it.distanceInMeters }
            .take(LOCAL_RESULT_LIMIT)
    }

    fun getPlacesInBounds(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
        limit: Int = BBOX_RESULT_LIMIT,
    ): List<SearchResultPlace> {
        val db = database ?: return emptyList()
        val sql = """
            SELECT name, address, city, lat, lng, category
            FROM places
            WHERE lat BETWEEN ? AND ?
              AND lng BETWEEN ? AND ?
            LIMIT ?
        """.trimIndent()

        return runCatching {
            db.rawQuery(
                sql,
                arrayOf(
                    minLat.toString(),
                    maxLat.toString(),
                    minLng.toString(),
                    maxLng.toString(),
                    limit.toString(),
                ),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.toSearchResultPlace())
                    }
                }
            }
        }.getOrElse { error ->
            Log.w(TAG, "BBox query failed: ${error.message}")
            emptyList()
        }
    }

    private fun searchWithFts5(
        db: SQLiteDatabase,
        query: String,
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
    ): List<SearchResultPlace> {
        val ftsQuery = buildFtsQuery(query) ?: return emptyList()
        val sql = """
            SELECT p.name, p.address, p.city, p.lat, p.lng, p.category
            FROM places p
            JOIN places_fts fts ON p.rowid = fts.rowid
            WHERE places_fts MATCH ?
              AND p.lat BETWEEN ? AND ?
              AND p.lng BETWEEN ? AND ?
            LIMIT ?
        """.trimIndent()

        return runCatching {
            db.rawQuery(
                sql,
                arrayOf(
                    ftsQuery,
                    minLat.toString(),
                    maxLat.toString(),
                    minLng.toString(),
                    maxLng.toString(),
                    LOCAL_RESULT_LIMIT.toString(),
                ),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.toSearchResultPlace())
                    }
                }
            }
        }.getOrElse { error ->
            Log.w(TAG, "FTS5 search failed, falling back to LIKE: ${error.message}")
            fts5Available = false
            searchWithLike(db, query, minLat, maxLat, minLng, maxLng)
        }
    }

    private fun searchWithLike(
        db: SQLiteDatabase,
        query: String,
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
    ): List<SearchResultPlace> {
        val likePattern = "%${query.trim()}%"
        val sql = """
            SELECT name, address, city, lat, lng, category
            FROM places
            WHERE (name LIKE ? OR address LIKE ? OR city LIKE ?)
              AND lat BETWEEN ? AND ?
              AND lng BETWEEN ? AND ?
            LIMIT ?
        """.trimIndent()

        return runCatching {
            db.rawQuery(
                sql,
                arrayOf(
                    likePattern,
                    likePattern,
                    likePattern,
                    minLat.toString(),
                    maxLat.toString(),
                    minLng.toString(),
                    maxLng.toString(),
                    LOCAL_RESULT_LIMIT.toString(),
                ),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.toSearchResultPlace())
                    }
                }
            }
        }.getOrElse { error ->
            Log.w(TAG, "LIKE search failed: ${error.message}")
            emptyList()
        }
    }

    private fun detectFts5(): Boolean {
        val db = database ?: return false
        return runCatching {
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='places_fts'",
                null,
            ).use { it.count > 0 }
        }.getOrDefault(false)
    }

    private fun buildFtsQuery(query: String): String? {
        val tokens = query.trim()
            .lowercase()
            .split(Regex("\\s+"))
            .map { token ->
                token.replace("\"", "")
                    .replace("'", "")
                    .replace("*", "")
                    .filter { it.isLetterOrDigit() || it == '-' }
            }
            .filter { it.length >= 2 }
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "$it*" }
    }

    private fun android.database.Cursor.toSearchResultPlace(): SearchResultPlace {
        val name = getString(0).orEmpty()
        val address = getString(1).orEmpty()
        val city = getString(2).orEmpty()
        val lat = getDouble(3)
        val lng = getDouble(4)
        val categoryIndex = getColumnIndex("category")
        val category = if (categoryIndex >= 0) getString(categoryIndex).orEmpty() else ""
        val subTitle = listOf(address, city)
            .filter { it.isNotBlank() && !it.equals(name, ignoreCase = true) }
            .joinToString(", ")
        return SearchResultPlace(
            name = name,
            subTitle = subTitle,
            latitude = lat,
            longitude = lng,
            category = category,
        )
    }

    companion object {
        private const val TAG = "LocalPlacesRepository"
        private const val PLACES_DIR = "places"
        private const val PREFS_NAME = "places_prefs"
        private const val KEY_ACTIVE_ISO = "active_iso"
        private const val BBOX_DELTA = 0.5
        private const val LOCAL_RESULT_LIMIT = 15
        private const val BBOX_RESULT_LIMIT = 100
        private const val DOWNLOAD_USER_AGENT = "MixAutoCarLauncher/1.0"
        private const val DOWNLOAD_CONNECT_TIMEOUT_MS = 30_000
        private const val DOWNLOAD_READ_TIMEOUT_MS = 300_000
    }
}
