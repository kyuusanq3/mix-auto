package com.kyuusanq3.mixauto.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.kyuusanq3.mixauto.ui.settings.LauncherViewModel

internal const val VOICE_SEARCH_TAG = "NavigationSearchOverlay"

internal fun speechErrorLabel(error: Int): String = when (error) {
    SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
    SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
    SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
    SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
    SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
    else -> "ERROR_UNKNOWN($error)"
}

internal class SpeechSearchAudioFocus(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null

    fun request() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            )
        }
    }

    fun abandon() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }
}

internal data class VoiceSearchUiState(
    val isListening: Boolean,
    val micPulseAlpha: Float,
    val speechAvailable: Boolean,
    val tryStartVoiceSearch: () -> Unit,
)

@Composable
internal fun rememberVoiceSearch(
    context: Context,
    launcherViewModel: LauncherViewModel,
): VoiceSearchUiState {
    var isListening by remember { mutableStateOf(false) }
    var pendingVoiceStart by remember { mutableStateOf(false) }
    var pendingVoiceRestart by remember { mutableStateOf(false) }
    var recognizerSessionActive by remember { mutableStateOf(false) }

    val speechAudioFocus = remember(context) {
        SpeechSearchAudioFocus(context.applicationContext)
    }

    val speechAvailable = remember(context) {
        SpeechRecognizer.isRecognitionAvailable(context.applicationContext)
    }
    val speechRecognizer = remember(context, speechAvailable) {
        if (speechAvailable) {
            SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        } else {
            null
        }
    }

    val recognitionIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }

    val requestVoiceListeningRef = rememberUpdatedState<(SpeechRecognizer) -> Unit> { recognizer ->
        if (recognizerSessionActive) {
            pendingVoiceRestart = true
            isListening = true
            recognizer.cancel()
        } else {
            speechAudioFocus.request()
            isListening = true
            recognizerSessionActive = true
            recognizer.startListening(recognitionIntent)
        }
    }

    val stopVoiceListeningRef = rememberUpdatedState {
        pendingVoiceRestart = false
        recognizerSessionActive = false
        isListening = false
        speechAudioFocus.abandon()
    }

    DisposableEffect(speechRecognizer) {
        val recognizer = speechRecognizer
        if (recognizer != null) {
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    recognizerSessionActive = true
                }

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    if (!pendingVoiceRestart) {
                        stopVoiceListeningRef.value()
                    }
                }

                override fun onError(error: Int) {
                    Log.w(VOICE_SEARCH_TAG, "SpeechRecognizer error: $error (${speechErrorLabel(error)})")
                    if (pendingVoiceRestart) {
                        pendingVoiceRestart = false
                        isListening = true
                        recognizer.startListening(recognitionIntent)
                    } else {
                        stopVoiceListeningRef.value()
                    }
                }

                override fun onResults(resultsBundle: Bundle) {
                    if (pendingVoiceRestart) return
                    resultsBundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.let { spoken ->
                            launcherViewModel.updateDestinationSearch { state ->
                                state.copy(query = spoken)
                            }
                        }
                    stopVoiceListeningRef.value()
                }

                override fun onPartialResults(partialResults: Bundle) = Unit

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
        onDispose {
            pendingVoiceRestart = false
            recognizerSessionActive = false
            speechAudioFocus.abandon()
            speechRecognizer?.destroy()
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingVoiceStart) {
            pendingVoiceStart = false
            speechRecognizer?.let { requestVoiceListeningRef.value(it) }
        } else {
            pendingVoiceStart = false
        }
    }

    val tryStartVoiceSearch = rememberUpdatedState {
        val recognizer = speechRecognizer ?: return@rememberUpdatedState
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingVoiceStart = true
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return@rememberUpdatedState
        }
        requestVoiceListeningRef.value(recognizer)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val pulsingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "micPulseAlpha",
    )
    val micPulseAlpha = if (isListening) pulsingAlpha else 1f

    return VoiceSearchUiState(
        isListening = isListening,
        micPulseAlpha = micPulseAlpha,
        speechAvailable = speechAvailable,
        tryStartVoiceSearch = { tryStartVoiceSearch.value() },
    )
}