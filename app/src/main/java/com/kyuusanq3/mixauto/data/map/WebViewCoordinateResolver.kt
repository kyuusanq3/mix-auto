package com.kyuusanq3.mixauto.data.map

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Last-resort fallback for Google Maps share links that encode a place purely as a Google
 * feature ID (`!1s0x...:0x...`) with no `@lat,lng` / `!3d/!4d` anywhere in the URL or a plain
 * HTTP GET response body -- only Google's own client-side JS can resolve that ID into
 * coordinates. This loads the URL in a headless WebView (JS enabled) and watches every page-load
 * callback for Google's JS to rewrite the page URL to include `@lat,lng` or `!3d/!4d`, which it
 * does once the place resolves client-side. Must be driven from the main thread (WebView
 * requirement) -- callers should launch this from `Dispatchers.Main`.
 */
internal object WebViewCoordinateResolver {

    private const val TAG = "MixAutoShare"
    private const val TIMEOUT_MS = 9000L
    private val CAMERA_COORDINATES_REGEX = Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)""")
    private val PIN_COORDINATES_REGEX = Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)""")
    private const val POLL_INTERVAL_MS = 300L

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolveCoordinates(context: Context, url: String): Pair<Double, Double>? =
        suspendCancellableCoroutine { continuation ->
            val mainHandler = Handler(Looper.getMainLooper())
            var webView: WebView? = null
            var finished = false
            // Google's page URL often gets a rough @lat,lng map-camera-center hop before the
            // JS resolves the precise !3d/!4d pin for the actual business/POI a few hundred ms
            // later -- accepting @lat,lng immediately can land kilometers from the real place.
            // Keep it only as a last-resort fallback if the precise pin never shows up in time.
            var cameraFallback: Pair<Double, Double>? = null

            fun finish(result: Pair<Double, Double>?) {
                if (finished) return
                finished = true
                mainHandler.post {
                    webView?.stopLoading()
                    webView?.destroy()
                    webView = null
                }
                if (continuation.isActive) continuation.resume(result ?: cameraFallback)
            }

            fun tryExtract(pageUrl: String?): Boolean {
                if (pageUrl == null) return false
                PIN_COORDINATES_REGEX.find(pageUrl)?.let { match ->
                    val (lat, lng) = match.destructured
                    Log.d(TAG, "WebViewCoordinateResolver: resolved precise pin lat=$lat lng=$lng from $pageUrl")
                    finish(lat.toDouble() to lng.toDouble())
                    return true
                }
                CAMERA_COORDINATES_REGEX.find(pageUrl)?.let { match ->
                    val (lat, lng) = match.destructured
                    if (cameraFallback == null) {
                        Log.d(TAG, "WebViewCoordinateResolver: camera-center lat=$lat lng=$lng (fallback only)")
                    }
                    cameraFallback = lat.toDouble() to lng.toDouble()
                }
                return false
            }

            mainHandler.post {
                Log.d(TAG, "WebViewCoordinateResolver: loading $url")
                val view = WebView(context.applicationContext)
                webView = view
                view.settings.javaScriptEnabled = true
                view.settings.domStorageEnabled = true
                view.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val requestUrl = request?.url?.toString() ?: return false
                        // Google's mobile page tries to deep-link into the real Maps app via an
                        // intent:// URL once it resolves the place, which a plain WebView cannot
                        // load. Swallow it (stay on the current page) instead of navigating --
                        // the polling loop below reads window.location.href directly, which
                        // Google's JS updates via history.replaceState before firing this intent,
                        // so the coordinates are already available on the current page by then.
                        if (Uri.parse(requestUrl).scheme == "intent") {
                            Log.d(TAG, "WebViewCoordinateResolver: swallowed intent:// deep-link")
                            return true
                        }
                        return false
                    }

                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                        super.onPageFinished(view, pageUrl)
                        Log.d(TAG, "WebViewCoordinateResolver: onPageFinished url=$pageUrl")
                        tryExtract(pageUrl)
                    }
                }
                fun poll() {
                    if (finished) return
                    tryExtract(webView?.url)
                    if (!finished) mainHandler.postDelayed(::poll, POLL_INTERVAL_MS)
                }
                mainHandler.postDelayed(::poll, POLL_INTERVAL_MS)
                mainHandler.postDelayed({
                    Log.w(
                        TAG,
                        "WebViewCoordinateResolver: timed out after ${TIMEOUT_MS}ms, " +
                            "cameraFallback=$cameraFallback",
                    )
                    finish(null)
                }, TIMEOUT_MS)
                view.loadUrl(url)
            }

            continuation.invokeOnCancellation {
                mainHandler.post { webView?.destroy() }
            }
        }
}
