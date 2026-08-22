package com.kyuusanq3.mixauto.data.places

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.location.Location
import android.util.Log
import com.kyuusanq3.mixauto.data.map.rankSearchResults
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import com.kyuusanq3.mixauto.ui.settings.DeveloperSettings

/**
 * Overture SQLite search extracted from [LocalPlacesRepository].
 * Open/lifecycle and pack install stay on the repository.
 */
internal class LocalPlacesSearch(
    private val ensureDatabaseOpen: () -> Boolean,
    private val readableDatabase: () -> SQLiteDatabase?,
    private val isFts5Available: () -> Boolean,
    private val markFts5Unavailable: () -> Unit,
) {
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

            val results = if (isFts5Available()) {
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
            markFts5Unavailable()
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

    private fun Cursor.toSearchResultPlace(): SearchResultPlace {
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
        private const val TAG = "LocalPlacesSearch"
        private const val BBOX_DELTA = 0.5
        private const val LOCAL_RESULT_LIMIT = 15
        internal const val BBOX_RESULT_LIMIT = 100
    }
}
