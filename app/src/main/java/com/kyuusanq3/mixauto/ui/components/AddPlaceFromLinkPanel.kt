package com.kyuusanq3.mixauto.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyuusanq3.mixauto.data.map.GoogleMapsLinkParser
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import com.kyuusanq3.mixauto.ui.settings.LauncherViewModel
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun AddPlaceFromLinkContent(
    mapEngine: CarMapEngine,
    onDismiss: () -> Unit,
    onConfirmSuccess: (SearchResultPlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    val launcherViewModel: LauncherViewModel = viewModel()
    val linkState = launcherViewModel.addPlaceLinkState
    val mapUiState by mapEngine.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

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
                title = "Add Place",
                onClose = onDismiss,
                closeContentDescription = "Back to destination search",
                compact = true,
            )

            CarBodyText(
                text = "Paste a Google Maps share link or coordinates (latitude, longitude).",
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = linkState.linkText,
                onValueChange = { text ->
                    launcherViewModel.updateAddPlaceLink { state ->
                        state.copy(linkText = text, errorMessage = null)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                placeholder = {
                    CarLabelText(
                        text = "https://maps.app.goo.gl/...",
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                minLines = 3,
                maxLines = 6,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = OledBlack,
                    unfocusedContainerColor = OledBlack,
                ),
            )

            if (linkState.isResolving) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            linkState.errorMessage?.let { message ->
                CarBodyText(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.error,
                    ),
                )
            }

            Button(
                onClick = {
                    if (mapUiState.isNavigating) {
                        launcherViewModel.updateAddPlaceLink { state ->
                            state.copy(errorMessage = "End navigation before adding a pin")
                        }
                        return@Button
                    }
                    val input = linkState.linkText.trim()
                    if (input.isBlank()) return@Button

                    launcherViewModel.updateAddPlaceLink { state ->
                        state.copy(isResolving = true, errorMessage = null)
                    }
                    scope.launch {
                        val result = GoogleMapsLinkParser.resolve(input)
                        launcherViewModel.updateAddPlaceLink { state ->
                            state.copy(isResolving = false)
                        }
                        result.fold(
                            onSuccess = { parsed ->
                                val place = SearchResultPlace(
                                    name = parsed.suggestedName?.takeIf { it.isNotBlank() }
                                        ?: "Dropped Pin",
                                    subTitle = formatLatLngSubtitle(parsed.lat, parsed.lng),
                                    latitude = parsed.lat,
                                    longitude = parsed.lng,
                                    isDroppedPin = true,
                                )
                                onConfirmSuccess(place)
                            },
                            onFailure = { error ->
                                launcherViewModel.updateAddPlaceLink { state ->
                                    state.copy(
                                        errorMessage = error.message
                                            ?: "Could not find coordinates in this link",
                                    )
                                }
                            },
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CarDimensions.MinTapTarget),
                enabled = linkState.linkText.isNotBlank() && !linkState.isResolving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = OledBlack,
                    disabledContainerColor = ElectricCyan.copy(alpha = 0.38f),
                    disabledContentColor = OledBlack.copy(alpha = 0.38f),
                ),
            ) {
                CarLabelText(
                    text = "Confirm",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

private fun formatLatLngSubtitle(lat: Double, lng: Double): String {
    val latDir = if (lat >= 0) "N" else "S"
    val lngDir = if (lng >= 0) "E" else "W"
    return String.format(
        Locale.US,
        "%.5f° %s, %.5f° %s",
        abs(lat),
        latDir,
        abs(lng),
        lngDir,
    )
}
