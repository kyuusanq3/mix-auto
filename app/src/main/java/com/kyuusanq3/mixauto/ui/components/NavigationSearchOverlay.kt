package com.kyuusanq3.mixauto.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import com.kyuusanq3.mixauto.ui.settings.LauncherViewModel
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

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
    val isSearching = searchState.isSearching
    val isLoadingRemote = searchState.isLoadingRemote
    val listState = rememberLazyListState()

    val voiceSearch = rememberVoiceSearch(context, launcherViewModel)
    val tryStartVoiceSearch = rememberUpdatedState(voiceSearch.tryStartVoiceSearch)

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
                state.copy(
                    results = emptyList(),
                    hasSearched = false,
                    isSearching = false,
                    isLoadingRemote = false,
                )
            }
            return@LaunchedEffect
        }
        val origin = snapshotOrigin ?: return@LaunchedEffect
        launcherViewModel.updateDestinationSearch { state ->
            state.copy(
                hasSearched = true,
                isSearching = true,
                isLoadingRemote = true,
            )
        }
        delay(300)
        try {
            val fetched = engine.searchDestination(
                query = query,
                currentLat = origin.first,
                currentLng = origin.second,
                limitDistance = limitSearchDistance,
                onLocalResults = { local ->
                    launcherViewModel.updateDestinationSearch { state ->
                        state.copy(results = local, isSearching = false)
                    }
                },
            )
            launcherViewModel.updateDestinationSearch { state ->
                state.copy(results = fetched)
            }
        } finally {
            if (coroutineContext.isActive) {
                launcherViewModel.updateDestinationSearch { state ->
                    state.copy(isSearching = false, isLoadingRemote = false)
                }
            }
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
                    NavigationSearchHeaderActions(
                        savedFilterActive = savedFilterActive,
                        onOpenAddFromLink = onOpenAddFromLink,
                        onToggleSavedFilter = {
                            launcherViewModel.updateDestinationSearch { state ->
                                state.copy(savedFilterActive = !state.savedFilterActive)
                            }
                        },
                    )
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
                trailingIcon = if (voiceSearch.speechAvailable) {
                    {
                        IconButton(onClick = { tryStartVoiceSearch.value() }) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = if (voiceSearch.isListening) {
                                    "Listening for destination"
                                } else {
                                    "Voice search"
                                },
                                tint = if (voiceSearch.isListening) {
                                    ElectricCyan.copy(alpha = voiceSearch.micPulseAlpha)
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

            if (voiceSearch.isListening) {
                CarLabelText(
                    text = "Listening…",
                    style = MaterialTheme.typography.labelLarge.copy(color = ElectricCyan),
                )
            }

            if (!savedFilterActive && (isSearching || isLoadingRemote)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when {
                savedFilterActive -> {
                    SavedPlacesResultsSection(
                        query = query,
                        filteredSaved = filteredSaved,
                        displayedSaved = displayedSaved,
                        listState = listState,
                        isPlaceSaved = isPlaceSaved,
                        onPreviewPlace = previewPlace,
                        onToggleSavedPlace = onToggleSavedPlace,
                        listModifier = Modifier.weight(1f),
                    )
                }
                query.length < 2 -> {
                    DestinationSuggestionsSection(
                        displayedRecents = displayedRecents,
                        displayedSuggestionsNearby = displayedSuggestionsNearby,
                        snapshotOriginReliable = snapshotOriginReliable,
                        hasOfflinePlacesDatabase = engine.hasOfflinePlacesDatabase(),
                        listState = listState,
                        isPlaceSaved = isPlaceSaved,
                        onPreviewPlace = previewPlace,
                        onToggleSavedPlace = onToggleSavedPlace,
                        onOpenMapData = onOpenMapData,
                        listModifier = Modifier.weight(1f),
                    )
                }
                hasSearched && !isSearching && !isLoadingRemote && results.isEmpty() -> {
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
                    TypedSearchResultsList(
                        results = results,
                        listState = listState,
                        isPlaceSaved = isPlaceSaved,
                        onPreviewPlace = previewPlace,
                        onToggleSavedPlace = onToggleSavedPlace,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
