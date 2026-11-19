package com.kyuusanq3.mixauto.ui.dashboard

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kyuusanq3.mixauto.R
import com.kyuusanq3.mixauto.BuildConfig
import com.kyuusanq3.mixauto.data.apps.LaunchableAppEntry
import com.kyuusanq3.mixauto.ui.components.AppContextDropdownMenu
import com.kyuusanq3.mixauto.ui.components.PanelHeaderIconButton
import com.kyuusanq3.mixauto.ui.components.PanelHeaderRow
import com.kyuusanq3.mixauto.ui.components.rememberAppIcon
import com.kyuusanq3.mixauto.ui.firstparty.FIRST_PARTY_APPS
import com.kyuusanq3.mixauto.ui.firstparty.FirstPartyAppEntry
import com.kyuusanq3.mixauto.ui.firstparty.FirstPartyAppId
import com.kyuusanq3.mixauto.ui.settings.LauncherPreferences
import com.kyuusanq3.mixauto.ui.settings.LauncherViewModel
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack

private const val TAG = "AppDrawerOverlay"

@Composable
fun AppDrawerOverlay(
    launchableApps: List<LaunchableAppEntry>,
    isLoading: Boolean,
    dockPinnedPackages: List<String>,
    maxDockPinnedApps: Int,
    onToggleDockPin: (String) -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onOpenFirstPartyApp: (FirstPartyAppId) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val launcherViewModel: LauncherViewModel = viewModel()
    var query by remember { mutableStateOf("") }

    val filteredApps = remember(launchableApps, query) {
        if (query.length < 1) {
            launchableApps
        } else {
            launchableApps.filter { app ->
                app.label.contains(query, ignoreCase = true)
            }
        }
    }

    // Map Source (Mix Auto vs embedded Google Maps) only applies to the headunit-signed flavor —
    // on standard builds there is no second map engine to switch to.
    val availableFirstPartyApps = remember {
        if (BuildConfig.HEADUNIT_SYSTEM) {
            FIRST_PARTY_APPS
        } else {
            FIRST_PARTY_APPS.filter { it.id != FirstPartyAppId.MAP_SOURCE }
        }
    }

    val filteredFirstPartyApps = remember(query, availableFirstPartyApps) {
        if (query.length < 1) {
            availableFirstPartyApps
        } else {
            availableFirstPartyApps.filter { app ->
                app.label.contains(query, ignoreCase = true)
            }
        }
    }

    Surface(
        modifier = modifier,
        color = OledBlack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = CarDimensions.PaneGap * 2,
                    vertical = CarDimensions.PaneGap / 2,
                ),
        ) {
            PanelHeaderRow(
                title = "Apps",
                onClose = onDismiss,
                closeContentDescription = "Close app drawer",
                largeActions = true,
            ) {
                PanelHeaderIconButton(
                    onClick = onOpenLauncherSettings,
                    contentDescription = "Launcher settings",
                    icon = Icons.Filled.Settings,
                    large = true,
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = CarDimensions.PaneGap),
                placeholder = { CarLabelText("Search apps") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = ElectricCyan,
                    focusedIndicatorColor = ElectricCyan,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                ),
            )

            when {
                isLoading && filteredApps.isEmpty() && filteredFirstPartyApps.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(CarDimensions.PaneGap),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = ElectricCyan)
                    }
                }
                filteredApps.isEmpty() && filteredFirstPartyApps.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(CarDimensions.PaneGap),
                        contentAlignment = Alignment.Center,
                    ) {
                        CarBodyText("No apps found")
                    }
                }
                else -> {
                    AppDrawerAppGrid(
                        apps = filteredApps,
                        columnCount = launcherViewModel.appDrawerColumnCount,
                        pagingMode = launcherViewModel.appDrawerPagingMode,
                        dockPinnedPackages = dockPinnedPackages,
                        maxDockPinnedApps = maxDockPinnedApps,
                        onToggleDockPin = onToggleDockPin,
                        onLaunch = { app -> launchApp(context, app) },
                        mixAutoAppCount = filteredFirstPartyApps.size,
                        mixAutoHeader = if (filteredFirstPartyApps.isNotEmpty()) {
                            {
                                MixAutoDrawerHeader(
                                    apps = filteredFirstPartyApps,
                                    columnCount = launcherViewModel.appDrawerColumnCount,
                                    onLaunch = { app -> onOpenFirstPartyApp(app.id) },
                                )
                            }
                        } else {
                            null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MixAutoDrawerHeader(
    apps: List<FirstPartyAppEntry>,
    columnCount: Int,
    onLaunch: (FirstPartyAppEntry) -> Unit,
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(MixAutoLabelBlockHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            CarLabelText(text = stringResource(R.string.app_name))
        }
        FirstPartyColumnGroup(
            apps = apps,
            columnCount = columnCount,
            onLaunch = onLaunch,
        )
    }
}

@Composable
private fun FirstPartyColumnGroup(
    apps: List<FirstPartyAppEntry>,
    columnCount: Int,
    onLaunch: (FirstPartyAppEntry) -> Unit,
) {
    val columns = columnCount.coerceIn(
        LauncherPreferences.MIN_APP_DRAWER_COLUMNS,
        LauncherPreferences.MAX_APP_DRAWER_COLUMNS,
    )
    Column(verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap)) {
        apps.chunked(columns).forEach { rowApps ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppDrawerCellHeight),
                horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
            ) {
                rowApps.forEach { app ->
                    FirstPartyAppCell(
                        app = app,
                        onLaunch = { onLaunch(app) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - rowApps.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FirstPartyAppCell(
    app: FirstPartyAppEntry,
    onLaunch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .height(AppDrawerCellHeight)
            .clickable(onClick = onLaunch),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = CarDimensions.CardElevation),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CarDimensions.PaneGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
        ) {
            Icon(
                imageVector = app.icon,
                contentDescription = app.label,
                modifier = Modifier.size(CarDimensions.AppIconSize),
                tint = MaterialTheme.colorScheme.primary,
            )
            CarLabelText(
                text = app.label,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppDrawerItem(
    app: LaunchableAppEntry,
    isPinnedToDock: Boolean,
    canAddToDock: Boolean,
    onToggleDockPin: () -> Unit,
    onLaunch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = rememberAppIcon(app.packageName)
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = onLaunch,
                    onLongClick = { showMenu = true },
                ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = CarDimensions.CardElevation),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CarDimensions.PaneGap),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
            ) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = app.label,
                        modifier = Modifier.size(CarDimensions.AppIconSize),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Android,
                        contentDescription = app.label,
                        modifier = Modifier.size(CarDimensions.AppIconSize),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                CarLabelText(
                    text = app.label,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        AppContextDropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            packageName = app.packageName,
            isPinnedToDock = isPinnedToDock,
            canAddToDock = canAddToDock,
            onToggleDockPin = onToggleDockPin,
        )
    }
}

internal fun launchApp(context: android.content.Context, app: LaunchableAppEntry) {
    try {
        app.launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(app.launchIntent)
    } catch (exception: Exception) {
        Log.w(TAG, "Failed to launch ${app.label}", exception)
    }
}
