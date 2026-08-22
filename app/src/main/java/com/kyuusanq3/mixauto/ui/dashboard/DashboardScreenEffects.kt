package com.kyuusanq3.mixauto.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.domain.map.MapUiState
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import com.kyuusanq3.mixauto.ui.settings.LauncherViewModel

internal fun dismissToBasePanel(musicPaneEnabled: Boolean): ActivePanel =
    if (musicPaneEnabled) ActivePanel.MEDIA else ActivePanel.HIDDEN

/**
 * Dashboard side-effects extracted from [DashboardScreen]: shared-place focus,
 * map-tap dismiss, saved-pin sync, POI return-to-search, and layout-changed tick.
 * Layout trees stay on [DashboardScreen].
 */
@Composable
internal fun DashboardScreenEffects(
    mapEngine: CarMapEngine,
    launcherViewModel: LauncherViewModel,
    activePanel: ActivePanel,
    musicPaneEnabled: Boolean,
    savedPlaces: List<SearchResultPlace>,
    mapUiState: MapUiState,
    poiReturnToSearch: Boolean,
    effectiveMapMediaRatio: Float,
    onEnsureLaunchableAppsLoaded: () -> Unit,
    onDismissAddPlacePanel: () -> Unit,
    onDismissSelectedPoi: () -> Unit,
    onDismissPanel: () -> Unit,
    pendingSharedPlace: SearchResultPlace?,
    onConsumeSharedPlace: () -> Unit,
) {
    LaunchedEffect(pendingSharedPlace) {
        pendingSharedPlace?.let { place ->
            mapEngine.focusOnPoi(place, moveCamera = true)
            onConsumeSharedPlace()
        }
    }

    LaunchedEffect(activePanel) {
        if (activePanel == ActivePanel.APP_DRAWER) {
            onEnsureLaunchableAppsLoaded()
        }
    }

    LaunchedEffect(savedPlaces) {
        mapEngine.setSavedPlaces(savedPlaces)
    }

    LaunchedEffect(activePanel) {
        launcherViewModel.isDestinationSearchOpen = activePanel == ActivePanel.SEARCH
        when (activePanel) {
            ActivePanel.ADD_PLACE -> mapEngine.setMapTapDismissHandler(onDismissAddPlacePanel)
            ActivePanel.POI_DETAIL -> mapEngine.setMapTapDismissHandler(onDismissSelectedPoi)
            ActivePanel.SEARCH,
            ActivePanel.MAP_DATA,
            ActivePanel.AUDIO_SETTINGS,
            -> mapEngine.setMapTapDismissHandler(onDismissPanel)
            else -> mapEngine.setMapTapDismissHandler(null)
        }
    }

    LaunchedEffect(mapUiState.selectedPoi) {
        if (mapUiState.selectedPoi != null) {
            launcherViewModel.setActivePanel(ActivePanel.POI_DETAIL)
        } else if (activePanel == ActivePanel.POI_DETAIL) {
            if (poiReturnToSearch) {
                launcherViewModel.clearPoiReturnToSearch()
                launcherViewModel.setActivePanel(ActivePanel.SEARCH)
            } else {
                launcherViewModel.setActivePanel(dismissToBasePanel(musicPaneEnabled))
            }
        }
    }

    LaunchedEffect(activePanel, effectiveMapMediaRatio, mapUiState.selectedPoi) {
        mapEngine.onMapHostLayoutChanged()
    }
}
