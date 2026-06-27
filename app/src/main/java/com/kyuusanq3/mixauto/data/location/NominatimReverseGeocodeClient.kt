package com.kyuusanq3.mixauto.data.location

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs

object NominatimReverseGeocodeClient {
    private const val TAG = "NominatimReverseGeocode"
    private const val USER_AGENT = "MixAutoCarLauncher/1.0"
    private const val CACHE_TTL_MS = 60 * 60 * 1000L
    private const val COORD_CACHE_DELTA = 0.05

    private val cityAddressKeys = listOf(
        "city",
        "town",
        "municipality",
        "village",
        "city_district",
        "suburb",
        "county",
    )

    @Volatile
    private var cachedLat: Double? = null

    @Volatile
    private var cachedLng: Double? = null

    @Volatile
    private var cachedCity: String? = null

    @Volatile
    private var cachedAtMs: Long = 0L

    fun fetchCityName(latitude: Double, longitude: Double): String? {
        val now = System.currentTimeMillis()
        val cacheLat = cachedLat
        val cacheLng = cachedLng
        if (
            cachedCity != null &&
            cacheLat != null &&
            cacheLng != null &&
            now - cachedAtMs < CACHE_TTL_MS &&
            abs(cacheLat - latitude) < COORD_CACHE_DELTA &&
            abs(cacheLng - longitude) < COORD_CACHE_DELTA
        ) {
            return cachedCity
        }

        val latParam = String.format(Locale.US, "%.4f", latitude)
        val lngParam = String.format(Locale.US, "%.4f", longitude)
        val url = URL(
            "https://nominatim.openstreetmap.org/reverse" +
                "?lat=$latParam&lon=$lngParam&format=json",
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000

        return try {
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "fetchCityName HTTP ${connection.responseCode}")
                return cachedCity
            }
            val body = connection.inputStream.bufferedReader().readText()
            val address = JSONObject(body).optJSONObject("address") ?: return cachedCity
            val city = cityAddressKeys
                .asSequence()
                .map { key -> address.optString(key).trim() }
                .firstOrNull { it.isNotBlank() }
            if (city != null) {
                cachedLat = latitude
                cachedLng = longitude
                cachedCity = city
                cachedAtMs = now
            }
            city ?: cachedCity
        } catch (e: Exception) {
            Log.w(TAG, "fetchCityName failed: ${e.message}")
            cachedCity
        } finally {
            connection.disconnect()
        }
    }
}
