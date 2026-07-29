package com.kyuusanq3.mixauto.data.map

import android.content.Context
import android.util.Log
import android.view.Gravity
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

/**
 * Style-level rendering concerns for the map: UI chrome placement, automotive road width
 * boosting, 3D building visibility, the TomTom traffic raster overlay, and driving tile
 * prefetch/ambient-cache setup.
 *
 * Nav-mode stretched/streaked map labels under tilted camera: fix bundled driving style symbol
 * `layout["text-pitch-alignment"]` = `"viewport"` in mix-auto-driving.json — not the layer root
 * (MapLibre ignores root-level pitch), not Compose, not mix-poi-label VIEWPORT→MAP flips in
 * [PoiOverlayRenderer] (mix labels hidden while navigating). Prefer
 * `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-tool.ps1 fix_driving_text_pitch`.
 *
 * This is a stateless helper extracted from [MapLibreEngineImpl] — it does not own any of the
 * `useVectorTiles`/`show3dBuildings`/`trafficEnabled`/`tomTomApiKey` flags (those stay on the
 * engine because they are also read by unrelated navigation/POI/search code); callers pass the
 * current values in on every call. Puck-layer-ordering operations that must stay coordinated
 * with route/POI rendering (not yet extracted) are injected as constructor callbacks rather than
 * duplicated here.
 */
internal class MapStyleController(
    private val trafficSourceId: String,
    private val trafficLayerId: String,
    private val rasterBaseLayerId: String,
    private val routeTraveledCasingLayerId: String,
    private val ensurePuckAboveOverlays: () -> Unit,
    private val addLayerBelowPuckOrAbove: (Style, Layer, String?) -> Unit,
    private val restackRouteLayersAbove: (Style, String) -> Unit,
) {

    fun configureMapUiChrome(map: MapLibreMap, context: Context) {
        val marginPx = (MAP_UI_MARGIN_DP * context.resources.displayMetrics.density).toInt()
        // Attribution info icon sits to the right of the logo; left inset = logo width + gap.
        val attributionLeftPx =
            (ATTRIBUTION_LEFT_MARGIN_DP * context.resources.displayMetrics.density).toInt()
        map.uiSettings.apply {
            setCompassGravity(Gravity.BOTTOM or Gravity.END)
            setCompassMargins(marginPx, marginPx, marginPx, marginPx)
            setLogoGravity(Gravity.BOTTOM or Gravity.START)
            setLogoMargins(marginPx, marginPx, marginPx, marginPx)
            setAttributionGravity(Gravity.BOTTOM or Gravity.START)
            setAttributionMargins(attributionLeftPx, marginPx, marginPx, marginPx)
        }
    }

    fun apply3dBuildingVisibility(style: Style, show3dBuildings: Boolean) {
        val visibility = if (show3dBuildings) Property.VISIBLE else Property.NONE
        style.layers.forEach { layer ->
            if (layer is FillExtrusionLayer) {
                layer.setProperties(PropertyFactory.visibility(visibility))
            }
        }
    }

    /** Extra width on bundled Liberty fork (~3x in JSON at nav zoom); runtime factor ramps with zoom. */
    fun applyAutomotiveRoadBoost(style: Style, useVectorTiles: Boolean) {
        if (!useVectorTiles) return
        style.layers.forEach { layer ->
            if (layer !is LineLayer) return@forEach
            val layerId = layer.id
            if (!isAutomotiveRoadLineLayer(layerId)) return@forEach
            val factor = automotiveRoadWidthFactor(layerId)
            val zoomFactor = automotiveRoadWidthZoomFactor(layerId, factor)
            val widthProp = layer.lineWidth
            when {
                widthProp.isExpression -> {
                    val expr = widthProp.expression ?: return@forEach
                    layer.setProperties(
                        PropertyFactory.lineWidth(
                            Expression.product(zoomFactor, expr),
                        ),
                    )
                }
                widthProp.isValue -> {
                    val value = widthProp.value ?: return@forEach
                    layer.setProperties(
                        PropertyFactory.lineWidth(
                            Expression.product(zoomFactor, Expression.literal(value)),
                        ),
                    )
                }
            }
        }
    }

    fun applyTrafficOverlay(style: Style, trafficEnabled: Boolean, tomTomApiKey: String) {
        if (style.getLayer(trafficLayerId) != null) {
            style.removeLayer(trafficLayerId)
        }
        if (style.getSource(trafficSourceId) != null) {
            style.removeSource(trafficSourceId)
        }

        if (!trafficEnabled || tomTomApiKey.isBlank()) {
            ensurePuckAboveOverlays()
            return
        }

        val tileUrl =
            "https://api.tomtom.com/maps/orbis/traffic/flow/raster/tile/{z}/{x}/{y}" +
                "?apiVersion=2&key=$tomTomApiKey&style=light&tileSize=256"
        val tileSet = TileSet("2.1.0", tileUrl)
        style.addSource(RasterSource(trafficSourceId, tileSet, 256))

        val trafficLayer = RasterLayer(trafficLayerId, trafficSourceId).withProperties(
            PropertyFactory.rasterOpacity(0.7f),
        )
        // Vector (Liberty) style has no rasterBaseLayerId, so a plain addLayer() call would
        // append on top of the entire stack -- including the puck. Always stay below the puck.
        addLayerBelowPuckOrAbove(style, trafficLayer, rasterBaseLayerId)
        val existingCasing = style.getLayer(routeTraveledCasingLayerId)
        if (existingCasing != null) {
            restackRouteLayersAbove(style, trafficLayerId)
        }
        ensurePuckAboveOverlays()
    }

    fun configureDrivingTilePrefetch(map: MapLibreMap, context: Context, useVectorTiles: Boolean) {
        if (!useVectorTiles) return
        map.prefetchZoomDelta = DRIVING_PREFETCH_ZOOM_DELTA
        OfflineManager.getInstance(context).setMaximumAmbientCacheSize(
            AMBIENT_CACHE_MAX_BYTES,
            object : OfflineManager.FileSourceCallback {
                override fun onSuccess() = Unit

                override fun onError(message: String) {
                    Log.w(TAG, "setMaximumAmbientCacheSize failed: $message")
                }
            },
        )
    }

    private companion object {
        private const val TAG = "MapStyleController"
        private const val MAP_UI_MARGIN_DP = 8f
        private const val ATTRIBUTION_LEFT_MARGIN_DP = 98f
        private const val DRIVING_PREFETCH_ZOOM_DELTA = 3
        private const val AMBIENT_CACHE_MAX_BYTES = 256L * 1024L * 1024L
    }
}
