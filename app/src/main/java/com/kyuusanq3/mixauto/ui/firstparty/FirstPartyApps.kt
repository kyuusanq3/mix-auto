package com.kyuusanq3.mixauto.ui.firstparty

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.ui.graphics.vector.ImageVector

/** Identifiers for Mix Auto's own first-party mini apps shown at the top of the app drawer. */
enum class FirstPartyAppId {
    REMINDER_CHECKLIST,
    FLOATING_WINDOW,
    ANDROID_AUTO,
    /** Headunit flavor only — see [FIRST_PARTY_APPS]. */
    MAP_SOURCE,
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
    FirstPartyAppEntry(
        id = FirstPartyAppId.FLOATING_WINDOW,
        label = "Floating Window",
        icon = Icons.Filled.PictureInPicture,
    ),
    FirstPartyAppEntry(
        id = FirstPartyAppId.ANDROID_AUTO,
        label = "Android Auto",
        icon = Icons.Filled.DirectionsCar,
    ),
    FirstPartyAppEntry(
        id = FirstPartyAppId.MAP_SOURCE,
        label = "Map Source",
        icon = Icons.Filled.Map,
    ),
)
