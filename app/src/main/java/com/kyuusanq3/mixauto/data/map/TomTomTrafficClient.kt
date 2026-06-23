package com.kyuusanq3.mixauto.data.map

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

sealed class TomTomKeyCheckResult {
    data class Success(val message: String) : TomTomKeyCheckResult()
    data class Failure(val message: String) : TomTomKeyCheckResult()
}

object TomTomTrafficClient {
    private const val TAG = "TomTomTrafficClient"
    private const val USER_AGENT = "MixAutoCarLauncher/1.0"

    /** Bacolod area tile — same endpoint and params as the traffic overlay layer. */
    private const val TEST_TILE_ZOOM = 12
    private const val TEST_TILE_X = 3444
    private const val TEST_TILE_Y = 1926

    fun verifyApiKey(apiKey: String): TomTomKeyCheckResult {
        val trimmed = apiKey.trim()
        if (trimmed.isBlank()) {
            return TomTomKeyCheckResult.Failure("Enter an API key first")
        }

        val url = URL(
            "https://api.tomtom.com/maps/orbis/traffic/flow/raster/tile/" +
                "$TEST_TILE_ZOOM/$TEST_TILE_X/$TEST_TILE_Y" +
                "?apiVersion=2&key=$trimmed&style=light&tileSize=256",
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000

        return try {
            val code = connection.responseCode
            val contentType = connection.contentType.orEmpty()
            Log.d(TAG, "verifyApiKey HTTP $code contentType=$contentType")

            when (code) {
                in 200..299 -> {
                    val bytes = connection.inputStream.use { it.readBytes() }
                    if (bytes.size < 100) {
                        TomTomKeyCheckResult.Failure(
                            "HTTP $code but tile was empty (${bytes.size} bytes). " +
                                "Key may lack Traffic Flow API access.",
                        )
                    } else {
                        TomTomKeyCheckResult.Success(
                            "Key valid — received traffic tile (${bytes.size} bytes)",
                        )
                    }
                }
                401 -> TomTomKeyCheckResult.Failure("Unauthorized (HTTP 401) — check your API key")
                403 -> TomTomKeyCheckResult.Failure(
                    "Forbidden (HTTP 403) — invalid key or Traffic Flow API not enabled for this key",
                )
                429 -> TomTomKeyCheckResult.Failure("Rate limited (HTTP 429) — try again in a few minutes")
                else -> {
                    val errorBody = runCatching {
                        connection.errorStream?.bufferedReader()?.readText()?.take(200)
                    }.getOrNull()
                    TomTomKeyCheckResult.Failure(
                        "HTTP $code${errorBody?.let { ": $it" } ?: ""}",
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "verifyApiKey failed: ${e.message}")
            TomTomKeyCheckResult.Failure("Network error: ${e.message ?: "unknown"}")
        } finally {
            connection.disconnect()
        }
    }
}
