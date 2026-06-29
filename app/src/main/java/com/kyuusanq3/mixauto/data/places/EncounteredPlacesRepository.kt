package com.kyuusanq3.mixauto.data.places

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.location.Location
import android.util.Log
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import java.io.File
import kotlin.math.roundToInt

/**
 * Persists POIs encountered while driving (Overture corridor, vector labels, search hits).
 * Stored at [filesDir]/places/encountered.db alongside Overture country packs.
 */
class EncounteredPlacesRepository(context: Context) {

    private val appContext = context.applicationContext
    private val placesDir: File = File(appContext.filesDir, PLACES_DIR)
    private val dbFile: File = File(placesDir, ENCOUNTERED_DB)
    private val dbLock = Any()

    private var database: SQLiteDatabase? = null

    init {
        placesDir.mkdirs()
        synchronized(dbLock) {
            openDatabaseUnlocked()
        }
    }

    fun upsertAll(places: List<SearchResultPlace>, source: String) {
        if (places.isEmpty()) return
        val now = System.currentTimeMillis()
        synchronized(dbLock) {
            val db = writableDatabase() ?: return
            db.beginTransaction()
            try {
                for (place in places) {
                    if (place.name.isBlank()) continue
                    val key = placeKey(place.latitude, place.longitude)
                    val existing = db.rawQuery(
                        "SELECT seen_count FROM $TABLE WHERE place_key = ?",
                        arrayOf(key),
                    ).use { cursor ->
                        if (cursor.moveToFirst()) cursor.getInt(0) else null
                    }
                    if (existing == null) {
                        db.execSQL(
                            """
                            INSERT INTO $TABLE (
                                place_key, name, subtitle, category, lat, lng, source,
                                first_seen_ms, last_seen_ms, seen_count
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                            """.trimIndent(),
                            arrayOf(
                                key,
                                place.name,
                                place.subTitle,
                                place.category,
                                place.latitude,
                                place.longitude,
                                source,
                                now,
                                now,
                            ),
                        )
                    } else {
                        db.execSQL(
                            """
                            UPDATE $TABLE SET
                                name = ?,
                                subtitle = ?,
                                category = CASE WHEN ? != '' THEN ? ELSE category END,
                                lat = ?,
                                lng = ?,
                                source = ?,
                                last_seen_ms = ?,
                                seen_count = seen_count + 1
                            WHERE place_key = ?
                            """.trimIndent(),
                            arrayOf(
                                place.name,
                                place.subTitle,
                                place.category,
                                place.category,
                                place.latitude,
                                place.longitude,
                                source,
                                now,
                                key,
                            ),
                        )
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun searchPlaces(
        query: String,
        originLat: Double,
        originLng: Double,
        limit: Int = SEARCH_LIMIT,
    ): List<SearchResultPlace> {
        val tokens = tokenizeSearchQuery(query)
        if (tokens.isEmpty()) return emptyList()

        synchronized(dbLock) {
            val db = readableDatabase() ?: return emptyList()
            return db.rawQuery(
                "SELECT name, subtitle, category, lat, lng FROM $TABLE",
                null,
            ).use { cursor ->
                val results = mutableListOf<SearchResultPlace>()
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    val subtitle = cursor.getString(1)
                    val category = cursor.getString(2)
                    val lat = cursor.getDouble(3)
                    val lng = cursor.getDouble(4)
                    val haystack = "$name $subtitle $category".lowercase()
                    if (!tokens.all { token -> haystack.contains(token) }) continue
                    val distanceResults = FloatArray(1)
                    Location.distanceBetween(originLat, originLng, lat, lng, distanceResults)
                    results.add(
                        SearchResultPlace(
                            name = name,
                            subTitle = subtitle,
                            latitude = lat,
                            longitude = lng,
                            distanceInMeters = distanceResults[0],
                            category = category,
                        ),
                    )
                }
                results.sortedBy { it.distanceInMeters }.take(limit)
            }
        }
    }

    fun getPlacesNear(
        lat: Double,
        lng: Double,
        maxRadiusM: Float,
        limit: Int,
    ): List<SearchResultPlace> {
        if (limit <= 0) return emptyList()
        val delta = (maxRadiusM / METERS_PER_DEGREE_LAT).toDouble().coerceAtLeast(0.01)
        val minLat = lat - delta
        val maxLat = lat + delta
        val minLng = lng - delta
        val maxLng = lng + delta

        synchronized(dbLock) {
            val db = readableDatabase() ?: return emptyList()
            return db.rawQuery(
                """
                SELECT name, subtitle, category, lat, lng FROM $TABLE
                WHERE lat BETWEEN ? AND ? AND lng BETWEEN ? AND ?
                ORDER BY last_seen_ms DESC
                LIMIT ?
                """.trimIndent(),
                arrayOf(
                    minLat.toString(),
                    maxLat.toString(),
                    minLng.toString(),
                    maxLng.toString(),
                    (limit * 3).toString(),
                ),
            ).use { cursor ->
                val results = mutableListOf<SearchResultPlace>()
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    val subtitle = cursor.getString(1)
                    val category = cursor.getString(2)
                    val placeLat = cursor.getDouble(3)
                    val placeLng = cursor.getDouble(4)
                    val distanceResults = FloatArray(1)
                    Location.distanceBetween(lat, lng, placeLat, placeLng, distanceResults)
                    if (distanceResults[0] > maxRadiusM) continue
                    results.add(
                        SearchResultPlace(
                            name = name,
                            subTitle = subtitle,
                            latitude = placeLat,
                            longitude = placeLng,
                            distanceInMeters = distanceResults[0],
                            category = category,
                        ),
                    )
                }
                results.sortedBy { it.distanceInMeters }.take(limit)
            }
        }
    }

    fun pruneToMaxRecords(maxRecords: Int = MAX_RECORDS) {
        synchronized(dbLock) {
            val db = writableDatabase() ?: return
            val count = db.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
            if (count <= maxRecords) return
            val toDelete = count - maxRecords
            db.execSQL(
                """
                DELETE FROM $TABLE WHERE place_key IN (
                    SELECT place_key FROM $TABLE
                    ORDER BY last_seen_ms ASC
                    LIMIT ?
                )
                """.trimIndent(),
                arrayOf(toDelete),
            )
            Log.i(TAG, "Pruned $toDelete encountered places (cap $maxRecords)")
        }
    }

    fun clearAll() {
        synchronized(dbLock) {
            writableDatabase()?.execSQL("DELETE FROM $TABLE")
            Log.i(TAG, "Cleared all encountered places")
        }
    }

    private fun openDatabaseUnlocked(): Boolean {
        return try {
            if (!dbFile.exists()) {
                database = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
                createSchema(database!!)
            } else {
                database = SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READWRITE,
                )
            }
            true
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to open encountered places database", exception)
            database = null
            false
        }
    }

    private fun createSchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE (
                place_key TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                subtitle TEXT NOT NULL,
                category TEXT NOT NULL,
                lat REAL NOT NULL,
                lng REAL NOT NULL,
                source TEXT NOT NULL,
                first_seen_ms INTEGER NOT NULL,
                last_seen_ms INTEGER NOT NULL,
                seen_count INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_encountered_last_seen ON $TABLE(last_seen_ms DESC)",
        )
    }

    private fun readableDatabase(): SQLiteDatabase? {
        if (database?.isOpen != true) {
            openDatabaseUnlocked()
        }
        return database?.takeIf { it.isOpen }
    }

    private fun writableDatabase(): SQLiteDatabase? = readableDatabase()

    companion object {
        private const val TAG = "EncounteredPlaces"
        private const val PLACES_DIR = "places"
        private const val ENCOUNTERED_DB = "encountered.db"
        private const val TABLE = "encountered_places"
        private const val SEARCH_LIMIT = 15
        private const val MAX_RECORDS = 5_000
        private const val METERS_PER_DEGREE_LAT = 111_000f

        fun placeKey(lat: Double, lng: Double): String {
            val roundedLat = (lat * 100_000.0).roundToInt() / 100_000.0
            val roundedLng = (lng * 100_000.0).roundToInt() / 100_000.0
            return "$roundedLat,$roundedLng"
        }

        private fun tokenizeSearchQuery(query: String): List<String> {
            val trimmed = query.trim().lowercase()
            if (trimmed.length < 2) return emptyList()
            return trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
        }
    }
}
