package com.kyuusanq3.mixauto.domain.map

enum class RouteProvider {
    OSRM_FASTEST,
    TOMTOM_TRAFFIC,
    OSRM_ALTERNATE,
}

data class RouteOption(
    val id: String,
    val provider: RouteProvider,
    val label: String,
    val etaMinutes: Int,
    val distanceMeters: Double,
    val subtitle: String,
    /** Lat/lng pairs for map preview and bounds. */
    val geometryPoints: List<Pair<Double, Double>>,
)
