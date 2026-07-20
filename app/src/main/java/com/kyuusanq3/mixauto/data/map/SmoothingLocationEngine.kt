package com.kyuusanq3.mixauto.data.map

import android.app.PendingIntent
import android.location.Location
import android.os.Looper
import android.view.Choreographer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.engine.LocationEngine
import org.maplibre.android.location.engine.LocationEngineCallback
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.engine.LocationEngineResult

/**
 * Interpolates between GPS fixes at frame rate so MapLibre's puck/camera animator
 * can glide smoothly without high-frequency [LocationComponent.forceLocationUpdate] calls.
 */
internal class SmoothingLocationEngine(
    private val delegate: LocationEngine,
    private val shouldSmooth: () -> Boolean,
) : LocationEngine {

    private val callbackMap =
        ConcurrentHashMap<LocationEngineCallback<LocationEngineResult>, LocationEngineCallback<LocationEngineResult>>()
    private val downstreamCallbacks =
        ConcurrentHashMap.newKeySet<LocationEngineCallback<LocationEngineResult>>()
    private val choreographer = Choreographer.getInstance()
    private var frameCallbackPosted = false
    private var displayLocation: Location? = null
    private var targetLocation: Location? = null
    private var blendFrom: Location? = null
    private var blendStartMs: Long = 0L
    private var currentBlendDurationMs: Long = SMOOTHING_BLEND_DURATION_DEFAULT_MS
    private var extrapolationAnchor: Location? = null
    private var extrapolationStartMs: Long = 0L
    private var extrapolationDistanceM: Float = 0f
    private var lastDelegateFix: Location? = null
    private var lastMeasuredInterFixMs: Long = 1_000L
    private var effectiveSpeedMps: Float = 0f
    private var effectiveBearingDeg: Float = 0f
    private var lastFrameTimeNanos: Long = 0L
    private var lastEmittedLocation: Location? = null
    private var lastEmitTimeMs: Long = 0L
    private var smoothedTargetLat: Double? = null
    private var smoothedTargetLng: Double? = null
    private var lastEmittedBearing: Float? = null

    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        frameCallbackPosted = false
        val target = targetLocation
        if (target != null && shouldSmooth()) {
            val deltaS = frameDeltaSeconds(frameTimeNanos)
            val now = System.currentTimeMillis()
            val from = blendFrom
            val rawEmitted = when {
                from != null && now - blendStartMs < currentBlendDurationMs -> {
                    val t = ((now - blendStartMs).toFloat() / currentBlendDurationMs).coerceIn(0f, 1f)
                    interpolateLocation(from, target, t)
                }
                shouldExtrapolate(now) -> {
                    val maxAheadM = effectiveSpeedMps * (lastMeasuredInterFixMs / 1000f) * EXTRAPOLATION_AHEAD_FACTOR
                    extrapolationDistanceM = (extrapolationDistanceM + effectiveSpeedMps * deltaS)
                        .coerceAtMost(maxAheadM.coerceAtLeast(0f))
                    extrapolateLocation(
                        extrapolationAnchor ?: target,
                        effectiveBearingDeg,
                        extrapolationDistanceM,
                    )
                }
                else -> Location(target)
            }
            val emitted = polishEmittedLocation(rawEmitted)
            displayLocation = Location(emitted)
            emitToAll(emitted)
        }
        scheduleNextFrameIfNeeded()
    }

    fun reset() {
        targetLocation = null
        displayLocation = null
        blendFrom = null
        extrapolationAnchor = null
        extrapolationDistanceM = 0f
        lastDelegateFix = null
        lastFrameTimeNanos = 0L
        effectiveSpeedMps = 0f
        effectiveBearingDeg = 0f
        lastEmittedLocation = null
        lastEmitTimeMs = 0L
        smoothedTargetLat = null
        smoothedTargetLng = null
        lastEmittedBearing = null
        choreographer.removeFrameCallback(frameCallback)
        frameCallbackPosted = false
    }

    fun currentDisplayLocation(): Location? = displayLocation?.let { Location(it) }

    private fun frameDeltaSeconds(frameTimeNanos: Long): Float {
        val deltaS = if (lastFrameTimeNanos > 0L) {
            ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000.0).toFloat()
        } else {
            1f / 60f
        }
        lastFrameTimeNanos = frameTimeNanos
        return deltaS.coerceIn(0f, 0.1f)
    }

    private fun shouldExtrapolate(now: Long): Boolean {
        if (effectiveSpeedMps < STOPPED_SPEED_MPS) return false
        if (now - extrapolationStartMs > EXTRAPOLATION_MAX_MS) return false
        if (extrapolationDistanceM >= EXTRAPOLATION_MAX_M) return false
        return extrapolationAnchor != null
    }

    private fun estimateSpeedMps(fix: Location, prevFix: Location?): Float {
        if (fix.hasSpeed() && fix.speed >= STOPPED_SPEED_MPS) return fix.speed
        val prev = prevFix ?: return if (fix.hasSpeed()) fix.speed else 0f
        val interFixMs = (fix.time - prev.time).coerceAtLeast(1L)
        if (interFixMs > 5_000L) return if (fix.hasSpeed()) fix.speed else 0f
        val distM = prev.distanceTo(fix)
        val derivedSpeed = distM / (interFixMs / 1000f)
        return when {
            fix.hasSpeed() && fix.speed >= STOPPED_SPEED_MPS -> fix.speed
            derivedSpeed >= STOPPED_SPEED_MPS || distM >= EMIT_MIN_DIST_M -> derivedSpeed
            fix.hasSpeed() -> fix.speed
            else -> derivedSpeed
        }
    }

    private fun resolveEffectiveBearing(fix: Location, prevFix: Location?): Float {
        if (fix.hasBearing() && fix.bearing != 0f) return fix.bearing
        val prev = prevFix ?: return effectiveBearingDeg
        val distM = prev.distanceTo(fix)
        return if (distM >= EMIT_MIN_DIST_M) prev.bearingTo(fix) else effectiveBearingDeg
    }

    private fun shouldAcceptDelegateFix(fix: Location): Boolean {
        val prevTarget = targetLocation ?: return true
        val speedMps = estimateSpeedMps(fix, lastDelegateFix)
        val minDist = if (speedMps >= EMIT_FAST_SPEED_MPS) EMIT_FAST_DIST_M else EMIT_MIN_DIST_M
        val distM = prevTarget.distanceTo(fix)
        if (distM >= minDist) return true
        // Bearing-only updates while stopped cause visible puck rotation flicker.
        if (speedMps < STOPPED_SPEED_MPS) return false
        if (!fix.hasBearing() || !prevTarget.hasBearing()) return false
        val minBearing = if (speedMps >= EMIT_FAST_SPEED_MPS) {
            EMIT_FAST_BEARING_DEG
        } else {
            EMIT_MIN_BEARING_DEG
        }
        val bearingChange = kotlin.math.abs(normalizeBearingDelta(fix.bearing - prevTarget.bearing))
        return bearingChange >= minBearing
    }

    private fun blendDurationForSpeed(speedMps: Float): Long {
        val speedKmh = speedMps * 3.6f
        return when {
            speedKmh < 20f -> 500L
            speedKmh < 60f -> 700L
            else -> SMOOTHING_BLEND_DURATION_DEFAULT_MS
        }
    }

    private fun scheduleNextFrameIfNeeded() {
        if (frameCallbackPosted || downstreamCallbacks.isEmpty()) return
        if (!shouldSmooth() && targetLocation == null) return
        frameCallbackPosted = true
        choreographer.postFrameCallback(frameCallback)
    }

    private fun onDelegateFix(fix: Location) {
        if (!shouldAcceptDelegateFix(fix)) return

        val prevFix = lastDelegateFix
        val speedMps = estimateSpeedMps(fix, prevFix)
        val bearingDeg = resolveEffectiveBearing(fix, prevFix)
        if (prevFix != null) {
            val interFixMs = (fix.time - prevFix.time).coerceAtLeast(1L)
            if (interFixMs in 1..5_000L) {
                lastMeasuredInterFixMs = interFixMs
            }
        }
        lastDelegateFix = Location(fix)
        effectiveSpeedMps = speedMps
        effectiveBearingDeg = bearingDeg

        val motionFix = smoothTargetCoords(
            Location(fix).apply {
                this.speed = speedMps
                this.bearing = bearingDeg
            },
        )

        val speedBasedBlend = blendDurationForSpeed(speedMps)
        currentBlendDurationMs = min(lastMeasuredInterFixMs, speedBasedBlend).coerceAtLeast(100L)

        val previousDisplay = displayLocation
        val blendStart = resolveBlendStart(previousDisplay, motionFix)
        blendFrom = blendStart
        targetLocation = Location(motionFix)
        blendStartMs = System.currentTimeMillis()
        extrapolationAnchor = Location(motionFix)
        extrapolationStartMs = blendStartMs
        extrapolationDistanceM = 0f
        if (!shouldSmooth()) {
            val polished = polishEmittedLocation(motionFix)
            displayLocation = Location(polished)
            emitToAll(polished, force = true)
            return
        }
        scheduleNextFrameIfNeeded()
    }

    /** EMA-smooth GPS targets to reduce jitter; snap on large corrections. */
    private fun smoothTargetCoords(fix: Location): Location {
        val lat = fix.latitude
        val lng = fix.longitude
        val prevLat = smoothedTargetLat
        val prevLng = smoothedTargetLng
        if (prevLat == null || prevLng == null || effectiveSpeedMps < STOPPED_SPEED_MPS) {
            smoothedTargetLat = lat
            smoothedTargetLng = lng
            return fix
        }
        val distFromSmoothed = FloatArray(1)
        Location.distanceBetween(prevLat, prevLng, lat, lng, distFromSmoothed)
        if (distFromSmoothed[0] > TARGET_SNAP_DIST_M) {
            smoothedTargetLat = lat
            smoothedTargetLng = lng
            return fix
        }
        val alpha = TARGET_EMA_ALPHA
        smoothedTargetLat = prevLat + (lat - prevLat) * alpha
        smoothedTargetLng = prevLng + (lng - prevLng) * alpha
        return Location(fix).apply {
            latitude = smoothedTargetLat!!
            longitude = smoothedTargetLng!!
        }
    }

    /**
     * Avoid backward puck animation when extrapolation ran ahead of the next GPS fix.
     * Snap to the fix instead of blending the display backward.
     */
    private fun resolveBlendStart(previousDisplay: Location?, fix: Location): Location {
        if (previousDisplay == null) return Location(fix)
        val distM = previousDisplay.distanceTo(fix)
        if (distM <= EXTRAPOLATION_SNAP_BACK_MAX_M) return Location(previousDisplay)
        if (distM > MAX_BACKWARD_BLEND_M && isFixBehindDisplay(previousDisplay, fix)) {
            currentBlendDurationMs = BACKWARD_SNAP_BLEND_MS
            return Location(fix)
        }
        return Location(previousDisplay)
    }

    private fun isFixBehindDisplay(display: Location, fix: Location): Boolean {
        val bearingToFix = display.bearingTo(fix)
        val delta = kotlin.math.abs(normalizeBearingDelta(bearingToFix - effectiveBearingDeg))
        return delta > 90f
    }

    private fun polishEmittedLocation(location: Location): Location {
        return Location(location).apply {
            if (effectiveSpeedMps >= STOPPED_SPEED_MPS && hasBearing()) {
                bearing = smoothEmittedBearing(bearing)
            }
            if (time <= 0L) time = System.currentTimeMillis()
        }
    }

    private fun smoothEmittedBearing(bearing: Float): Float {
        val prev = lastEmittedBearing
        if (prev == null) {
            lastEmittedBearing = bearing
            return bearing
        }
        val delta = normalizeBearingDelta(bearing - prev)
        val smoothed = normalizeBearing(prev + delta * BEARING_EMIT_ALPHA)
        lastEmittedBearing = smoothed
        return smoothed
    }

    private fun shouldEmitToComponent(location: Location, force: Boolean): Boolean {
        if (force) return true
        val prev = lastEmittedLocation ?: return true
        val now = System.currentTimeMillis()
        if (now - lastEmitTimeMs < EMIT_MIN_INTERVAL_MS) return false
        val distM = prev.distanceTo(location)
        if (distM >= EMIT_FRAME_MIN_DIST_M) return true
        if (effectiveSpeedMps < STOPPED_SPEED_MPS) return false
        if (prev.hasBearing() && location.hasBearing()) {
            val bearingChange = kotlin.math.abs(normalizeBearingDelta(location.bearing - prev.bearing))
            if (bearingChange >= EMIT_FRAME_MIN_BEARING_DEG) return true
        }
        return false
    }

    private fun emitToAll(location: Location, force: Boolean = false) {
        if (!shouldEmitToComponent(location, force)) return
        lastEmittedLocation = Location(location)
        lastEmitTimeMs = System.currentTimeMillis()
        val result = LocationEngineResult.create(Location(location))
        downstreamCallbacks.forEach { callback ->
            callback.onSuccess(result)
        }
    }

    private fun interpolateLocation(from: Location, to: Location, fraction: Float): Location {
        val f = fraction.coerceIn(0f, 1f)
        return Location(to).apply {
            latitude = from.latitude + (to.latitude - from.latitude) * f
            longitude = from.longitude + (to.longitude - from.longitude) * f
            if (from.hasBearing() && to.hasBearing()) {
                val delta = normalizeBearingDelta(to.bearing - from.bearing)
                bearing = normalizeBearing(from.bearing + delta * f)
            } else if (to.hasBearing()) {
                bearing = to.bearing
            }
            speed = to.speed
            if (to.hasAccuracy()) accuracy = to.accuracy
            time = to.time
        }
    }

    private fun extrapolateLocation(anchor: Location, bearingDeg: Float, distanceM: Float): Location {
        val latMetersPerDegree = 111_320.0
        val lngMetersPerDegree = latMetersPerDegree * kotlin.math.cos(Math.toRadians(anchor.latitude))
        val bearingRad = Math.toRadians(bearingDeg.toDouble())
        val dLat = distanceM * kotlin.math.cos(bearingRad) / latMetersPerDegree
        val dLng = distanceM * kotlin.math.sin(bearingRad) / lngMetersPerDegree
        return Location(anchor).apply {
            latitude = anchor.latitude + dLat
            longitude = anchor.longitude + dLng
            bearing = bearingDeg
            speed = effectiveSpeedMps
            if (anchor.hasAccuracy()) accuracy = anchor.accuracy
            time = System.currentTimeMillis()
        }
    }

    private fun normalizeBearingDelta(delta: Float): Float {
        var d = delta % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    private fun normalizeBearing(bearing: Float): Float {
        var b = bearing % 360f
        if (b < 0f) b += 360f
        return b
    }

    private fun wrapCallback(
        callback: LocationEngineCallback<LocationEngineResult>,
    ): LocationEngineCallback<LocationEngineResult> {
        downstreamCallbacks.add(callback)
        val wrapped = object : LocationEngineCallback<LocationEngineResult> {
            override fun onSuccess(result: LocationEngineResult) {
                val raw = result.lastLocation
                if (raw == null) {
                    callback.onSuccess(result)
                    return
                }
                onDelegateFix(raw)
            }

            override fun onFailure(exception: Exception) {
                callback.onFailure(exception)
            }
        }
        callbackMap[callback] = wrapped
        return wrapped
    }

    override fun getLastLocation(callback: LocationEngineCallback<LocationEngineResult>) {
        delegate.getLastLocation(wrapCallback(callback))
    }

    override fun requestLocationUpdates(
        request: LocationEngineRequest,
        callback: LocationEngineCallback<LocationEngineResult>,
        looper: Looper?,
    ) {
        delegate.requestLocationUpdates(request, wrapCallback(callback), looper)
    }

    override fun requestLocationUpdates(
        request: LocationEngineRequest,
        pendingIntent: PendingIntent,
    ) {
        delegate.requestLocationUpdates(request, pendingIntent)
    }

    override fun removeLocationUpdates(callback: LocationEngineCallback<LocationEngineResult>) {
        downstreamCallbacks.remove(callback)
        val wrapped = callbackMap.remove(callback)
        if (wrapped != null) {
            delegate.removeLocationUpdates(wrapped)
        }
        if (downstreamCallbacks.isEmpty()) {
            reset()
        }
    }

    override fun removeLocationUpdates(pendingIntent: PendingIntent) {
        delegate.removeLocationUpdates(pendingIntent)
    }

    companion object {
        private const val SMOOTHING_BLEND_DURATION_DEFAULT_MS = 900L
        private const val STOPPED_SPEED_MPS = 1.4f
        private const val EXTRAPOLATION_MAX_MS = 2_500L
        private const val EXTRAPOLATION_MAX_M = 50f
        private const val EXTRAPOLATION_SNAP_BACK_MAX_M = 8f
        private const val EMIT_MIN_DIST_M = 2f
        private const val EMIT_MIN_BEARING_DEG = 4f
        private const val EMIT_FAST_SPEED_MPS = 8f
        private const val EMIT_FAST_DIST_M = 1.0f
        private const val EMIT_FAST_BEARING_DEG = 2f
        private const val EMIT_MIN_INTERVAL_MS = 33L
        private const val EMIT_FRAME_MIN_DIST_M = 0.3f
        private const val EMIT_FRAME_MIN_BEARING_DEG = 1.5f
        private const val TARGET_EMA_ALPHA = 0.45f
        private const val TARGET_SNAP_DIST_M = 25f
        private const val MAX_BACKWARD_BLEND_M = 3f
        private const val BACKWARD_SNAP_BLEND_MS = 80L
        private const val BEARING_EMIT_ALPHA = 0.3f
        private const val EXTRAPOLATION_AHEAD_FACTOR = 0.85f
    }
}
