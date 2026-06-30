package com.kyuusanq3.mixauto.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val DEDUP_THRESHOLD_M = 50f
private const val VOICE_SEARCH_TAG = "NavigationSearchOverlay"
private const val NEARBY_POI_SUGGESTION_LIMIT = 20

private fun speechErrorLabel(error: Int): String = when (error) {
    SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
    SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
    SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
    SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
    SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
    else -> "ERROR_UNKNOWN($error)"
}

private class SpeechSearchAudioFocus(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null

    fun request() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            )
        }
    }

    fun abandon() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }
}

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

private fun filterSavedPlaces(
    places: List<SearchResultPlace>,
    query: String,
): List<SearchResultPlace> {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.length < 2) return places
    return places.filter { place ->
        place.name.lowercase().contains(normalizedQuery) ||
            place.subTitle.lowercase().contains(normalizedQuery)
    }
}

@Composable
fun NavigationSearchContent(
    engine: CarMapEngine,
    limitSearchDistance: Boolean,
    recentDestinations: List<SearchResultPlace>,
    savedPlaces: List<SearchResultPlace>,
    onToggleSavedPlace: (SearchResultPlace) -> Unit,
    onPreviewPlace: (SearchResultPlace) -> Unit,
    onDismiss: () -> Unit,
    onOpenMapData: () -> Unit = {},
    onOpenAddFromLink: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val launcherViewModel: LauncherViewModel = viewModel()
    val searchState = launcherViewModel.destinationSearchState
    val query = searchState.query
    val snapshotOrigin = searchState.snapshotOriginLat?.let { lat ->
        searchState.snapshotOriginLng?.let { lng -> lat to lng }
    }
    val snapshotOriginReliable = searchState.snapshotOriginReliable
    val results = searchState.results
    val nearbyPois = searchState.nearbyPois
    val hasSearched = searchState.hasSearched
    val savedFilterActive = searchState.savedFilterActive
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingRemote by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var pendingVoiceStart by remember { mutableStateOf(false) }
    var pendingVoiceRestart by remember { mutableStateOf(false) }
    var recognizerSessionActive by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val speechAudioFocus = remember(context) {
        SpeechSearchAudioFocus(context.applicationContext)
    }

    val speechAvailable = remember(context) {
        SpeechRecognizer.isRecognitionAvailable(context.applicationContext)
    }
    val speechRecognizer = remember(context, speechAvailable) {
        if (speechAvailable) {
            SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        } else {
            null
        }
    }

    val recognitionIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }

    val requestVoiceListeningRef = rememberUpdatedState<(SpeechRecognizer) -> Unit> { recognizer ->
        if (recognizerSessionActive) {
            pendingVoiceRestart = true
            isListening = true
            recognizer.cancel()
        } else {
            speechAudioFocus.request()
            isListening = true
            recognizerSessionActive = true
            recognizer.startListening(recognitionIntent)
        }
    }

    val stopVoiceListeningRef = rememberUpdatedState {
        pendingVoiceRestart = false
        recognizerSessionActive = false
        isListening = false
        speechAudioFocus.abandon()
    }

    DisposableEffect(speechRecognizer) {
        val recognizer = speechRecognizer
        if (recognizer != null) {
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    recognizerSessionActive = true
                }

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    if (!pendingVoiceRestart) {
                        stopVoiceListeningRef.value()
                    }
                }

                override fun onError(error: Int) {
                    Log.w(VOICE_SEARCH_TAG, "SpeechRecognizer error: $error (${speechErrorLabel(error)})")
                    if (pendingVoiceRestart) {
                        pendingVoiceRestart = false
                        isListening = true
                        recognizer.startListening(recognitionIntent)
                    } else {
                        stopVoiceListeningRef.value()
                    }
                }

                override fun onResults(resultsBundle: Bundle) {
                    if (pendingVoiceRestart) return
                    resultsBundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.let { spoken ->
                            launcherViewModel.updateDestinationSearch { state ->
                                state.copy(query = spoken)
                            }
                        }
                    stopVoiceListeningRef.value()
                }

                override fun onPartialResults(partialResults: Bundle) = Unit

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
        onDispose {
            pendingVoiceRestart = false
            recognizerSessionActive = false
            speechAudioFocus.abandon()
            speechRecognizer?.destroy()
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingVoiceStart) {
            pendingVoiceStart = false
            speechRecognizer?.let { requestVoiceListeningRef.value(it) }
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
        requestVoiceListeningRef.value(recognizer)
    }

    LaunchedEffect(Unit) {
        engine.refreshSearchOrigin()
        withContext(Dispatchers.IO) {
            engine.seedSearchFromMapViewport()
        }
        if (launcherViewModel.destinationSearchState.snapshotOriginLat == null) {
            val origin = engine.resolveSearchOrigin()
            launcherViewModel.updateDestinationSearch { state ->
                state.copy(
                    snapshotOriginLat = origin.first,
                    snapshotOriginLng = origin.second,
                    snapshotOriginReliable = engine.hasReliableSearchOrigin(),
                )
            }
        }
        if (launcherViewModel.consumeStartVoiceOnSearchOpen()) {
            tryStartVoiceSearch.value()
        }
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

    val snapshotLat = snapshotOrigin?.first
    val snapshotLng = snapshotOrigin?.second

    LaunchedEffect(
        query,
        savedFilterActive,
        limitSearchDistance,
        snapshotOrigin,
    ) {
        if (savedFilterActive || query.length < 2) {
            launcherViewModel.updateDestinationSearch { state ->
                state.copy(results = emptyList(), hasSearched = false)
            }
            isLoading = false
            isLoadingRemote = false
            return@LaunchedEffect
        }
        val origin = snapshotOrigin ?: return@LaunchedEffect
        delay(300)
        launcherViewModel.updateDestinationSearch { state ->
            state.copy(results = emptyList(), hasSearched = true)
        }
        isLoading = true
        isLoadingRemote = true
        try {
            val fetched = engine.searchDestination(
                query = query,
                currentLat = origin.first,
                currentLng = origin.second,
                limitDistance = limitSearchDistance,
                onLocalResults = { local ->
                    launcherViewModel.updateDestinationSearch { state ->
                        state.copy(results = local)
                    }
                    isLoading = false
                },
            )
            launcherViewModel.updateDestinationSearch { state ->
                state.copy(results = fetched)
            }
        } finally {
            isLoading = false
            isLoadingRemote = false
        }
    }

    LaunchedEffect(
        snapshotOrigin,
        query,
        savedFilterActive,
        recentDestinations,
        savedPlaces,
    ) {
        if (savedFilterActive || query.length >= 2) {
            launcherViewModel.updateDestinationSearch { state ->
                state.copy(nearbyPois = emptyList())
            }
            return@LaunchedEffect
        }
        val origin = snapshotOrigin ?: return@LaunchedEffect
        val nearby = withContext(Dispatchers.IO) {
            engine.getNearbyPois(
                origin.first,
                origin.second,
                NEARBY_POI_SUGGESTION_LIMIT,
            )
        }.filterNot { place ->
            recentDestinations.any { recent -> isWithinDedupThreshold(recent, place) } ||
                savedPlaces.any { saved -> isWithinDedupThreshold(saved, place) }
        }
        launcherViewModel.updateDestinationSearch { state ->
            state.copy(nearbyPois = nearby)
        }
    }

    val displayedRecents = remember(recentDestinations, snapshotLat, snapshotLng) {
        if (snapshotLat == null || snapshotLng == null) {
            recentDestinations
        } else {
            recentDestinations.map { it.withDistanceFrom(snapshotLat, snapshotLng) }
        }
    }

    val displayedSaved = remember(savedPlaces, snapshotLat, snapshotLng) {
        if (snapshotLat == null || snapshotLng == null) {
            savedPlaces
        } else {
            savedPlaces.map { it.withDistanceFrom(snapshotLat, snapshotLng) }
        }
    }

    val displayedNearby = remember(nearbyPois, snapshotLat, snapshotLng) {
        if (snapshotLat == null || snapshotLng == null) {
            nearbyPois
        } else {
            nearbyPois.map { it.withDistanceFrom(snapshotLat, snapshotLng) }
        }
    }

    val displayedSuggestionsNearby = remember(displayedNearby, displayedRecents) {
        displayedNearby.filterNot { nearby ->
            displayedRecents.any { recent -> isWithinDedupThreshold(recent, nearby) }
        }
    }

    val filteredSaved = remember(displayedSaved, query) {
        filterSavedPlaces(displayedSaved, query)
    }

    val isPlaceSaved: (SearchResultPlace) -> Boolean = { place ->
        savedPlaces.any { saved -> isWithinDedupThreshold(saved, place) }
    }

    val previewPlace: (SearchResultPlace) -> Unit = onPreviewPlace

    Surface(
        modifier = modifier.fillMaxSize(),
        color = OledBlack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = CarDimensions.PaneGap * 2,
                    vertical = CarDimensions.PaneGap / 2,
                ),
            verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
        ) {
                PanelHeaderRow(
                    title = "Navigate To",
                    onClose = onDismiss,
                    closeContentDescription = "Close search",
                    compact = true,
                    trailingContent = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap / 4),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = onOpenAddFromLink,
                                modifier = Modifier.size(CarDimensions.PanelCompactHeaderTapTarget),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Add place from Google Maps link",
                                    modifier = Modifier.size(CarDimensions.PanelCompactHeaderIconSize),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(
                                onClick = {
                                    launcherViewModel.updateDestinationSearch { state ->
                                        state.copy(savedFilterActive = !state.savedFilterActive)
                                    }
                                },
                                modifier = Modifier.size(CarDimensions.PanelCompactHeaderTapTarget),
                            ) {
                                Icon(
                                    imageVector = if (savedFilterActive) {
                                        Icons.Filled.Star
                                    } else {
                                        Icons.Outlined.Star
                                    },
                                    contentDescription = if (savedFilterActive) {
                                        "Show all suggestions"
                                    } else {
                                        "Show saved places only"
                                    },
                                    modifier = Modifier.size(CarDimensions.PanelCompactHeaderIconSize),
                                    tint = if (savedFilterActive) {
                                        Color(0xFFFFD700)
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                )
                            }
                        }
                    },
                )

                OutlinedTextField(
                    value = query,
                    onValueChange = { text ->
                        launcherViewModel.updateDestinationSearch { state ->
                            state.copy(query = text)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        CarLabelText(
                            text = if (savedFilterActive) {
                                "Search saved places"
                            } else {
                                "Destination"
                            },
                            style = MaterialTheme.typography.labelMedium,
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

                if (!savedFilterActive && (isLoading || isLoadingRemote)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                when {
                    savedFilterActive -> {
                        when {
                            query.length >= 2 && filteredSaved.isEmpty() -> {
                                CarBodyText(
                                    text = "No saved places match your search",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            query.length < 2 && displayedSaved.isEmpty() -> {
                                CarBodyText(
                                    text = "No saved places — star a POI on the map",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            else -> {
                                val savedPlacesToShow = if (query.length >= 2) {
                                    filteredSaved
                                } else {
                                    displayedSaved
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .carLazyScrollbar(listState),
                                ) {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap / 2),
                                    ) {
                                        items(
                                            savedPlacesToShow,
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
                    }
                    query.length < 2 -> {
                        val suggestionsEmpty = displayedRecents.isEmpty() &&
                            displayedSuggestionsNearby.isEmpty()

                        if (suggestionsEmpty) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap / 2),
                            ) {
                                CarBodyText(
                                    text = when {
                                        !snapshotOriginReliable ->
                                            "Waiting for GPS — nearby suggestions appear once location is available"
                                        engine.hasOfflinePlacesDatabase() ->
                                            "No recent destinations — drive to build nearby suggestions from places you pass"
                                        else ->
                                            "No recent destinations — install a country pack in Map Data for offline nearby search and richer suggestions while driving"
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                if (snapshotOriginReliable && !engine.hasOfflinePlacesDatabase()) {
                                    TextButton(onClick = onOpenMapData) {
                                        CarLabelText(
                                            text = "Open Map Data",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                color = ElectricCyan,
                                            ),
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .carLazyScrollbar(listState),
                            ) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap / 2),
                                ) {
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
                                }
                            }
                        }
                    }
                    hasSearched && !isLoading && !isLoadingRemote && results.isEmpty() -> {
                        CarBodyText(
                            text = if (snapshotOriginReliable) {
                                "No results found"
                            } else {
                                "Waiting for GPS — try again in a moment"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .carLazyScrollbar(listState),
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
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
