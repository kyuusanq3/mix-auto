package com.kyuusanq3.mixauto.domain.map

data class SearchResultPlace(
    val name: String,
    val subTitle: String,
    val latitude: Double,
    val longitude: Double,
    val distanceInMeters: Float = 0f,
    val category: String = "",
    val isDroppedPin: Boolean = false,
    /** Provenance for map-pin merge priority: overture | vector | photon | search */
    val poiSource: String = "",
    /** Overture places.confidence when known; null for Photon/vector/saved. */
    val confidence: Float? = null,
    /** True when Overture freeform address is present and not equal to city. */
    val hasStreetAddress: Boolean = false,
)
