package com.kyuusanq3.mixauto.ui.components

import android.graphics.PixelFormat
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.kyuusanq3.mixauto.data.map.EmbeddedMapDisplay
import com.kyuusanq3.mixauto.data.map.EmbeddedMapHost
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.OledBlack
import kotlinx.coroutines.delay

/**
 * Map-pane host that paints a shortcut-bar app via [EmbeddedMapDisplay] onto a [SurfaceView].
 * Replaces [CarMapViewContainer] while [targetPackage] is set for this session.
 *
 * Stops the MapLibre [engine] (pause + stop) while composed so its GL context is released.
 * The map pane's [AndroidView] resumes it when this frame leaves the composition.
 * Night-mode dim is drawn above the surface and must stay non-clickable so the embedded app
 * still gets touch events forwarded by the SurfaceView.
 *
 * The surface is not clipped. A parent compositing clip fights the [SurfaceView] hole-punch
 * that [EmbeddedMapDisplay] renders into, and on this head unit that path has taken the
 * process down shortly after the embedded app starts drawing.
 */
@Composable
fun EmbeddedMapFrame(
    engine: CarMapEngine,
    targetPackage: String,
    nightModeEnabled: Boolean = false,
    nightModeAuto: Boolean = false,
    reduceTopInset: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val display = remember { EmbeddedMapDisplay(context.applicationContext) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var surfaceReady by remember { mutableStateOf(false) }

    DisposableEffect(engine) {
        EmbeddedMapHost.register(display)
        // onStop releases the MapLibre EGL context. onPause alone leaves the GL thread
        // running under the embedded app and has crashed the process once tiles arrive.
        engine.onPause()
        engine.onStop()
        onDispose {
            display.release()
            EmbeddedMapHost.unregister(display)
        }
    }

    LaunchedEffect(targetPackage, surfaceReady) {
        if (!surfaceReady) return@LaunchedEffect
        if (display.isShowing(targetPackage)) return@LaunchedEffect
        statusMessage = if (display.launchPackage(targetPackage)) {
            null
        } else {
            "Could not open this app in the map pane."
        }
    }

    Box(
        modifier = modifier
            .padding(
                start = CarDimensions.PaneGap,
                end = CarDimensions.PaneGap,
                bottom = CarDimensions.PaneGap,
                top = if (reduceTopInset) {
                    CarDimensions.StatusStripAdjacentGap
                } else {
                    CarDimensions.PaneGap
                },
            )
            .background(OledBlack),
    ) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    val view = this
                    holder.setFormat(PixelFormat.OPAQUE)
                    setZOrderOnTop(false)
                    setZOrderMediaOverlay(false)
                    holder.addCallback(object : SurfaceHolder.Callback {
                        fun bind(holder: SurfaceHolder, widthPx: Int, heightPx: Int) {
                            if (widthPx <= 0 || heightPx <= 0) return
                            val metrics = ctx.resources.displayMetrics
                            val ok = display.attachSurface(
                                surface = holder.surface,
                                widthPx = widthPx,
                                heightPx = heightPx,
                                densityDpi = metrics.densityDpi,
                            )
                            surfaceReady = ok
                            if (!ok) {
                                statusMessage =
                                    "Embedded app unavailable (platform permissions or SELinux)."
                            }
                        }

                        override fun surfaceCreated(holder: SurfaceHolder) {
                            bind(holder, view.width, view.height)
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int,
                        ) {
                            if (!display.isActive) {
                                bind(holder, width, height)
                            } else if (width > 0 && height > 0) {
                                display.resize(width, height)
                            }
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            // Keep the virtual display. Releasing here finishes the embedded
                            // task (DESTROY_CONTENT_ON_REMOVAL) on any surface blink.
                            surfaceReady = false
                            display.detachSurface()
                        }
                    })
                    setOnTouchListener { _, event ->
                        // Forward a copy so the SurfaceView's own recycling does not invalidate
                        // the event after we hand it to InputManager.
                        val copy = MotionEvent.obtain(event)
                        val injected = display.injectMotionEvent(copy)
                        copy.recycle()
                        injected
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                if (surfaceReady && view.width > 0 && view.height > 0) {
                    display.resize(view.width, view.height)
                }
            },
        )

        var isNightActive by remember {
            mutableStateOf(isNightModeActive(nightModeEnabled, nightModeAuto))
        }
        LaunchedEffect(nightModeEnabled, nightModeAuto) {
            while (true) {
                isNightActive = isNightModeActive(nightModeEnabled, nightModeAuto)
                if (!nightModeAuto) break
                val delayMs = 60_000L - (System.currentTimeMillis() % 60_000L)
                delay(delayMs.coerceAtLeast(1_000L))
            }
        }
        // Non-clickable dim — a clickable overlay would eat Maps touches.
        if (isNightActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = NIGHT_MODE_DIM_ALPHA)),
            )
        }

        statusMessage?.let { message ->
            CarBodyText(
                text = message,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(CarDimensions.PaneGap),
            )
        }
    }
}
