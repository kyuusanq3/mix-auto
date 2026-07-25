package com.kyuusanq3.mixauto.data.map

import android.util.Log
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.maplibre.android.geometry.LatLng

private const val TAG = "MapLibreEngineImpl"

/** Stateless Photon geocoder client for destination search and nearby POI refresh. */
internal object PhotonSearchClient {
    suspend fun fetchPhoton(
        query: String,
        currentLat: Double,
        currentLng: Double,
    ): List<SearchResultPlace> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL(
            "https://photon.komoot.io/api/" +
                "?q=$encoded&lat=$currentLat&lon=$currentLng&limit=10",
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "MixAutoCarLauncher/1.0")
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        return try {
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "Photon HTTP error: ${connection.responseCode}")
                emptyList()
            } else {
                val body = connection.inputStream.bufferedReader().readText()
                parsePhotonResponse(body, currentLat, currentLng)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Photon search failed: ${e.message}")
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    suspend fun fetchPhotonNearby(
        center: LatLng,
        bounds: GeoBounds,
        onQueryCenterRecorded: (LatLng) -> Unit,
    ): List<SearchResultPlace> {
        val encodedQuery = URLEncoder.encode("+", "UTF-8")
        val url = URL(
            "https://photon.komoot.io/api/" +
                "?q=$encodedQuery&lat=${center.latitude}&lon=${center.longitude}&limit=50",
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "MixAutoCarLauncher/1.0")
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        return try {
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "Photon nearby HTTP error: ${connection.responseCode}")
                emptyList()
            } else {
                val body = connection.inputStream.bufferedReader().readText()
                onQueryCenterRecorded(center)
                filterPlacesToBounds(
                    places = parsePhotonResponse(body, center.latitude, center.longitude),
                    bounds = bounds,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Photon nearby fetch failed: ${e.message}")
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

}
