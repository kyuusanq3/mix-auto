package com.kyuusanq3.mixauto.ui.status

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kyuusanq3.mixauto.data.location.NominatimReverseGeocodeClient
import com.kyuusanq3.mixauto.data.map.TomTomTrafficClient
import com.kyuusanq3.mixauto.data.map.TrafficHeadline
import com.kyuusanq3.mixauto.data.weather.OpenMeteoClient
import com.kyuusanq3.mixauto.data.weather.WeatherSnapshot
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class WeatherUiState(
    val isLoading: Boolean = false,
    val cityName: String? = null,
    val snapshot: WeatherSnapshot? = null,
)

data class TrafficUiState(
    val isLoading: Boolean = false,
    val headlines: List<TrafficHeadline> = emptyList(),
    /** True when the last fetch returned a definitive API response (including empty). */
    val fetchSucceeded: Boolean = false,
)

class StatusBarViewModel(application: Application) : AndroidViewModel(application) {
    var weatherState by mutableStateOf(WeatherUiState())
        private set

    var trafficState by mutableStateOf(TrafficUiState())
        private set

    private var lastRequestedLat: Double? = null
    private var lastRequestedLng: Double? = null
    private var lastWeatherRequestMs = 0L
    private var weatherJob: Job? = null

    private var lastTrafficLat: Double? = null
    private var lastTrafficLng: Double? = null
    private var lastTrafficApiKey: String? = null

    fun refreshWeather(latitude: Double, longitude: Double) {
        val prevLat = lastRequestedLat
        val prevLng = lastRequestedLng
        val now = System.currentTimeMillis()
        val nearLastRequest = prevLat != null &&
            prevLng != null &&
            abs(prevLat - latitude) < WEATHER_COORD_SKIP_DELTA &&
            abs(prevLng - longitude) < WEATHER_COORD_SKIP_DELTA
        val inFlight = weatherJob?.isActive == true
        val minIntervalMs = if (weatherState.snapshot != null) WEATHER_REFRESH_MS else WEATHER_RETRY_MS
        val recent = now - lastWeatherRequestMs < minIntervalMs
        if (nearLastRequest && (inFlight || recent)) {
            return
        }
        lastRequestedLat = latitude
        lastRequestedLng = longitude
        lastWeatherRequestMs = now
        weatherState = weatherState.copy(isLoading = weatherState.snapshot == null)
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            val weatherDeferred = async(Dispatchers.IO) {
                OpenMeteoClient.fetchCurrentWeather(latitude, longitude)
            }
            val cityDeferred = async(Dispatchers.IO) {
                NominatimReverseGeocodeClient.fetchCityName(latitude, longitude)
            }
            weatherState = WeatherUiState(
                isLoading = false,
                cityName = cityDeferred.await(),
                snapshot = weatherDeferred.await(),
            )
        }
    }

    fun refreshTraffic(latitude: Double, longitude: Double, apiKey: String) {
        val trimmedKey = apiKey.trim()
        if (
            lastTrafficLat != null &&
            lastTrafficLng != null &&
            abs(lastTrafficLat!! - latitude) < TRAFFIC_COORD_SKIP_DELTA &&
            abs(lastTrafficLng!! - longitude) < TRAFFIC_COORD_SKIP_DELTA &&
            lastTrafficApiKey == trimmedKey &&
            trafficState.fetchSucceeded &&
            !trafficState.isLoading
        ) {
            return
        }
        lastTrafficLat = latitude
        lastTrafficLng = longitude
        lastTrafficApiKey = trimmedKey
        trafficState = trafficState.copy(isLoading = trafficState.headlines.isEmpty())
        viewModelScope.launch {
            val headlines = async(Dispatchers.IO) {
                TomTomTrafficClient.fetchNearbyIncidents(latitude, longitude, trimmedKey)
            }.await()
            trafficState = if (headlines != null) {
                TrafficUiState(
                    isLoading = false,
                    headlines = headlines,
                    fetchSucceeded = true,
                )
            } else {
                trafficState.copy(isLoading = false)
            }
        }
    }

    fun clearWeather() {
        weatherJob?.cancel()
        weatherJob = null
        lastRequestedLat = null
        lastRequestedLng = null
        lastWeatherRequestMs = 0L
        weatherState = WeatherUiState()
    }

    fun clearTraffic() {
        lastTrafficLat = null
        lastTrafficLng = null
        lastTrafficApiKey = null
        trafficState = TrafficUiState()
    }

    companion object {
        /** Skip refetch until the puck moves roughly this far (degrees). */
        private const val TRAFFIC_COORD_SKIP_DELTA = 0.015

        /** Skip weather refetch until the puck moves roughly this far (degrees, ~2 km). */
        private const val WEATHER_COORD_SKIP_DELTA = 0.02

        /** Refetch weather at the same spot after this long. */
        private const val WEATHER_REFRESH_MS = 15 * 60 * 1000L

        /** Retry interval at the same spot while no weather snapshot has loaded yet. */
        private const val WEATHER_RETRY_MS = 60 * 1000L
    }
}
