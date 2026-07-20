package com.kyuusanq3.mixauto.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyuusanq3.mixauto.ui.settings.LauncherViewModel
import com.kyuusanq3.mixauto.ui.settings.TomTomKeyCheckState
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack

@Composable
internal fun TomTomApiKeySection(
    tomTomApiKey: String,
    onTomTomApiKeyChange: (String) -> Unit,
    showTraffic: Boolean,
    onToggleTraffic: (() -> Unit)?,
) {
    val launcherViewModel: LauncherViewModel = viewModel()
    val tomTomKeyCheckState = launcherViewModel.tomTomKeyCheckState
    val isChecking = tomTomKeyCheckState is TomTomKeyCheckState.Checking

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CarBodyText(
            text = "TomTom API Key (traffic)",
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedTextField(
            value = tomTomApiKey,
            onValueChange = onTomTomApiKeyChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(CarDimensions.PrimaryTapTarget + CarDimensions.PaneGap),
            placeholder = {
                CarBodyText(
                    text = "Paste key from developer.tomtom.com",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            singleLine = true,
            trailingIcon = {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(CarDimensions.PanelHeaderIconSize),
                        color = ElectricCyan,
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(
                        onClick = launcherViewModel::checkTomTomApiKey,
                        modifier = Modifier.size(CarDimensions.PanelHeaderTapTarget),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NetworkCheck,
                            contentDescription = "Test TomTom API Key",
                            modifier = Modifier.size(CarDimensions.PanelHeaderIconSize),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = OledBlack,
                unfocusedContainerColor = OledBlack,
                focusedTextColor = ElectricCyan,
                unfocusedTextColor = ElectricCyan,
            ),
        )
        CarLabelText(
            text = "Free tier covers Philippines. Required for traffic overlay.",
            style = MaterialTheme.typography.labelMedium,
        )
        if (onToggleTraffic != null) {
            SettingsSwitchRow(
                label = "Traffic Overlay",
                checked = showTraffic,
                onCheckedChange = { checked ->
                    if (checked != showTraffic) {
                        onToggleTraffic()
                    }
                },
            )
        }
        when (tomTomKeyCheckState) {
            TomTomKeyCheckState.Idle, TomTomKeyCheckState.Checking -> Unit
            is TomTomKeyCheckState.Success -> {
                CarLabelText(
                    text = tomTomKeyCheckState.message,
                    style = MaterialTheme.typography.labelMedium.copy(color = ElectricCyan),
                )
            }
            is TomTomKeyCheckState.Error -> {
                CarLabelText(
                    text = tomTomKeyCheckState.message,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.error,
                    ),
                )
            }
        }
    }
}
