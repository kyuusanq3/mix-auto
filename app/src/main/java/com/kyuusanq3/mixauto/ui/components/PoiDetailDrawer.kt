package com.kyuusanq3.mixauto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarHeadlineText
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.DeepCharcoal
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import kotlin.math.roundToInt

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

private fun categoryDisplayName(category: String): String = when (category) {
    "food" -> "Food & Drink"
    "fuel" -> "Gas Station"
    "health" -> "Health"
    "accommodation" -> "Accommodation"
    "finance" -> "Finance"
    "shopping" -> "Shopping"
    "recreation" -> "Recreation"
    else -> category.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun categoryColor(category: String): Color = when (category) {
    "food" -> Color(0xFFFF8C00)
    "fuel" -> Color(0xFFFFD700)
    "health" -> Color(0xFFFF4444)
    "accommodation" -> Color(0xFF9C27B0)
    "finance" -> Color(0xFF4CAF50)
    "shopping" -> Color(0xFFE91E63)
    "recreation" -> Color(0xFF8BC34A)
    else -> ElectricCyan
}

@Composable
fun PoiDetailPane(
    poi: SearchResultPlace,
    isStarred: Boolean,
    nearbyPois: List<SearchResultPlace>,
    onStar: (customName: String) -> Unit,
    onNavigate: (customName: String) -> Unit,
    onSelectNearby: (SearchResultPlace) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = OledBlack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(CarDimensions.PaneGap * 2),
            verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CarHeadlineText(
                    text = if (poi.isDroppedPin) "Custom Pin" else "Place Details",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                OverlayCloseButton(
                    onClick = onDismiss,
                    contentDescription = "Close place details",
                )
            }

            PoiDetailCardContent(
                poi = poi,
                isStarred = isStarred,
                nearbyPois = nearbyPois,
                onStar = onStar,
                onNavigate = onNavigate,
                onSelectNearby = onSelectNearby,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun PoiDetailDrawer(
    poi: SearchResultPlace,
    isStarred: Boolean,
    onStar: (customName: String) -> Unit,
    onNavigate: (customName: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OledBlack.copy(alpha = 0.4f))
                .clickable(onClick = onDismiss),
        )

        PoiDetailCardContent(
            poi = poi,
            isStarred = isStarred,
            nearbyPois = emptyList(),
            onStar = onStar,
            onNavigate = onNavigate,
            onSelectNearby = {},
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(CarDimensions.PaneGap),
        )
    }
}

@Composable
private fun PoiDetailCardContent(
    poi: SearchResultPlace,
    isStarred: Boolean,
    nearbyPois: List<SearchResultPlace>,
    onStar: (customName: String) -> Unit,
    onNavigate: (customName: String) -> Unit,
    onSelectNearby: (SearchResultPlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    var customName by remember(poi.latitude, poi.longitude, poi.name) {
        mutableStateOf(poi.name)
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = DeepCharcoal,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(CarDimensions.PaneGap * 2),
            verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
        ) {
            if (poi.isDroppedPin) {
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CarDimensions.MinTapTarget),
                    label = {
                        CarLabelText(
                            text = "Pin name",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DeepCharcoal,
                        unfocusedContainerColor = DeepCharcoal,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            } else {
                CarHeadlineText(
                    text = poi.name,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            if (poi.category.isNotBlank()) {
                CarLabelText(
                    text = categoryDisplayName(poi.category),
                    modifier = Modifier
                        .background(
                            categoryColor(poi.category).copy(alpha = 0.25f),
                            MaterialTheme.shapes.small,
                        )
                        .padding(
                            horizontal = CarDimensions.PaneGap,
                            vertical = CarDimensions.PaneGap / 2,
                        ),
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = categoryColor(poi.category),
                    ),
                )
            }
            if (poi.subTitle.isNotBlank()) {
                CarBodyText(
                    text = poi.subTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
            }
            if (poi.distanceInMeters > 0f) {
                CarLabelText(
                    text = poi.distanceInMeters.formatDistance(),
                    style = MaterialTheme.typography.labelLarge.copy(color = ElectricCyan),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { onNavigate(customName.trim().ifBlank { poi.name }) },
                    modifier = Modifier
                        .weight(1f)
                        .height(CarDimensions.MinTapTarget),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = OledBlack,
                    ),
                ) {
                    CarLabelText(
                        text = "Navigate",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                IconButton(
                    onClick = {
                        val resolvedName = customName.trim().ifBlank { poi.name }
                        onStar(resolvedName)
                    },
                    modifier = Modifier.size(CarDimensions.MinTapTarget),
                ) {
                    Icon(
                        imageVector = if (isStarred) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = if (isStarred) "Remove from saved" else "Save place",
                        tint = if (isStarred) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (poi.isDroppedPin && nearbyPois.isNotEmpty()) {
                CarLabelText(
                    text = "Nearby",
                    style = MaterialTheme.typography.labelLarge.copy(color = ElectricCyan),
                )
                nearbyPois.forEach { nearby ->
                    NearbyPoiRow(
                        place = nearby,
                        onClick = { onSelectNearby(nearby) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NearbyPoiRow(
    place: SearchResultPlace,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CarDimensions.MinTapTarget)
            .clickable(onClick = onClick)
            .padding(horizontal = CarDimensions.PaneGap / 2),
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
        }
        CarLabelText(
            text = place.distanceInMeters.formatDistance(),
            style = MaterialTheme.typography.labelMedium.copy(color = ElectricCyan),
        )
    }
}
