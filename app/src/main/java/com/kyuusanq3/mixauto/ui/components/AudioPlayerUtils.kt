package com.kyuusanq3.mixauto.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

data class AudioPlayerApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
)

fun loadAudioPlayerPackageNames(context: Context): Set<String> {
    val packageManager = context.packageManager
    val ownPackage = context.packageName
    val packageNames = linkedSetOf<String>()

    val musicIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
    @Suppress("DEPRECATION")
    packageManager.queryIntentActivities(musicIntent, PackageManager.MATCH_ALL).forEach { resolveInfo ->
        packageNames.add(resolveInfo.activityInfo.packageName)
    }

    val browserIntent = Intent("android.media.browse.MediaBrowserService")
    @Suppress("DEPRECATION")
    packageManager.queryIntentServices(browserIntent, PackageManager.MATCH_ALL).forEach { resolveInfo ->
        packageNames.add(resolveInfo.serviceInfo.packageName)
    }

    return packageNames.filter { it != ownPackage }.toSet()
}

fun loadAudioPlayerApps(context: Context): List<AudioPlayerApp> {
    val packageManager = context.packageManager
    val packageNames = loadAudioPlayerPackageNames(context)

    return packageNames
        .mapNotNull { packageName ->
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return@mapNotNull null
            if (launchIntent.component == null) return@mapNotNull null
            val applicationInfo = runCatching {
                packageManager.getApplicationInfo(packageName, 0)
            }.getOrNull() ?: return@mapNotNull null
            val label = packageManager.getApplicationLabel(applicationInfo).toString()
            val icon = runCatching {
                packageManager.getApplicationIcon(applicationInfo).toImageBitmap()
            }.getOrNull()
            AudioPlayerApp(
                packageName = packageName,
                label = label,
                icon = icon,
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
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
