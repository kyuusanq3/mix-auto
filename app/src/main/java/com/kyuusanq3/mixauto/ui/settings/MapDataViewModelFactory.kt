package com.kyuusanq3.mixauto.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kyuusanq3.mixauto.data.map.OfflineMapRepository
import com.kyuusanq3.mixauto.data.places.LocalPlacesRepository

class MapDataViewModelFactory(
    private val application: Application,
    private val localPlacesRepository: LocalPlacesRepository,
    private val offlineMapRepository: OfflineMapRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapDataViewModel::class.java)) {
            return MapDataViewModel(
                application = application,
                localPlacesRepository = localPlacesRepository,
                offlineMapRepository = offlineMapRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
