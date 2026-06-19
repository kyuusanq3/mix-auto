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
    fun navigateToCoordinates(lat: Double, lng: Double)
    fun retryLocationActivation()
    fun setMapStyle(useVectorTiles: Boolean)
    suspend fun searchDestination(
        query: String,
        currentLat: Double,
        currentLng: Double,
        limitDistance: Boolean = true,
    ): List<SearchResultPlace>
}
