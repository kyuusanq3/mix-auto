package com.kyuusanq3.mixauto.ui.settings

import android.content.Context

class LauncherPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isLeftHandDrive: Boolean
        get() = prefs.getBoolean(KEY_LEFT_HAND_DRIVE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_LEFT_HAND_DRIVE, value).apply()
        }

    var isShortcutsHorizontal: Boolean
        get() = prefs.getBoolean(KEY_SHORTCUTS_HORIZONTAL, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SHORTCUTS_HORIZONTAL, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "launcher_prefs"
        private const val KEY_LEFT_HAND_DRIVE = "lhd"
        private const val KEY_SHORTCUTS_HORIZONTAL = "shortcuts_horizontal"
    }
}
