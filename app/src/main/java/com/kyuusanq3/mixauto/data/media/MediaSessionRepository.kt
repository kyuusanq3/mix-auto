package com.kyuusanq3.mixauto.data.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import com.kyuusanq3.mixauto.domain.media.MediaPlaybackState
import com.kyuusanq3.mixauto.service.MixAutoNotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MediaSessionRepository(context: Context) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(MediaPlaybackState())
    val state: StateFlow<MediaPlaybackState> = _state.asStateFlow()

    private var activeController: MediaController? = null
    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            publishControllerState(activeController)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publishControllerState(activeController)
        }

        override fun onSessionDestroyed() {
            refreshSessions()
        }
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

    private fun attachController(controller: MediaController?) {
        if (activeController?.sessionToken == controller?.sessionToken) {
            publishControllerState(controller)
            return
        }

        detachController()
        activeController = controller
        controller?.registerCallback(controllerCallback)
        publishControllerState(controller)
    }

    private fun detachController() {
        activeController?.unregisterCallback(controllerCallback)
        activeController = null
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

        _state.update {
            MediaPlaybackState(
                title = readTitle(metadata),
                artist = readArtist(metadata),
                albumArt = readAlbumArt(metadata),
                isPlaying = isPlaying,
                hasActiveSession = metadata != null || playbackState != null,
                needsNotificationAccess = false,
            )
        }
    }

    private fun selectController(controllers: List<MediaController>): MediaController? {
        if (controllers.isEmpty()) return null

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

    companion object {
        private const val TAG = "MediaSessionRepository"

        @Volatile
        private var instance: MediaSessionRepository? = null

        fun getInstance(context: Context): MediaSessionRepository {
            return instance ?: synchronized(this) {
                instance ?: MediaSessionRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
