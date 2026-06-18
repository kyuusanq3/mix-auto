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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
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
import androidx.compose.ui.unit.dp
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.DeepCharcoal

private const val TAG = "ShortcutDock"
private const val LAUNCHER_SETTINGS_KEY = "launcher_settings"

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
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shortcuts = remember(context) {
        resolveShortcuts(context.packageManager)
    }

    ElevatedCard(
        modifier = if (isHorizontal) {
            modifier
                .padding(
                    start = CarDimensions.PaneGap,
                    end = CarDimensions.PaneGap,
                    top = CarDimensions.PaneGap,
                )
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
                        .height(CarDimensions.MinTapTarget)
                        .padding(horizontal = CarDimensions.PaneGap),
                    horizontalArrangement = Arrangement.spacedBy(CarDimensions.DockItemSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(shortcuts, key = { it.id }) { shortcut ->
                        MinimizedShortcutItem(
                            shortcut = shortcut,
                            onClick = { launchShortcut(context, shortcut) },
                        )
                    }
                    item(key = LAUNCHER_SETTINGS_KEY) {
                        SettingsDockItem(
                            horizontal = true,
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
                                onClick = { launchShortcut(context, shortcut) },
                            )
                        }
                        item(key = LAUNCHER_SETTINGS_KEY) {
                            SettingsDockItem(
                                horizontal = false,
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
private fun SettingsDockItem(
    horizontal: Boolean,
    onClick: () -> Unit,
) {
    if (horizontal) {
        Box(
            modifier = Modifier
                .size(CarDimensions.MinTapTarget)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = "Launcher",
                modifier = Modifier.size(CarDimensions.AppIconSize),
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
                    modifier = Modifier.size(CarDimensions.AppIconSize),
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
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(CarDimensions.MinTapTarget)
            .clickable(enabled = shortcut.intent != null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ShortcutIcon(shortcut = shortcut)
    }
}

@Composable
private fun ShortcutItem(
    shortcut: AppShortcut,
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
            ShortcutIcon(shortcut = shortcut)

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
private fun ShortcutIcon(shortcut: AppShortcut) {
    if (shortcut.icon != null) {
        Image(
            bitmap = shortcut.icon,
            contentDescription = shortcut.label,
            modifier = Modifier.size(CarDimensions.AppIconSize),
        )
    } else {
        Icon(
            imageVector = shortcut.fallbackIcon,
            contentDescription = shortcut.label,
            modifier = Modifier.size(CarDimensions.AppIconSize),
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
