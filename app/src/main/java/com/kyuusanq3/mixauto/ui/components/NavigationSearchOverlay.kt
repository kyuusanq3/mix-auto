package com.kyuusanq3.mixauto.ui.components

import android.location.Location
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import com.kyuusanq3.mixauto.ui.settings.LauncherPreferences
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarHeadlineText
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val DEDUP_THRESHOLD_M = 50f

private fun Float.formatDistance(): String {
    return if (this < 1000f) {
        "${roundToInt()} m"
    } else {
        val km = this / 1000f
        val rounded = (km * 10f).roundToInt() / 10f
        if (rounded == rounded.toLong().toFloat()) {
            "${rounded.toLong()} km"
        } else {
            "$rounded km"
        }
    }
}

private fun SearchResultPlace.withDistanceFrom(lat: Double, lng: Double): SearchResultPlace {
    val distanceResults = FloatArray(1)
    Location.distanceBetween(lat, lng, latitude, longitude, distanceResults)
    return copy(distanceInMeters = distanceResults[0])
}

private fun isWithinDedupThreshold(a: SearchResultPlace, b: SearchResultPlace): Boolean {
    val distanceResults = FloatArray(1)
    Location.distanceBetween(
        a.latitude,
        a.longitude,
        b.latitude,
        b.longitude,
        distanceResults,
    )
    return distanceResults[0] < DEDUP_THRESHOLD_M
}

@Composable
fun NavigationSearchOverlay(
    engine: CarMapEngine,
    limitSearchDistance: Boolean,
    recentDestinations: List<SearchResultPlace>,
    onDestinationSelected: (SearchResultPlace) -> Unit,
    onDismiss: () -> Unit,
) {
    val uiState by engine.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResultPlace>>(emptyList()) }
    var nearbyPois by remember { mutableStateOf<List<SearchResultPlace>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingRemote by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }

    val currentLat = uiState.currentLat ?: 0.0
    val currentLng = uiState.currentLng ?: 0.0

    LaunchedEffect(query, currentLat, currentLng, limitSearchDistance) {
        if (query.length < 2) {
            results = emptyList()
            hasSearched = false
            isLoading = false
            isLoadingRemote = false
            return@LaunchedEffect
        }
        delay(300)
        results = emptyList()
        isLoading = true
        isLoadingRemote = true
        hasSearched = true
        try {
            results = engine.searchDestination(
                query = query,
                currentLat = currentLat,
                currentLng = currentLng,
                limitDistance = limitSearchDistance,
                onLocalResults = { local ->
                    results = local
                    isLoading = false
                },
            )
        } finally {
            isLoading = false
            isLoadingRemote = false
        }
    }

    LaunchedEffect(query, currentLat, currentLng, recentDestinations) {
        if (query.length >= 2) {
            nearbyPois = emptyList()
            return@LaunchedEffect
        }
        val remaining = LauncherPreferences.MAX_RECENT_DESTINATIONS - recentDestinations.size
        nearbyPois = if (remaining > 0) {
            engine.getNearbyPois(currentLat, currentLng, remaining)
                .filterNot { nearby ->
                    recentDestinations.any { recent -> isWithinDedupThreshold(recent, nearby) }
                }
                .take(remaining)
        } else {
            emptyList()
        }
    }

    val displayedRecents = remember(recentDestinations, currentLat, currentLng) {
        recentDestinations.map { it.withDistanceFrom(currentLat, currentLng) }
    }

    val suggestedDestinations = remember(displayedRecents, nearbyPois) {
        displayedRecents.map { it to "Recent" } + nearbyPois.map { it to "Nearby" }
    }

    val selectDestination: (SearchResultPlace) -> Unit = { place ->
        onDestinationSelected(place)
        engine.navigateToCoordinates(place.latitude, place.longitude)
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = OledBlack,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(CarDimensions.PaneGap * 2),
                verticalArrangement = Arrangement.spacedBy(CarDimensions.DockItemSpacing),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CarHeadlineText(
                        text = "Navigate To",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f),
                    )
                    OverlayCloseButton(
                        onClick = onDismiss,
                        contentDescription = "Close search",
                    )
                }

                CarBodyText(
                    text = "Point A: your current location",
                    style = MaterialTheme.typography.bodyMedium,
                )

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CarDimensions.PrimaryTapTarget + CarDimensions.PaneGap),
                    label = {
                        CarLabelText(
                            text = "Destination (Point B)",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    placeholder = {
                        CarBodyText(
                            text = "Search address or place",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = OledBlack,
                        unfocusedContainerColor = OledBlack,
                    ),
                )

                if (isLoading || isLoadingRemote) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                when {
                    query.length < 2 -> {
                        if (suggestedDestinations.isEmpty()) {
                            CarBodyText(
                                text = "Type at least 2 characters to search",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap / 2),
                            ) {
                                items(
                                    suggestedDestinations,
                                    key = { (place, badge) ->
                                        "$badge-${place.latitude},${place.longitude},${place.name}"
                                    },
                                ) { (place, badge) ->
                                    SearchResultRow(
                                        place = place,
                                        badge = badge,
                                        onClick = { selectDestination(place) },
                                    )
                                }
                            }
                        }
                    }
                    hasSearched && !isLoading && !isLoadingRemote && results.isEmpty() -> {
                        CarBodyText(
                            text = "No results found",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap / 2),
                        ) {
                            items(
                                results,
                                key = { "${it.latitude},${it.longitude},${it.name}" },
                            ) { place ->
                                SearchResultRow(
                                    place = place,
                                    onClick = { selectDestination(place) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    place: SearchResultPlace,
    onClick: () -> Unit,
    badge: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CarDimensions.MinTapTarget)
            .clickable(onClick = onClick)
            .padding(horizontal = CarDimensions.PaneGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap / 4),
        ) {
            CarBodyText(
                text = place.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            if (place.subTitle.isNotBlank()) {
                CarLabelText(
                    text = place.subTitle,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (badge != null) {
                CarLabelText(
                    text = badge,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = ElectricCyan.copy(alpha = 0.75f),
                    ),
                )
            }
        }
        CarLabelText(
            text = place.distanceInMeters.formatDistance(),
            style = MaterialTheme.typography.labelMedium.copy(
                color = ElectricCyan,
            ),
        )
    }
}
