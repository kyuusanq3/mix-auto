package com.kyuusanq3.mixauto.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.kyuusanq3.mixauto.ui.components.loadAudioPlayerPackageNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LaunchableAppsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()

    @Volatile
    private var cachedApps: List<LaunchableAppEntry>? = null

    @Volatile
    private var cachedAudioPackages: Set<String>? = null

    suspend fun getLaunchableApps(): List<LaunchableAppEntry> = mutex.withLock {
        cachedApps?.let { return it }
        val apps = withContext(Dispatchers.Default) {
            loadLaunchableApps(appContext.packageManager, appContext.packageName)
        }
        cachedApps = apps
        apps
    }

    suspend fun getAudioPlayerPackages(): Set<String> = mutex.withLock {
        cachedAudioPackages?.let { return it }
        val packages = withContext(Dispatchers.Default) {
            loadAudioPlayerPackageNames(appContext)
        }
        cachedAudioPackages = packages
        packages
    }

    suspend fun loadAll(): Pair<List<LaunchableAppEntry>, Set<String>> {
        val apps = getLaunchableApps()
        val audioPackages = getAudioPlayerPackages()
        return apps to audioPackages
    }

    fun invalidate() {
        cachedApps = null
        cachedAudioPackages = null
    }

    private fun loadLaunchableApps(
        packageManager: PackageManager,
        ownPackage: String,
    ): List<LaunchableAppEntry> {
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolveInfos = packageManager.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)

        return resolveInfos
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                if (packageName == ownPackage) return@mapNotNull null
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                    ?: return@mapNotNull null
                LaunchableAppEntry(
                    packageName = packageName,
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    launchIntent = launchIntent,
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
