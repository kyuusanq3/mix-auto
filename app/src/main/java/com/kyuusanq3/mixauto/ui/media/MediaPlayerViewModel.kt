package com.kyuusanq3.mixauto.ui.media

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kyuusanq3.mixauto.data.media.MediaSessionRepository
import com.kyuusanq3.mixauto.domain.media.MediaPlaybackState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MediaPlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MediaSessionRepository.getInstance(application)

    val mediaState: StateFlow<MediaPlaybackState> = repository.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MediaPlaybackState(),
    )

    fun refreshSessions() {
        repository.refreshSessions()
    }

    fun playPause() {
        repository.playPause()
    }

    fun skipToNext() {
        repository.skipToNext()
    }

    fun skipToPrevious() {
        repository.skipToPrevious()
    }

    fun toggleLike() {
        repository.toggleLike()
    }

    fun toggleShuffle() {
        repository.toggleShuffle()
    }
}
