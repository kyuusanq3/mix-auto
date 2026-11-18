package com.kyuusanq3.mixauto.ui.firstparty

import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kyuusanq3.mixauto.R
import com.kyuusanq3.mixauto.data.androidauto.AndroidAutoSelfSession
import com.kyuusanq3.mixauto.data.androidauto.InstalledAndroidAuto
import com.kyuusanq3.mixauto.ui.components.PanelHeaderRow
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack

private const val GEARHEAD_PACKAGE = "com.google.android.projection.gearhead"

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
    val appName = stringResource(R.string.app_name)
    val installed = remember { installedAndroidAuto(context) }
    var sessionStatus by remember { mutableStateOf("") }
    var sessionRunning by remember { mutableStateOf(false) }
    val session = remember {
        AndroidAutoSelfSession(
            context = context,
            onStatus = { sessionStatus = it },
            onRunning = { sessionRunning = it },
        )
    }
    DisposableEffect(session) {
        onDispose { session.stop() }
    }
    val idleStatus = when {
        installed == null -> "Android Auto is not installed on this unit."
        installed.usesHeadUnitServer ->
            "Android Auto ${installed.versionName} is installed. Turn on Start head unit server, then start self mode. $appName connects on this device."
        else ->
            "Android Auto ${installed.versionName} is installed. Self mode waits for it on this device."
    }
    val status = sessionStatus.ifEmpty { idleStatus }
    val buttonLabel = when {
        installed == null -> "Android Auto is not installed"
        sessionRunning -> "Stop self mode"
        else -> "Start self mode"
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
                maxLines = 8,
            )
            Button(
                onClick = {
                    val current = installed ?: return@Button
                    if (sessionRunning) session.stop() else session.start(current)
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
