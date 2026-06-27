package com.kyuusanq3.mixauto.data.weather

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class WeatherSnapshot(
    val temperatureC: Int,
    val symbol: String,
)

object OpenMeteoClient {
    private const val TAG = "OpenMeteoClient"
    private const val USER_AGENT = "MixAutoCarLauncher/1.0"
    private const val CACHE_TTL_MS = 30 * 60 * 1000L
    private const val COORD_CACHE_DELTA = 0.05

    @Volatile
    private var cachedLat: Double? = null

    @Volatile
    private var cachedLng: Double? = null

    @Volatile
    private var cachedSnapshot: WeatherSnapshot? = null

    @Volatile
    private var cachedAtMs: Long = 0L

    fun fetchCurrentWeather(latitude: Double, longitude: Double): WeatherSnapshot? {
        val now = System.currentTimeMillis()
        val cached = cachedSnapshot
        val cacheLat = cachedLat
        val cacheLng = cachedLng
        if (
            cached != null &&
            cacheLat != null &&
            cacheLng != null &&
            now - cachedAtMs < CACHE_TTL_MS &&
            abs(cacheLat - latitude) < COORD_CACHE_DELTA &&
            abs(cacheLng - longitude) < COORD_CACHE_DELTA
        ) {
            return cached
        }

        val latParam = String.format(Locale.US, "%.4f", latitude)
        val lngParam = String.format(Locale.US, "%.4f", longitude)
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?" +
                "latitude=$latParam&longitude=$lngParam&current=temperature_2m,weather_code",
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000

        return try {
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "fetchCurrentWeather HTTP ${connection.responseCode}")
                return cached
            }
            val body = connection.inputStream.bufferedReader().readText()
            val current = JSONObject(body).getJSONObject("current")
            val temperature = current.getDouble("temperature_2m").roundToInt()
            val weatherCode = current.getInt("weather_code")
            val snapshot = WeatherSnapshot(
                temperatureC = temperature,
                symbol = wmoSymbol(weatherCode),
            )
            cachedLat = latitude
            cachedLng = longitude
            cachedSnapshot = snapshot
            cachedAtMs = now
            snapshot
        } catch (e: Exception) {
            Log.w(TAG, "fetchCurrentWeather failed: ${e.message}")
            cached
        } finally {
            connection.disconnect()
        }
    }

    private fun wmoSymbol(code: Int): String = when (code) {
        0 -> "☀"
        1, 2, 3 -> "⛅"
        45, 48 -> "🌫"
        51, 53, 55, 56, 57 -> "🌦"
        61, 63, 65, 66, 67, 80, 81, 82 -> "🌧"
        71, 73, 75, 77, 85, 86 -> "🌨"
        95, 96, 99 -> "⛈"
        else -> "☁"
    }
}
