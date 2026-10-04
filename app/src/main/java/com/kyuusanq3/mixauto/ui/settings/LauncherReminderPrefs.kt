package com.kyuusanq3.mixauto.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Reminder Checklist enable flag + dynamic item list, extracted from [LauncherViewModel].
 * Boot popup checked-state / open-page session state stays on the ViewModel (never persisted).
 */
internal class LauncherReminderPrefs(
    private val preferences: LauncherPreferences,
) {
    var reminderChecklistEnabled by mutableStateOf(preferences.reminderChecklistEnabled)
        private set

    var reminderChecklistItems by mutableStateOf(preferences.reminderChecklistItems)
        private set

    var reminderChecklistOncePerDay by mutableStateOf(preferences.reminderChecklistOncePerDay)
        private set

    var reminderChecklistConfirmButtonMode by mutableStateOf(preferences.reminderChecklistConfirmButtonMode)
        private set

    fun setEnabled(enabled: Boolean) {
        reminderChecklistEnabled = enabled
        preferences.reminderChecklistEnabled = enabled
    }

    fun setOncePerDay(oncePerDay: Boolean) {
        reminderChecklistOncePerDay = oncePerDay
        preferences.reminderChecklistOncePerDay = oncePerDay
    }

    fun setConfirmButtonMode(confirmButtonMode: Boolean) {
        reminderChecklistConfirmButtonMode = confirmButtonMode
        preferences.reminderChecklistConfirmButtonMode = confirmButtonMode
    }

    /** Returns true if the boot popup should be shown right now given the once-per-day setting. */
    fun shouldShowPopupNow(): Boolean {
        if (!preferences.reminderChecklistOncePerDay) return true
        val todayEpochDay = System.currentTimeMillis() / DAY_IN_MILLIS
        return preferences.reminderChecklistLastShownEpochDay != todayEpochDay
    }

    /** Records that the popup was shown/dismissed today, for once-per-day gating. */
    fun markShownToday() {
        preferences.reminderChecklistLastShownEpochDay = System.currentTimeMillis() / DAY_IN_MILLIS
    }

    fun addItem(text: String) {
        if (reminderChecklistItems.size >= LauncherPreferences.MAX_REMINDER_CHECKLIST_ITEMS) return
        reminderChecklistItems = reminderChecklistItems + text
        preferences.reminderChecklistItems = reminderChecklistItems
    }

    fun updateItem(index: Int, text: String) {
        if (index < 0 || index >= reminderChecklistItems.size) return
        reminderChecklistItems = reminderChecklistItems.toMutableList().also { it[index] = text }
        preferences.reminderChecklistItems = reminderChecklistItems
    }

    fun removeItem(index: Int) {
        if (index < 0 || index >= reminderChecklistItems.size) return
        reminderChecklistItems = reminderChecklistItems.toMutableList().also { it.removeAt(index) }
        preferences.reminderChecklistItems = reminderChecklistItems
    }
}

private const val DAY_IN_MILLIS = 24L * 60L * 60L * 1000L
