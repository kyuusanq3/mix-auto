package com.kyuusanq3.mixauto.data.map

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object MapDownloadNetworkGate {

    const val OFFLINE_MESSAGE = "No internet connection."
    const val WIFI_REQUIRED_MESSAGE =
        "Large downloads use Wi‑Fi by default. Connect to Wi‑Fi or enable \"Download on mobile data\" in Map Data."

    fun canDownload(context: Context, allowMobileData: Boolean): Boolean =
        blockReason(context, allowMobileData) == null

    fun blockReason(context: Context, allowMobileData: Boolean): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return OFFLINE_MESSAGE
        val network = connectivityManager.activeNetwork ?: return OFFLINE_MESSAGE
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return OFFLINE_MESSAGE
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return OFFLINE_MESSAGE
        }
        if (allowMobileData) return null
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return null
        return WIFI_REQUIRED_MESSAGE
    }
}
