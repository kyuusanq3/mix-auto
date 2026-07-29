package com.kyuusanq3.mixauto.data.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.kyuusanq3.mixauto.BuildConfig
import com.kyuusanq3.mixauto.domain.map.MapUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.engine.LocationEngine

/** GPS permission, listener registration, polling, and last-known reads. */
internal class LocationAcquisitionHelper(
    private val engineScope: CoroutineScope,
    private val appContext: () -> Context?,
    private val updateUiState: ((MapUiState) -> MapUiState) -> Unit,
    private val hasSnappedCameraToGps: () -> Boolean,
    private val lastKnownLocation: () -> LatLng?,
    private val rawLocationEngine: () -> LocationEngine?,
    private val onLocationFix: (Location, snapCamera: Boolean) -> Unit,
    private val onSystemLocation: (LatLng, snapCamera: Boolean) -> Unit,
    private val flushPendingLocationFix: () -> Unit,
) {
    private var freshLocationListener: LocationListener? = null
    private var locationPollJob: Job? = null
    private var locationRetryJob: Job? = null

    fun beginLocationAcquisition(context: Context) {
        if (!hasLocationPermission(context)) {
            Log.w(TAG, "beginLocationAcquisition skipped: permission not granted")
            return
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        Log.i(
            TAG,
            "beginLocationAcquisition: providers=${locationManager?.allProviders}, " +
                "enabled=${isLocationEnabled(context)}",
        )
        logPermissionState(context)

        refreshLocationFromSystem(context)
        ensureLocationListeners(context)
        startLocationPolling(context)
    }

    fun refreshLocationOnly(context: Context) {
        if (!hasLocationPermission(context)) return
        refreshLocationFromSystem(context)
        flushPendingLocationFix()
    }

    fun scheduleLocationRetries(context: Context) {
        locationRetryJob?.cancel()
        locationRetryJob = engineScope.launch {
            for (delayMs in LOCATION_RETRY_DELAYS_MS) {
                delay(delayMs)
                if (hasSnappedCameraToGps() && lastKnownLocation() != null) return@launch
                Log.i(TAG, "Scheduled location retry after ${delayMs}ms")
                refreshLocationOnly(context)
            }
        }
    }

    fun readLastKnownLocation(context: Context): LatLng? {
        if (!hasLocationPermission(context)) {
            return null
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val bestLocation = locationManager.allProviders
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }

        return bestLocation?.let { LatLng(it.latitude, it.longitude) }
    }

    fun hasLocationPermission(context: Context): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    fun removeFreshLocationListener() {
        val context = appContext() ?: return
        val listener = freshLocationListener ?: return
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return
        runCatching { locationManager.removeUpdates(listener) }
        freshLocationListener = null
    }

    fun cancelJobs() {
        locationPollJob?.cancel()
        locationPollJob = null
        locationRetryJob?.cancel()
        locationRetryJob = null
    }

    private fun refreshLocationFromSystem(context: Context): LatLng? {
        val location = readLastKnownLocation(context) ?: return null
        onSystemLocation(location, !hasSnappedCameraToGps())
        return location
    }

    private fun ensureLocationListeners(context: Context) {
        if (freshLocationListener != null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "GPS location listener already active; skipping re-register")
            }
            return
        }
        ensureGpsLocationListener(context)
    }

    private fun ensureGpsLocationListener(context: Context) {
        if (freshLocationListener != null) return
        if (rawLocationEngine() != null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Skipping direct GPS listener; using LocationEngine")
            }
            return
        }
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return

        val listener = LocationListener { location ->
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "GPS update: ${location.latitude}, ${location.longitude} from ${location.provider}",
                )
            }
            onLocationFix(location, !hasSnappedCameraToGps())
        }
        freshLocationListener = listener

        runCatching {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                FRESH_LOCATION_MIN_TIME_MS,
                0f,
                listener,
                Looper.getMainLooper(),
            )
            Log.i(TAG, "GPS location listener registered")
        }.onFailure { error ->
            Log.w(TAG, "Failed to register GPS listener: ${error.message}")
            freshLocationListener = null
        }
    }

    private fun startLocationPolling(context: Context) {
        if (locationPollJob?.isActive == true) return
        locationPollJob?.cancel()
        locationPollJob = engineScope.launch {
            repeat(LOCATION_POLL_ATTEMPTS) {
                readLastKnownLocation(context)?.let { latLng ->
                    onSystemLocation(latLng, !hasSnappedCameraToGps())
                    if (hasSnappedCameraToGps()) return@launch
                }
                delay(LOCATION_POLL_INTERVAL_MS)
            }
            if (lastKnownLocation() == null) {
                updateUiState { state ->
                    if (state.streetName == "Map ready" || state.streetName == "Locating..." ||
                        state.streetName == "Scanning Road..." || state.streetName == "Free Drive"
                    ) {
                        state.copy(streetName = "Zoom map to your area (no GPS fix)")
                    } else {
                        state
                    }
                }
            }
        }
    }

    private fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun logPermissionState(context: Context) {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        Log.i(TAG, "Location permission: fine=$fineGranted coarse=$coarseGranted")
    }

    companion object {
        private const val TAG = "LocationAcquisitionHelper"
        private const val FRESH_LOCATION_MIN_TIME_MS = 500L
        private const val LOCATION_POLL_INTERVAL_MS = 1000L
        private const val LOCATION_POLL_ATTEMPTS = 15
        private val LOCATION_RETRY_DELAYS_MS = longArrayOf(1_000L, 3_000L, 8_000L)
    }
}
