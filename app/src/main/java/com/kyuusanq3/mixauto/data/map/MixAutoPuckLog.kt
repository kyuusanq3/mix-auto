package com.kyuusanq3.mixauto.data.map

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.kyuusanq3.mixauto.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import org.maplibre.android.location.modes.CameraMode

/**
 * Puck centering vs flicker probe. Also persists a shareable text file.
 * Settings: Launcher Settings → Share Debug Logs.
 */
internal object MixAutoPuckLog {
    const val TAG = "MixAutoPuck"

    private const val DIR_NAME = "debug"
    private const val LOG_NAME = "mix-auto-puck.log"
    private const val SHARE_NAME = "mix-auto-puck-share.txt"
    private const val MAX_LOG_BYTES = 512 * 1024
    private const val KEEP_LOG_BYTES = 256 * 1024

    private val writer = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null

    fun install(context: Context) {
        val app = context.applicationContext
        appContext = app
        writer.execute { ensureLogFile(app) }
    }

    fun event(name: String, detail: String) {
        val line = name + " " + detail
        Log.i(TAG, line)
        val stamped = nowStamp() + " " + line
        val app = appContext
        if (app != null) {
            writer.execute { appendLine(app, stamped) }
        }
    }

    fun share(context: Context) {
        val app = context.applicationContext
        install(app)
        writer.execute {
            val snapshot = writeShareSnapshot(app)
            mainHandler.post { launchShare(context, snapshot) }
        }
    }

    fun cameraModeName(mode: Int): String {
        return when (mode) {
            CameraMode.NONE -> "NONE"
            CameraMode.TRACKING_GPS -> "TRACKING_GPS"
            else -> "mode=" + mode
        }
    }

    fun key(values: IntArray?): String {
        if (values == null || values.size < 4) return "null"
        return values[0].toString() + "," + values[1] + "," + values[2] + "," + values[3]
    }

    private fun debugDir(app: Context): File {
        val dir = File(app.cacheDir, DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun logFile(app: Context): File = File(debugDir(app), LOG_NAME)

    private fun ensureLogFile(app: Context) {
        val file = logFile(app)
        if (file.exists() && file.length() > 0L) return
        file.writeText(headerText())
    }

    private fun headerText(): String {
        return "Mix Auto puck debug log\n" +
            "version=" + BuildConfig.VERSION_NAME +
            " versionCode=" + BuildConfig.VERSION_CODE + "\n" +
            "started=" + nowStamp() + "\n" +
            "---\n"
    }

    private fun appendLine(app: Context, line: String) {
        val file = logFile(app)
        if (!file.exists()) {
            file.writeText(headerText())
        }
        file.appendText(line + "\n")
        rotateIfNeeded(file)
    }

    private fun rotateIfNeeded(file: File) {
        if (file.length() <= MAX_LOG_BYTES) return
        val bytes = file.readBytes()
        val keepFrom = (bytes.size - KEEP_LOG_BYTES).coerceAtLeast(0)
        var start = keepFrom
        while (start < bytes.size && bytes[start] != '\n'.code.toByte()) {
            start++
        }
        if (start < bytes.size) {
            start++
        }
        file.writeText(
            "Mix Auto puck debug log (trimmed)\n" +
                "version=" + BuildConfig.VERSION_NAME + "\n" +
                "trimmed=" + nowStamp() + "\n" +
                "---\n",
        )
        if (start < bytes.size) {
            file.appendBytes(bytes.copyOfRange(start, bytes.size))
        }
    }

    private fun writeShareSnapshot(app: Context): File {
        ensureLogFile(app)
        val snapshot = File(debugDir(app), SHARE_NAME)
        logFile(app).copyTo(snapshot, overwrite = true)
        return snapshot
    }

    private fun launchShare(context: Context, snapshot: File) {
        if (!snapshot.exists() || snapshot.length() == 0L) {
            Toast.makeText(context, "No debug logs yet", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            snapshot,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Mix Auto puck debug logs")
            putExtra(Intent.EXTRA_TEXT, "Mix Auto puck/camera probe log")
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("debug log", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val launched = runCatching {
            context.startActivity(Intent.createChooser(send, "Share Debug Logs"))
        }.isSuccess
        if (!launched) {
            Toast.makeText(context, "No app can share this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun nowStamp(): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        return format.format(Date())
    }
}
