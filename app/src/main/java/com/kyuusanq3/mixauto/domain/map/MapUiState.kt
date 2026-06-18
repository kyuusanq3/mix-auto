package com.kyuusanq3.mixauto.domain.map

data class MapUiState(
    val currentSpeed: Int = 0,
    val streetName: String = "Scanning Road...",
    val isNavigating: Boolean = false,
    val distanceToNextTurn: String? = null,
    val turnInstruction: String? = null,
    val isCameraDetached: Boolean = false,
)
