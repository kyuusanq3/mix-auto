package com.kyuusanq3.mixauto.ui.settings

import android.content.Context

class LauncherPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isLeftHandDrive: Boolean
        get() = prefs.getBoolean(KEY_LEFT_HAND_DRIVE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_LEFT_HAND_DRIVE, value).apply()
        }

    var isShortcutsHorizontal: Boolean
        get() = prefs.getBoolean(KEY_SHORTCUTS_HORIZONTAL, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SHORTCUTS_HORIZONTAL, value).apply()
        }

    var mapMediaRatio: Float
        get() = prefs.getFloat(KEY_MAP_MEDIA_RATIO, DEFAULT_MAP_MEDIA_RATIO)
        set(value) {
            prefs.edit().putFloat(KEY_MAP_MEDIA_RATIO, value).apply()
        }

    var limitSearchDistance: Boolean
        get() = prefs.getBoolean(KEY_LIMIT_SEARCH_DISTANCE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_LIMIT_SEARCH_DISTANCE, value).apply()
        }

    var useVectorTiles: Boolean
        get() = prefs.getBoolean(KEY_VECTOR_TILES, false)
        set(value) {
            prefs.edit().putBoolean(KEY_VECTOR_TILES, value).apply()
        }

    var isLauncherMode: Boolean
        get() = prefs.getBoolean(KEY_LAUNCHER_MODE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_LAUNCHER_MODE, value).apply()
        }

    var isLargeShortcutIcons: Boolean
        get() = prefs.getBoolean(KEY_LARGE_SHORTCUT_ICONS, false)
        set(value) {
            prefs.edit().putBoolean(KEY_LARGE_SHORTCUT_ICONS, value).apply()
        }

    var drivingZoom: Float
        get() = prefs.getFloat(KEY_DRIVING_ZOOM, DEFAULT_DRIVING_ZOOM)
        set(value) {
            prefs.edit().putFloat(KEY_DRIVING_ZOOM, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "launcher_prefs"
        private const val KEY_LEFT_HAND_DRIVE = "lhd"
        private const val KEY_SHORTCUTS_HORIZONTAL = "shortcuts_horizontal"
        private const val KEY_MAP_MEDIA_RATIO = "map_media_ratio"
        private const val KEY_LIMIT_SEARCH_DISTANCE = "limit_search_distance"
        private const val KEY_VECTOR_TILES = "vector_tiles"
        private const val KEY_LAUNCHER_MODE = "launcher_mode"
        private const val KEY_LARGE_SHORTCUT_ICONS = "large_shortcut_icons"
        private const val KEY_DRIVING_ZOOM = "driving_zoom"
        const val DEFAULT_MAP_MEDIA_RATIO = 0.6f
        const val DEFAULT_DRIVING_ZOOM = 17.5f
    }
}
