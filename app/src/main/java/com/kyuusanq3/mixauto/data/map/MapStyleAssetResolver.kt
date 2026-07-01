package com.kyuusanq3.mixauto.data.map

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Map display uses [displayStyleUri] (asset). Offline region downloads use [prepareOfflineRegionStyleUri]
 * which copies the bundled minimal pack to app storage and serves it on loopback HTTP — OfflineManager
 * rejects asset:// and file:// with "Unable to parse resourceUrl".
 */
object MapStyleAssetResolver {

    private const val TAG = "MapStyleAssetResolver"
    private const val OFFLINE_PACK_ASSET = "map/mix-auto-offline-pack.json"
    private const val OFFLINE_PACK_FILE = "mix-auto-offline-pack.json"
    private const val PLANET_TILEJSON_CACHE_FILE = "planet-tilejson.json"
    private const val PLANET_TILEJSON_URL = "https://tiles.openfreemap.org/planet"
    private const val USER_AGENT = "MixAutoCarLauncher/1.0"
    private const val TILEJSON_TIMEOUT_MS = 30_000
    private const val TILEJSON_CACHE_TTL_MS = 24L * 60L * 60L * 1000L

    fun displayStyleUri(): String = MapStyleConstants.VECTOR_STYLE_URI

    /** Loopback http URL for [org.maplibre.android.offline.OfflineTilePyramidRegionDefinition]. */
    fun prepareOfflineRegionStyleUri(context: Context): String {
        val appContext = context.applicationContext
        val tileJson = fetchPlanetTileJson(appContext)
        val styleFile = writeOfflinePackStyleFile(appContext, tileJson)
        return OfflineStyleLocalServer.ensureRunning(styleFile)
    }

    private fun fetchPlanetTileJson(context: Context): JSONObject {
        val cacheFile = planetTileJsonCacheFile(context)
        return try {
            val fresh = fetchPlanetTileJsonFromNetwork()
            writePlanetTileJsonCache(cacheFile, fresh)
            fresh
        } catch (networkError: Exception) {
            readPlanetTileJsonCache(cacheFile)?.let { cached ->
                Log.w(TAG, "Using cached planet TileJSON after network failure", networkError)
                cached
            } ?: throw networkError
        }
    }

    private fun fetchPlanetTileJsonFromNetwork(): JSONObject {
        val connection = (URL(PLANET_TILEJSON_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = TILEJSON_TIMEOUT_MS
            readTimeout = TILEJSON_TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException(
                    "OpenFreeMap TileJSON failed: HTTP ${connection.responseCode}",
                )
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return parsePlanetTileJson(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parsePlanetTileJson(body: String): JSONObject {
        val json = JSONObject(body)
        val tiles = json.optJSONArray("tiles")
            ?: throw IllegalStateException("OpenFreeMap TileJSON missing tiles array")
        if (tiles.length() == 0) {
            throw IllegalStateException("OpenFreeMap TileJSON tiles array is empty")
        }
        Log.i(TAG, "Resolved planet tiles: ${tiles.optString(0)}")
        return json
    }

    private fun planetTileJsonCacheFile(context: Context): File {
        val dir = File(context.filesDir, "map").apply { mkdirs() }
        return File(dir, PLANET_TILEJSON_CACHE_FILE)
    }

    private fun writePlanetTileJsonCache(cacheFile: File, tileJson: JSONObject) {
        val envelope = JSONObject()
            .put("fetchedAtMs", System.currentTimeMillis())
            .put("tileJson", tileJson)
        cacheFile.writeText(envelope.toString())
    }

    private fun readPlanetTileJsonCache(cacheFile: File): JSONObject? {
        if (!cacheFile.exists()) return null
        return try {
            val envelope = JSONObject(cacheFile.readText())
            val fetchedAt = envelope.optLong("fetchedAtMs", 0L)
            val ageMs = System.currentTimeMillis() - fetchedAt
            val stale = ageMs > TILEJSON_CACHE_TTL_MS
            if (stale) {
                Log.i(TAG, "Planet TileJSON cache is stale (${ageMs / 3_600_000}h old) — will use as fallback only")
            }
            envelope.getJSONObject("tileJson")
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to read planet TileJSON cache", exception)
            null
        }
    }

    private fun writeOfflinePackStyleFile(context: Context, tileJson: JSONObject): File {
        val dir = File(context.filesDir, "map").apply { mkdirs() }
        val out = File(dir, OFFLINE_PACK_FILE)
        val baseStyle = context.assets.open(OFFLINE_PACK_ASSET).bufferedReader().use { it.readText() }
        val style = JSONObject(baseStyle)
        val source = style.getJSONObject("sources").getJSONObject("openmaptiles")
        source.remove("url")
        source.put("tiles", tileJson.getJSONArray("tiles"))
        source.put("minzoom", tileJson.optInt("minzoom", 0))
        source.put("maxzoom", tileJson.optInt("maxzoom", 17))
        // glyphs + sprite from bundled pack are preserved — only openmaptiles source is inlined.
        out.writeText(style.toString())
        return out
    }
}
