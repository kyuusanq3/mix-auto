package com.kyuusanq3.mixauto.domain.media

import android.graphics.Bitmap

data class MediaPlaybackState(
    val title: String = "",
    val artist: String = "",
    val albumArt: Bitmap? = null,
    val isPlaying: Boolean = false,
    val hasActiveSession: Boolean = false,
    val needsNotificationAccess: Boolean = false,
    val sourcePackage: String = "",
    val supportsLike: Boolean = false,
    val isLiked: Boolean? = null,
    val supportsShuffle: Boolean = false,
    val isShuffleOn: Boolean = false,
) {
    val displayTitle: String
        get() = title.ifBlank { "No track playing" }

    val displayArtist: String
        get() = artist.ifBlank { if (hasActiveSession) "Unknown artist" else "" }
}
