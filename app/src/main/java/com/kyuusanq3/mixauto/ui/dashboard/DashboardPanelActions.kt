package com.kyuusanq3.mixauto.ui.dashboard

import com.kyuusanq3.mixauto.domain.map.CarMapEngine
import com.kyuusanq3.mixauto.domain.map.SearchResultPlace
import com.kyuusanq3.mixauto.ui.settings.LauncherViewModel

internal data class DashboardPanelActions(
    val onTogglePanel: (ActivePanel) -> Unit,
    val onDismissPanel: () -> Unit,
    val onOpenMapData: () -> Unit,
    val onDismissAppDrawer: () -> Unit,
    val onOpenLauncherSettingsFromDrawer: () -> Unit,
    val isDestinationPanelOpen: Boolean,
    val isMapSettingsPanelOpen: Boolean,
    val onToggleSearch: () -> Unit,
    val onToggleMapSettings: () -> Unit,
    val onVoiceSearch: () -> Unit,
    val onPreviewSearchPlace: (SearchResultPlace) -> Unit,
    val onOpenAddFromLink: () -> Unit,
    val onDismissAddPlacePanel: () -> Unit,
    val onDismissSelectedPoi: () -> Unit,
    val onConfirmAddPlaceFromLink: (SearchResultPlace) -> Unit,
)

/**
 * Panel-session lambdas extracted from [DashboardScreen].
 * Layout trees and [DashboardScreenEffects] stay on the screen.
 */
internal fun dashboardPanelActions(
    mapEngine: CarMapEngine,
    launcherViewModel: LauncherViewModel,
): DashboardPanelActions {
    val activePanel = launcherViewModel.activePanel
    val musicPaneEnabled = launcherViewModel.musicPaneEnabled

    val onTogglePanel: (ActivePanel) -> Unit = { target ->
        when (target) {
            ActivePanel.MEDIA -> when (activePanel) {
                ActivePanel.MEDIA -> {
                    launcherViewModel.updateMusicPaneEnabled(false)
                    launcherViewModel.setActivePanel(ActivePanel.HIDDEN)
                }
                ActivePanel.HIDDEN -> {
                    launcherViewModel.updateMusicPaneEnabled(true)
                    launcherViewModel.setActivePanel(ActivePanel.MEDIA)
                }
                else -> {
                    launcherViewModel.updateMusicPaneEnabled(true)
                    launcherViewModel.setActivePanel(ActivePanel.MEDIA)
                }
            }
            ActivePanel.SETTINGS -> when (activePanel) {
                ActivePanel.SETTINGS -> launcherViewModel.setActivePanel(dismissToBasePanel(musicPaneEnabled))
                else -> launcherViewModel.setActivePanel(ActivePanel.SETTINGS)
            }
            ActivePanel.APP_DRAWER -> when (activePanel) {
                ActivePanel.APP_DRAWER -> launcherViewModel.setActivePanel(dismissToBasePanel(musicPaneEnabled))
                else -> launcherViewModel.setActivePanel(ActivePanel.APP_DRAWER)
            }
            ActivePanel.SEARCH,
            ActivePanel.ADD_PLACE,
            ActivePanel.POI_DETAIL,
            ActivePanel.MAP_DATA,
            ActivePanel.AUDIO_SETTINGS,
            -> Unit
            ActivePanel.HIDDEN -> {
                launcherViewModel.updateMusicPaneEnabled(true)
                launcherViewModel.setActivePanel(ActivePanel.MEDIA)
            }
        }
    }
    val onDismissPanel = {
        if (activePanel == ActivePanel.SEARCH) {
            launcherViewModel.isDestinationSearchOpen = false
            launcherViewModel.clearDestinationSearchState()
        }
        launcherViewModel.setActivePanel(dismissToBasePanel(musicPaneEnabled))
    }
    val onOpenMapData = {
        if (activePanel == ActivePanel.SEARCH) {
            launcherViewModel.isDestinationSearchOpen = false
            launcherViewModel.clearDestinationSearchState()
        }
        launcherViewModel.setActivePanel(ActivePanel.MAP_DATA)
    }
    val onPreviewSearchPlace: (SearchResultPlace) -> Unit = { place ->
        launcherViewModel.setPoiReturnToSearch(true)
        mapEngine.focusOnPoi(place)
        launcherViewModel.isDestinationSearchOpen = false
        launcherViewModel.setActivePanel(ActivePanel.POI_DETAIL)
    }

    return DashboardPanelActions(
        onTogglePanel = onTogglePanel,
        onDismissPanel = onDismissPanel,
        onOpenMapData = onOpenMapData,
        onDismissAppDrawer = { launcherViewModel.setActivePanel(dismissToBasePanel(musicPaneEnabled)) },
        onOpenLauncherSettingsFromDrawer = { launcherViewModel.setActivePanel(ActivePanel.SETTINGS) },
        isDestinationPanelOpen =
            activePanel == ActivePanel.SEARCH ||
                activePanel == ActivePanel.ADD_PLACE ||
                activePanel == ActivePanel.POI_DETAIL,
        isMapSettingsPanelOpen = activePanel == ActivePanel.MAP_DATA,
        onToggleSearch = {
            when (activePanel) {
                ActivePanel.SEARCH,
                ActivePanel.ADD_PLACE,
                -> {
                    launcherViewModel.isDestinationSearchOpen = false
                    launcherViewModel.clearDestinationSearchState()
                    launcherViewModel.clearAddPlaceLinkState()
                    launcherViewModel.setActivePanel(dismissToBasePanel(musicPaneEnabled))
                }
                ActivePanel.POI_DETAIL -> mapEngine.dismissSelectedPoi()
                else -> {
                    launcherViewModel.isDestinationSearchOpen = true
                    launcherViewModel.setActivePanel(ActivePanel.SEARCH)
                }
            }
        },
        onToggleMapSettings = {
            when (activePanel) {
                ActivePanel.MAP_DATA -> launcherViewModel.setActivePanel(dismissToBasePanel(musicPaneEnabled))
                ActivePanel.POI_DETAIL -> mapEngine.dismissSelectedPoi()
                else -> launcherViewModel.setActivePanel(ActivePanel.MAP_DATA)
            }
        },
        onVoiceSearch = {
            if (activePanel == ActivePanel.POI_DETAIL) {
                mapEngine.dismissSelectedPoi()
            }
            if (activePanel == ActivePanel.SEARCH) {
                launcherViewModel.triggerVoiceSearch()
            } else {
                launcherViewModel.setStartVoiceOnSearchOpen()
                launcherViewModel.isDestinationSearchOpen = true
                launcherViewModel.setActivePanel(ActivePanel.SEARCH)
            }
        },
        onPreviewSearchPlace = onPreviewSearchPlace,
        onOpenAddFromLink = { launcherViewModel.setActivePanel(ActivePanel.ADD_PLACE) },
        onDismissAddPlacePanel = {
            launcherViewModel.clearAddPlaceLinkState()
            launcherViewModel.setActivePanel(ActivePanel.SEARCH)
        },
        onDismissSelectedPoi = { mapEngine.dismissSelectedPoi() },
        onConfirmAddPlaceFromLink = { place ->
            launcherViewModel.clearAddPlaceLinkState()
            onPreviewSearchPlace(place)
        },
    )
}
