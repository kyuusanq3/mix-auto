package com.kyuusanq3.mixauto.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyuusanq3.mixauto.data.map.TrafficFlowLevel
import com.kyuusanq3.mixauto.data.weather.WeatherSnapshot
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.ui.status.StatusBarViewModel
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.TrafficFlowFree
import com.kyuusanq3.mixauto.ui.theme.TrafficFlowHeavy
import com.kyuusanq3.mixauto.ui.theme.TrafficFlowLight
import com.kyuusanq3.mixauto.ui.theme.TrafficFlowModerate
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
    val trafficState = statusBarViewModel.trafficState
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
    // Bucket coords so GPS jitter does not restart the traffic poll loop every fix.
    val trafficBucketLat = lat?.let { kotlin.math.round(it * 50.0) / 50.0 }
    val trafficBucketLng = lng?.let { kotlin.math.round(it * 50.0) / 50.0 }
    LaunchedEffect(lat, lng) {
        if (lat != null && lng != null) {
            statusBarViewModel.refreshWeather(lat, lng)
        } else {
            statusBarViewModel.clearWeather()
        }
    }

    LaunchedEffect(trafficBucketLat, trafficBucketLng, showTraffic, tomTomApiKey) {
        if (trafficBucketLat != null && trafficBucketLng != null && showTraffic && tomTomApiKey.isNotBlank()) {
            while (true) {
                statusBarViewModel.refreshTraffic(trafficBucketLat, trafficBucketLng, tomTomApiKey)
                delay(TRAFFIC_REFRESH_MS)
            }
        } else {
            statusBarViewModel.clearTraffic()
        }
    }

    val headlines = trafficState.headlines
    var headlineIndex by remember(headlines) { mutableIntStateOf(0) }
    LaunchedEffect(headlines) {
        headlineIndex = 0
        if (headlines.size <= 1) return@LaunchedEffect
        while (true) {
            delay(TRAFFIC_CYCLE_MS)
            headlineIndex = (headlineIndex + 1) % headlines.size
        }
    }

    val locale = Locale.getDefault()
    val timeText = remember(now, locale) {
        now.format(DateTimeFormatter.ofPattern("h:mm a", locale))
    }
    val dateText = remember(now, locale) {
        now.format(DateTimeFormatter.ofPattern("EEE MMM d", locale))
    }
    val activeHeadline = headlines.getOrNull(headlineIndex)
    val trafficDisplay = when {
        !showTraffic -> TrafficReelDisplay("Traffic off", null)
        tomTomApiKey.isBlank() -> TrafficReelDisplay("Traffic —", null)
        lat == null || lng == null -> TrafficReelDisplay("Waiting for GPS…", null)
        trafficState.isLoading -> TrafficReelDisplay("Checking traffic…", null)
        activeHeadline != null -> TrafficReelDisplay(activeHeadline.text, activeHeadline.level)
        trafficState.fetchSucceeded -> TrafficReelDisplay("No major traffic detected", TrafficFlowLevel.CLEAR)
        else -> TrafficReelDisplay("Traffic on", null)
    }
    val stripTextStyle = MaterialTheme.typography.headlineSmall.copy(
        fontSize = 16.sp,
        lineHeight = 20.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    val weatherIconStyle = stripTextStyle.copy(fontSize = 26.sp, lineHeight = 28.sp)
    val weatherTempStyle = MaterialTheme.typography.headlineMedium.copy(
        fontSize = 18.sp,
        lineHeight = 22.sp,
        color = ElectricCyan,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    val cityStyle = stripTextStyle.copy(color = OnDark.copy(alpha = 0.88f))

    Surface(
        color = OledBlack,
        contentColor = OnDark,
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = CarDimensions.StatusStripPaddingTop,
                    start = CarDimensions.StatusStripPaddingHorizontal,
                    end = CarDimensions.StatusStripPaddingHorizontal,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RectangleShape),
            ) {
                Crossfade(
                    targetState = trafficDisplay,
                    label = "trafficHeadline",
                    modifier = Modifier.fillMaxWidth(),
                ) { display ->
                    TrafficReelMarqueeText(
                        text = display.text,
                        style = stripTextStyle.copy(color = trafficReelColor(display.level)),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(modifier = Modifier.width(CarDimensions.StatusStripTrafficReelGap))
            Text(
                text = "$timeText · $dateText",
                style = stripTextStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(CarDimensions.StatusStripTimeWeatherGap))
            StatusBarWeatherCluster(
                cityName = weatherState.cityName,
                isLoading = weatherState.isLoading,
                snapshot = weatherState.snapshot,
                cityStyle = cityStyle,
                weatherIconStyle = weatherIconStyle,
                weatherTempStyle = weatherTempStyle,
                modifier = Modifier.wrapContentWidth(),
            )
        }
    }
}

private data class TrafficReelDisplay(
    val text: String,
    val level: TrafficFlowLevel?,
)

private fun trafficReelColor(level: TrafficFlowLevel?): Color = when (level) {
    TrafficFlowLevel.CLEAR -> TrafficFlowFree
    TrafficFlowLevel.LIGHT -> TrafficFlowLight
    TrafficFlowLevel.MODERATE -> TrafficFlowModerate
    TrafficFlowLevel.HEAVY -> TrafficFlowHeavy
    null -> OnDark.copy(alpha = 0.55f)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrafficReelMarqueeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = style,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.basicMarquee(
            iterations = Int.MAX_VALUE,
            initialDelayMillis = 1_500,
            delayMillis = 2_000,
        ),
    )
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
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!cityName.isNullOrBlank()) {
            Text(
                text = cityName,
                style = cityStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 1.dp),
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
                    modifier = Modifier.padding(start = 1.dp),
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

private const val TRAFFIC_REFRESH_MS = 3 * 60 * 1000L
private const val TRAFFIC_CYCLE_MS = 6_000L
