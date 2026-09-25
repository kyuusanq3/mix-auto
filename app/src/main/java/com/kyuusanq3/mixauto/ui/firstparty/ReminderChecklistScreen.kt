package com.kyuusanq3.mixauto.ui.firstparty

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kyuusanq3.mixauto.ui.components.PanelHeaderRow
import com.kyuusanq3.mixauto.ui.components.SettingsSwitchRow
import com.kyuusanq3.mixauto.ui.components.carLazyScrollbar
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack

/**
 * Full-screen editor for the Reminder Checklist first-party app: an enable toggle plus a
 * dynamic enumerated list of checklist item strings. Persists immediately via the callbacks.
 */
@Composable
fun ReminderChecklistScreen(
    enabled: Boolean,
    items: List<String>,
    onToggleEnabled: (Boolean) -> Unit,
    onUpdateItem: (Int, String) -> Unit,
    onAddItem: (String) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    Surface(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding(),
        color = OledBlack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = CarDimensions.PaneGap * 2, vertical = CarDimensions.PaneGap / 2),
        ) {
            PanelHeaderRow(
                title = "Reminder Checklist",
                onClose = onDismiss,
                closeContentDescription = "Close Reminder Checklist",
            )

            SettingsSwitchRow(
                label = "Show checklist on startup",
                checked = enabled,
                onCheckedChange = onToggleEnabled,
            )

            CarLabelText(
                text = "Checklist items",
                modifier = Modifier.padding(top = CarDimensions.PaneGap, bottom = CarDimensions.PaneGap / 2),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (items.isEmpty()) {
                    CarBodyText(
                        text = "No items yet. Add one below.",
                        modifier = Modifier.padding(vertical = CarDimensions.PaneGap),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .carLazyScrollbar(listState),
                        verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap / 2),
                    ) {
                        itemsIndexed(items) { index, item ->
                            ReminderChecklistItemRow(
                                index = index,
                                text = item,
                                onTextChange = { onUpdateItem(index, it) },
                                onRemove = { onRemoveItem(index) },
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { onAddItem("") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = CarDimensions.PaneGap),
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                CarLabelText(
                    text = "Add item",
                    modifier = Modifier.padding(start = CarDimensions.PaneGap / 2),
                )
            }
        }
    }
}

@Composable
private fun ReminderChecklistItemRow(
    index: Int,
    text: String,
    onTextChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap / 2),
    ) {
        CarBodyText(
            text = "${index + 1}.",
            modifier = Modifier.padding(end = CarDimensions.PaneGap / 2),
        )
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = ElectricCyan,
                focusedIndicatorColor = ElectricCyan,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
            ),
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Remove item ${index + 1}",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
