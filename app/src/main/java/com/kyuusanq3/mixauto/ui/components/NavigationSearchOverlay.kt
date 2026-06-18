package com.kyuusanq3.mixauto.ui.components

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
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.domain.map.PlaceResult
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarHeadlineText
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import kotlinx.coroutines.delay

@Composable
fun NavigationSearchOverlay(
    engine: CarMapEngine,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.length < 2) {
            results = emptyList()
            hasSearched = false
            isLoading = false
            return@LaunchedEffect
        }
        delay(400)
        isLoading = true
        hasSearched = true
        results = engine.searchDestination(query)
        isLoading = false
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
                    )
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.height(CarDimensions.MinTapTarget),
                    ) {
                        CarLabelText(
                            text = "Close",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
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

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                when {
                    query.length < 2 -> {
                        CarBodyText(
                            text = "Type at least 2 characters to search",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    hasSearched && !isLoading && results.isEmpty() -> {
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
                            items(results, key = { "${it.lat},${it.lng},${it.displayName}" }) { place ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(CarDimensions.MinTapTarget)
                                        .clickable {
                                            engine.navigateToCoordinates(place.lat, place.lng)
                                            onDismiss()
                                        }
                                        .padding(horizontal = CarDimensions.PaneGap),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CarBodyText(
                                        text = place.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 2,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
