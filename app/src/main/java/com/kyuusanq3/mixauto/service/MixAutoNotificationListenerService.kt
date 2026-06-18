package com.kyuusanq3.mixauto.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.kyuusanq3.mixauto.data.media.MediaSessionRepository

class MixAutoNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        MediaSessionRepository.getInstance(this).refreshSessions()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshIfMediaRelated(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshIfMediaRelated(sbn)
    }

    private fun refreshIfMediaRelated(sbn: StatusBarNotification?) {
        if (sbn == null) return
        MediaSessionRepository.getInstance(this).refreshSessions()
    }
}
