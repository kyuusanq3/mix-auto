package com.kyuusanq3.mixauto.data.map

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import com.kyuusanq3.mixauto.ui.components.canLaunchApp
import kotlin.math.abs

object FreeformMapManager {

    const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

    private var lastLaunchBounds: Rect? = null

    fun isGoogleMapsInstalled(context: Context): Boolean =
        canLaunchApp(context, GOOGLE_MAPS_PACKAGE)

    fun isFreeformSupported(packageManager: PackageManager): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
    }

    fun boundsChangedEnough(
        previous: Rect?,
        current: Rect,
        thresholdPx: Int = 10,
    ): Boolean {
        if (previous == null) return true
        return abs(previous.left - current.left) > thresholdPx ||
            abs(previous.top - current.top) > thresholdPx ||
            abs(previous.right - current.right) > thresholdPx ||
            abs(previous.bottom - current.bottom) > thresholdPx
    }

    fun shouldRelaunch(bounds: Rect, destinationUri: String?): Boolean {
        if (destinationUri != null) return true
        return boundsChangedEnough(lastLaunchBounds, bounds)
    }

    fun clearLastLaunchBounds() {
        lastLaunchBounds = null
    }

    fun launchGoogleMaps(
        context: Context,
        bounds: Rect,
        destinationUri: String? = null,
        useFreeform: Boolean = true,
    ): Boolean {
        if (!isGoogleMapsInstalled(context)) return false

        val intent = if (destinationUri != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse(destinationUri)).apply {
                setPackage(GOOGLE_MAPS_PACKAGE)
            }
        } else {
            context.packageManager.getLaunchIntentForPackage(GOOGLE_MAPS_PACKAGE)
                ?: Intent(Intent.ACTION_MAIN).apply {
                    setPackage(GOOGLE_MAPS_PACKAGE)
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)

        val options = ActivityOptions.makeBasic()
        if (useFreeform && bounds.width() > 0 && bounds.height() > 0) {
            options.launchBounds = bounds
        }

        return runCatching {
            context.startActivity(intent, options.toBundle())
            lastLaunchBounds = Rect(bounds)
            true
        }.getOrDefault(false)
    }

    fun dismissGoogleMaps(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { context.startActivity(intent) }
    }
}
