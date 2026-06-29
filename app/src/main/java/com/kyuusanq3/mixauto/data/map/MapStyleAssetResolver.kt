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
    private const val PLANET_TILEJSON_URL = "https://tiles.openfreemap.org/planet"
    private const val USER_AGENT = "MixAutoCarLauncher/1.0"
    private const val TILEJSON_TIMEOUT_MS = 30_000

    fun displayStyleUri(): String = MapStyleConstants.VECTOR_STYLE_URI

    /** Loopback http URL for [org.maplibre.android.offline.OfflineTilePyramidRegionDefinition]. */
    fun prepareOfflineRegionStyleUri(context: Context): String {
        val appContext = context.applicationContext
        val tileJson = fetchPlanetTileJson()
        val styleFile = writeOfflinePackStyleFile(appContext, tileJson)
        return OfflineStyleLocalServer.ensureRunning(styleFile)
    }

    private fun fetchPlanetTileJson(): JSONObject {
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
            val json = JSONObject(body)
            val tiles = json.optJSONArray("tiles")
                ?: throw IllegalStateException("OpenFreeMap TileJSON missing tiles array")
            if (tiles.length() == 0) {
                throw IllegalStateException("OpenFreeMap TileJSON tiles array is empty")
            }
            Log.i(TAG, "Resolved planet tiles: ${tiles.optString(0)}")
            return json
        } finally {
            connection.disconnect()
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
        source.put("maxzoom", tileJson.optInt("maxzoom", 14))
        out.writeText(style.toString())
        return out
    }
}
