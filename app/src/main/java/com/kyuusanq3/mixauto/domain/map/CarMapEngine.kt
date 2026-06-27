package com.kyuusanq3.mixauto.domain.map

import android.content.Context
import android.view.View
import kotlinx.coroutines.flow.StateFlow

interface CarMapEngine {
    val uiState: StateFlow<MapUiState>

    fun createMapView(context: Context): View

    fun onStart()
    fun onResume()
    fun onPause()
    fun onStop()
    fun onDestroy()

    fun startFreeDrive()
    fun recenterCamera()
    fun dismissSelectedPoi()
    fun focusOnLocation(lat: Double, lng: Double)
    fun focusOnPoi(place: SearchResultPlace, moveCamera: Boolean = true)
    fun enterTopDownView()
    fun navigateToCoordinates(lat: Double, lng: Double)
    fun retryLocationActivation()
    fun setMapStyle(useVectorTiles: Boolean)
    fun setShow3dBuildings(show: Boolean)
    fun setTrafficEnabled(enabled: Boolean, apiKey: String)
    fun setDrivingZoom(zoom: Double)
    fun setViewportPadding(horizontalFraction: Float, verticalFraction: Float)
    fun setPuckScale(scale: Float)
    suspend fun searchDestination(
        query: String,
        currentLat: Double,
        currentLng: Double,
        limitDistance: Boolean = true,
        onLocalResults: suspend (List<SearchResultPlace>) -> Unit = {},
    ): List<SearchResultPlace>

    fun getNearbyPois(lat: Double, lng: Double, limit: Int): List<SearchResultPlace>

    /** GPS when available, else last fix, else map camera center (zoom ≥ 10). */
    fun resolveSearchOrigin(): Pair<Double, Double>

    /** True when origin comes from GPS, last fix, or map camera — not the static country fallback. */
    fun hasReliableSearchOrigin(): Boolean

    /** Re-read system location and sync into search origin before querying. */
    fun refreshSearchOrigin()

    fun setSavedPlaces(places: List<SearchResultPlace>)

    /** Compose/host resized the map pane — engine should re-apply preview camera if needed. */
    fun onMapHostLayoutChanged() {}

    /** When set, a map tap invokes the handler and skips POI/pin selection. */
    fun setMapTapDismissHandler(handler: (() -> Unit)?) {}
}
