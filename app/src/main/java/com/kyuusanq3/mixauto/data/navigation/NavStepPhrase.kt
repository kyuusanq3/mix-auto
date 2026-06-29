package com.kyuusanq3.mixauto.data.navigation

data class NavStepPhrase(
    val instruction: String,
    val shortInstruction: String,
    val streetName: String,
    val distanceMeters: Double,
    val maneuverType: String,
)

data class NavTickContext(
    val currentStepIndex: Int,
    val steps: List<NavStepPhrase>,
    val distToNextManeuverM: Float,
    val speedMps: Float,
    val isRouteOverviewActive: Boolean,
    val isRerouteInProgress: Boolean = false,
)
