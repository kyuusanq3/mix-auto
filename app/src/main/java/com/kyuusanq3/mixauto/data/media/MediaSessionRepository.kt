package com.kyuusanq3.mixauto.data.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.Rating
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.kyuusanq3.mixauto.domain.media.MediaPlaybackState
import com.kyuusanq3.mixauto.service.MixAutoNotificationListenerService
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

    fun ensureDefaultPlayerIfNeeded(
        defaultPackage: String?,
        fallbackResumeLink: String? = null,
        resumeOnStartupEnabled: Boolean = true,
    ) {
        if (!resumeOnStartupEnabled) return
        if (hasAttemptedBootLaunch) return
        if (_state.value.hasActiveSession) return

        val trimmedDefault = defaultPackage?.takeIf { it.isNotBlank() }
        val trimmedFallback = fallbackResumeLink?.takeIf { it.isNotBlank() }
        if (trimmedDefault == null && trimmedFallback == null) return
        hasAttemptedBootLaunch = true

        trimmedDefault?.let { packageName ->
            Log.i(TAG, "Waking default audio app on boot: $packageName")
            BackgroundAudioLauncher.wakeWithForegroundFallback(appContext, packageName)
        }
        for (delayMs in BOOT_REFRESH_DELAYS_MS) {
            scope.launch {
                delay(delayMs)
                if (!_state.value.hasActiveSession) {
                    refreshSessions()
                }
            }
        }

        trimmedFallback?.let { link ->
            scope.launch {
                delay(FALLBACK_LAUNCH_DELAY_MS)
                if (!_state.value.hasActiveSession || _state.value.title.isBlank()) {
                    BackgroundAudioLauncher.launchFallbackResumeLink(appContext, link, trimmedDefault)
                }
            }
        }
    }

    /**
     * User-initiated resume attempt (e.g. right after editing Audio Settings) — unlike
     * [ensureDefaultPlayerIfNeeded] this is not gated by [hasAttemptedBootLaunch] and uses
     * shorter delays since the app is already running in the foreground.
     */
    fun attemptResumeNow(defaultPackage: String?, fallbackResumeLink: String?) {
        if (_state.value.hasActiveSession) return
        val trimmedDefault = defaultPackage?.takeIf { it.isNotBlank() }
        val trimmedFallback = fallbackResumeLink?.takeIf { it.isNotBlank() }
        if (trimmedDefault == null && trimmedFallback == null) return

        if (trimmedDefault == null) {
            Log.i(TAG, "Manual resume attempt: no default source set, launching fallback link")
            trimmedFallback?.let { BackgroundAudioLauncher.launchFallbackResumeLink(appContext, it, null) }
            return
        }

        Log.i(TAG, "Manual resume attempt: waking $trimmedDefault")
        BackgroundAudioLauncher.wakeWithForegroundFallback(appContext, trimmedDefault)
        for (delayMs in MANUAL_RESUME_REFRESH_DELAYS_MS) {
            scope.launch {
                delay(delayMs)
                if (!_state.value.hasActiveSession) {
                    refreshSessions()
                }
            }
        }
        trimmedFallback?.let { link ->
            scope.launch {
                delay(MANUAL_FALLBACK_LAUNCH_DELAY_MS)
                if (!_state.value.hasActiveSession || _state.value.title.isBlank()) {
                    BackgroundAudioLauncher.launchFallbackResumeLink(appContext, link, trimmedDefault)
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
            MediaSessionParsers.isShuffleModeOn(shuffleMode) -> true
            shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_NONE -> false
            else -> cachedShuffleOn
        }
        val customAction = shuffleCustomActionId
        if (!MediaSessionParsers.isShuffleModeKnown(shuffleMode) && customAction != null) {
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
        val userRating = MediaSessionParsers.readUserLikeRating(controller.metadata)
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

        MediaSessionParsers.readTrackKey(controller.metadata)?.let { trackKey ->
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
        val userRating = MediaSessionParsers.readUserLikeRating(metadata)
        val trackKey = MediaSessionParsers.readTrackKey(metadata)
        val supportsSetRating = (playbackState?.actions ?: 0L) and PlaybackState.ACTION_SET_RATING != 0L
        val likeActions = MediaSessionParsers.parseLikeCustomActions(playbackState?.customActions.orEmpty())
        likeCustomActionId = likeActions
        shuffleCustomActionId = MediaSessionParsers.parseShuffleCustomAction(
            playbackState?.customActions.orEmpty(),
        )
        val supportsLike = when {
            userRating?.ratingStyle == Rating.RATING_HEART -> true
            userRating?.ratingStyle == Rating.RATING_THUMB_UP_DOWN -> true
            likeActions != null -> true
            userRating != null -> false
            supportsSetRating -> true
            else -> false
        }
        val sessionLiked = MediaSessionParsers.readIsLikedFromSession(userRating)
        if (sessionLiked != null && trackKey != null) {
            likedTrackCache[trackKey] = sessionLiked
        }
        val isLiked = when {
            !supportsLike -> null
            sessionLiked != null -> sessionLiked
            trackKey != null -> likedTrackCache[trackKey] ?: false
            else -> false
        }
        val shuffleState = MediaSessionParsers.readShuffleState(
            controller,
            compatController(controller),
            shuffleCustomActionId,
            cachedShuffleOn,
        )
        cachedShuffleOn = shuffleState.isShuffleOn

        _state.update {
            MediaPlaybackState(
                title = MediaSessionParsers.readTitle(metadata),
                artist = MediaSessionParsers.readArtist(metadata),
                albumArt = MediaSessionParsers.readAlbumArt(metadata),
                isPlaying = isPlaying,
                playbackPositionMs = MediaSessionParsers.readPlaybackPositionMs(playbackState),
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

    private fun compatController(controller: MediaController): MediaControllerCompat? {
        return runCatching {
            MediaControllerCompat(
                appContext,
                MediaSessionCompat.Token.fromToken(controller.sessionToken),
            )
        }.getOrNull()
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

    companion object {
        private const val TAG = "MediaSessionRepository"
        private val BOOT_REFRESH_DELAYS_MS = listOf(2_000L, 5_000L, 10_000L)
        private const val FALLBACK_LAUNCH_DELAY_MS = 12_000L
        private val MANUAL_RESUME_REFRESH_DELAYS_MS = listOf(1_500L, 3_500L)
        private const val MANUAL_FALLBACK_LAUNCH_DELAY_MS = 5_000L

        @Volatile
        private var instance: MediaSessionRepository? = null

        fun getInstance(context: Context): MediaSessionRepository {
            return instance ?: synchronized(this) {
                instance ?: MediaSessionRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
