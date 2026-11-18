package com.kyuusanq3.mixauto.data.map

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import com.kyuusanq3.mixauto.ui.components.canLaunchApp
import com.kyuusanq3.mixauto.ui.settings.DeveloperSettings

/**
 * Owns a trusted virtual display that paints onto the map-pane [Surface], launches the
 * configured Maps (or probe) package onto that display, and forwards touch events into it.
 *
 * Requires the headunit flavor (platform-signed + sharedUserId=android.uid.system) so the
 * signature permissions ADD_TRUSTED_DISPLAY / INJECT_EVENTS / … are granted. Without those,
 * [create] logs and returns false; the Compose host shows a "not available" message.
 *
 * Flag values for @hide [DisplayManager] constants (AOSP DisplayManager.java):
 *   PRESENTATION=1<<1, OWN_CONTENT_ONLY=1<<3, SUPPORTS_TOUCH=1<<6,
 *   DESTROY_CONTENT_ON_REMOVAL=1<<8, TRUSTED=1<<10.
 */
class EmbeddedMapDisplay(
    private val appContext: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var virtualDisplay: VirtualDisplay? = null
    private var surface: Surface? = null
    private var widthPx: Int = 0
    private var heightPx: Int = 0
    private var densityDpi: Int = 0

    val displayId: Int
        get() = virtualDisplay?.display?.displayId ?: android.view.Display.INVALID_DISPLAY

    val isActive: Boolean
        get() = virtualDisplay != null

    fun create(
        surface: Surface,
        widthPx: Int,
        heightPx: Int,
        densityDpi: Int,
    ): Boolean {
        release()
        if (widthPx <= 0 || heightPx <= 0) {
            Log.w(TAG, "create: invalid size ${widthPx}x$heightPx")
            return false
        }
        this.surface = surface
        this.widthPx = widthPx
        this.heightPx = heightPx
        this.densityDpi = densityDpi.coerceAtLeast(1)

        val displayManager = appContext.getSystemService(DisplayManager::class.java)
            ?: run {
                Log.e(TAG, "create: DisplayManager missing")
                return false
            }

        return try {
            val vd = displayManager.createVirtualDisplay(
                DISPLAY_NAME,
                this.widthPx,
                this.heightPx,
                this.densityDpi,
                surface,
                VIRTUAL_DISPLAY_FLAGS,
                null,
                mainHandler,
            )
            if (vd == null) {
                Log.e(TAG, "create: createVirtualDisplay returned null")
                return false
            }
            virtualDisplay = vd
            Log.i(TAG, "create: displayId=${vd.display.displayId} ${this.widthPx}x${this.heightPx}")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "create: failed — missing ADD_TRUSTED_DISPLAY / SELinux?", t)
            virtualDisplay = null
            false
        }
    }

    fun resize(widthPx: Int, heightPx: Int) {
        if (widthPx <= 0 || heightPx <= 0) return
        val vd = virtualDisplay ?: return
        if (widthPx == this.widthPx && heightPx == this.heightPx) return
        this.widthPx = widthPx
        this.heightPx = heightPx
        try {
            vd.resize(widthPx, heightPx, densityDpi)
            Log.d(TAG, "resize: ${widthPx}x$heightPx")
        } catch (t: Throwable) {
            Log.w(TAG, "resize failed", t)
        }
    }

    /**
     * Launch the target package on this virtual display. When [destinationUri] is set
     * (e.g. `google.navigation:q=lat,lng`), that intent is used instead of the launcher activity.
     */
    fun launchTarget(destinationUri: String? = null): Boolean {
        val vd = virtualDisplay ?: run {
            Log.w(TAG, "launchTarget: no virtual display")
            return false
        }
        val packageName = resolveTargetPackage()
        if (!canLaunchApp(appContext, packageName) && destinationUri == null) {
            Log.w(TAG, "launchTarget: $packageName not installed")
            return false
        }

        val intent = if (destinationUri != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse(destinationUri)).apply {
                setPackage(packageName)
            }
        } else {
            appContext.packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(Intent.ACTION_MAIN).apply {
                    setPackage(packageName)
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
        }
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
        )

        val options = ActivityOptions.makeBasic()
        try {
            options.launchDisplayId = vd.display.displayId
        } catch (t: Throwable) {
            Log.e(TAG, "launchTarget: setLaunchDisplayId failed", t)
            return false
        }

        return try {
            appContext.startActivity(intent, options.toBundle())
            Log.i(TAG, "launchTarget: $packageName on display ${vd.display.displayId}")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "launchTarget: startActivity failed", t)
            false
        }
    }

    fun navigateTo(lat: Double, lng: Double): Boolean {
        val uri = "google.navigation:q=$lat,$lng"
        return launchTarget(destinationUri = uri)
    }

    /** Forward a pane-local [MotionEvent] into the virtual display. */
    fun injectMotionEvent(event: MotionEvent): Boolean {
        val vd = virtualDisplay ?: return false
        val displayId = vd.display.displayId
        return try {
            MotionEvent::class.java
                .getMethod("setDisplayId", Int::class.javaPrimitiveType)
                .invoke(event, displayId)
            val inputManager = appContext.getSystemService(InputManager::class.java)
                ?: return false
            val inject = InputManager::class.java.getMethod(
                "injectInputEvent",
                android.view.InputEvent::class.java,
                Int::class.javaPrimitiveType,
            )
            // INJECT_INPUT_EVENT_MODE_ASYNC = 0
            inject.invoke(inputManager, event, 0) as Boolean
        } catch (t: Throwable) {
            Log.w(TAG, "injectMotionEvent failed — missing INJECT_EVENTS?", t)
            false
        }
    }

    /**
     * Tear down the virtual display. With DESTROY_CONTENT_ON_REMOVAL the Maps task on that
     * display is finished. Also best-effort removes any same-package task that escaped onto
     * the default display (MANAGE_ACTIVITY_TASKS / removeTask via reflection).
     */
    fun release() {
        val packageName = resolveTargetPackage()
        val vd = virtualDisplay
        virtualDisplay = null
        surface = null
        if (vd != null) {
            try {
                vd.release()
                Log.i(TAG, "release: virtual display released")
            } catch (t: Throwable) {
                Log.w(TAG, "release: VirtualDisplay.release failed", t)
            }
        }
        finishEscapedTasks(packageName)
    }

    private fun finishEscapedTasks(packageName: String) {
        try {
            val am = appContext.getSystemService(ActivityManager::class.java) ?: return
            @Suppress("DEPRECATION")
            val tasks = am.getRunningTasks(32) ?: return
            for (task in tasks) {
                val top = task.topActivity?.packageName
                val base = task.baseActivity?.packageName
                if (top != packageName && base != packageName) continue
                // Skip tasks still on our (now-released) display — DESTROY_CONTENT covers those.
                try {
                    val remove = ActivityManager::class.java.getMethod(
                        "removeTask",
                        Int::class.javaPrimitiveType,
                    )
                    remove.invoke(am, task.id)
                    Log.i(TAG, "finishEscapedTasks: removed task ${task.id} for $packageName")
                } catch (t: Throwable) {
                    Log.d(TAG, "finishEscapedTasks: removeTask unavailable for ${task.id}", t)
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "finishEscapedTasks: getRunningTasks denied", t)
        }
    }

    companion object {
        private const val TAG = "EmbeddedMapDisplay"
        private const val DISPLAY_NAME = "mix-auto-embedded-map"

        const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

        // @hide DisplayManager flags — see class KDoc.
        private const val FLAG_PRESENTATION = 1 shl 1
        private const val FLAG_OWN_CONTENT_ONLY = 1 shl 3
        private const val FLAG_SUPPORTS_TOUCH = 1 shl 6
        private const val FLAG_DESTROY_CONTENT_ON_REMOVAL = 1 shl 8
        private const val FLAG_TRUSTED = 1 shl 10
        private const val VIRTUAL_DISPLAY_FLAGS =
            FLAG_PRESENTATION or
                FLAG_OWN_CONTENT_ONLY or
                FLAG_SUPPORTS_TOUCH or
                FLAG_DESTROY_CONTENT_ON_REMOVAL or
                FLAG_TRUSTED

        fun resolveTargetPackage(): String {
            val override = DeveloperSettings.EMBEDDED_MAP_PACKAGE_OVERRIDE.trim()
            return override.ifEmpty { GOOGLE_MAPS_PACKAGE }
        }

        fun isTargetInstalled(context: Context): Boolean =
            canLaunchApp(context, resolveTargetPackage())
    }
}

/**
 * Process-scoped holder so Navigate / dock taps can reach the live [EmbeddedMapDisplay]
 * without threading it through every Compose prop. Only one frame is active at a time.
 */
object EmbeddedMapHost {
    @Volatile
    var active: EmbeddedMapDisplay? = null
        private set

    fun register(display: EmbeddedMapDisplay) {
        active = display
    }

    fun unregister(display: EmbeddedMapDisplay) {
        if (active === display) {
            active = null
        }
    }

    fun navigateTo(lat: Double, lng: Double): Boolean =
        active?.navigateTo(lat, lng) ?: false

    fun relaunchTarget(): Boolean =
        active?.launchTarget() ?: false
}
