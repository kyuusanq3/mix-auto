package com.kyuusanq3.mixauto.data.places

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.location.Location
import android.net.Uri
import android.util.Log
import com.kyuusanq3.mixauto.data.map.rankSearchResults
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import com.kyuusanq3.mixauto.ui.settings.DeveloperSettings
import java.io.File

class LocalPlacesRepository(context: Context) {

    private val appContext = context.applicationContext
    private val placesDir: File = File(appContext.filesDir, PLACES_DIR)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val dbLock = Any()

    private var database: SQLiteDatabase? = null
    private var activeIsoCode: String? = null
    private var fts5Available: Boolean = true

    private val downloader = LocalPlacesDownloader(
        appContext = appContext,
        placesDir = placesDir,
        dbLock = dbLock,
        databaseFile = ::databaseFile,
        prepareDatabaseFileReplacement = ::prepareDatabaseFileReplacement,
        openDatabase = ::openDatabase,
        readMeta = ::readMeta,
        deleteDatabase = ::deleteDatabase,
    )

    init {
        placesDir.mkdirs()
        synchronized(dbLock) {
            val savedIso = prefs.getString(KEY_ACTIVE_ISO, null)
            when {
                savedIso != null && databaseFile(savedIso).exists() ->
                    openDatabaseUnlocked(savedIso)
                else ->
                    discoverAndOpenInstalledDatabaseUnlocked()
            }
        }
    }

    val isOpen: Boolean get() = database?.isOpen == true

    val activeCountryIso: String? get() = activeIsoCode

    val hasInstalledDatabase: Boolean
        get() = listInstalledDatabaseIsos().isNotEmpty()

    /** Opens the largest installed DB if a file exists but the connection is closed. */
    fun ensureDatabaseOpen(): Boolean = synchronized(dbLock) {
        if (database?.isOpen == true) return true
        if (!hasInstalledDatabase) return false
        val opened = discoverAndOpenInstalledDatabaseUnlocked()
        if (!opened) {
            Log.w(TAG, "Places database file exists but could not be opened")
        }
        opened
    }

    fun databaseFile(isoCode: String): File = File(placesDir, "${isoCode.lowercase()}.db")

    fun isDatabaseInstalled(isoCode: String): Boolean = databaseFile(isoCode).exists()

    fun openDatabase(isoCode: String): Boolean = synchronized(dbLock) {
        openDatabaseUnlocked(isoCode)
    }

    fun closeDatabase() = synchronized(dbLock) {
        closeDatabaseUnlocked()
    }

    fun deleteDatabase(isoCode: String) = synchronized(dbLock) {
        if (activeIsoCode.equals(isoCode, ignoreCase = true)) {
            closeDatabaseUnlocked()
            prefs.edit().remove(KEY_ACTIVE_ISO).apply()
        }
        deleteDatabaseFiles(isoCode)
    }

    private fun openDatabaseUnlocked(isoCode: String): Boolean {
        val file = databaseFile(isoCode)
        if (!file.exists()) {
            Log.w(TAG, "Places database missing for $isoCode")
            return false
        }
        closeDatabaseUnlocked()
        return openDatabaseFile(isoCode, file, retryAfterSidecarCleanup = true)
    }

    private fun openDatabaseFile(
        isoCode: String,
        file: File,
        retryAfterSidecarCleanup: Boolean,
    ): Boolean {
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
            if (retryAfterSidecarCleanup) {
                Log.w(
                    TAG,
                    "Failed to open places database for $isoCode, clearing sidecars and retrying",
                    exception,
                )
                deleteSidecarFiles(file)
                return openDatabaseFile(isoCode, file, retryAfterSidecarCleanup = false)
            }
            Log.w(TAG, "Failed to open places database for $isoCode", exception)
            database = null
            activeIsoCode = null
            false
        }
    }

    private fun closeDatabaseUnlocked() {
        database?.close()
        database = null
        activeIsoCode = null
    }

    private fun readableDatabase(): SQLiteDatabase? = synchronized(dbLock) {
        val open = database
        if (open != null && open.isOpen) return open

        val savedIso = activeIsoCode ?: prefs.getString(KEY_ACTIVE_ISO, null)
        if (savedIso != null && databaseFile(savedIso).exists() && openDatabaseUnlocked(savedIso)) {
            return database
        }
        if (discoverAndOpenInstalledDatabaseUnlocked()) return database
        return null
    }

    private fun listInstalledDatabaseIsos(): List<String> {
        return placesDir.listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.name.endsWith(".db", ignoreCase = true) &&
                    !file.name.endsWith(".tmp", ignoreCase = true)
            }
            ?.map { it.name.removeSuffix(".db").uppercase() }
            .orEmpty()
    }

    private fun discoverAndOpenInstalledDatabaseUnlocked(): Boolean {
        val installed = placesDir.listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.name.endsWith(".db", ignoreCase = true) &&
                    !file.name.endsWith(".tmp", ignoreCase = true)
            }
            .orEmpty()
        if (installed.isEmpty()) return false
        val best = installed.maxByOrNull { it.length() } ?: return false
        val iso = best.name.removeSuffix(".db").uppercase()
        Log.i(TAG, "Auto-discovered installed places database: $iso")
        return openDatabaseUnlocked(iso)
    }

    private fun prepareDatabaseFileReplacement(isoCode: String) {
        if (databaseFile(isoCode).exists()) {
            closeDatabaseUnlocked()
            deleteSidecarFiles(databaseFile(isoCode))
        }
    }

    private fun deleteSidecarFiles(dbFile: File) {
        File("${dbFile.path}-wal").delete()
        File("${dbFile.path}-shm").delete()
        File("${dbFile.path}-journal").delete()
    }

    private fun deleteDatabaseFiles(isoCode: String) {
        deleteSidecarFiles(databaseFile(isoCode))
        databaseFile(isoCode).delete()
        File(placesDir, "${isoCode.lowercase()}.db.tmp").delete()
    }

    fun downloadDatabaseFromUrl(
        isoCode: String,
        url: String,
        onProgress: (Float) -> Unit,
    ): Result<LocalDbMeta> = downloader.downloadDatabaseFromUrl(isoCode, url, onProgress)

    fun installFromAsset(assetPath: String, isoCode: String): Boolean =
        downloader.installFromAsset(assetPath, isoCode)

    fun importDatabaseFromUri(
        defaultIsoCode: String,
        uri: Uri,
        onProgress: (Float) -> Unit,
    ): Result<LocalDbMeta> = downloader.importDatabaseFromUri(defaultIsoCode, uri, onProgress)

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
        if (query.isBlank()) return emptyList()

        return runCatching {
            if (!ensureDatabaseOpen()) return emptyList()
            val db = readableDatabase() ?: return emptyList()

            val minLat = currentLat - BBOX_DELTA
            val maxLat = currentLat + BBOX_DELTA
            val minLng = currentLng - BBOX_DELTA
            val maxLng = currentLng + BBOX_DELTA

            val results = if (fts5Available) {
                searchWithFts5(db, query, currentLat, currentLng, minLat, maxLat, minLng, maxLng)
            } else {
                searchWithLike(db, query, currentLat, currentLng, minLat, maxLat, minLng, maxLng)
            }

            rankSearchResults(
                results.map { row ->
                    val distanceResults = FloatArray(1)
                    Location.distanceBetween(
                        currentLat,
                        currentLng,
                        row.latitude,
                        row.longitude,
                        distanceResults,
                    )
                    row.copy(distanceInMeters = distanceResults[0])
                },
            ).take(LOCAL_RESULT_LIMIT)
        }.getOrElse { error ->
            Log.w(TAG, "Search failed: ${error.message}")
            emptyList()
        }
    }

    fun getPlacesInBounds(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
        limit: Int = BBOX_RESULT_LIMIT,
    ): List<SearchResultPlace> {
        if (!ensureDatabaseOpen()) return emptyList()
        val db = readableDatabase() ?: return emptyList()
        val sql = """
            SELECT name, address, city, lat, lng, category, confidence
            FROM places
            WHERE lat BETWEEN ? AND ?
              AND lng BETWEEN ? AND ?
              ${confidenceSqlPredicate()}
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
        currentLat: Double,
        currentLng: Double,
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
    ): List<SearchResultPlace> {
        val ftsQuery = buildFtsQuery(query) ?: return emptyList()
        val sql = """
            SELECT p.name, p.address, p.city, p.lat, p.lng, p.category, p.confidence
            FROM places p
            JOIN places_fts fts ON p.rowid = fts.rowid
            WHERE places_fts MATCH ?
              AND p.lat BETWEEN ? AND ?
              AND p.lng BETWEEN ? AND ?
              ${confidenceSqlPredicate("p")}
            ORDER BY ((p.lat - ?) * (p.lat - ?) + (p.lng - ?) * (p.lng - ?))
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
                    currentLat.toString(),
                    currentLat.toString(),
                    currentLng.toString(),
                    currentLng.toString(),
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
            searchWithLike(db, query, currentLat, currentLng, minLat, maxLat, minLng, maxLng)
        }
    }

    private fun searchWithLike(
        db: SQLiteDatabase,
        query: String,
        currentLat: Double,
        currentLng: Double,
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
    ): List<SearchResultPlace> {
        val likePattern = "%${query.trim()}%"
        val sql = """
            SELECT name, address, city, lat, lng, category, confidence
            FROM places
            WHERE (name LIKE ? OR address LIKE ? OR city LIKE ?)
              AND lat BETWEEN ? AND ?
              AND lng BETWEEN ? AND ?
              ${confidenceSqlPredicate()}
            ORDER BY ((lat - ?) * (lat - ?) + (lng - ?) * (lng - ?))
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
                    currentLat.toString(),
                    currentLat.toString(),
                    currentLng.toString(),
                    currentLng.toString(),
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

    private fun confidenceSqlPredicate(tableAlias: String = ""): String {
        if (!DeveloperSettings.FILTER_LOW_CONFIDENCE_POIS) return ""
        val column = if (tableAlias.isBlank()) {
            "confidence"
        } else {
            "$tableAlias.confidence"
        }
        return " AND $column >= ${DeveloperSettings.MIN_POI_CONFIDENCE}"
    }

    private fun android.database.Cursor.toSearchResultPlace(): SearchResultPlace {
        val name = getString(0).orEmpty()
        val address = getString(1).orEmpty()
        val city = getString(2).orEmpty()
        val lat = getDouble(3)
        val lng = getDouble(4)
        val categoryIndex = getColumnIndex("category")
        val category = if (categoryIndex >= 0) getString(categoryIndex).orEmpty() else ""
        val confidenceIndex = getColumnIndex("confidence")
        val confidence = if (confidenceIndex >= 0 && !isNull(confidenceIndex)) {
            getFloat(confidenceIndex)
        } else {
            null
        }
        val hasStreet = address.isNotBlank() && !address.equals(city, ignoreCase = true)
        val subTitle = listOf(address, city)
            .filter { it.isNotBlank() && !it.equals(name, ignoreCase = true) }
            .joinToString(", ")
        return SearchResultPlace(
            name = name,
            subTitle = subTitle,
            latitude = lat,
            longitude = lng,
            category = category,
            confidence = confidence,
            hasStreetAddress = hasStreet,
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
    }
}
