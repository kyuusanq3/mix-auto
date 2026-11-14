package com.kyuusanq3.mixauto.ui.firstparty

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.kyuusanq3.mixauto.ui.components.PanelHeaderRow
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack

private const val GEARHEAD_PACKAGE = "com.google.android.projection.gearhead"
private const val SETTINGS_ACTIVITY =
    "com.google.android.projection.gearhead.companion.settings.DefaultSettingsActivity"
private const val WIRELESS_STARTUP_ACTIVITY =
    "com.google.android.apps.auto.wireless.setup.service.impl.WirelessStartupActivity"
private const val START_WIRELESS_PROJECTION =
    "com.google.android.projection.gearhead.START_WIRELESS_PROJECTION"
private const val LOCAL_HOST = "127.0.0.1"
private const val DIRECT_PROJECTION_PORT = 5288

internal data class InstalledAndroidAuto(
    val versionName: String,
    val usesHeadUnitServer: Boolean,
)

internal fun installedAndroidAuto(context: Context): InstalledAndroidAuto? {
    val versionName = readGearheadVersionName(context) ?: return null
    return InstalledAndroidAuto(
        versionName = versionName,
        usesHeadUnitServer = androidAutoUsesHeadUnitServer(versionName),
    )
}

/**
 * 17.4 and newer expect a head unit to dial the developer server on port 5277.
 * 10.9 on this unit takes the older direct wireless-startup path instead.
 */
internal fun androidAutoUsesHeadUnitServer(versionName: String): Boolean {
    val parts = versionName.substringBefore('-').split('.')
    val major = parts.getOrNull(0)?.toIntOrNull() ?: return true
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return major > 17 || (major == 17 && minor >= 4)
}

internal fun startInstalledAndroidAuto(context: Context): String {
    val installed = installedAndroidAuto(context)
        ?: return "Android Auto is not installed"
    return if (installed.usesHeadUnitServer) {
        openAndroidAutoSettings(context, installed.versionName)
    } else {
        startAndroidAutoDirectly(context, installed.versionName)
    }
}

private fun startAndroidAutoDirectly(context: Context, versionName: String): String {
    val intent = Intent(START_WIRELESS_PROJECTION).apply {
        component = ComponentName(GEARHEAD_PACKAGE, WIRELESS_STARTUP_ACTIVITY)
        putExtra("PARAM_HOST_ADDRESS", LOCAL_HOST)
        putExtra("PARAM_SERVICE_PORT", DIRECT_PROJECTION_PORT)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent)
        "Started Android Auto $versionName directly"
    } catch (_: ActivityNotFoundException) {
        openAndroidAutoSettings(context, versionName)
    } catch (_: SecurityException) {
        openAndroidAutoSettings(context, versionName)
    }
}

private fun openAndroidAutoSettings(context: Context, versionName: String): String {
    val intent = Intent().apply {
        component = ComponentName(GEARHEAD_PACKAGE, SETTINGS_ACTIVITY)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent)
        "Opened Android Auto $versionName. Tap Version ten times, then Start head unit server."
    } catch (_: ActivityNotFoundException) {
        "Android Auto is installed, but its screen could not be opened"
    } catch (_: SecurityException) {
        "Android Auto is installed, but its screen could not be opened"
    }
}

private fun readGearheadVersionName(context: Context): String? {
    return try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(GEARHEAD_PACKAGE, 0).versionName
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}

@Composable
fun AndroidAutoScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val installed = remember { installedAndroidAuto(context) }
    val status = when {
        installed == null -> "Android Auto is not installed on this unit."
        installed.usesHeadUnitServer ->
            "Android Auto ${installed.versionName} is installed. This version connects through the head unit server."
        else ->
            "Android Auto ${installed.versionName} is installed. This version starts directly and does not use the head unit server."
    }
    val buttonLabel = when {
        installed == null -> "Android Auto is not installed"
        installed.usesHeadUnitServer -> "Open Android Auto"
        else -> "Start Android Auto"
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
                .padding(horizontal = CarDimensions.PaneGap * 2, vertical = CarDimensions.PaneGap / 2),
        ) {
            PanelHeaderRow(
                title = "Android Auto",
                onClose = onDismiss,
                closeContentDescription = "Close Android Auto",
            )
            CarBodyText(
                text = status,
                modifier = Modifier.padding(vertical = CarDimensions.PaneGap),
                maxLines = 6,
            )
            Button(
                onClick = {
                    Toast.makeText(
                        context,
                        startInstalledAndroidAuto(context),
                        Toast.LENGTH_LONG,
                    ).show()
                },
                enabled = installed != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CarDimensions.PrimaryTapTarget),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = OledBlack,
                ),
            ) {
                CarBodyText(text = buttonLabel, maxLines = 1)
            }
        }
    }
}
