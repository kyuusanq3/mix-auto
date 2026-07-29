package com.kyuusanq3.mixauto.data.map

/** Stable entry point for TomTom traffic API — delegates to focused collaborators. */
object TomTomTrafficClient {
    fun verifyApiKey(apiKey: String): TomTomKeyCheckResult =
        TomTomApiKeyVerifier.verifyApiKey(apiKey)

    fun fetchNearbyIncidents(
        latitude: Double,
        longitude: Double,
        apiKey: String,
    ): List<TrafficHeadline>? =
        TomTomIncidentHeadlines.fetchNearbyIncidents(latitude, longitude, apiKey)

    fun findJamOnRoute(
        routePoints: List<Pair<Double, Double>>,
        apiKey: String,
        maxLookAheadM: Double = 10_000.0,
        corridorM: Double = 120.0,
    ): RouteTrafficJam? =
        TomTomRouteJamFinder.findJamOnRoute(routePoints, apiKey, maxLookAheadM, corridorM)
}
