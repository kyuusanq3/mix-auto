package com.kyuusanq3.mixauto.data.map

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.os.Process
import com.kyuusanq3.mixauto.ui.settings.DeveloperSettings
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Best-effort capture of system lines about process death / Android Auto.
 * Regular apps often cannot read other apps' private logs; ActivityManager
 * "has died" / FATAL lines are what we can usually see on AOSP head units.
 */
internal object DebugLogcatCapture {
    private const val DIR_NAME = "debug"
    private const val LOG_NAME = "mix-auto-system.log"
    private const val MAX_BYTES = 256 * 1024
    private const val KEEP_BYTES = 128 * 1024
    private const val DUMP_LINE_LIMIT = 600

    /** Live watch starts after cold-start work; the backlog is dumped at share/save time instead. */
    private const val WATCH_START_DELAY_MS = 20_000L

    private val started = AtomicBoolean(false)

    fun start(app: Context) {
        if (!DeveloperSettings.CAPTURE_SYSTEM_LOGCAT) return
        if (!started.compareAndSet(false, true)) return
        val ctx = app.applicationContext
        thread(name = "mixauto-logcat", isDaemon = true) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            Thread.sleep(WATCH_START_DELAY_MS)
            appendLine(ctx, "watch started")
            runWatch(ctx)
        }
    }

    fun hostState(activity: Activity): String {
        val uiMask = activity.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        val uiName = when (uiMask) {
            Configuration.UI_MODE_TYPE_CAR -> "CAR"
            Configuration.UI_MODE_TYPE_NORMAL -> "NORMAL"
            else -> "type=" + uiMask
        }
        return "configChange=" + activity.isChangingConfigurations +
            " uiMode=" + uiName +
            " aa=" + runningAutoSummary(activity)
    }

    fun appendToSnapshot(app: Context, snapshot: File) {
        start(app)
        snapshot.appendText("\n--- system crash/AA (watched) ---\n")
        val watched = systemLogFile(app)
        if (watched.exists() && watched.length() > 0L) {
            snapshot.appendBytes(watched.readBytes())
        } else {
            snapshot.appendText("(empty)\n")
        }
        snapshot.appendText("\n--- system crash/AA (logcat dump) ---\n")
        snapshot.appendText(dumpLogcatOnce())
    }

    private fun runningAutoSummary(activity: Activity): String {
        val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return "unknown"
        val procs = runCatching { am.runningAppProcesses }.getOrNull().orEmpty()
        val hits = procs.filter { isAutoRelated(it.processName) }
            .joinToString(",") { proc -> proc.processName + ":" + proc.importance }
        return hits.ifBlank { "none" }
    }

    private fun runWatch(app: Context) {
        val process = runCatching {
            ProcessBuilder("logcat", "-v", "threadtime", "-T", "1")
                .redirectErrorStream(true)
                .start()
        }.getOrElse { error ->
            appendLine(app, "watch failed: " + error.javaClass.simpleName + " " + error.message)
            return
        }
        runCatching {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (isInteresting(line)) {
                        appendLine(app, line)
                    }
                }
            }
        }.onFailure { error ->
            appendLine(app, "watch ended: " + error.javaClass.simpleName + " " + error.message)
        }
        runCatching { process.destroy() }
        started.set(false)
    }

    private fun dumpLogcatOnce(): String {
        return "buffer=main\n" +
            dumpLogcat(listOf("logcat", "-d", "-v", "threadtime", "-t", DUMP_LINE_LIMIT.toString())) +
            "buffer=crash\n" +
            dumpLogcat(listOf("logcat", "-b", "crash", "-d", "-v", "threadtime"))
    }

    private fun dumpLogcat(args: List<String>): String {
        val process = runCatching {
            ProcessBuilder(args).redirectErrorStream(true).start()
        }.getOrElse { error ->
            return "logcat dump failed: " + error.javaClass.simpleName + " " + error.message + "\n"
        }
        val kept = ArrayList<String>(64)
        var total = 0
        runCatching {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    total++
                    if (isInteresting(line)) {
                        kept.add(line)
                    }
                }
            }
        }
        runCatching { process.destroy() }
        if (kept.isEmpty()) {
            return "filtered=0 scanned=" + total +
                " (no crash/AA lines visible; unit may hide other-app logcat)\n"
        }
        return "filtered=" + kept.size + " scanned=" + total + "\n" +
            kept.joinToString("\n") + "\n"
    }

    private fun isInteresting(line: String): Boolean {
        return AUTO_NEEDLES.any { line.contains(it, ignoreCase = true) } ||
            CRASH_NEEDLES.any { line.contains(it, ignoreCase = true) }
    }

    private fun isAutoRelated(processName: String): Boolean {
        val lower = processName.lowercase(Locale.US)
        return AUTO_NEEDLES.any { lower.contains(it) }
    }

    private fun systemLogFile(app: Context): File {
        val dir = File(app.cacheDir, DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, LOG_NAME)
    }

    private fun appendLine(app: Context, line: String) {
        val file = systemLogFile(app)
        if (!file.exists()) {
            file.writeText("Mix Auto system crash/AA watch\n---\n")
        }
        file.appendText(line + "\n")
        rotateIfNeeded(file)
    }

    private fun rotateIfNeeded(file: File) {
        if (file.length() <= MAX_BYTES) return
        val bytes = file.readBytes()
        val keepFrom = (bytes.size - KEEP_BYTES).coerceAtLeast(0)
        var start = keepFrom
        while (start < bytes.size && bytes[start] != '\n'.code.toByte()) {
            start++
        }
        if (start < bytes.size) {
            start++
        }
        file.writeText("Mix Auto system crash/AA watch (trimmed)\n---\n")
        if (start < bytes.size) {
            file.appendBytes(bytes.copyOfRange(start, bytes.size))
        }
    }

    private val AUTO_NEEDLES = listOf(
        "gearhead",
        "android.auto",
        "androidauto",
        "gms.car",
        "aamirror",
        "embedded.projection",
    )

    private val CRASH_NEEDLES = listOf(
        "fatal exception",
        "androidruntime",
        " has died",
        "force finishing",
        "am_crash",
        "am_proc_died",
        "am_anr",
        "win death",
    )
}
