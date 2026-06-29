package com.kyuusanq3.mixauto.data.map

/**
 * Process-wide [OfflineMapRepository] used by [com.kyuusanq3.mixauto.service.OfflineMapDownloadService]
 * and [com.kyuusanq3.mixauto.ui.map.MapHostViewModel] so install-state flows stay in sync.
 */
object OfflineMapRepositoryHolder {
    @Volatile
    var instance: OfflineMapRepository? = null
}
