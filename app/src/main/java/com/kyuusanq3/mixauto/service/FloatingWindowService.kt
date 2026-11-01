package com.kyuusanq3.mixauto.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.kyuusanq3.mixauto.MainActivity
import com.kyuusanq3.mixauto.R
import com.kyuusanq3.mixauto.data.floatingwindow.FloatingWindowState
import com.kyuusanq3.mixauto.data.map.TomTomTrafficClient
import com.kyuusanq3.mixauto.data.map.TrafficFlowLevel
import com.kyuusanq3.mixauto.data.map.TrafficHeadline
import com.kyuusanq3.mixauto.data.media.MediaSessionRepository
import com.kyuusanq3.mixauto.data.weather.OpenMeteoClient
import com.kyuusanq3.mixauto.data.weather.WeatherSnapshot
import com.kyuusanq3.mixauto.domain.media.MediaPlaybackState
import com.kyuusanq3.mixauto.ui.settings.LauncherPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service that paints the Mix Auto glance card as a system overlay while another
 * app (currently Google Maps, launched from the shortcut dock) is in the foreground.
 *
 * The overlay shows the current time, outside weather, nearby traffic, and the now-playing
 * track. Tapping the overlay brings [MainActivity] back to the foreground and stops the service.
 *
 * The caller must have already granted [Settings.canDrawOverlays] before starting the service;
 * the service is a no-op otherwise.
 */
class FloatingWindowService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var dataFetchJob: Job? = null

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private lateinit var preferences: LauncherPreferences

    private var timeView: TextView? = null
    private var weatherView: TextView? = null
    private var trafficView: TextView? = null
    private var trackTitleView: TextView? = null
    private var trackArtistView: TextView? = null

    private val tickRunnable = object : Runnable {
        override fun run() {
            updateClock()
            mainHandler.postDelayed(this, CLOCK_INTERVAL_MS)
        }
    }

    private val fetchRunnable = object : Runnable {
        override fun run() {
            fetchWeatherAndTraffic()
            mainHandler.postDelayed(this, DATA_REFRESH_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        preferences = LauncherPreferences(this)
        startForegroundWithNotification()
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW not granted; stopping service")
            stopSelf()
            return
        }
        showOverlay()
        observeMedia()
        mainHandler.post(tickRunnable)
        mainHandler.post(fetchRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Keep running until the activity explicitly stops us (user returned to Mix Auto).
        return START_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        dataFetchJob?.cancel()
        serviceScope.cancel()
        removeOverlay()
        super.onDestroy()
    }

    private fun startForegroundWithNotification() {
        val channelId = ensureNotificationChannel()
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, tapIntent, pendingFlags)
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Mix Auto glance")
            .setContentText("Showing a floating card while another app is in front")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun ensureNotificationChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Mix Auto floating window",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Keeps the Mix Auto glance card visible over other apps."
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
        return CHANNEL_ID
    }

    private fun showOverlay() {
        val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: run {
            stopSelf()
            return
        }
        val root = LayoutInflater.from(this).inflate(R.layout.floating_window, null) as LinearLayout
        timeView = root.findViewById(R.id.mix_auto_floating_time)
        weatherView = root.findViewById(R.id.mix_auto_floating_weather)
        trafficView = root.findViewById(R.id.mix_auto_floating_traffic)
        trackTitleView = root.findViewById(R.id.mix_auto_floating_track_title)
        trackArtistView = root.findViewById(R.id.mix_auto_floating_track_artist)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = DEFAULT_MARGIN_PX
        }

        // Simple drag: let the user reposition the card so it doesn't cover a Google Maps control.
        val touchListener = DragTouchListener(wm, params)
        root.setOnTouchListener(touchListener)
        root.setOnClickListener { returnToMixAutoAndStop() }

        try {
            wm.addView(root, params)
            overlayView = root
            windowManager = wm
            root.post { applySavedPosition(root, params, wm) }
            // Prime UI with current music state before the first fetch tick runs.
            renderMedia(MediaSessionRepository.getInstance(this).state.value)
            updateClock()
        } catch (e: Exception) {
            Log.w(TAG, "addView failed: ${e.message}")
            stopSelf()
        }
    }

    private fun applySavedPosition(
        view: View,
        params: WindowManager.LayoutParams,
        wm: WindowManager,
    ) {
        val hPercent = preferences.floatingWindowHorizontalPercent.coerceIn(0, 100)
        val vPercent = preferences.floatingWindowVerticalPercent.coerceIn(0, 100)
        val metrics = resources.displayMetrics
        val overlayWidth = view.width.coerceAtLeast(1)
        val overlayHeight = view.height.coerceAtLeast(1)
        val maxX = (metrics.widthPixels - overlayWidth).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - overlayHeight).coerceAtLeast(0)
        params.gravity = Gravity.TOP or Gravity.START
        params.x = (maxX * (hPercent / 100f)).toInt()
        params.y = (maxY * (vPercent / 100f)).toInt()
        try {
            wm.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.w(TAG, "applySavedPosition failed: ${e.message}")
        }
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        val wm = windowManager ?: return
        try {
            wm.removeViewImmediate(view)
        } catch (e: Exception) {
            Log.w(TAG, "removeView failed: ${e.message}")
        }
        overlayView = null
        windowManager = null
    }

    private fun returnToMixAutoAndStop() {
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        startActivity(launch)
        stopSelf()
    }

    private fun observeMedia() {
        serviceScope.launch {
            MediaSessionRepository.getInstance(this@FloatingWindowService).state
                .collectLatest { state -> renderMedia(state) }
        }
    }

    private fun renderMedia(state: MediaPlaybackState) {
        val title = trackTitleView ?: return
        val artist = trackArtistView ?: return
        if (state.hasActiveSession && state.title.isNotBlank()) {
            val playingPrefix = if (state.isPlaying) "♪ " else "⏸ "
            title.text = playingPrefix + state.title
            if (state.artist.isNotBlank()) {
                artist.text = state.artist
                artist.visibility = View.VISIBLE
            } else {
                artist.visibility = View.GONE
            }
        } else {
            title.text = "Nothing playing"
            artist.visibility = View.GONE
        }
    }

    private fun updateClock() {
        val view = timeView ?: return
        view.text = CLOCK_FORMATTER.format(Date())
    }

    private fun fetchWeatherAndTraffic() {
        val lat = FloatingWindowState.lastKnownLat
        val lng = FloatingWindowState.lastKnownLng
        if (lat == null || lng == null) {
            weatherView?.text = ""
            trafficView?.visibility = View.GONE
            return
        }
        val apiKey = preferences.tomTomApiKey
        val showTraffic = preferences.showTraffic
        dataFetchJob?.cancel()
        dataFetchJob = serviceScope.launch {
            val weather: WeatherSnapshot? = withContext(Dispatchers.IO) {
                runCatching { OpenMeteoClient.fetchCurrentWeather(lat, lng) }.getOrNull()
            }
            renderWeather(weather)
            if (showTraffic && apiKey.isNotBlank()) {
                val headlines: List<TrafficHeadline>? = withContext(Dispatchers.IO) {
                    runCatching {
                        TomTomTrafficClient.fetchNearbyIncidents(lat, lng, apiKey)
                    }.getOrNull()
                }
                renderTraffic(headlines)
            } else {
                trafficView?.visibility = View.GONE
            }
        }
    }

    private fun renderWeather(snapshot: WeatherSnapshot?) {
        val view = weatherView ?: return
        view.text = if (snapshot != null) {
            snapshot.symbol + "  " + snapshot.temperatureC + "°"
        } else {
            ""
        }
    }

    private fun renderTraffic(headlines: List<TrafficHeadline>?) {
        val view = trafficView ?: return
        val worst = headlines
            ?.filter { it.level != TrafficFlowLevel.CLEAR }
            ?.maxByOrNull { it.level.ordinal }
        if (worst == null) {
            view.visibility = View.GONE
            return
        }
        view.visibility = View.VISIBLE
        view.text = worst.text
        view.setTextColor(
            when (worst.level) {
                TrafficFlowLevel.HEAVY -> 0xFFEF4444.toInt()
                TrafficFlowLevel.MODERATE -> 0xFFF59E0B.toInt()
                TrafficFlowLevel.LIGHT -> 0xFF86C871.toInt()
                TrafficFlowLevel.CLEAR -> 0xFFCFCFCF.toInt()
            },
        )
    }

    /**
     * Lets the user drag the floating card to a new position. A tap (no appreciable movement)
     * falls through to [View.OnClickListener] so the card still routes back to Mix Auto.
     */
    private class DragTouchListener(
        private val wm: WindowManager,
        private val params: WindowManager.LayoutParams,
    ) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var dragging = false

        @Suppress("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    dragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!dragging &&
                        (kotlin.math.abs(dx) > DRAG_SLOP_PX || kotlin.math.abs(dy) > DRAG_SLOP_PX)
                    ) {
                        dragging = true
                    }
                    if (dragging) {
                        val metrics = v.resources.displayMetrics
                        val maxX = (metrics.widthPixels - v.width).coerceAtLeast(0)
                        val maxY = (metrics.heightPixels - v.height).coerceAtLeast(0)
                        params.x = (initialX + dx).toInt().coerceIn(0, maxX)
                        params.y = (initialY + dy).toInt().coerceIn(0, maxY)
                        try {
                            wm.updateViewLayout(v, params)
                        } catch (_: Exception) {
                            // The window may already be detaching; ignore.
                        }
                        return true
                    }
                    return false
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        dragging = false
                        return true
                    }
                    v.performClick()
                    return true
                }
            }
            return false
        }
    }

    companion object {
        private const val TAG = "FloatingWindowService"
        private const val CHANNEL_ID = "mix_auto_floating_window"
        private const val NOTIFICATION_ID = 4731
        private const val CLOCK_INTERVAL_MS = 30_000L
        private const val DATA_REFRESH_INTERVAL_MS = 5 * 60_000L
        private const val DEFAULT_MARGIN_PX = 24
        private const val DRAG_SLOP_PX = 12f
        private val CLOCK_FORMATTER = SimpleDateFormat("h:mm a", Locale.getDefault())

        /** True when the app currently has permission to draw a system overlay. */
        fun canShowOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

        /** Starts the overlay service. No-op when overlay permission has not been granted. */
        fun start(context: Context) {
            if (!canShowOverlay(context)) return
            val intent = Intent(context.applicationContext, FloatingWindowService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.applicationContext.startForegroundService(intent)
            } else {
                context.applicationContext.startService(intent)
            }
        }

        /** Stops the overlay service. Safe to call when the service is not running. */
        fun stop(context: Context) {
            val intent = Intent(context.applicationContext, FloatingWindowService::class.java)
            context.applicationContext.stopService(intent)
        }
    }
}
