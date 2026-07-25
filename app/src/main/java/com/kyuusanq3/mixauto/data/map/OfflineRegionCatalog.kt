package com.kyuusanq3.mixauto.data.map

import android.content.Context
import android.util.Log
import org.json.JSONObject

data class OfflineRegionDefinition(
    val id: String,
    val name: String,
    val countryIso: String,
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
    val minZoom: Double,
    val maxZoom: Double,
    val sizeEstimateMb: String,
) {
    fun contains(lat: Double, lng: Double): Boolean =
        lat in south..north && lng in west..east

    fun toBounds(): org.maplibre.android.geometry.LatLngBounds =
        org.maplibre.android.geometry.LatLngBounds.Builder()
            .include(org.maplibre.android.geometry.LatLng(south, west))
            .include(org.maplibre.android.geometry.LatLng(north, east))
            .build()
}

data class OfflineCountryCatalog(
    val iso: String,
    val name: String,
    val regions: List<OfflineRegionDefinition>,
)

internal object OfflineRegionCatalog {
    private const val TAG = "OfflineRegionCatalog"
    private const val CATALOG_ASSET = "map/offline_regions.json"

    fun loadCatalog(appContext: Context): List<OfflineCountryCatalog> {
        return try {
            val jsonText = appContext.assets.open(CATALOG_ASSET).bufferedReader().use { it.readText() }
            val root = JSONObject(jsonText)
            val countriesArray = root.getJSONArray("countries")
            buildList {
                for (index in 0 until countriesArray.length()) {
                    val country = countriesArray.getJSONObject(index)
                    val iso = country.getString("iso").uppercase()
                    val name = country.getString("name")
                    val regionsArray = country.getJSONArray("regions")
                    val regions = buildList {
                        for (regionIndex in 0 until regionsArray.length()) {
                            val region = regionsArray.getJSONObject(regionIndex)
                            add(
                                OfflineRegionDefinition(
                                    id = region.getString("id"),
                                    name = region.getString("name"),
                                    countryIso = iso,
                                    south = region.getDouble("south"),
                                    west = region.getDouble("west"),
                                    north = region.getDouble("north"),
                                    east = region.getDouble("east"),
                                    minZoom = region.getDouble("minZoom"),
                                    maxZoom = region.getDouble("maxZoom"),
                                    sizeEstimateMb = region.getString("sizeEstimateMb"),
                                ),
                            )
                        }
                    }
                    add(OfflineCountryCatalog(iso = iso, name = name, regions = regions))
                }
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to load offline region catalog", exception)
            emptyList()
        }
    }
}
