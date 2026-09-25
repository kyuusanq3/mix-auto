package com.kyuusanq3.mixauto.ui.firstparty

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarHeadlineText
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.DarkSurface
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import com.kyuusanq3.mixauto.ui.theme.SuccessGreen
import kotlinx.coroutines.delay

private const val COMPLETION_DISMISS_DELAY_MS = 900L

/**
 * Blocking full-screen boot popup: shows once per cold start when the Reminder Checklist is
 * enabled and has items. There is no close/skip affordance — every item must be tapped before
 * it auto-dismisses. Checked state resets on the next boot (never persisted).
 */
@Composable
fun ReminderChecklistPopup(
    items: List<String>,
    checkedIndices: Set<Int>,
    onToggleChecked: (Int) -> Unit,
    onAllChecked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val allChecked = items.isNotEmpty() && checkedIndices.size == items.size

    LaunchedEffect(allChecked) {
        if (allChecked) {
            delay(COMPLETION_DISMISS_DELAY_MS)
            onAllChecked()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding(),
        color = OledBlack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(CarDimensions.PaneGap * 2),
        ) {
            CarHeadlineText(
                text = "Reminder Checklist",
                style = MaterialTheme.typography.headlineMedium,
            )
            CarLabelText(
                text = "${checkedIndices.size} of ${items.size} checked",
                modifier = Modifier.padding(top = CarDimensions.PaneGap / 2, bottom = CarDimensions.PaneGap),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
            ) {
                itemsIndexed(items) { index, item ->
                    ReminderChecklistPopupRow(
                        text = item,
                        isChecked = index in checkedIndices,
                        allChecked = allChecked,
                        onToggle = { onToggleChecked(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderChecklistPopupRow(
    text: String,
    isChecked: Boolean,
    allChecked: Boolean,
    onToggle: () -> Unit,
) {
    val accentColor = if (allChecked) SuccessGreen else ElectricCyan
    val containerColor = if (isChecked) accentColor.copy(alpha = 0.16f) else DarkSurface
    val borderColor = if (isChecked) accentColor else MaterialTheme.colorScheme.outline

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CarDimensions.MinTapTarget)
            .clip(RoundedCornerShape(CarDimensions.CardCornerRadius))
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(width = 1.5.dp, color = borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CarDimensions.PaneGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
        ) {
            Icon(
                imageVector = if (isChecked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isChecked) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CarBodyText(
                text = text,
                modifier = Modifier.weight(1f),
            )
        }
    }
}