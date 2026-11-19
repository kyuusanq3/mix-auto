package com.kyuusanq3.mixauto.ui.settings

import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.kyuusanq3.mixauto.BuildConfig
import com.kyuusanq3.mixauto.R
import com.kyuusanq3.mixauto.ui.components.carScrollbar
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarHeadlineText
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import java.util.Locale

private const val ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead"
private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
private const val PLAY_STORE_PACKAGE = "com.android.vending"

private data class InfoLine(val label: String, val value: String)

@Composable
fun HeadUnitInfoPane(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lines = remember { readHeadUnitInfo(context) }
    val scrollState = rememberScrollState()
    Surface(
        modifier = modifier.carScrollbar(scrollState),
        color = OledBlack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    horizontal = CarDimensions.PaneGap * 2,
                    vertical = CarDimensions.PaneGap,
                ),
            verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
        ) {
            lines.forEach { line ->
                if (line.label.isEmpty()) {
                    CarHeadlineText(
                        text = line.value,
                        style = MaterialTheme.typography.titleLarge.copy(color = ElectricCyan),
                    )
                } else {
                    InfoLineRow(line)
                }
            }
        }
    }
}

@Composable
private fun InfoLineRow(line: InfoLine) {
    Column(modifier = Modifier.fillMaxWidth()) {
        CarLabelText(
            text = line.label,
            style = MaterialTheme.typography.labelMedium,
        )
        CarBodyText(
            text = line.value,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 3,
        )
    }
}

private fun readHeadUnitInfo(context: android.content.Context): List<InfoLine> {
    val metrics = context.resources.displayMetrics
    val packageManager = context.packageManager
    return listOf(
        InfoLine("", "Head unit"),
        InfoLine("Manufacturer", Build.MANUFACTURER),
        InfoLine("Model", Build.MODEL),
        InfoLine("Device", Build.DEVICE),
        InfoLine("Product", Build.PRODUCT),
        InfoLine("Hardware", Build.HARDWARE),
        InfoLine("Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"),
        InfoLine("Firmware", Build.DISPLAY),
        InfoLine(
            "Screen",
            metrics.widthPixels.toString() + " x " + metrics.heightPixels +
                " px, " + metrics.densityDpi + " dpi",
        ),
        InfoLine("CPU", Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown"),
        InfoLine("Storage free", freeStorageLabel()),
        InfoLine("", "Apps"),
        InfoLine(
            context.getString(R.string.app_name),
            BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")",
        ),
        InfoLine("Android Auto", packageVersion(packageManager, ANDROID_AUTO_PACKAGE)),
        InfoLine("Google Maps", packageVersion(packageManager, GOOGLE_MAPS_PACKAGE)),
        InfoLine("Play Store", packageVersion(packageManager, PLAY_STORE_PACKAGE)),
    )
}

private fun packageVersion(packageManager: PackageManager, packageName: String): String {
    return try {
        @Suppress("DEPRECATION")
        val info = packageManager.getPackageInfo(packageName, 0)
        val version = info.versionName ?: "Unknown"
        val code = if (Build.VERSION.SDK_INT >= 28) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        "$version ($code)"
    } catch (_: PackageManager.NameNotFoundException) {
        "Not installed"
    }
}

private fun freeStorageLabel(): String {
    return try {
        val stat = StatFs(android.os.Environment.getDataDirectory().path)
        formatBytes(stat.availableBytes) + " free of " + formatBytes(stat.totalBytes)
    } catch (_: Exception) {
        "Unavailable"
    }
}

private fun formatBytes(bytes: Long): String {
    val gigabytes = bytes / (1024.0 * 1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f GB", gigabytes)
}
