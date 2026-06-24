package com.kyuusanq3.mixauto.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import com.kyuusanq3.mixauto.ui.settings.LauncherPreferences
import com.kyuusanq3.mixauto.ui.settings.LauncherViewModel
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

private enum class SuggestedTab {
    Suggestions,
    Saved,
}

@Composable
fun NavigationSearchContent(
    engine: CarMapEngine,
    limitSearchDistance: Boolean,
    recentDestinations: List<SearchResultPlace>,
    savedPlaces: List<SearchResultPlace>,
    onToggleSavedPlace: (SearchResultPlace) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val launcherViewModel: LauncherViewModel = viewModel()
    val uiState by engine.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResultPlace>>(emptyList()) }
    var nearbyPois by remember { mutableStateOf<List<SearchResultPlace>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingRemote by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var pendingVoiceStart by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(SuggestedTab.Suggestions) }

    val speechAvailable = remember(context) {
        SpeechRecognizer.isRecognitionAvailable(context)
    }
    val speechRecognizer = remember(context, speechAvailable) {
        if (speechAvailable) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    DisposableEffect(speechRecognizer) {
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    val startListeningRef = rememberUpdatedState<(SpeechRecognizer) -> Unit> { recognizer ->
        isListening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
            }

            override fun onResults(resultsBundle: Bundle) {
                resultsBundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.let { query = it }
                isListening = false
            }

            override fun onPartialResults(partialResults: Bundle) {
                partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.let { query = it }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        recognizer.startListening(intent)
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingVoiceStart) {
            pendingVoiceStart = false
            speechRecognizer?.let { startListeningRef.value(it) }
        } else {
            pendingVoiceStart = false
        }
    }

    val tryStartVoiceSearch = rememberUpdatedState {
        val recognizer = speechRecognizer ?: return@rememberUpdatedState
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingVoiceStart = true
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return@rememberUpdatedState
        }
        startListeningRef.value(recognizer)
    }

    LaunchedEffect(launcherViewModel) {
        launcherViewModel.voiceSearchTrigger.collect {
            tryStartVoiceSearch.value()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val pulsingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "micPulseAlpha",
    )
    val micPulseAlpha = if (isListening) pulsingAlpha else 1f

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

    LaunchedEffect(query, currentLat, currentLng, recentDestinations, savedPlaces) {
        if (query.length >= 2) {
            nearbyPois = emptyList()
            return@LaunchedEffect
        }
        nearbyPois = engine.getNearbyPois(currentLat, currentLng, LauncherPreferences.MAX_RECENT_DESTINATIONS)
            .filterNot { nearby ->
                recentDestinations.any { recent -> isWithinDedupThreshold(recent, nearby) } ||
                    savedPlaces.any { saved -> isWithinDedupThreshold(saved, nearby) }
            }
    }

    val displayedRecents = remember(recentDestinations, currentLat, currentLng) {
        recentDestinations.map { it.withDistanceFrom(currentLat, currentLng) }
    }

    val displayedSaved = remember(savedPlaces, currentLat, currentLng) {
        savedPlaces.map { it.withDistanceFrom(currentLat, currentLng) }
    }

    val displayedNearby = remember(nearbyPois, currentLat, currentLng) {
        nearbyPois.map { it.withDistanceFrom(currentLat, currentLng) }
    }

    val displayedSuggestionsNearby = remember(displayedNearby, displayedRecents) {
        displayedNearby.filterNot { nearby ->
            displayedRecents.any { recent -> isWithinDedupThreshold(recent, nearby) }
        }
    }

    val isPlaceSaved: (SearchResultPlace) -> Boolean = { place ->
        savedPlaces.any { saved -> isWithinDedupThreshold(saved, place) }
    }

    val previewPlace: (SearchResultPlace) -> Unit = { place ->
        engine.focusOnPoi(place)
        onDismiss()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
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
                    trailingIcon = if (speechAvailable) {
                        {
                            IconButton(onClick = { tryStartVoiceSearch.value() }) {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = if (isListening) {
                                        "Listening for destination"
                                    } else {
                                        "Voice search"
                                    },
                                    tint = if (isListening) {
                                        ElectricCyan.copy(alpha = micPulseAlpha)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    } else {
                        null
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = OledBlack,
                        unfocusedContainerColor = OledBlack,
                    ),
                )

                if (isListening) {
                    CarLabelText(
                        text = "Listening…",
                        style = MaterialTheme.typography.labelLarge.copy(color = ElectricCyan),
                    )
                }

                if (isLoading || isLoadingRemote) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                when {
                    query.length < 2 -> {
                        TabRow(selectedTabIndex = selectedTab.ordinal) {
                            SuggestedTab.entries.forEach { tab ->
                                Tab(
                                    selected = selectedTab == tab,
                                    onClick = { selectedTab = tab },
                                    text = {
                                        CarLabelText(
                                            text = tab.name,
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    },
                                )
                            }
                        }

                        val suggestionsEmpty = displayedRecents.isEmpty() && displayedSuggestionsNearby.isEmpty()
                        val savedEmpty = displayedSaved.isEmpty()

                        if (selectedTab == SuggestedTab.Suggestions && suggestionsEmpty) {
                            CarBodyText(
                                text = "No recent destinations — pan the map to load nearby POIs",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        } else if (selectedTab == SuggestedTab.Saved && savedEmpty) {
                            CarBodyText(
                                text = "No saved places — star a POI on the map",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap / 2),
                            ) {
                                if (selectedTab == SuggestedTab.Suggestions) {
                                    if (displayedRecents.isNotEmpty()) {
                                        item(key = "header-recent") {
                                            CarLabelText(
                                                text = "Recent",
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    color = ElectricCyan,
                                                ),
                                                modifier = Modifier.padding(
                                                    horizontal = CarDimensions.PaneGap,
                                                    vertical = CarDimensions.PaneGap / 4,
                                                ),
                                            )
                                        }
                                        items(
                                            displayedRecents,
                                            key = { "recent-${it.latitude},${it.longitude},${it.name}" },
                                        ) { place ->
                                            SearchResultRow(
                                                place = place,
                                                isStarred = isPlaceSaved(place),
                                                onClick = { previewPlace(place) },
                                                onToggleStar = { onToggleSavedPlace(place) },
                                            )
                                        }
                                    }
                                    if (displayedSuggestionsNearby.isNotEmpty()) {
                                        item(key = "header-nearby") {
                                            CarLabelText(
                                                text = "Nearby",
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    color = ElectricCyan,
                                                ),
                                                modifier = Modifier.padding(
                                                    horizontal = CarDimensions.PaneGap,
                                                    vertical = CarDimensions.PaneGap / 4,
                                                ),
                                            )
                                        }
                                        items(
                                            displayedSuggestionsNearby,
                                            key = { "nearby-${it.latitude},${it.longitude},${it.name}" },
                                        ) { place ->
                                            SearchResultRow(
                                                place = place,
                                                isStarred = isPlaceSaved(place),
                                                onClick = { previewPlace(place) },
                                                onToggleStar = { onToggleSavedPlace(place) },
                                            )
                                        }
                                    }
                                } else {
                                    items(
                                        displayedSaved,
                                        key = { "saved-${it.latitude},${it.longitude},${it.name}" },
                                    ) { place ->
                                        SearchResultRow(
                                            place = place,
                                            isStarred = isPlaceSaved(place),
                                            onClick = { previewPlace(place) },
                                            onToggleStar = { onToggleSavedPlace(place) },
                                        )
                                    }
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
                                    isStarred = isPlaceSaved(place),
                                    onClick = { previewPlace(place) },
                                    onToggleStar = { onToggleSavedPlace(place) },
                                )
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
    isStarred: Boolean = false,
    onToggleStar: (() -> Unit)? = null,
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
        if (onToggleStar != null) {
            IconButton(
                onClick = onToggleStar,
                modifier = Modifier.size(CarDimensions.MinTapTarget),
            ) {
                Icon(
                    imageVector = if (isStarred) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = if (isStarred) "Remove from saved" else "Save place",
                    tint = if (isStarred) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
