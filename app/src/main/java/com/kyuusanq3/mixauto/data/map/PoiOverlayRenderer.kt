package com.kyuusanq3.mixauto.data.map

import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.PropertyValue
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.math.roundToInt

/** Rounded lat/lng key used to match a place against the starred/saved-places set. */
internal fun savedPlaceKey(place: SearchResultPlace): String {
    val lat = (place.latitude * 100_000.0).roundToInt() / 100_000.0
    val lng = (place.longitude * 100_000.0).roundToInt() / 100_000.0
    return "$lat,$lng"
}

internal fun buildPoiGeoJson(
    places: List<SearchResultPlace>,
    savedPlacesKeys: Set<String>,
    forceStarred: Boolean = false,
): String {
    val features = places.map { place ->
        JSONObject().apply {
            put("type", "Feature")
            put(
                "geometry",
                JSONObject().apply {
                    put("type", "Point")
                    put(
                        "coordinates",
                        JSONArray().apply {
                            put(place.longitude)
                            put(place.latitude)
                        },
                    )
                },
            )
            put(
                "properties",
                JSONObject().apply {
                    put("name", place.name)
                    put("subtitle", place.subTitle)
                    put("lat", place.latitude)
                    put("lng", place.longitude)
                    put("category", place.category)
                    put(
                        "starred",
                        forceStarred || savedPlacesKeys.contains(savedPlaceKey(place)),
                    )
                },
            )
        }
    }
    return JSONObject().apply {
        put("type", "FeatureCollection")
        put("features", JSONArray(features))
    }.toString()
}

/**
 * Icon-only symbol properties for POI pins under a tilted driving camera.
 * Do not add [PropertyFactory.textField] here — icon+text on one [SymbolLayer] triggers
 * MapLibre #2788 color streaks / stretch artifacts under nav tilt.
 */
internal fun poiIconOnlyLayerProperties(iconImageExpression: Expression): Array<PropertyValue<*>> {
    return arrayOf(
        PropertyFactory.iconImage(iconImageExpression),
        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
        PropertyFactory.iconAllowOverlap(true),
        PropertyFactory.iconSize(1f),
        PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
        PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
    )
}

/**
 * Text-only symbol properties for POI name labels. Kept on a separate layer from icons so
 * viewport-aligned text does not streak under tilted nav camera (MapLibre #2788).
 */
internal fun poiTextOnlyLayerProperties(): Array<PropertyValue<*>> {
    return arrayOf(
        PropertyFactory.textField(Expression.get("name")),
        PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
        PropertyFactory.textSize(12f),
        PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
        PropertyFactory.textOffset(arrayOf(0f, 0.8f)),
        PropertyFactory.textColor("#E0E0E0"),
        PropertyFactory.textHaloColor("#000000"),
        PropertyFactory.textHaloWidth(1.5f),
        PropertyFactory.textHaloBlur(0.5f),
        PropertyFactory.textAllowOverlap(false),
        PropertyFactory.textOptional(true),
        PropertyFactory.textMaxWidth(9f),
        PropertyFactory.textPitchAlignment(Property.TEXT_PITCH_ALIGNMENT_VIEWPORT),
        PropertyFactory.textRotationAlignment(Property.TEXT_ROTATION_ALIGNMENT_VIEWPORT),
    )
}

internal fun customPinIconExpression(): Expression {
    return Expression.switchCase(
        Expression.eq(Expression.get("pending"), Expression.literal(true)),
        Expression.literal(PoiIconFactory.CUSTOM_PIN_PENDING_ICON_ID),
        Expression.literal(PoiIconFactory.CUSTOM_PIN_ICON_ID),
    )
}

/** Mix overlay always uses registered bitmap teardrops, not Liberty style sprites. */
internal fun mixPoiIconExpression(): Expression {
    return Expression.match(
        Expression.get("category"),
        Expression.literal("poi_icon_default"),
        Expression.stop("food", Expression.literal("poi_icon_food")),
        Expression.stop("fuel", Expression.literal("poi_icon_fuel")),
        Expression.stop("health", Expression.literal("poi_icon_health")),
        Expression.stop("accommodation", Expression.literal("poi_icon_accommodation")),
        Expression.stop("finance", Expression.literal("poi_icon_finance")),
        Expression.stop("shopping", Expression.literal("poi_icon_shopping")),
        Expression.stop("recreation", Expression.literal("poi_icon_recreation")),
    )
}

internal fun normalCategoryIconExpression(): Expression = mixPoiIconExpression()

internal fun starredCategoryIconExpression(): Expression {
    return Expression.match(
        Expression.get("category"),
        Expression.literal(PoiIconFactory.starredIconId("poi_icon_default")),
        Expression.stop("food", Expression.literal(PoiIconFactory.starredIconId("poi_icon_food"))),
        Expression.stop("fuel", Expression.literal(PoiIconFactory.starredIconId("poi_icon_fuel"))),
        Expression.stop("health", Expression.literal(PoiIconFactory.starredIconId("poi_icon_health"))),
        Expression.stop(
            "accommodation",
            Expression.literal(PoiIconFactory.starredIconId("poi_icon_accommodation")),
        ),
        Expression.stop("finance", Expression.literal(PoiIconFactory.starredIconId("poi_icon_finance"))),
        Expression.stop("shopping", Expression.literal(PoiIconFactory.starredIconId("poi_icon_shopping"))),
        Expression.stop(
            "recreation",
            Expression.literal(PoiIconFactory.starredIconId("poi_icon_recreation")),
        ),
    )
}

internal fun poiCategoryIconExpression(): Expression {
    return Expression.switchCase(
        Expression.eq(Expression.get("starred"), Expression.literal(true)),
        starredCategoryIconExpression(),
        normalCategoryIconExpression(),
    )
}

/**
 * Renders the mix POI overlay (search/nearby pins) and saved-places overlay layers onto a
 * MapLibre [Style], and computes their visibility against the native vector POI layers.
 *
 * Extracted from [MapLibreEngineImpl] as a stateless helper: `mapLibreMap`/`_uiState`/cache
 * fields stay on the engine (they are also read by unrelated search, custom-pin, and route
 * code), and are passed in explicitly on every call. Layer-anchoring against traffic/route/base
 * layers is injected as a constructor callback since it is shared with other overlays.
 */
internal class PoiOverlayRenderer(
    private val poiSourceId: String,
    private val poiLayerId: String,
    private val poiLabelLayerId: String,
    private val savedPlacesSourceId: String,
    private val savedPlacesLayerId: String,
    private val vectorPoiLayerIds: Array<String>,
    private val minPoiZoom: Double,
    private val resolveAnchorLayerId: (Style) -> String?,
) {

    fun ensureMixPoiOverlayLayers(style: Style, geoJson: String) {
        val existing = style.getSource(poiSourceId)
        if (existing is GeoJsonSource) {
            existing.setGeoJson(geoJson)
            return
        }
        style.addSource(GeoJsonSource(poiSourceId, geoJson))
        val poiLayer = SymbolLayer(poiLayerId, poiSourceId).withProperties(
            *poiIconOnlyLayerProperties(mixPoiIconExpression()),
        )
        val anchor = resolveAnchorLayerId(style)
        if (anchor != null) {
            style.addLayerAbove(poiLayer, anchor)
        } else {
            style.addLayer(poiLayer)
        }
        val labelLayer = SymbolLayer(poiLabelLayerId, poiSourceId).withProperties(
            *poiTextOnlyLayerProperties(),
        )
        style.addLayerAbove(labelLayer, poiLayerId)
    }

    fun clearMixPoiSource(style: Style, emptyGeoJson: String) {
        val existing = style.getSource(poiSourceId)
        if (existing is GeoJsonSource) {
            existing.setGeoJson(emptyGeoJson)
        }
    }

    fun updateSavedPlacesLayer(style: Style, geoJson: String, hasPlaces: Boolean) {
        val existing = style.getSource(savedPlacesSourceId)
        if (existing is GeoJsonSource) {
            existing.setGeoJson(geoJson)
        } else if (hasPlaces) {
            style.addSource(GeoJsonSource(savedPlacesSourceId, geoJson))
            val savedLayer = SymbolLayer(savedPlacesLayerId, savedPlacesSourceId).withProperties(
                *poiIconOnlyLayerProperties(poiCategoryIconExpression()),
            )
            when {
                style.getLayer(poiLayerId) != null -> {
                    style.addLayerAbove(savedLayer, poiLayerId)
                }
                else -> {
                    val anchor = resolveAnchorLayerId(style)
                    if (anchor != null) {
                        style.addLayerAbove(savedLayer, anchor)
                    } else {
                        style.addLayer(savedLayer)
                    }
                }
            }
        }
    }

    fun syncPoiLabelVisibility(style: Style, shouldShowLabels: Boolean) {
        val visibility = if (shouldShowLabels) Property.VISIBLE else Property.NONE
        style.getLayer(poiLabelLayerId)?.setProperties(PropertyFactory.visibility(visibility))
    }

    fun syncNativePoiLayerVisibility(
        style: Style,
        useVectorTiles: Boolean,
        isNavigating: Boolean,
        zoom: Double,
        mixPoiOverlayActive: Boolean,
        isInTopDownView: Boolean,
        hasSelectedPoi: Boolean,
    ) {
        if (!useVectorTiles) return
        if (isNavigating) {
            vectorPoiLayerIds.forEach { id ->
                style.getLayer(id)?.setProperties(PropertyFactory.visibility(Property.NONE))
            }
            return
        }
        val showLiberty = when {
            zoom < minPoiZoom -> true
            !mixPoiOverlayActive -> true
            isInTopDownView || hasSelectedPoi -> true
            else -> false
        }
        val visibility = if (showLiberty) Property.VISIBLE else Property.NONE
        vectorPoiLayerIds.forEach { id ->
            style.getLayer(id)?.setProperties(PropertyFactory.visibility(visibility))
        }
    }
}
