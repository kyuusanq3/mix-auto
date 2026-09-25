package com.kyuusanq3.mixauto.ui.firstparty

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.ui.graphics.vector.ImageVector

/** Identifiers for Mix Auto's own first-party mini apps shown at the top of the app drawer. */
enum class FirstPartyAppId {
    REMINDER_CHECKLIST,
}

data class FirstPartyAppEntry(
    val id: FirstPartyAppId,
    val label: String,
    val icon: ImageVector,
)

val FIRST_PARTY_APPS: List<FirstPartyAppEntry> = listOf(
    FirstPartyAppEntry(
        id = FirstPartyAppId.REMINDER_CHECKLIST,
        label = "Reminder Checklist",
        icon = Icons.Filled.Checklist,
    ),
)
