package com.kyuusanq3.mixauto.data.map

import android.content.Context
import com.kyuusanq3.mixauto.BuildConfig
import java.io.File

/**
 * Map display uses [displayStyleUri] (asset). OfflineManager needs absolute file:// URLs
 * (asset:// stalls) and a **minimal** pack style without glyphs/sprites — the full
 * Liberty fork enumerates thousands of font resources and can hang on "Preparing…".
 */
object MapStyleAssetResolver {

    private const val DISPLAY_STYLE_ASSET = "map/mix-auto-driving.json"
    private const val OFFLINE_PACK_ASSET = "map/mix-auto-offline-pack.json"
    private const val OFFLINE_PACK_FILE = "mix-auto-offline-pack.json"
    private const val PREFS_NAME = "map_style_assets"
    private const val KEY_OFFLINE_PACK_VERSION = "offline_pack_version"

    fun displayStyleUri(): String = MapStyleConstants.VECTOR_STYLE_URI

    /** Style for [OfflineTilePyramidRegionDefinition] only — vector tiles, no label glyphs. */
    fun offlineRegionStyleUri(context: Context): String {
        val styleFile = ensureOfflinePackStyleFile(context)
        return "file://${styleFile.absolutePath}"
    }

    private fun ensureOfflinePackStyleFile(context: Context): File {
        val dir = File(context.filesDir, "map").apply { mkdirs() }
        val out = File(dir, OFFLINE_PACK_FILE)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val installedVersion = prefs.getInt(KEY_OFFLINE_PACK_VERSION, -1)
        if (!out.exists() || out.length() == 0L || installedVersion != BuildConfig.VERSION_CODE) {
            context.assets.open(OFFLINE_PACK_ASSET).use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            prefs.edit().putInt(KEY_OFFLINE_PACK_VERSION, BuildConfig.VERSION_CODE).apply()
        }
        return out
    }
}
