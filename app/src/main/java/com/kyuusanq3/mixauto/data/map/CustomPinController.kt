package com.kyuusanq3.mixauto.data.map

import android.graphics.PointF
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import kotlin.math.hypot
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

internal fun buildCustomPinGeoJson(lat: Double, lng: Double, pending: Boolean): String {
    return JSONObject().apply {
        put("type", "FeatureCollection")
        put(
            "features",
            JSONArray().apply {
                put(
                    JSONObject().apply {
                        put("type", "Feature")
                        put(
                            "geometry",
                            JSONObject().apply {
                                put("type", "Point")
                                put(
                                    "coordinates",
                                    JSONArray().apply {
                                        put(lng)
                                        put(lat)
                                    },
                                )
                            },
                        )
                        put(
                            "properties",
                            JSONObject().apply {
                                put("pending", pending)
                            },
                        )
                    },
                )
            },
        )
    }.toString()
}

/**
 * Renders the single custom-pin (dropped pin) layer and provides the hit-testing/lookup helpers
 * used when the user taps or long-presses the map.
 *
 * Extracted from [MapLibreEngineImpl] as a stateless helper: `mapLibreMap`, camera control
 * (`focusOnPoi`/top-down entry), and the saved-places cache stay on the engine, which still owns
 * orchestration (`handleMapPointSelection`, `startCustomPinDraft`) since those flows call back
 * into camera/POI-preview logic not yet extracted.
 */
internal class CustomPinController(
    private val customPinSourceId: String,
    private val customPinLayerId: String,
    private val savedPlacesLayerId: String,
    private val poiLayerId: String,
    private val pinHitRadiusDp: Float,
    private val resolveAnchorLayerId: (Style) -> String?,
) {

    fun placeCustomPin(style: Style, geoJson: String) {
        val existing = style.getSource(customPinSourceId)
        if (existing is GeoJsonSource) {
            existing.setGeoJson(geoJson)
            return
        }
        style.addSource(GeoJsonSource(customPinSourceId, geoJson))
        val customPinLayer = SymbolLayer(customPinLayerId, customPinSourceId).withProperties(
            PropertyFactory.iconImage(customPinIconExpression()),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
            PropertyFactory.iconSize(1f),
            PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
        )
        when {
            style.getLayer(savedPlacesLayerId) != null -> {
                style.addLayerAbove(customPinLayer, savedPlacesLayerId)
            }
            style.getLayer(poiLayerId) != null -> {
                style.addLayerAbove(customPinLayer, poiLayerId)
            }
            else -> {
                val anchor = resolveAnchorLayerId(style)
                if (anchor != null) {
                    style.addLayerAbove(customPinLayer, anchor)
                } else {
                    style.addLayer(customPinLayer)
                }
            }
        }
    }

    fun clearCustomPin(style: Style, emptyGeoJson: String) {
        val existing = style.getSource(customPinSourceId)
        if (existing is GeoJsonSource) {
            existing.setGeoJson(emptyGeoJson)
        }
    }

    fun isTapNearPinIcon(
        map: MapLibreMap,
        screenPoint: PointF,
        pinLat: Double,
        pinLng: Double,
        density: Float,
    ): Boolean {
        val pinScreen = map.projection.toScreenLocation(LatLng(pinLat, pinLng))
        val distPx = hypot(
            (screenPoint.x - pinScreen.x).toDouble(),
            (screenPoint.y - pinScreen.y).toDouble(),
        ).toFloat()
        return distPx <= pinHitRadiusDp * density
    }

    fun findSavedPlaceAt(
        savedPlacesCache: List<SearchResultPlace>,
        lat: Double,
        lng: Double,
        nearbyThresholdM: Float,
    ): SearchResultPlace? {
        val tapKey = savedPlaceKey(
            SearchResultPlace(name = "", subTitle = "", latitude = lat, longitude = lng),
        )
        return savedPlacesCache.find { savedPlaceKey(it) == tapKey }
            ?: savedPlacesCache.find { saved ->
                placesWithinMeters(saved.latitude, saved.longitude, lat, lng, nearbyThresholdM)
            }
    }
}
