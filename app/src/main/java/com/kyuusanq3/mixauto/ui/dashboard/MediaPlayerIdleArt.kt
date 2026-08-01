package com.kyuusanq3.mixauto.ui.dashboard

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kyuusanq3.mixauto.ui.components.rememberAppIcon
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan

@Composable
internal fun IdleManualLinkArt(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(CarDimensions.PaneGap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(CarDimensions.AppIconSize),
            tint = ElectricCyan,
        )
        CarLabelText(
            text = "Play the album manually",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = CarDimensions.PaneGap / 2),
        )
    }
}

@Composable
internal fun IdleDefaultPlayerArt(
    packageName: String,
    modifier: Modifier = Modifier,
) {
    val appIcon = rememberAppIcon(packageName)
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (appIcon != null) {
            Image(
                bitmap = appIcon,
                contentDescription = "Open default audio player",
                modifier = Modifier.size(CarDimensions.PrimaryTapTarget),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = "Open default audio player",
                modifier = Modifier.size(CarDimensions.PrimaryTapTarget),
                tint = ElectricCyan,
            )
        }
    }
}

internal fun launchManualFallbackLink(
    context: Context,
    resumeLink: String,
    preferredPackage: String?,
) {
    val uri = runCatching { Uri.parse(resumeLink) }.getOrNull() ?: return
    val targeted = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!preferredPackage.isNullOrBlank()) {
            setPackage(preferredPackage)
        }
    }
    val canResolveTargeted = runCatching {
        context.packageManager.resolveActivity(targeted, PackageManager.MATCH_DEFAULT_ONLY)
    }.getOrNull() != null
    val intent = if (canResolveTargeted) {
        targeted
    } else {
        Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    runCatching { context.startActivity(intent) }
}
