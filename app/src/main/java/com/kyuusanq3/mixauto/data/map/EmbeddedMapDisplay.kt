package com.kyuusanq3.mixauto.data.map

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import java.lang.reflect.Method
import com.kyuusanq3.mixauto.ui.components.canLaunchApp
import com.kyuusanq3.mixauto.ui.settings.DeveloperSettings

/**
 * Owns a trusted virtual display that paints onto the map-pane [Surface], launches a
 * shortcut-bar app onto that display, and forwards touch events into it.
 *
 * Requires the headunit flavor (platform-signed + sharedUserId=android.uid.system) so the
 * signature permissions ADD_TRUSTED_DISPLAY / INJECT_EVENTS / … are granted. Without those,
 * [create] logs and returns false; the Compose host shows a "not available" message.
 *
 * Flag values for @hide [DisplayManager] constants (AOSP DisplayManager.java):
 *   PRESENTATION=1<<1, OWN_CONTENT_ONLY=1<<3, SUPPORTS_TOUCH=1<<6,
 *   DESTROY_CONTENT_ON_REMOVAL=1<<8, TRUSTED=1<<10,
 *   OWN_DISPLAY_GROUP=1<<11, ALWAYS_UNLOCKED=1<<12.
 *
 * OWN_DISPLAY_GROUP puts this display in its own focus group so the embedded app does not
 * steal the default-display focus. Without it, ActivityManager treats the launch as an app
 * switch and stops the launcher about 5 seconds later (APP_SWITCH_DELAY_TIME), which destroys
 * the [Surface] and takes the embedded task down with it.
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
    private var launchedPackage: String? = null
    private var pendingWidth: Int = 0
    private var pendingHeight: Int = 0
    private val resizeRunnable = Runnable { applyPendingResize() }
    private val inputManager: InputManager? by lazy {
        appContext.getSystemService(InputManager::class.java)
    }
    private val setDisplayIdMethod: Method? by lazy {
        reflectedMethod(MotionEvent::class.java, "setDisplayId", Int::class.javaPrimitiveType)
    }
    private val injectInputEventMethod: Method? by lazy {
        reflectedMethod(
            InputManager::class.java,
            "injectInputEvent",
            android.view.InputEvent::class.java,
            Int::class.javaPrimitiveType,
        )
    }

    val displayId: Int
        get() = virtualDisplay?.display?.displayId ?: android.view.Display.INVALID_DISPLAY

    val isActive: Boolean
        get() = virtualDisplay != null

    /**
     * Bind [surface] to the virtual display, creating it on the first call.
     * A later call (surface recreated after a transient detach) reattaches and keeps the
     * embedded task — it does not [release].
     */
    fun attachSurface(
        surface: Surface,
        widthPx: Int,
        heightPx: Int,
        densityDpi: Int,
    ): Boolean {
        if (widthPx <= 0 || heightPx <= 0) {
            Log.w(TAG, "attachSurface: invalid size ${widthPx}x$heightPx")
            return false
        }
        this.surface = surface
        this.widthPx = widthPx
        this.heightPx = heightPx
        this.densityDpi = densityDpi.coerceAtLeast(1)

        val existing = virtualDisplay
        if (existing != null) {
            return try {
                existing.setSurface(surface)
                existing.resize(this.widthPx, this.heightPx, this.densityDpi)
                Log.i(TAG, "attachSurface: reattached ${this.widthPx}x${this.heightPx}")
                true
            } catch (t: Throwable) {
                Log.w(TAG, "attachSurface: reattach failed, recreating", t)
                launchedPackage = null
                releaseDisplay()
                createVirtualDisplay(surface)
            }
        }
        return createVirtualDisplay(surface)
    }

    /**
     * Drop the current [Surface] without destroying the display or the embedded task.
     * [SurfaceView] fires this when the window surface blinks; [release] is only for teardown.
     */
    fun detachSurface() {
        surface = null
        val vd = virtualDisplay ?: return
        try {
            vd.setSurface(null)
            Log.i(TAG, "detachSurface: surface cleared, display kept")
        } catch (t: Throwable) {
            Log.w(TAG, "detachSurface failed", t)
        }
    }

    fun isShowing(packageName: String): Boolean =
        launchedPackage == packageName && virtualDisplay != null

    private fun createVirtualDisplay(surface: Surface): Boolean {
        val displayManager = appContext.getSystemService(DisplayManager::class.java)
            ?: run {
                Log.e(TAG, "create: DisplayManager missing")
                return false
            }
        val vd = createWithFallback(displayManager, surface)
        if (vd == null) {
            Log.e(TAG, "create: createVirtualDisplay returned null — missing ADD_TRUSTED_DISPLAY?")
            virtualDisplay = null
            return false
        }
        virtualDisplay = vd
        return true
    }

    private fun createWithFallback(displayManager: DisplayManager, surface: Surface): VirtualDisplay? {
        val extended = virtualDisplayFlags()
        val attempts = LinkedHashSet<Int>()
        attempts.add(extended)
        if (extended != BASE_VIRTUAL_DISPLAY_FLAGS) {
            attempts.add(BASE_VIRTUAL_DISPLAY_FLAGS or FLAG_OWN_DISPLAY_GROUP)
            attempts.add(BASE_VIRTUAL_DISPLAY_FLAGS)
        }
        for (flags in attempts) {
            val vd = tryCreate(displayManager, surface, flags)
            if (vd != null) {
                Log.i(
                    TAG,
                    "create: displayId=${vd.display.displayId} flags=$flags ${widthPx}x$heightPx",
                )
                return vd
            }
        }
        return null
    }

    private fun tryCreate(
        displayManager: DisplayManager,
        surface: Surface,
        flags: Int,
    ): VirtualDisplay? {
        return try {
            displayManager.createVirtualDisplay(
                DISPLAY_NAME,
                widthPx,
                heightPx,
                densityDpi,
                surface,
                flags,
                null,
                mainHandler,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "create: flags=$flags failed — ADD_TRUSTED_DISPLAY / SELinux / unknown flag?", t)
            null
        }
    }

    fun resize(widthPx: Int, heightPx: Int) {
        if (widthPx <= 0 || heightPx <= 0) return
        if (virtualDisplay == null) return
        if (widthPx == this.widthPx && heightPx == this.heightPx) {
            pendingWidth = widthPx
            pendingHeight = heightPx
            mainHandler.removeCallbacks(resizeRunnable)
            return
        }
        if (widthPx == pendingWidth && heightPx == pendingHeight) return
        pendingWidth = widthPx
        pendingHeight = heightPx
        // Divider drags emit a new pane size every frame. One resize after the size
        // settles avoids forcing the embedded app through a configuration change per pixel.
        mainHandler.removeCallbacks(resizeRunnable)
        mainHandler.postDelayed(resizeRunnable, RESIZE_DEBOUNCE_MS)
    }

    private fun applyPendingResize() {
        val vd = virtualDisplay ?: return
        val w = pendingWidth
        val h = pendingHeight
        if (w <= 0 || h <= 0 || (w == widthPx && h == heightPx)) return
        widthPx = w
        heightPx = h
        try {
            vd.resize(w, h, densityDpi)
            Log.d(TAG, "resize: ${w}x$h")
        } catch (t: Throwable) {
            Log.w(TAG, "resize failed", t)
        }
    }

    /**
     * Launch [packageName] on this virtual display. When [destinationUri] is set
     * (e.g. `google.navigation:q=lat,lng` for Google Maps), that intent is used instead
     * of the launcher activity.
     */
    fun launchPackage(packageName: String, destinationUri: String? = null): Boolean {
        val vd = virtualDisplay ?: run {
            Log.w(TAG, "launchPackage: no virtual display")
            return false
        }
        val target = resolveEmbeddedPackage(packageName)
        if (target.isBlank()) return false
        if (!canLaunchApp(appContext, target) && destinationUri == null) {
            Log.w(TAG, "launchPackage: $target not installed")
            return false
        }

        val previous = launchedPackage
        val switchingPackage = previous != null && previous != target
        if (switchingPackage) {
            finishEscapedTasks(previous!!)
        }

        val intent = if (destinationUri != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse(destinationUri)).apply {
                setPackage(target)
            }
        } else {
            appContext.packageManager.getLaunchIntentForPackage(target)
                ?: Intent(Intent.ACTION_MAIN).apply {
                    setPackage(target)
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
            Log.e(TAG, "launchPackage: setLaunchDisplayId failed", t)
            if (switchingPackage) launchedPackage = null
            return false
        }

        return try {
            appContext.startActivity(intent, options.toBundle())
            launchedPackage = target
            Log.i(TAG, "launchPackage: $target on display ${vd.display.displayId}")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "launchPackage: startActivity failed", t)
            if (switchingPackage) launchedPackage = null
            false
        }
    }

    fun navigateTo(lat: Double, lng: Double): Boolean {
        val pkg = launchedPackage ?: return false
        if (pkg != GOOGLE_MAPS_PACKAGE) return false
        return launchPackage(pkg, destinationUri = "google.navigation:q=$lat,$lng")
    }

    /** Forward a pane-local [MotionEvent] into the virtual display. */
    fun injectMotionEvent(event: MotionEvent): Boolean {
        val vd = virtualDisplay ?: return false
        val setDisplayId = setDisplayIdMethod ?: return false
        val inject = injectInputEventMethod ?: return false
        val input = inputManager ?: return false
        return try {
            setDisplayId.invoke(event, vd.display.displayId)
            // INJECT_INPUT_EVENT_MODE_ASYNC = 0. Move events arrive every frame; the Method
            // lookups are cached so this stays off the class-lookup path.
            inject.invoke(input, event, INJECT_INPUT_EVENT_MODE_ASYNC) as Boolean
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
        val packageName = launchedPackage
        launchedPackage = null
        releaseDisplay()
        if (!packageName.isNullOrBlank()) {
            finishEscapedTasks(packageName)
        }
    }

    private fun releaseDisplay() {
        mainHandler.removeCallbacks(resizeRunnable)
        val vd = virtualDisplay
        virtualDisplay = null
        surface = null
        if (vd == null) return
        try {
            vd.release()
            Log.i(TAG, "release: virtual display released")
        } catch (t: Throwable) {
            Log.w(TAG, "release: VirtualDisplay.release failed", t)
        }
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
        private const val RESIZE_DEBOUNCE_MS = 120L
        private const val INJECT_INPUT_EVENT_MODE_ASYNC = 0

        private fun reflectedMethod(clazz: Class<*>, name: String, vararg params: Class<*>?): Method? {
            return try {
                clazz.getMethod(name, *params)
            } catch (t: Throwable) {
                Log.w(TAG, "$name unavailable", t)
                null
            }
        }

        const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

        // @hide DisplayManager flags — see class KDoc.
        private const val FLAG_PRESENTATION = 1 shl 1
        private const val FLAG_OWN_CONTENT_ONLY = 1 shl 3
        private const val FLAG_SUPPORTS_TOUCH = 1 shl 6
        private const val FLAG_DESTROY_CONTENT_ON_REMOVAL = 1 shl 8
        private const val FLAG_TRUSTED = 1 shl 10
        private const val FLAG_OWN_DISPLAY_GROUP = 1 shl 11
        private const val FLAG_ALWAYS_UNLOCKED = 1 shl 12
        private const val BASE_VIRTUAL_DISPLAY_FLAGS =
            FLAG_PRESENTATION or
                FLAG_OWN_CONTENT_ONLY or
                FLAG_SUPPORTS_TOUCH or
                FLAG_DESTROY_CONTENT_ON_REMOVAL or
                FLAG_TRUSTED

        /**
         * API 31+ display group keeps embedded-app focus off the default display so the
         * launcher is not stopped ~5s after launch. Older platforms keep the base flags.
         */
        private fun virtualDisplayFlags(): Int {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return BASE_VIRTUAL_DISPLAY_FLAGS
            return BASE_VIRTUAL_DISPLAY_FLAGS or FLAG_OWN_DISPLAY_GROUP or FLAG_ALWAYS_UNLOCKED
        }

        /** Blank override keeps [requested]. A non-blank override is the embed-probe package. */
        fun resolveEmbeddedPackage(requested: String): String {
            val override = DeveloperSettings.EMBEDDED_MAP_PACKAGE_OVERRIDE.trim()
            return override.ifEmpty { requested }
        }
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

    fun launchPackage(packageName: String): Boolean =
        active?.launchPackage(packageName) ?: false
}
