package com.kyuusanq3.mixauto.ui.dashboard

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kyuusanq3.mixauto.ui.components.OverlayCloseButton
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarHeadlineText
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack

private const val TAG = "AppDrawerOverlay"

private data class LaunchableApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val launchIntent: Intent,
)

@Composable
fun AppDrawerOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var contextMenuApp by remember { mutableStateOf<LaunchableApp?>(null) }

    val allApps = remember(context) {
        loadLaunchableApps(context.packageManager)
    }
    val filteredApps = remember(allApps, query) {
        if (query.length < 1) {
            allApps
        } else {
            allApps.filter { app ->
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
                .padding(CarDimensions.PaneGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CarHeadlineText(
                    text = "Apps",
                    modifier = Modifier.weight(1f),
                )
                OverlayCloseButton(
                    onClick = onDismiss,
                    contentDescription = "Close app drawer",
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

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = CarDimensions.PaneGap),
                horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
                verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    AppDrawerItem(
                        app = app,
                        onLaunch = { launchApp(context, app) },
                        onLongPress = { contextMenuApp = app },
                    )
                }
            }
        }

        contextMenuApp?.let { app ->
            DropdownMenu(
                expanded = true,
                onDismissRequest = { contextMenuApp = null },
            ) {
                DropdownMenuItem(
                    text = { CarLabelText("App Info") },
                    onClick = {
                        openAppInfo(context, app.packageName)
                        contextMenuApp = null
                    },
                )
                DropdownMenuItem(
                    text = { CarLabelText("Uninstall") },
                    onClick = {
                        uninstallApp(context, app.packageName)
                        contextMenuApp = null
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppDrawerItem(
    app: LaunchableApp,
    onLaunch: () -> Unit,
    onLongPress: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CarDimensions.MinTapTarget)
            .combinedClickable(
                onClick = onLaunch,
                onLongClick = onLongPress,
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
            if (app.icon != null) {
                Image(
                    bitmap = app.icon,
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
}

private fun loadLaunchableApps(packageManager: PackageManager): List<LaunchableApp> {
    val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    val resolveInfos = packageManager.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)

    return resolveInfos
        .mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return@mapNotNull null
            val label = resolveInfo.loadLabel(packageManager).toString()
            val icon = runCatching {
                resolveInfo.loadIcon(packageManager).toImageBitmap()
            }.getOrNull()
            LaunchableApp(
                packageName = packageName,
                label = label,
                icon = icon,
                launchIntent = launchIntent,
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

private fun launchApp(context: android.content.Context, app: LaunchableApp) {
    try {
        app.launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(app.launchIntent)
    } catch (exception: Exception) {
        Log.w(TAG, "Failed to launch ${app.label}", exception)
    }
}

private fun openAppInfo(context: android.content.Context, packageName: String) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (exception: Exception) {
        Log.w(TAG, "Failed to open app info for $packageName", exception)
    }
}

private fun uninstallApp(context: android.content.Context, packageName: String) {
    try {
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (exception: Exception) {
        Log.w(TAG, "Failed to uninstall $packageName", exception)
    }
}

private fun Drawable.toImageBitmap(): ImageBitmap {
    if (this is BitmapDrawable && bitmap != null) {
        return bitmap.asImageBitmap()
    }

    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}
