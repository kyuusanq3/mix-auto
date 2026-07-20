package com.kyuusanq3.mixauto.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan

@Composable
internal fun NavigationSearchHeaderActions(
    savedFilterActive: Boolean,
    onOpenAddFromLink: () -> Unit,
    onToggleSavedFilter: () -> Unit,
) {
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
            onClick = onToggleSavedFilter,
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
}

@Composable
internal fun SavedPlacesResultsSection(
    query: String,
    filteredSaved: List<SearchResultPlace>,
    displayedSaved: List<SearchResultPlace>,
    listState: LazyListState,
    isPlaceSaved: (SearchResultPlace) -> Boolean,
    onPreviewPlace: (SearchResultPlace) -> Unit,
    onToggleSavedPlace: (SearchResultPlace) -> Unit,
    listModifier: Modifier = Modifier,
) {
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
            val savedPlacesToShow = if (query.length >= 2) filteredSaved else displayedSaved
            Box(
                modifier = listModifier.carLazyScrollbar(listState),
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
                            onClick = { onPreviewPlace(place) },
                            onToggleStar = { onToggleSavedPlace(place) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DestinationSuggestionsSection(
    displayedRecents: List<SearchResultPlace>,
    displayedSuggestionsNearby: List<SearchResultPlace>,
    snapshotOriginReliable: Boolean,
    hasOfflinePlacesDatabase: Boolean,
    listState: LazyListState,
    isPlaceSaved: (SearchResultPlace) -> Boolean,
    onPreviewPlace: (SearchResultPlace) -> Unit,
    onToggleSavedPlace: (SearchResultPlace) -> Unit,
    onOpenMapData: () -> Unit,
    listModifier: Modifier = Modifier,
) {
    val suggestionsEmpty = displayedRecents.isEmpty() && displayedSuggestionsNearby.isEmpty()

    if (suggestionsEmpty) {
        Column(
            verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap / 2),
        ) {
            CarBodyText(
                text = when {
                    !snapshotOriginReliable ->
                        "Waiting for GPS — nearby suggestions appear once location is available"
                    hasOfflinePlacesDatabase ->
                        "No recent destinations — drive to build nearby suggestions from places you pass"
                    else ->
                        "No recent destinations — install a country pack in Map Data for offline nearby search and richer suggestions while driving"
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            if (snapshotOriginReliable && !hasOfflinePlacesDatabase) {
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
            modifier = listModifier.carLazyScrollbar(listState),
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
                            onClick = { onPreviewPlace(place) },
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
                            onClick = { onPreviewPlace(place) },
                            onToggleStar = { onToggleSavedPlace(place) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TypedSearchResultsList(
    results: List<SearchResultPlace>,
    listState: LazyListState,
    isPlaceSaved: (SearchResultPlace) -> Boolean,
    onPreviewPlace: (SearchResultPlace) -> Unit,
    onToggleSavedPlace: (SearchResultPlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.carLazyScrollbar(listState),
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
                    onClick = { onPreviewPlace(place) },
                    onToggleStar = { onToggleSavedPlace(place) },
                )
            }
        }
    }
}
