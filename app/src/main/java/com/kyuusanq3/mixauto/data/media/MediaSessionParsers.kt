package com.kyuusanq3.mixauto.data.media

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.Rating
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat

internal data class ShuffleState(
    val supportsShuffle: Boolean,
    val isShuffleOn: Boolean,
)

/**
 * Title / art / like / shuffle readers extracted from [MediaSessionRepository].
 * Session attach, boot/resume, and transport stay on the repository.
 */
internal object MediaSessionParsers {
    fun readTitle(metadata: MediaMetadata?): String {
        return metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""
    }

    fun readArtist(metadata: MediaMetadata?): String {
        return metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION)
            ?: ""
    }

    fun readAlbumArt(metadata: MediaMetadata?): Bitmap? {
        return metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    }

    fun readPlaybackPositionMs(playbackState: PlaybackState?): Long {
        if (playbackState == null) return 0L
        val position = playbackState.position.coerceAtLeast(0L)
        if (playbackState.state != PlaybackState.STATE_PLAYING) return position
        val elapsed = SystemClock.elapsedRealtime() - playbackState.lastPositionUpdateTime
        return (position + elapsed * playbackState.playbackSpeed).toLong().coerceAtLeast(0L)
    }

    fun readTrackKey(metadata: MediaMetadata?): String? {
        if (metadata == null) return null
        val mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        if (!mediaId.isNullOrBlank()) return mediaId
        val title = readTitle(metadata)
        if (title.isBlank()) return null
        return "$title|${readArtist(metadata)}"
    }

    fun readUserLikeRating(metadata: MediaMetadata?): Rating? {
        return metadata?.getRating(MediaMetadata.METADATA_KEY_USER_RATING)
            ?: metadata?.getRating(MediaMetadata.METADATA_KEY_RATING)
    }

    /** Returns null when the session omits rating metadata (use track cache instead). */
    fun readIsLikedFromSession(userRating: Rating?): Boolean? {
        if (userRating == null) return null
        return when (userRating.ratingStyle) {
            Rating.RATING_HEART -> {
                if (userRating.isRated) userRating.hasHeart() else false
            }
            Rating.RATING_THUMB_UP_DOWN -> {
                if (userRating.isRated) userRating.isThumbUp() else false
            }
            else -> null
        }
    }

    fun parseLikeCustomActions(
        customActions: List<PlaybackState.CustomAction>,
    ): String? {
        for (action in customActions) {
            val actionId = action.action
            val id = actionId.lowercase()
            val name = action.name?.toString()?.lowercase().orEmpty()
            if (isLikeAction(id, name)) {
                return actionId
            }
        }
        return null
    }

    fun isLikeAction(id: String, name: String): Boolean {
        if (isDislikeAction(id, name)) return false
        return id.contains("like") ||
            id.contains("thumb_up") ||
            id.contains("favorite") ||
            id.contains("favourite") ||
            id.contains("heart") ||
            name.contains("like") ||
            name.contains("thumb up") ||
            name.contains("favorite") ||
            name.contains("favourite")
    }

    fun isDislikeAction(id: String, name: String): Boolean {
        return id.contains("unlike") ||
            id.contains("thumb_down") ||
            id.contains("dislike") ||
            name.contains("unlike") ||
            name.contains("thumb down") ||
            name.contains("dislike")
    }

    fun parseShuffleCustomAction(
        customActions: List<PlaybackState.CustomAction>,
    ): String? {
        for (action in customActions) {
            val actionId = action.action
            val id = actionId.lowercase()
            val name = action.name?.toString()?.lowercase().orEmpty()
            if (id.contains("shuffle") || name.contains("shuffle")) {
                return actionId
            }
        }
        return null
    }

    fun isShuffleModeOn(shuffleMode: Int): Boolean {
        return shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL ||
            shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_GROUP
    }

    fun isShuffleModeKnown(shuffleMode: Int): Boolean {
        return shuffleMode != PlaybackStateCompat.SHUFFLE_MODE_INVALID
    }

    fun readShuffleState(
        controller: MediaController,
        compat: MediaControllerCompat?,
        shuffleCustomActionId: String?,
        cachedShuffleOn: Boolean,
    ): ShuffleState {
        if (compat == null) return ShuffleState(false, false)
        val shuffleMode = runCatching { compat.shuffleMode }
            .getOrDefault(PlaybackStateCompat.SHUFFLE_MODE_INVALID)
        // Fallback: some apps (e.g. YT Music) advertise shuffle via actions bit only
        val actionsSupportsShuffle = (controller.playbackState?.actions ?: 0L) and
            PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE != 0L
        val supportsShuffle = isShuffleModeKnown(shuffleMode) ||
            actionsSupportsShuffle ||
            shuffleCustomActionId != null
        val isShuffleOn = when {
            isShuffleModeOn(shuffleMode) -> true
            shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_NONE -> false
            else -> cachedShuffleOn
        }
        return ShuffleState(supportsShuffle, isShuffleOn)
    }
}
