package com.kyuusanq3.mixauto.domain.map

data class SearchResultPlace(
    val name: String,
    val subTitle: String,
    val latitude: Double,
    val longitude: Double,
    val distanceInMeters: Float = 0f,
)
