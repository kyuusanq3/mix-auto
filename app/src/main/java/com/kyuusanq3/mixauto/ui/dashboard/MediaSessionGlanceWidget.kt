package com.kyuusanq3.mixauto.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyuusanq3.mixauto.data.map.TrafficFlowLevel
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.ui.status.StatusBarViewModel
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.DeepCharcoal
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OnDark
import com.kyuusanq3.mixauto.ui.theme.TrafficFlowFree
import com.kyuusanq3.mixauto.ui.theme.TrafficFlowHeavy
import com.kyuusanq3.mixauto.ui.theme.TrafficFlowLight
import com.kyuusanq3.mixauto.ui.theme.TrafficFlowModerate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/** Glance strip stacked above MediaPlayerPane; shares StatusBarViewModel with DashboardStatusBar. */
@Composable
fun MediaSessionGlanceWidget(
    mapEngine: CarMapEngine,
    showTraffic: Boolean,
    tomTomApiKey: String,
    onToggleMinimized: (() -> Unit)? = null,
    chevronAlignment: Alignment = Alignment.TopCenter,
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

    val locale = Locale.getDefault()
    val timeText = remember(now, locale) {
        now.format(DateTimeFormatter.ofPattern("h:mm", locale))
    }
    val amPmText = remember(now, locale) {
        now.format(DateTimeFormatter.ofPattern("a", locale))
    }
    val dateText = remember(now, locale) {
        now.format(DateTimeFormatter.ofPattern("MMM d yyyy", locale))
    }
    val trafficLevel = overallTrafficLevel(
        showTraffic = showTraffic,
        tomTomApiKey = tomTomApiKey,
        hasGps = lat != null && lng != null,
        isLoading = trafficState.isLoading,
        fetchSucceeded = trafficState.fetchSucceeded,
        headlines = trafficState.headlines.map { it.level },
    )
    val trafficTint = trafficIconColor(trafficLevel)

    val timeStyle = MaterialTheme.typography.displayLarge.copy(
        fontSize = 56.sp,
        lineHeight = 60.sp,
        fontWeight = FontWeight.Bold,
        color = OnDark,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    val amPmStyle = MaterialTheme.typography.headlineMedium.copy(
        fontSize = 22.sp,
        lineHeight = 26.sp,
        color = OnDark.copy(alpha = 0.72f),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    val dateStyle = MaterialTheme.typography.headlineSmall.copy(
        fontSize = 16.sp,
        lineHeight = 20.sp,
        color = OnDark.copy(alpha = 0.72f),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    val weatherIconStyle = MaterialTheme.typography.headlineMedium.copy(
        fontSize = 28.sp,
        lineHeight = 32.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    val weatherTempStyle = MaterialTheme.typography.headlineMedium.copy(
        fontSize = 24.sp,
        lineHeight = 28.sp,
        color = ElectricCyan,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepCharcoal)
            .padding(
                horizontal = CarDimensions.StatusStripPaddingHorizontal,
                vertical = 12.dp,
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // no glance chevron
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = timeText,
                style = timeStyle,
                maxLines = 1,
            )
            Text(
                text = amPmText,
                style = amPmStyle,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
            )
        }
        Text(
            text = dateText,
            style = dateStyle,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when {
                    weatherState.isLoading -> {
                        Text(text = "...", style = weatherTempStyle, maxLines = 1)
                    }
                    weatherState.snapshot != null -> {
                        val snap = weatherState.snapshot!!
                        Text(text = snap.symbol, style = weatherIconStyle, maxLines = 1)
                        Text(
                            text = "${snap.temperatureC}\u00B0C",
                            style = weatherTempStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    else -> {
                        Text(text = "--", style = weatherTempStyle, maxLines = 1)
                    }
                }
            }
            Icon(
                imageVector = Icons.Filled.DirectionsCar,
                contentDescription = "Traffic",
                tint = trafficTint,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

private fun overallTrafficLevel(
    showTraffic: Boolean,
    tomTomApiKey: String,
    hasGps: Boolean,
    isLoading: Boolean,
    fetchSucceeded: Boolean,
    headlines: List<TrafficFlowLevel>,
): TrafficFlowLevel? {
    if (!showTraffic || tomTomApiKey.isBlank() || !hasGps) return null
    if (isLoading && headlines.isEmpty()) return null
    if (headlines.isEmpty()) {
        return if (fetchSucceeded) TrafficFlowLevel.CLEAR else null
    }
    return headlines.maxBy { levelRank(it) }
}

private fun levelRank(level: TrafficFlowLevel): Int = when (level) {
    TrafficFlowLevel.HEAVY -> 3
    TrafficFlowLevel.MODERATE -> 2
    TrafficFlowLevel.LIGHT -> 1
    TrafficFlowLevel.CLEAR -> 0
}

private fun trafficIconColor(level: TrafficFlowLevel?): Color = when (level) {
    TrafficFlowLevel.CLEAR -> TrafficFlowFree
    TrafficFlowLevel.LIGHT -> TrafficFlowLight
    TrafficFlowLevel.MODERATE -> TrafficFlowModerate
    TrafficFlowLevel.HEAVY -> TrafficFlowHeavy
    null -> OnDark.copy(alpha = 0.45f)
}

private const val TRAFFIC_REFRESH_MS = 3 * 60 * 1000L
