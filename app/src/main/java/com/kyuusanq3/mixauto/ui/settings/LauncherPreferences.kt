package com.kyuusanq3.mixauto.ui.settings

import android.content.Context
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import org.json.JSONArray
import org.json.JSONObject

class LauncherPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isLeftHandDrive: Boolean
        get() = prefs.getBoolean(KEY_LEFT_HAND_DRIVE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_LEFT_HAND_DRIVE, value).apply()
        }

    var isShortcutsHorizontal: Boolean
        get() = prefs.getBoolean(KEY_SHORTCUTS_HORIZONTAL, true)
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
        get() = prefs.getBoolean(KEY_VECTOR_TILES, true)
        set(value) {
            prefs.edit().putBoolean(KEY_VECTOR_TILES, value).apply()
        }

    var show3dBuildings: Boolean
        get() = prefs.getBoolean(KEY_3D_BUILDINGS, false)
        set(value) {
            prefs.edit().putBoolean(KEY_3D_BUILDINGS, value).apply()
        }

    var isLauncherMode: Boolean
        get() = prefs.getBoolean(KEY_LAUNCHER_MODE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_LAUNCHER_MODE, value).apply()
        }

    var isLargeShortcutIcons: Boolean
        get() = prefs.getBoolean(KEY_LARGE_SHORTCUT_ICONS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_LARGE_SHORTCUT_ICONS, value).apply()
        }

    var drivingZoom: Float
        get() = prefs.getFloat(KEY_DRIVING_ZOOM, DEFAULT_DRIVING_ZOOM)
        set(value) {
            prefs.edit().putFloat(KEY_DRIVING_ZOOM, value).apply()
        }

    var puckHorizontalOffset: Float
        get() = prefs.getFloat(KEY_PUCK_H_OFFSET, DEFAULT_PUCK_H_OFFSET)
        set(value) {
            prefs.edit().putFloat(KEY_PUCK_H_OFFSET, value).apply()
        }

    var puckVerticalOffset: Float
        get() = prefs.getFloat(KEY_PUCK_V_OFFSET, DEFAULT_PUCK_V_OFFSET)
        set(value) {
            prefs.edit().putFloat(KEY_PUCK_V_OFFSET, value).apply()
        }

    var puckScale: Float
        get() = prefs.getFloat(KEY_PUCK_SCALE, DEFAULT_PUCK_SCALE)
        set(value) {
            prefs.edit().putFloat(KEY_PUCK_SCALE, value).apply()
        }

    var showTraffic: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TRAFFIC, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_TRAFFIC, value).apply()
        }

    var tomTomApiKey: String
        get() = prefs.getString(KEY_TOMTOM_API_KEY, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_TOMTOM_API_KEY, value).apply()
        }

    var recentDestinations: List<SearchResultPlace>
        get() {
            val json = prefs.getString(KEY_RECENT_DESTINATIONS, null) ?: return emptyList()
            return parsePlacesJson(json)
        }
        set(value) {
            val array = JSONArray()
            value.take(MAX_RECENT_DESTINATIONS).forEach { place ->
                array.put(placeToJson(place))
            }
            prefs.edit().putString(KEY_RECENT_DESTINATIONS, array.toString()).apply()
        }

    var savedPlaces: List<SearchResultPlace>
        get() {
            val json = prefs.getString(KEY_SAVED_PLACES, null) ?: return emptyList()
            return parsePlacesJson(json)
        }
        set(value) {
            val array = JSONArray()
            value.take(MAX_SAVED_PLACES).forEach { place ->
                array.put(placeToJson(place))
            }
            prefs.edit().putString(KEY_SAVED_PLACES, array.toString()).apply()
        }

    var onboardingVersion: Int
        get() = prefs.getInt(KEY_ONBOARDING_VERSION, 0)
        set(value) {
            prefs.edit().putInt(KEY_ONBOARDING_VERSION, value).apply()
        }

    var defaultAudioPackage: String
        get() = prefs.getString(KEY_DEFAULT_AUDIO_PACKAGE, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_DEFAULT_AUDIO_PACKAGE, value).apply()
        }

    var dockPinnedPackages: List<String>
        get() {
            val json = prefs.getString(KEY_DOCK_PINNED_PACKAGES, null) ?: return emptyList()
            return parsePackageListJson(json)
        }
        set(value) {
            val array = JSONArray()
            value.take(MAX_DOCK_PINNED_APPS).forEach { pkg ->
                array.put(pkg)
            }
            prefs.edit().putString(KEY_DOCK_PINNED_PACKAGES, array.toString()).apply()
        }

    var albumArtMode: String
        get() = prefs.getString(KEY_ALBUM_ART_MODE, DEFAULT_ALBUM_ART_MODE) ?: DEFAULT_ALBUM_ART_MODE
        set(value) {
            prefs.edit().putString(KEY_ALBUM_ART_MODE, value).apply()
        }

    var showStatusStrip: Boolean
        get() = prefs.getBoolean(KEY_SHOW_STATUS_STRIP, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_STATUS_STRIP, value).apply()
        }

    var showSystemStatusBar: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SYSTEM_STATUS_BAR, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_SYSTEM_STATUS_BAR, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "launcher_prefs"
        private const val KEY_LEFT_HAND_DRIVE = "lhd"
        private const val KEY_SHORTCUTS_HORIZONTAL = "shortcuts_horizontal"
        private const val KEY_MAP_MEDIA_RATIO = "map_media_ratio"
        private const val KEY_LIMIT_SEARCH_DISTANCE = "limit_search_distance"
        private const val KEY_VECTOR_TILES = "vector_tiles"
        private const val KEY_3D_BUILDINGS = "show_3d_buildings"
        private const val KEY_LAUNCHER_MODE = "launcher_mode"
        private const val KEY_LARGE_SHORTCUT_ICONS = "large_shortcut_icons"
        private const val KEY_DRIVING_ZOOM = "driving_zoom"
        private const val KEY_PUCK_H_OFFSET = "puck_h_offset"
        private const val KEY_PUCK_V_OFFSET = "puck_v_offset"
        private const val KEY_PUCK_SCALE = "puck_scale"
        private const val KEY_SHOW_TRAFFIC = "show_traffic"
        private const val KEY_TOMTOM_API_KEY = "tomtom_api_key"
        private const val KEY_RECENT_DESTINATIONS = "recent_destinations"
        private const val KEY_SAVED_PLACES = "saved_places"
        private const val KEY_ONBOARDING_VERSION = "onboarding_version"
        private const val KEY_DEFAULT_AUDIO_PACKAGE = "default_audio_package"
        private const val KEY_DOCK_PINNED_PACKAGES = "dock_pinned_packages"
        private const val KEY_ALBUM_ART_MODE = "album_art_mode"
        private const val KEY_SHOW_STATUS_STRIP = "show_status_strip"
        private const val KEY_SHOW_SYSTEM_STATUS_BAR = "show_system_status_bar"
        const val DEFAULT_ALBUM_ART_MODE = "PLAIN"
        const val MAX_RECENT_DESTINATIONS = 10
        const val MAX_SAVED_PLACES = 50
        const val MAX_DOCK_PINNED_APPS = 5
        const val DEFAULT_MAP_MEDIA_RATIO = 0.6f
        const val DEFAULT_DRIVING_ZOOM = 17.5f
        const val DEFAULT_PUCK_H_OFFSET = 0.3f
        const val DEFAULT_PUCK_V_OFFSET = 0.4f
        const val DEFAULT_PUCK_SCALE = 1.0f

        private fun placeToJson(place: SearchResultPlace): JSONObject =
            JSONObject().apply {
                put("name", place.name)
                put("subTitle", place.subTitle)
                put("latitude", place.latitude)
                put("longitude", place.longitude)
                put("distanceInMeters", place.distanceInMeters.toDouble())
                put("category", place.category)
                put("isDroppedPin", place.isDroppedPin)
            }

        private fun parsePackageListJson(json: String): List<String> {
            return try {
                val array = JSONArray(json)
                buildList {
                    for (i in 0 until array.length()) {
                        val pkg = array.optString(i, "").trim()
                        if (pkg.isNotEmpty()) add(pkg)
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        private fun parsePlacesJson(json: String): List<SearchResultPlace> {
            return try {
                val array = JSONArray(json)
                buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        add(
                            SearchResultPlace(
                                name = obj.getString("name"),
                                subTitle = obj.optString("subTitle", ""),
                                latitude = obj.getDouble("latitude"),
                                longitude = obj.getDouble("longitude"),
                                distanceInMeters = obj.optDouble("distanceInMeters", 0.0).toFloat(),
                                category = obj.optString("category", ""),
                                isDroppedPin = obj.optBoolean("isDroppedPin", false),
                            ),
                        )
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
