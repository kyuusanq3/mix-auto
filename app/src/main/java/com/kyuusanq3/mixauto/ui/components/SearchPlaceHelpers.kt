package com.kyuusanq3.mixauto.ui.components

import android.location.Location
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.outlined.NotListedLocation
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import com.kyuusanq3.mixauto.ui.settings.DeveloperSettings
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import kotlin.math.roundToInt

internal const val SEARCH_DEDUP_THRESHOLD_M = 50f
internal const val NEARBY_POI_SUGGESTION_LIMIT = 20

internal fun Float.formatSearchDistance(): String {
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

internal fun SearchResultPlace.withDistanceFrom(lat: Double, lng: Double): SearchResultPlace {
    val distanceResults = FloatArray(1)
    Location.distanceBetween(lat, lng, latitude, longitude, distanceResults)
    return copy(distanceInMeters = distanceResults[0])
}

internal fun isWithinDedupThreshold(a: SearchResultPlace, b: SearchResultPlace): Boolean {
    val distanceResults = FloatArray(1)
    Location.distanceBetween(
        a.latitude,
        a.longitude,
        b.latitude,
        b.longitude,
        distanceResults,
    )
    return distanceResults[0] < SEARCH_DEDUP_THRESHOLD_M
}

internal fun SearchResultPlace.shouldShowApproximateIcon(): Boolean {
    val conf = confidence ?: return false
    return !hasStreetAddress || conf < DeveloperSettings.APPROXIMATE_POI_CONFIDENCE_CEILING
}

@Composable
internal fun PlaceSubTitleWithApproximateIcon(
    place: SearchResultPlace,
    textStyle: TextStyle,
    useBodyText: Boolean = false,
) {
    if (place.subTitle.isBlank() && !place.shouldShowApproximateIcon()) return
    val mutedCyan = ElectricCyan.copy(alpha = 0.75f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (place.subTitle.isNotBlank()) {
            if (useBodyText) {
                CarBodyText(
                    text = place.subTitle,
                    style = textStyle,
                    maxLines = 2,
                )
            } else {
                CarLabelText(
                    text = place.subTitle,
                    style = textStyle,
                )
            }
        }
        if (place.shouldShowApproximateIcon()) {
            if (place.subTitle.isNotBlank()) {
                CarLabelText(
                    text = "\u00B7",
                    style = textStyle.copy(color = mutedCyan),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.NotListedLocation,
                contentDescription = "Approximate location",
                modifier = Modifier.size(14.dp),
                tint = mutedCyan,
            )
        }
    }
}

internal fun filterSavedPlaces(
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
internal fun SearchResultRow(
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
            PlaceSubTitleWithApproximateIcon(
                place = place,
                textStyle = MaterialTheme.typography.labelMedium,
            )
            if (badge != null) {
                CarLabelText(
                    text = badge,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = ElectricCyan.copy(alpha = 0.75f),
                    ),
                )
            }
        }
        val distanceLabel = place.distanceInMeters.formatSearchDistance()
        val trailingLabel = if (DeveloperSettings.SHOW_POI_SOURCE) {
            val sourceLabel = place.poiSource.trim()
            if (sourceLabel.isNotEmpty()) {
                distanceLabel + " | " + sourceLabel
            } else {
                distanceLabel
            }
        } else {
            distanceLabel
        }
        CarLabelText(
            text = trailingLabel,
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
