package com.kyuusanq3.mixauto.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kyuusanq3.mixauto.MainActivity
import com.kyuusanq3.mixauto.R
import com.kyuusanq3.mixauto.data.map.OfflineMapRepository
import com.kyuusanq3.mixauto.data.map.MapDownloadNetworkGate
import com.kyuusanq3.mixauto.data.map.OfflineMapRepositoryHolder
import com.kyuusanq3.mixauto.ui.settings.LauncherPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps MapLibre offline region downloads alive in the background with a foreground notification.
 * Tile bytes are fetched by the SDK; this service holds [OfflineRegion.STATE_ACTIVE] and progress observers.
 */
class OfflineMapDownloadService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    private lateinit var repository: OfflineMapRepository
    private var activeRegionId: String? = null
    private var downloadJob: Job? = null
    private var notificationCollector: Job? = null

    override fun onCreate() {
        super.onCreate()
        repository = OfflineMapRepositoryHolder.instance
            ?: OfflineMapRepository(applicationContext).also {
                OfflineMapRepositoryHolder.instance = it
            }
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopActiveDownload()
                return START_NOT_STICKY
            }
        }

        // startForegroundService() requires startForeground() within ~5 s — even if we stop shortly after.
        promoteToForegroundImmediately()

        when (intent?.action) {
            ACTION_RESUME -> {
                serviceScope.launch {
                    val pending = withContext(Dispatchers.IO) {
                        repository.findPendingResumeRegion()
                    }
                    if (pending == null) {
                        stopForegroundAndSelf()
                        return@launch
                    }
                    if (!ensureNetworkAllowed(pending.regionId)) {
                        return@launch
                    }
                    val regionName = repository.regionDefinition(pending.regionId)?.name ?: pending.regionId
                    activeRegionId = pending.regionId
                    updateNotification(regionName, progressPercent = 0, preparing = true)
                    observeNotificationUpdates(pending.regionId, regionName)
                    downloadJob = serviceScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { repository.downloadRegion(pending.regionId, pending.pixelRatio) }
                        }
                        result.onFailure { error ->
                            Log.e(TAG, "Offline resume failed for ${pending.regionId}", error)
                        }
                        stopForegroundAndSelf()
                    }
                }
                return START_STICKY
            }
        }

        val regionId = intent?.getStringExtra(EXTRA_REGION_ID)
        if (regionId.isNullOrBlank()) {
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }

        serviceScope.launch {
            if (!ensureNetworkAllowed(regionId)) {
                return@launch
            }

            val pixelRatio = intent.getFloatExtra(EXTRA_PIXEL_RATIO, DEFAULT_PIXEL_RATIO)
            val regionName = repository.regionDefinition(regionId)?.name ?: regionId
            activeRegionId = regionId
            updateNotification(regionName, progressPercent = 0, preparing = true)
            observeNotificationUpdates(regionId, regionName)

            downloadJob?.cancel()
            downloadJob = serviceScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { repository.downloadRegion(regionId, pixelRatio) }
                }
                result.onFailure { error ->
                    Log.e(TAG, "Offline download failed for $regionId", error)
                }
                stopForegroundAndSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        notificationCollector?.cancel()
        downloadJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun observeNotificationUpdates(regionId: String, regionName: String) {
        notificationCollector?.cancel()
        notificationCollector = serviceScope.launch {
            repository.installStates
                .map { states -> states[regionId] }
                .distinctUntilChanged()
                .collect { state ->
                    if (state == null) return@collect
                    val preparing = state.requiredResourceCount == 0L && !state.isComplete
                    val percent = (state.downloadProgress * 100f).toInt().coerceIn(0, 100)
                    updateNotification(regionName, percent, preparing)
                }
        }
    }

    private fun stopActiveDownload() {
        val regionId = activeRegionId
        downloadJob?.cancel()
        downloadJob = null
        notificationCollector?.cancel()
        notificationCollector = null
        if (regionId != null) {
            serviceScope.launch { repository.pauseDownload(regionId) }
        }
        stopForegroundAndSelf()
    }

    private fun stopForegroundAndSelf() {
        activeRegionId = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun promoteToForegroundImmediately() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Checking offline downloads…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, 0, true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(regionName: String, progressPercent: Int, preparing: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(regionName, progressPercent, preparing))
    }

    private fun buildNotification(regionName: String, progressPercent: Int, preparing: Boolean): Notification {
        val launchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = if (preparing) {
            "Preparing $regionName…"
        } else {
            "Downloading $regionName… $progressPercent%"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(body)
            .setContentIntent(launchIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, if (preparing) 0 else progressPercent, preparing)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Offline map downloads",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Progress for regional offline map tile downloads"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private suspend fun ensureNetworkAllowed(regionId: String): Boolean {
        val allowMobileData = LauncherPreferences(applicationContext).allowMapDownloadOnMobileData
        val blockReason = MapDownloadNetworkGate.blockReason(applicationContext, allowMobileData)
        if (blockReason == null) return true
        withContext(Dispatchers.IO) {
            repository.reportRegionDownloadError(regionId, blockReason)
        }
        stopForegroundAndSelf()
        return false
    }

    companion object {
        private const val TAG = "OfflineMapDownloadService"
        private const val CHANNEL_ID = "offline_map_download"
        private const val NOTIFICATION_ID = 4102
        private const val EXTRA_REGION_ID = "region_id"
        private const val EXTRA_PIXEL_RATIO = "pixel_ratio"
        private const val DEFAULT_PIXEL_RATIO = 2f
        private const val ACTION_STOP = "com.kyuusanq3.mixauto.action.STOP_OFFLINE_MAP_DOWNLOAD"
        private const val ACTION_RESUME = "com.kyuusanq3.mixauto.action.RESUME_OFFLINE_MAP_DOWNLOAD"

        fun startDownload(context: Context, regionId: String, pixelRatio: Float) {
            val intent = Intent(context, OfflineMapDownloadService::class.java).apply {
                putExtra(EXTRA_REGION_ID, regionId)
                putExtra(EXTRA_PIXEL_RATIO, pixelRatio)
            }
            ContextCompatStartForeground(context, intent)
        }

        fun resumeIfNeeded(context: Context) {
            val intent = Intent(context, OfflineMapDownloadService::class.java).apply {
                action = ACTION_RESUME
            }
            ContextCompatStartForeground(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, OfflineMapDownloadService::class.java).apply {
                    action = ACTION_STOP
                },
            )
        }

        private fun ContextCompatStartForeground(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
