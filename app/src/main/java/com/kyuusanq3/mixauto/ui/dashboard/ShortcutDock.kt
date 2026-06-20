package com.kyuusanq3.mixauto.ui.dashboard

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.DeepCharcoal

private const val TAG = "ShortcutDock"
private const val LAUNCHER_SETTINGS_KEY = "launcher_settings"
private const val MAP_DATA_KEY = "map_data"

data class AppShortcut(
    val id: String,
    val label: String,
    val icon: ImageBitmap?,
    val fallbackIcon: ImageVector,
    val intent: Intent?,
)

private data class ShortcutTarget(
    val id: String,
    val label: String,
    val packages: List<String>,
    val fallbackIcon: ImageVector,
    val fallbackIntent: Intent? = null,
    val forceFallbackIcon: Boolean = false,
)

private val shortcutTargets = listOf(
    ShortcutTarget(
        id = "system_settings",
        label = "Settings",
        packages = listOf("com.android.settings"),
        fallbackIcon = Icons.Filled.Settings,
    ),
    ShortcutTarget(
        id = "radio",
        label = "Radio",
        packages = listOf(
            "com.android.fmradio",
            "com.caf.fmradio",
            "com.eonon.radio",
        ),
        fallbackIcon = Icons.Filled.Radio,
        forceFallbackIcon = true,
    ),
    ShortcutTarget(
        id = "bluetooth",
        label = "Bluetooth",
        packages = listOf("com.android.settings"),
        fallbackIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
        fallbackIcon = Icons.Filled.Bluetooth,
        forceFallbackIcon = true,
    ),
)

@Composable
fun ShortcutDock(
    isHorizontal: Boolean,
    isLargeIcons: Boolean = false,
    onOpenSettings: () -> Unit,
    onOpenMapData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shortcuts = remember(context) {
        resolveShortcuts(context.packageManager)
    }
    val tapTarget = if (isLargeIcons) {
        CarDimensions.DockHorizontalTapTarget * 2
    } else {
        CarDimensions.DockHorizontalTapTarget
    }
    val horizontalIconSize = if (isLargeIcons) {
        CarDimensions.DockHorizontalIconSize * 2
    } else {
        CarDimensions.DockHorizontalIconSize
    }
    val verticalIconSize = if (isLargeIcons) {
        CarDimensions.AppIconSize * 2
    } else {
        CarDimensions.AppIconSize
    }

    ElevatedCard(
        modifier = if (isHorizontal) {
            modifier
                .padding(horizontal = CarDimensions.PaneGap)
                .wrapContentHeight()
        } else {
            modifier.padding(CarDimensions.PaneGap)
        },
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = DeepCharcoal,
        ),
    ) {
        key(isHorizontal) {
            if (isHorizontal) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(tapTarget)
                        .padding(horizontal = CarDimensions.PaneGap),
                    horizontalArrangement = Arrangement.spacedBy(CarDimensions.DockItemSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(shortcuts, key = { it.id }) { shortcut ->
                        MinimizedShortcutItem(
                            shortcut = shortcut,
                            tapTarget = tapTarget,
                            iconSize = horizontalIconSize,
                            onClick = { launchShortcut(context, shortcut) },
                        )
                    }
                    item(key = MAP_DATA_KEY) {
                        MapDataDockItem(
                            horizontal = true,
                            tapTarget = tapTarget,
                            iconSize = horizontalIconSize,
                            onClick = onOpenMapData,
                        )
                    }
                    item(key = LAUNCHER_SETTINGS_KEY) {
                        SettingsDockItem(
                            horizontal = true,
                            tapTarget = tapTarget,
                            iconSize = horizontalIconSize,
                            onClick = onOpenSettings,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(CarDimensions.PaneGap),
                ) {
                    CarLabelText(
                        text = "Shortcuts",
                        modifier = Modifier.padding(bottom = CarDimensions.DockItemSpacing),
                        style = MaterialTheme.typography.titleMedium,
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = CarDimensions.DockItemSpacing),
                        verticalArrangement = Arrangement.spacedBy(CarDimensions.DockItemSpacing),
                    ) {
                        items(shortcuts, key = { it.id }) { shortcut ->
                            ShortcutItem(
                                shortcut = shortcut,
                                iconSize = verticalIconSize,
                                onClick = { launchShortcut(context, shortcut) },
                            )
                        }
                        item(key = MAP_DATA_KEY) {
                            MapDataDockItem(
                                horizontal = false,
                                iconSize = verticalIconSize,
                                onClick = onOpenMapData,
                            )
                        }
                        item(key = LAUNCHER_SETTINGS_KEY) {
                            SettingsDockItem(
                                horizontal = false,
                                iconSize = verticalIconSize,
                                onClick = onOpenSettings,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapDataDockItem(
    horizontal: Boolean,
    iconSize: Dp,
    onClick: () -> Unit,
    tapTarget: Dp = CarDimensions.DockHorizontalTapTarget,
) {
    if (horizontal) {
        Box(
            modifier = Modifier
                .size(tapTarget)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Map,
                contentDescription = "Map Data",
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    } else {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = CarDimensions.MinTapTarget)
                .clickable(onClick = onClick),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = CarDimensions.CardElevation),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CarDimensions.PaneGap),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Map,
                    contentDescription = "Map Data",
                    modifier = Modifier.size(iconSize),
                    tint = MaterialTheme.colorScheme.primary,
                )
                CarBodyText(
                    text = "Map Data",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SettingsDockItem(
    horizontal: Boolean,
    iconSize: Dp,
    onClick: () -> Unit,
    tapTarget: Dp = CarDimensions.DockHorizontalTapTarget,
) {
    if (horizontal) {
        Box(
            modifier = Modifier
                .size(tapTarget)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = "Launcher",
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    } else {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = CarDimensions.MinTapTarget)
                .clickable(onClick = onClick),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = CarDimensions.CardElevation),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CarDimensions.PaneGap),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = "Launcher",
                    modifier = Modifier.size(iconSize),
                    tint = MaterialTheme.colorScheme.primary,
                )
                CarBodyText(
                    text = "Launcher",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun MinimizedShortcutItem(
    shortcut: AppShortcut,
    tapTarget: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(tapTarget)
            .clickable(enabled = shortcut.intent != null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ShortcutIcon(
            shortcut = shortcut,
            iconSize = iconSize,
        )
    }
}

@Composable
private fun ShortcutItem(
    shortcut: AppShortcut,
    iconSize: Dp,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = CarDimensions.MinTapTarget)
            .clickable(enabled = shortcut.intent != null, onClick = onClick),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = CarDimensions.CardElevation),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CarDimensions.PaneGap),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ShortcutIcon(shortcut = shortcut, iconSize = iconSize)

            CarBodyText(
                text = shortcut.label,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ShortcutIcon(
    shortcut: AppShortcut,
    iconSize: Dp = CarDimensions.AppIconSize,
) {
    if (shortcut.icon != null) {
        Image(
            bitmap = shortcut.icon,
            contentDescription = shortcut.label,
            modifier = Modifier.size(iconSize),
        )
    } else {
        Icon(
            imageVector = shortcut.fallbackIcon,
            contentDescription = shortcut.label,
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun resolveShortcuts(packageManager: PackageManager): List<AppShortcut> {
    return shortcutTargets.map { target ->
        resolveShortcut(packageManager, target)
    }
}

private fun resolveShortcut(
    packageManager: PackageManager,
    target: ShortcutTarget,
): AppShortcut {
    for (packageName in target.packages) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            val appInfo = runCatching {
                packageManager.getApplicationInfo(packageName, 0)
            }.getOrNull()

            val icon = if (target.forceFallbackIcon) {
                null
            } else {
                appInfo?.let {
                    runCatching {
                        packageManager.getApplicationIcon(it).toImageBitmap()
                    }.getOrNull()
                }
            }

            return AppShortcut(
                id = target.id,
                label = target.label,
                icon = icon,
                fallbackIcon = target.fallbackIcon,
                intent = if (target.fallbackIntent != null) target.fallbackIntent else launchIntent,
            )
        }
    }

    val fallbackIntent = target.fallbackIntent?.let { intent ->
        if (packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
            intent
        } else {
            null
        }
    }

    return AppShortcut(
        id = target.id,
        label = target.label,
        icon = null,
        fallbackIcon = target.fallbackIcon,
        intent = fallbackIntent,
    )
}

private fun launchShortcut(context: android.content.Context, shortcut: AppShortcut) {
    val intent = shortcut.intent ?: return
    try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (exception: Exception) {
        Log.w(TAG, "Failed to launch ${shortcut.label}", exception)
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
