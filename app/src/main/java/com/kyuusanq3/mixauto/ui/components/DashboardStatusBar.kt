package com.kyuusanq3.mixauto.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyuusanq3.mixauto.data.weather.WeatherSnapshot
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.ui.status.StatusBarViewModel
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import com.kyuusanq3.mixauto.ui.theme.OnDark
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun DashboardStatusBar(
    mapEngine: CarMapEngine,
    showTraffic: Boolean,
    tomTomApiKey: String,
    modifier: Modifier = Modifier,
) {
    val statusBarViewModel: StatusBarViewModel = viewModel()
    val mapUiState by mapEngine.uiState.collectAsStateWithLifecycle()
    val weatherState = statusBarViewModel.weatherState
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            val delayMs = 60_000L - (System.currentTimeMillis() % 60_000L)
            delay(delayMs.coerceAtLeast(1_000L))
        }
    }

    val lat = mapUiState.currentLat
    val lng = mapUiState.currentLng
    LaunchedEffect(lat, lng) {
        if (lat != null && lng != null) {
            statusBarViewModel.refreshWeather(lat, lng)
        } else {
            statusBarViewModel.clearWeather()
        }
    }

    val locale = Locale.getDefault()
    val timeText = remember(now, locale) {
        now.format(DateTimeFormatter.ofPattern("h:mm a", locale))
    }
    val dateText = remember(now, locale) {
        now.format(DateTimeFormatter.ofPattern("EEE MMM d", locale))
    }
    val trafficLabel = when {
        showTraffic && tomTomApiKey.isNotBlank() -> "Traffic on"
        showTraffic -> "Traffic —"
        else -> "Traffic off"
    }
    val trafficColor = when {
        showTraffic && tomTomApiKey.isNotBlank() -> ElectricCyan
        showTraffic -> OnDark.copy(alpha = 0.72f)
        else -> OnDark.copy(alpha = 0.45f)
    }
    val stripTextStyle = MaterialTheme.typography.headlineSmall
    val weatherIconStyle = stripTextStyle.copy(fontSize = 28.sp, lineHeight = 30.sp)
    val weatherTempStyle = MaterialTheme.typography.headlineMedium.copy(color = ElectricCyan)
    val cityStyle = stripTextStyle.copy(color = OnDark.copy(alpha = 0.88f))

    Surface(
        color = OledBlack,
        contentColor = OnDark,
        modifier = modifier
            .fillMaxWidth()
            .height(CarDimensions.StatusStripHeight),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = CarDimensions.PaneGap * 2),
        ) {
            Text(
                text = "$timeText · $dateText",
                style = stripTextStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = trafficLabel,
                    style = stripTextStyle.copy(color = trafficColor),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                StatusBarWeatherCluster(
                    cityName = weatherState.cityName,
                    isLoading = weatherState.isLoading,
                    snapshot = weatherState.snapshot,
                    cityStyle = cityStyle,
                    weatherIconStyle = weatherIconStyle,
                    weatherTempStyle = weatherTempStyle,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

@Composable
private fun StatusBarWeatherCluster(
    cityName: String?,
    isLoading: Boolean,
    snapshot: WeatherSnapshot?,
    cityStyle: TextStyle,
    weatherIconStyle: TextStyle,
    weatherTempStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!cityName.isNullOrBlank()) {
            Text(
                text = cityName,
                style = cityStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = CarDimensions.PaneGap),
            )
        }
        when {
            isLoading -> {
                Text(
                    text = "…",
                    style = weatherTempStyle,
                    maxLines = 1,
                )
            }
            snapshot != null -> {
                Text(
                    text = snapshot.symbol,
                    style = weatherIconStyle,
                    maxLines = 1,
                )
                Text(
                    text = "${snapshot.temperatureC}°C",
                    style = weatherTempStyle,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            else -> {
                Text(
                    text = "—",
                    style = weatherTempStyle,
                    maxLines = 1,
                )
            }
        }
    }
}
