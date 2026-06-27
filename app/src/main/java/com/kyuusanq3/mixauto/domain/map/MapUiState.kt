package com.kyuusanq3.mixauto.domain.map

data class MapUiState(
    val currentSpeed: Int = 0,
    val streetName: String = "Scanning Road...",
    val isNavigating: Boolean = false,
    val distanceToNextTurn: String? = null,
    val turnInstruction: String? = null,
    val isCameraDetached: Boolean = false,
    val isInTopDownView: Boolean = false,
    val currentLat: Double? = null,
    val currentLng: Double? = null,
    val routeOverviewProgress: Float = 0f,
    val selectedPoi: SearchResultPlace? = null,
    val nearbyPois: List<SearchResultPlace> = emptyList(),
    val routeOptions: List<RouteOption> = emptyList(),
    val selectedRouteId: String? = null,
    val isRouteSelecting: Boolean = false,
)
