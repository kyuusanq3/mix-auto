package com.kyuusanq3.mixauto.data.apps

import android.content.Intent

data class LaunchableAppEntry(
    val packageName: String,
    val label: String,
    val launchIntent: Intent,
)
