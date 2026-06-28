package com.kyuusanq3.mixauto.data.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.util.Log
import com.kyuusanq3.mixauto.ui.components.launchAppByPackage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object BackgroundAudioLauncher {
    private const val TAG = "BackgroundAudioLauncher"
    private const val CONNECT_TIMEOUT_MS = 5_000L
    private const val REFOCUS_DELAY_MS = 300L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun findMediaBrowserComponent(context: Context, packageName: String): ComponentName? {
        val intent = Intent("android.media.browse.MediaBrowserService").apply {
            setPackage(packageName)
        }
        @Suppress("DEPRECATION")
        val services = context.packageManager.queryIntentServices(intent, PackageManager.MATCH_ALL)
        val serviceInfo = services.firstOrNull()?.serviceInfo ?: return null
        return ComponentName(serviceInfo.packageName, serviceInfo.name)
    }

    fun wakeInBackground(
        context: Context,
        packageName: String,
        onComplete: (success: Boolean) -> Unit,
    ) {
        val appContext = context.applicationContext
        val component = findMediaBrowserComponent(appContext, packageName)
        if (component == null) {
            Log.w(TAG, "No MediaBrowserService for $packageName")
            onComplete(false)
            return
        }

        var completed = false
        lateinit var browser: MediaBrowserCompat

        fun finish(success: Boolean) {
            if (completed) return
            completed = true
            runCatching { browser.disconnect() }
            onComplete(success)
        }

        browser = MediaBrowserCompat(
            appContext,
            component,
            object : MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() {
                    Log.i(TAG, "MediaBrowser connected for $packageName")
                    runCatching {
                        val controller = MediaControllerCompat(appContext, browser.sessionToken)
                        controller.transportControls.play()
                    }.onFailure { error ->
                        Log.w(TAG, "Play via MediaBrowser failed for $packageName", error)
                        finish(false)
                        return
                    }
                    finish(true)
                }

                override fun onConnectionFailed() {
                    Log.w(TAG, "MediaBrowser connection failed for $packageName")
                    finish(false)
                }

                override fun onConnectionSuspended() {
                    if (!completed) {
                        Log.w(TAG, "MediaBrowser connection suspended for $packageName")
                        finish(false)
                    }
                }
            },
            null,
        )

        scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (!completed) {
                Log.w(TAG, "MediaBrowser connect timeout for $packageName")
                finish(false)
            }
        }

        browser.connect()
    }

    fun wakeWithForegroundFallback(context: Context, packageName: String) {
        val appContext = context.applicationContext
        Log.i(TAG, "Waking default audio app in background: $packageName")
        wakeInBackground(appContext, packageName) { success ->
            if (success) {
                Log.i(TAG, "Background wake succeeded for $packageName")
                return@wakeInBackground
            }
            Log.i(TAG, "Background wake failed, using foreground launch + refocus for $packageName")
            launchAppByPackage(appContext, packageName)
            scope.launch {
                delay(REFOCUS_DELAY_MS)
                refocusOwnApp(appContext)
            }
        }
    }

    private fun refocusOwnApp(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_NO_ANIMATION or
                Intent.FLAG_ACTIVITY_NEW_TASK,
        )
        runCatching {
            context.startActivity(launchIntent)
            Log.i(TAG, "Refocused Mix Auto after foreground audio launch")
        }.onFailure { error ->
            Log.w(TAG, "Failed to refocus Mix Auto", error)
        }
    }
}
