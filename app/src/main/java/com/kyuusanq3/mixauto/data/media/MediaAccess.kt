package com.kyuusanq3.mixauto.data.media

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.kyuusanq3.mixauto.service.MixAutoNotificationListenerService

fun isNotificationListenerEnabled(context: Context): Boolean {
    val component = ComponentName(context, MixAutoNotificationListenerService::class.java)
    val enabledListeners = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners",
    ) ?: return false

    return enabledListeners.split(':').any { entry ->
        entry.equals(component.flattenToString(), ignoreCase = true) ||
            entry.contains(component.packageName, ignoreCase = true)
    }
}
