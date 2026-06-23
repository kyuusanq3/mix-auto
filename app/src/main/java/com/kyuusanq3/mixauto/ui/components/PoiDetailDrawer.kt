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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
fun PoiDetailDrawer(
    poi: SearchResultPlace,
    isStarred: Boolean,
    onStar: () -> Unit,
    onNavigate: () -> Unit,
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

        ElevatedCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(CarDimensions.PaneGap),
            colors = CardDefaults.elevatedCardColors(
                containerColor = DeepCharcoal,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CarDimensions.PaneGap * 2),
                verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
            ) {
                CarHeadlineText(
                    text = poi.name,
                    style = MaterialTheme.typography.headlineSmall,
                )
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
                        onClick = onNavigate,
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
                        onClick = onStar,
                        modifier = Modifier.size(CarDimensions.MinTapTarget),
                    ) {
                        Icon(
                            imageVector = if (isStarred) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = if (isStarred) "Remove from saved" else "Save place",
                            tint = if (isStarred) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(CarDimensions.MinTapTarget),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close place details",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
