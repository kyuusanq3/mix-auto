package com.kyuusanq3.mixauto.data.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.Rating
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.kyuusanq3.mixauto.domain.media.MediaPlaybackState
import com.kyuusanq3.mixauto.service.MixAutoNotificationListenerService
import com.kyuusanq3.mixauto.ui.components.launchAppByPackage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MediaSessionRepository(context: Context) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(MediaPlaybackState())
    val state: StateFlow<MediaPlaybackState> = _state.asStateFlow()
    val hasActiveSession: Boolean
        get() = _state.value.hasActiveSession

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeController: MediaController? = null
    private var hasAutoPlayed = false
    private var hasAttemptedBootLaunch = false
    private var activeSessionsListenerRegistered = false
    private var likeCustomActionId: String? = null
    private var shuffleCustomActionId: String? = null
    private var cachedShuffleOn = false
    private var lastToggleLikeMs = 0L
    private var preferredPackage: String? = null
    private val likedTrackCache = mutableMapOf<String, Boolean>()
    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            publishControllerState(activeController)
            maybeAutoPlay(activeController)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publishControllerState(activeController)
        }

        override fun onSessionDestroyed() {
            refreshSessions()
        }
    }

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener {
        refreshSessions()
    }

    fun refreshSessions() {
        if (!isNotificationListenerEnabled(appContext)) {
            detachController()
            _state.update {
                MediaPlaybackState(needsNotificationAccess = true)
            }
            return
        }

        runCatching {
            val sessionManager = appContext.getSystemService(MediaSessionManager::class.java)
            val listenerComponent = ComponentName(appContext, MixAutoNotificationListenerService::class.java)
            if (!activeSessionsListenerRegistered) {
                sessionManager.addOnActiveSessionsChangedListener(activeSessionsListener, listenerComponent)
                activeSessionsListenerRegistered = true
            }
            val controllers = sessionManager.getActiveSessions(listenerComponent)
            val selected = selectController(controllers)
            attachController(selected)
        }.onFailure { error ->
            Log.w(TAG, "Failed to read active media sessions", error)
            detachController()
            _state.update {
                MediaPlaybackState(needsNotificationAccess = false)
            }
        }
    }

    fun ensureDefaultPlayerIfNeeded(defaultPackage: String?) {
        if (defaultPackage.isNullOrBlank()) return
        if (hasAttemptedBootLaunch) return
        if (_state.value.hasActiveSession) return
        hasAttemptedBootLaunch = true
        Log.i(TAG, "Launching default audio app on boot: $defaultPackage")
        launchAppByPackage(appContext, defaultPackage)
        for (delayMs in BOOT_REFRESH_DELAYS_MS) {
            scope.launch {
                delay(delayMs)
                if (!_state.value.hasActiveSession) {
                    refreshSessions()
                }
            }
        }
    }

    fun setPreferredAudioSource(packageName: String) {
        preferredPackage = packageName
        refreshSessions()
    }

    fun playPause() {
        val controller = activeController ?: return
        val transportControls = controller.transportControls
        if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
            transportControls.pause()
        } else {
            transportControls.play()
        }
    }

    fun skipToNext() {
        activeController?.transportControls?.skipToNext()
    }

    fun skipToPrevious() {
        activeController?.transportControls?.skipToPrevious()
    }

    fun toggleShuffle() {
        val controller = activeController ?: return
        val compat = compatController(controller) ?: return
        val shuffleMode = runCatching { compat.shuffleMode }
            .getOrDefault(PlaybackStateCompat.SHUFFLE_MODE_INVALID)
        val currentlyOn = when {
            isShuffleModeOn(shuffleMode) -> true
            shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_NONE -> false
            else -> cachedShuffleOn
        }
        val customAction = shuffleCustomActionId
        if (!isShuffleModeKnown(shuffleMode) && customAction != null) {
            controller.transportControls.sendCustomAction(customAction, null)
        } else {
            val next = if (currentlyOn) {
                PlaybackStateCompat.SHUFFLE_MODE_NONE
            } else {
                PlaybackStateCompat.SHUFFLE_MODE_ALL
            }
            compat.transportControls.setShuffleMode(next)
        }
        cachedShuffleOn = !currentlyOn
        publishControllerState(controller)
    }

    fun toggleLike() {
        val now = System.currentTimeMillis()
        if (now - lastToggleLikeMs < 400L) return
        lastToggleLikeMs = now

        val controller = activeController ?: return
        val transportControls = controller.transportControls
        val liked = _state.value.isLiked ?: false
        val likeAction = likeCustomActionId
        val userRating = readUserLikeRating(controller.metadata)
        val ratingStyle = userRating?.ratingStyle
        val supportsSetRating = (controller.playbackState?.actions ?: 0L) and
            PlaybackState.ACTION_SET_RATING != 0L

        if (liked) {
            // Unlike only — never send dislike/thumb_down; YT Music skips on those actions.
            when {
                supportsSetRating -> {
                    when (ratingStyle) {
                        Rating.RATING_THUMB_UP_DOWN -> {
                            transportControls.setRating(
                                Rating.newUnratedRating(Rating.RATING_THUMB_UP_DOWN),
                            )
                        }
                        Rating.RATING_HEART -> {
                            transportControls.setRating(Rating.newHeartRating(false))
                        }
                        else -> {
                            transportControls.setRating(
                                Rating.newUnratedRating(Rating.RATING_THUMB_UP_DOWN),
                            )
                        }
                    }
                }
                likeAction != null -> transportControls.sendCustomAction(likeAction, null)
            }
        } else {
            when {
                likeAction != null -> transportControls.sendCustomAction(likeAction, null)
                supportsSetRating -> {
                    when (ratingStyle) {
                        Rating.RATING_THUMB_UP_DOWN -> {
                            transportControls.setRating(Rating.newThumbRating(true))
                        }
                        else -> transportControls.setRating(Rating.newHeartRating(true))
                    }
                }
            }
        }

        readTrackKey(controller.metadata)?.let { trackKey ->
            likedTrackCache[trackKey] = !liked
        }
        publishControllerState(controller)
    }

    private fun attachController(controller: MediaController?) {
        if (activeController?.sessionToken == controller?.sessionToken) {
            publishControllerState(controller)
            maybeAutoPlay(controller)
            return
        }

        detachController()
        activeController = controller
        controller?.registerCallback(controllerCallback)
        publishControllerState(controller)
        maybeAutoPlay(controller)
    }

    private fun maybeAutoPlay(controller: MediaController?) {
        if (hasAutoPlayed || controller == null) return
        if (controller.metadata == null) return
        val playbackState = controller.playbackState
        if (playbackState?.state == PlaybackState.STATE_PLAYING) {
            hasAutoPlayed = true
            return
        }
        hasAutoPlayed = true
        Log.i(TAG, "Auto-playing paused media session on launch")
        controller.transportControls.play()
    }

    private fun detachController() {
        activeController?.unregisterCallback(controllerCallback)
        activeController = null
        likeCustomActionId = null
        shuffleCustomActionId = null
        cachedShuffleOn = false
    }

    private fun publishControllerState(controller: MediaController?) {
        if (controller == null) {
            _state.update {
                MediaPlaybackState(needsNotificationAccess = false)
            }
            return
        }

        val metadata = controller.metadata
        val playbackState = controller.playbackState
        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
        val userRating = readUserLikeRating(metadata)
        val trackKey = readTrackKey(metadata)
        val supportsSetRating = (playbackState?.actions ?: 0L) and PlaybackState.ACTION_SET_RATING != 0L
        val likeActions = parseLikeCustomActions(playbackState?.customActions.orEmpty())
        likeCustomActionId = likeActions
        shuffleCustomActionId = parseShuffleCustomAction(playbackState?.customActions.orEmpty())
        val supportsLike = when {
            userRating?.ratingStyle == Rating.RATING_HEART -> true
            userRating?.ratingStyle == Rating.RATING_THUMB_UP_DOWN -> true
            likeActions != null -> true
            userRating != null -> false
            supportsSetRating -> true
            else -> false
        }
        val sessionLiked = readIsLikedFromSession(userRating)
        if (sessionLiked != null && trackKey != null) {
            likedTrackCache[trackKey] = sessionLiked
        }
        val isLiked = when {
            !supportsLike -> null
            sessionLiked != null -> sessionLiked
            trackKey != null -> likedTrackCache[trackKey] ?: false
            else -> false
        }
        val shuffleState = readShuffleState(controller)

        _state.update {
            MediaPlaybackState(
                title = readTitle(metadata),
                artist = readArtist(metadata),
                albumArt = readAlbumArt(metadata),
                isPlaying = isPlaying,
                playbackPositionMs = readPlaybackPositionMs(playbackState),
                hasActiveSession = metadata != null || playbackState != null,
                needsNotificationAccess = false,
                sourcePackage = controller.packageName,
                supportsLike = supportsLike,
                isLiked = isLiked,
                supportsShuffle = shuffleState.supportsShuffle,
                isShuffleOn = shuffleState.isShuffleOn,
            )
        }
    }

    private data class ShuffleState(
        val supportsShuffle: Boolean,
        val isShuffleOn: Boolean,
    )

    private fun compatController(controller: MediaController): MediaControllerCompat? {
        return runCatching {
            MediaControllerCompat(
                appContext,
                MediaSessionCompat.Token.fromToken(controller.sessionToken),
            )
        }.getOrNull()
    }

    private fun readShuffleState(controller: MediaController): ShuffleState {
        val compat = compatController(controller) ?: return ShuffleState(false, false)
        val shuffleMode = runCatching { compat.shuffleMode }
            .getOrDefault(PlaybackStateCompat.SHUFFLE_MODE_INVALID)
        // Fallback: some apps (e.g. YT Music) advertise shuffle via actions bit only
        val actionsSupportsShuffle = (controller.playbackState?.actions ?: 0L) and
            PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE != 0L
        val supportsShuffle = isShuffleModeKnown(shuffleMode) ||
            actionsSupportsShuffle ||
            shuffleCustomActionId != null
        val isShuffleOn = when {
            isShuffleModeOn(shuffleMode) -> true.also { cachedShuffleOn = true }
            shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_NONE -> false.also { cachedShuffleOn = false }
            else -> cachedShuffleOn
        }
        return ShuffleState(supportsShuffle, isShuffleOn)
    }

    private fun isShuffleModeOn(shuffleMode: Int): Boolean {
        return shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL ||
            shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_GROUP
    }

    private fun isShuffleModeKnown(shuffleMode: Int): Boolean {
        return shuffleMode != PlaybackStateCompat.SHUFFLE_MODE_INVALID
    }

    private fun parseShuffleCustomAction(
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

    private fun selectController(controllers: List<MediaController>): MediaController? {
        if (controllers.isEmpty()) return null

        preferredPackage?.let { preferred ->
            return controllers.firstOrNull { it.packageName == preferred }
        }

        controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?.let { return it }

        return controllers.maxByOrNull { controller ->
            controller.playbackState?.lastPositionUpdateTime ?: 0L
        }
    }

    private fun readTitle(metadata: MediaMetadata?): String {
        return metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""
    }

    private fun readArtist(metadata: MediaMetadata?): String {
        return metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION)
            ?: ""
    }

    private fun readAlbumArt(metadata: MediaMetadata?): Bitmap? {
        return metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    }

    private fun readPlaybackPositionMs(playbackState: PlaybackState?): Long {
        if (playbackState == null) return 0L
        val position = playbackState.position.coerceAtLeast(0L)
        if (playbackState.state != PlaybackState.STATE_PLAYING) return position
        val elapsed = SystemClock.elapsedRealtime() - playbackState.lastPositionUpdateTime
        return (position + elapsed * playbackState.playbackSpeed).toLong().coerceAtLeast(0L)
    }

    private fun readTrackKey(metadata: MediaMetadata?): String? {
        if (metadata == null) return null
        val mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        if (!mediaId.isNullOrBlank()) return mediaId
        val title = readTitle(metadata)
        if (title.isBlank()) return null
        return "$title|${readArtist(metadata)}"
    }

    private fun readUserLikeRating(metadata: MediaMetadata?): Rating? {
        return metadata?.getRating(MediaMetadata.METADATA_KEY_USER_RATING)
            ?: metadata?.getRating(MediaMetadata.METADATA_KEY_RATING)
    }

    /** Returns null when the session omits rating metadata (use track cache instead). */
    private fun readIsLikedFromSession(userRating: Rating?): Boolean? {
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

    private fun parseLikeCustomActions(
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

    private fun isLikeAction(id: String, name: String): Boolean {
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

    private fun isDislikeAction(id: String, name: String): Boolean {
        return id.contains("unlike") ||
            id.contains("thumb_down") ||
            id.contains("dislike") ||
            name.contains("unlike") ||
            name.contains("thumb down") ||
            name.contains("dislike")
    }

    companion object {
        private const val TAG = "MediaSessionRepository"
        private val BOOT_REFRESH_DELAYS_MS = listOf(2_000L, 5_000L, 10_000L)

        @Volatile
        private var instance: MediaSessionRepository? = null

        fun getInstance(context: Context): MediaSessionRepository {
            return instance ?: synchronized(this) {
                instance ?: MediaSessionRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
