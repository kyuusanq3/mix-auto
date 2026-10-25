package com.kyuusanq3.mixauto

import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.kyuusanq3.mixauto.data.map.MixAutoPuckLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Cold-start timing marks (Logcat tag [TAG], also in Share/Save Debug Logs) plus the
 * "map settled" gate that boot-time work waits on so it does not compete with style load
 * and the first GPS camera snap.
 */
object StartupTrace {
    const val TAG = "MixAutoStartup"

    /** After style load, treat the map as settled this long later even without a GPS snap. */
    private const val STYLE_SETTLE_FALLBACK_MS = 3_000L

    private val mapSettled = MutableStateFlow(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun sinceProcessStartMs(): Long =
        SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()

    fun mark(stage: String) {
        val line = "+" + sinceProcessStartMs() + "ms " + stage
        Log.i(TAG, line)
        MixAutoPuckLog.event("startup", line)
    }

    inline fun <T> measure(stage: String, block: () -> T): T {
        val start = SystemClock.elapsedRealtime()
        val result = block()
        mark(stage + " took=" + (SystemClock.elapsedRealtime() - start) + "ms")
        return result
    }

    fun onStyleLoaded() {
        mark("style loaded")
        if (mapSettled.value) return
        mainHandler.postDelayed({ markMapSettled("style+fallback") }, STYLE_SETTLE_FALLBACK_MS)
    }

    fun markMapSettled(reason: String) {
        if (mapSettled.value) return
        mapSettled.value = true
        mark("map settled ($reason)")
    }

    /** Suspends until the map settles or [timeoutMs] elapses, whichever comes first. */
    suspend fun awaitMapSettled(timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) { mapSettled.first { it } }
    }
}
