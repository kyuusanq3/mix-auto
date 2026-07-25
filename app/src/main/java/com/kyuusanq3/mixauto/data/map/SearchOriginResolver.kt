package com.kyuusanq3.mixauto.data.map

import android.content.Context
import android.util.Log
import com.kyuusanq3.mixauto.domain.map.MapUiState
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.abs

internal val SEARCH_DEFAULT_LOCATION = LatLng(12.8797, 121.7740)
internal const val SEARCH_ROUTING_MIN_ZOOM = 10.0

private const val TAG = "MapLibreEngineImpl"

/**
 * Resolves destination-search and nearby-search origin coordinates from GPS, cached fix,
 * map camera, or Philippines fallback.
 */
internal class SearchOriginResolver(
    private val uiState: () -> MapUiState,
    private val updateUiState: ((MapUiState) -> MapUiState) -> Unit,
    private val lastKnownLocation: () -> LatLng?,
    private val setLastKnownLocation: (LatLng?) -> Unit,
    private val mapLibreMap: () -> MapLibreMap?,
    private val appContext: () -> Context?,
    private val hasLocationPermission: (Context) -> Boolean,
    private val readLastKnownLocation: (Context) -> LatLng?,
    private val refreshLocationOnly: (Context) -> Unit,
) {
    fun resolveSearchOrigin(): Pair<Double, Double> = resolveSearchOrigin(0.0, 0.0)

    fun resolveSearchOrigin(fallbackLat: Double, fallbackLng: Double): Pair<Double, Double> {
        val state = uiState()
        if (state.currentLat != null && state.currentLng != null &&
            isValidSearchOrigin(state.currentLat, state.currentLng)
        ) {
            return state.currentLat to state.currentLng
        }
        lastKnownLocation()?.let { loc ->
            if (isValidSearchOrigin(loc.latitude, loc.longitude)) {
                return loc.latitude to loc.longitude
            }
        }
        resolveMapViewOriginForSearch()?.let { target ->
            return target.latitude to target.longitude
        }
        if (isValidSearchOrigin(fallbackLat, fallbackLng)) {
            return fallbackLat to fallbackLng
        }
        appContext()?.let { ctx ->
            if (hasLocationPermission(ctx)) {
                readLastKnownLocation(ctx)?.let { latLng ->
                    if (isValidSearchOrigin(latLng.latitude, latLng.longitude)) {
                        setLastKnownLocation(latLng)
                        syncSearchOriginToUiState(latLng.latitude, latLng.longitude)
                        return latLng.latitude to latLng.longitude
                    }
                }
            }
        }
        Log.w(TAG, "Search origin unresolved; using Philippines fallback")
        return SEARCH_DEFAULT_LOCATION.latitude to SEARCH_DEFAULT_LOCATION.longitude
    }

    fun hasReliableSearchOrigin(): Boolean {
        val state = uiState()
        if (state.currentLat != null && state.currentLng != null &&
            isValidSearchOrigin(state.currentLat, state.currentLng)
        ) {
            return true
        }
        lastKnownLocation()?.let { loc ->
            if (isValidSearchOrigin(loc.latitude, loc.longitude)) return true
        }
        resolveMapViewOriginForSearch()?.let { return true }
        return false
    }

    fun refreshSearchOrigin() {
        val ctx = appContext() ?: return
        if (!hasLocationPermission(ctx)) return
        refreshLocationOnly(ctx)
        readLastKnownLocation(ctx)?.let { latLng ->
            setLastKnownLocation(latLng)
            syncSearchOriginToUiState(latLng.latitude, latLng.longitude)
        }
    }

    fun isValidSearchOrigin(lat: Double, lng: Double): Boolean {
        if (lat == 0.0 && lng == 0.0) return false
        if (abs(lat) < 0.01 && abs(lng) < 0.01) return false
        return true
    }

    fun syncSearchOriginToUiState(lat: Double, lng: Double) {
        updateUiState { state ->
            if (state.currentLat == lat && state.currentLng == lng) {
                state
            } else {
                state.copy(currentLat = lat, currentLng = lng)
            }
        }
    }

    fun resolveMapViewOriginForSearch(): LatLng? {
        val map = mapLibreMap() ?: return null
        val position = map.cameraPosition
        val target = position.target ?: return null
        if (!uiState().isNavigating && position.zoom < SEARCH_ROUTING_MIN_ZOOM) {
            return null
        }
        if (!isValidSearchOrigin(target.latitude, target.longitude)) {
            return null
        }
        return target
    }
}
