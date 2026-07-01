package com.kyuusanq3.mixauto.data.map

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.maplibre.android.offline.OfflineManager
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Warms MapLibre's ambient cache with vector tiles slightly ahead of the vehicle.
 */
class DrivingTilePrefetcher(
    private val appContext: Context,
    private val scope: CoroutineScope,
) {
    private var prefetchJob: Job? = null
    private var lastPrefetchMs: Long = 0L
    private var lastPrefetchLat: Double = Double.NaN
    private var lastPrefetchLng: Double = Double.NaN
    private var tileUrlTemplate: String? = null

    fun maybePrefetch(
        lat: Double,
        lng: Double,
        bearingDeg: Float,
        zoom: Double,
        enabled: Boolean,
    ) {
        if (!enabled) return
        val now = System.currentTimeMillis()
        val movedM = if (lastPrefetchLat.isNaN()) {
            Float.MAX_VALUE
        } else {
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                lastPrefetchLat,
                lastPrefetchLng,
                lat,
                lng,
                results,
            )
            results[0]
        }
        if (now - lastPrefetchMs < PREFETCH_MIN_INTERVAL_MS && movedM < PREFETCH_MIN_MOVE_M) {
            return
        }
        lastPrefetchMs = now
        lastPrefetchLat = lat
        lastPrefetchLng = lng

        prefetchJob?.cancel()
        prefetchJob = scope.launch(Dispatchers.IO) {
            runCatching {
                prefetchTiles(lat, lng, bearingDeg, zoom)
            }.onFailure { error ->
                Log.w(TAG, "Driving tile prefetch failed: ${error.message}")
            }
        }
    }

    fun cancel() {
        prefetchJob?.cancel()
        prefetchJob = null
    }

    private suspend fun prefetchTiles(
        lat: Double,
        lng: Double,
        bearingDeg: Float,
        zoom: Double,
    ) {
        val template = resolveTileUrlTemplate() ?: return
        val z = zoom.toInt().coerceIn(10, 17)
        val aheadLatLng = offsetMeters(lat, lng, bearingDeg, PREFETCH_AHEAD_M)
        val center = latLngToTileXY(aheadLatLng.first, aheadLatLng.second, z)
        val urls = linkedSetOf<String>()
        for (dz in 0..2) {
            val level = (z - dz).coerceAtLeast(10)
            val ahead = latLngToTileXY(aheadLatLng.first, aheadLatLng.second, level)
            for (dx in -1..1) {
                for (dy in 0..4) {
                    val x = ahead.first + dx
                    val y = ahead.second + dy
                    if (x < 0 || y < 0) continue
                    urls.add(
                        template
                            .replace("{z}", level.toString())
                            .replace("{x}", x.toString())
                            .replace("{y}", y.toString()),
                    )
                }
            }
        }
        // Always include tiles around current position at primary zoom.
        for (dx in -1..1) {
            for (dy in -1..1) {
                urls.add(
                    template
                        .replace("{z}", z.toString())
                        .replace("{x}", (center.first + dx).toString())
                        .replace("{y}", (center.second + dy).toString()),
                )
            }
        }

        val offlineManager = OfflineManager.getInstance(appContext)
        urls.take(MAX_URLS_PER_CYCLE).forEach { url ->
            val bytes = fetchTileBytes(url) ?: return@forEach
            runCatching {
                offlineManager.putResourceWithUrl(url, bytes, 0L, 0L, null, false)
            }
        }
    }

    private fun resolveTileUrlTemplate(): String? {
        tileUrlTemplate?.let { return it }
        val connection = (URL(PLANET_TILEJSON_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = TILEJSON_TIMEOUT_MS
            readTimeout = TILEJSON_TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
            instanceFollowRedirects = true
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val tiles = org.json.JSONObject(body).optJSONArray("tiles") ?: return null
            if (tiles.length() == 0) return null
            tiles.optString(0).also { tileUrlTemplate = it }
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to resolve tile URL template", exception)
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchTileBytes(url: String): ByteArray? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TILE_FETCH_TIMEOUT_MS
            readTimeout = TILE_FETCH_TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
            instanceFollowRedirects = true
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { it.readBytes() }
        } catch (exception: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun latLngToTileXY(lat: Double, lng: Double, zoom: Int): Pair<Int, Int> {
        val n = 1 shl zoom
        val x = floor((lng + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
        val latRad = Math.toRadians(lat)
        val y = floor(
            (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n,
        ).toInt().coerceIn(0, n - 1)
        return x to y
    }

    private fun offsetMeters(lat: Double, lng: Double, bearingDeg: Float, meters: Double): Pair<Double, Double> {
        val bearingRad = Math.toRadians(bearingDeg.toDouble())
        val dLat = meters * cos(bearingRad) / METERS_PER_DEGREE_LAT
        val dLng = meters * kotlin.math.sin(bearingRad) /
            (METERS_PER_DEGREE_LAT * cos(Math.toRadians(lat)))
        return lat + dLat to lng + dLng
    }

    companion object {
        private const val TAG = "DrivingTilePrefetcher"
        private const val PLANET_TILEJSON_URL = "https://tiles.openfreemap.org/planet"
        private const val USER_AGENT = "MixAutoCarLauncher/1.0"
        private const val TILEJSON_TIMEOUT_MS = 15_000
        private const val TILE_FETCH_TIMEOUT_MS = 8_000
        private const val PREFETCH_MIN_INTERVAL_MS = 3_000L
        private const val PREFETCH_MIN_MOVE_M = 80f
        private const val PREFETCH_AHEAD_M = 350.0
        private const val MAX_URLS_PER_CYCLE = 36
        private const val METERS_PER_DEGREE_LAT = 111_320.0
    }
}
