package com.kyuusanq3.mixauto.data.map

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/** MapView create/reuse, lifecycle forwards, and style-load orchestration. */
internal class MapViewHostCoordinator(
    private val getMapView: () -> MapView?,
    private val setMapView: (MapView?) -> Unit,
    private val getMapLibreMap: () -> MapLibreMap?,
    private val setMapLibreMap: (MapLibreMap?) -> Unit,
    private val isMapReleased: () -> Boolean,
    private val setMapReleased: (Boolean) -> Unit,
    private val isMapLibreInitialized: () -> Boolean,
    private val setMapLibreInitialized: (Boolean) -> Unit,
    private val useVectorTiles: () -> Boolean,
    private val show3dBuildings: () -> Boolean,
    private val trafficEnabled: () -> Boolean,
    private val tomTomApiKey: () -> String,
    private val getPendingLocationActivation: () -> Boolean,
    private val setPendingLocationActivation: (Boolean) -> Unit,
    private val getHasSnappedCameraToGps: () -> Boolean,
    private val setHasSnappedCameraToGps: (Boolean) -> Unit,
    private val savedPlacesCache: () -> List<SearchResultPlace>,
    private val onPrepareHost: (Context) -> Unit,
    private val onCameraGestureStarted: (MapLibreMap) -> Unit,
    private val configureMapUiChrome: (MapLibreMap, Context) -> Unit,
    private val registerPoiInteractions: (MapLibreMap) -> Unit,
    private val activateLocationTracking: (MapLibreMap, Style) -> Unit,
    private val hasLocationPermission: (Context) -> Boolean,
    private val beginLocationAcquisition: (Context) -> Unit,
    private val configureDrivingTilePrefetch: (MapLibreMap, Context, Boolean) -> Unit,
    private val startFreeDrive: () -> Unit,
    private val applyAutomotiveRoadBoost: (Style, Boolean) -> Unit,
    private val applyTrafficOverlay: (Style, Boolean, String) -> Unit,
    private val apply3dBuildingVisibility: (Style, Boolean) -> Unit,
    private val updateSavedPlacesLayer: (List<SearchResultPlace>) -> Unit,
    private val handleMapLayoutChange: (MapLibreMap) -> Unit,
    private val refreshLocationOnResume: () -> Unit,
) {
    fun createMapView(context: Context): View {
        getMapView()?.let { existing ->
            setMapReleased(false)
            (existing.parent as? ViewGroup)?.removeView(existing)
            return existing
        }

        if (!isMapLibreInitialized()) {
            MapLibreAppBootstrap.ensureInitialized(context)
            setMapLibreInitialized(true)
        }

        onPrepareHost(context)

        return MapView(context).also { view ->
            setMapReleased(false)
            view.onCreate(null)
            view.onStart()
            view.onResume()
            view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                if (v.width <= 0 || v.height <= 0) return@addOnLayoutChangeListener
                getMapLibreMap()?.let { map ->
                    view.post { handleMapLayoutChange(map) }
                }
            }
            view.getMapAsync { map ->
                setMapLibreMap(map)
                configureMapUiChrome(map, context)
                map.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        onCameraGestureStarted(map)
                    }
                }
                registerPoiInteractions(map)
                applyMapStyle(map, context)
            }
            setMapView(view)
        }
    }

    fun applyMapStyle(map: MapLibreMap, context: Context) {
        val builder = if (useVectorTiles()) {
            Style.Builder().fromUri(MapStyleConstants.VECTOR_STYLE_URI)
        } else {
            Style.Builder().fromJson(MapStyleConstants.OSM_STYLE_JSON)
        }

        map.setStyle(builder) { style ->
            PoiIconFactory.createAllIcons(context).forEach { (id, bitmap) ->
                style.addImage(id, bitmap)
            }
            activateLocationTracking(map, style)
            configureDrivingTilePrefetch(map, context, useVectorTiles())
            if (getPendingLocationActivation() && hasLocationPermission(context)) {
                beginLocationAcquisition(context)
                setPendingLocationActivation(false)
            }
            setHasSnappedCameraToGps(false)
            startFreeDrive()
            applyAutomotiveRoadBoost(style, useVectorTiles())
            applyTrafficOverlay(style, trafficEnabled(), tomTomApiKey())
            apply3dBuildingVisibility(style, show3dBuildings())
            updateSavedPlacesLayer(savedPlacesCache())
        }
    }

    fun onStart() {
        getMapView()?.onStart()
    }

    fun onResume() {
        getMapView()?.onResume()
        // GPS refresh + puck-offset restore must run after native MapView resume.
        refreshLocationOnResume()
    }

    fun onPause() {
        getMapView()?.onPause()
    }

    fun onStop() {
        getMapView()?.onStop()
    }

    fun destroyMapView() {
        getMapView()?.onDestroy()
        setMapView(null)
        setMapLibreMap(null)
    }
}
