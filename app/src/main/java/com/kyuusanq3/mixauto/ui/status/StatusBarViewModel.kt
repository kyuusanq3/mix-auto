package com.kyuusanq3.mixauto.ui.status

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kyuusanq3.mixauto.data.location.NominatimReverseGeocodeClient
import com.kyuusanq3.mixauto.data.weather.OpenMeteoClient
import com.kyuusanq3.mixauto.data.weather.WeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class WeatherUiState(
    val isLoading: Boolean = false,
    val cityName: String? = null,
    val snapshot: WeatherSnapshot? = null,
)

class StatusBarViewModel(application: Application) : AndroidViewModel(application) {
    var weatherState by mutableStateOf(WeatherUiState())
        private set

    private var lastRequestedLat: Double? = null
    private var lastRequestedLng: Double? = null

    fun refreshWeather(latitude: Double, longitude: Double) {
        if (
            latitude == lastRequestedLat &&
            longitude == lastRequestedLng &&
            weatherState.snapshot != null &&
            weatherState.cityName != null
        ) {
            return
        }
        lastRequestedLat = latitude
        lastRequestedLng = longitude
        weatherState = weatherState.copy(isLoading = weatherState.snapshot == null)
        viewModelScope.launch {
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

    fun clearWeather() {
        lastRequestedLat = null
        lastRequestedLng = null
        weatherState = WeatherUiState()
    }
}
