package com.kyuusanq3.mixauto.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpOffset
import com.kyuusanq3.mixauto.ui.theme.CarLabelText

private const val TAG = "AppContextMenu"

fun openAppInfo(context: Context, packageName: String) {
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

fun uninstallApp(context: Context, packageName: String) {
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

@Composable
fun AppContextDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    packageName: String,
    isPinnedToDock: Boolean,
    canAddToDock: Boolean,
    onToggleDockPin: () -> Unit,
    offset: DpOffset = DpOffset.Zero,
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset,
    ) {
        DropdownMenuItem(
            text = {
                CarLabelText(
                    if (isPinnedToDock) "Remove from shortcut bar" else "Add to shortcut bar",
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = if (isPinnedToDock) Icons.Filled.Delete else Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            enabled = isPinnedToDock || canAddToDock,
            onClick = {
                onDismissRequest()
                onToggleDockPin()
            },
        )
        DropdownMenuItem(
            text = { CarLabelText("App Info") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            onClick = {
                onDismissRequest()
                openAppInfo(context, packageName)
            },
        )
        DropdownMenuItem(
            text = { CarLabelText("Uninstall") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Android,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            onClick = {
                onDismissRequest()
                uninstallApp(context, packageName)
            },
        )
    }
}
