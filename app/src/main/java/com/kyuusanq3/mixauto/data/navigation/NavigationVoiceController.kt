package com.kyuusanq3.mixauto.data.navigation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class NavigationVoiceController(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val utteranceCounter = AtomicInteger(0)

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var audioFocusRequest: AudioFocusRequest? = null
    private var activeFocusUtteranceId: String? = null

    var enabled: Boolean = true

    /** Utterance volume scale (0.5–1.0); applied via [TextToSpeech.Engine.KEY_PARAM_VOLUME]. */
    var volume: Float = DEFAULT_VOLUME
        set(value) {
            field = value.coerceIn(MIN_VOLUME, MAX_VOLUME)
        }

    private var navStartSpoken = false
    private var trackedStepIndex = -1
    private var aheadSpoken = false
    private var prepareSpoken = false
    private var turnSpoken = false
    private var lastDistToManeuverM: Float? = null
    private var lastTimeToTurnSec: Float? = null
    private var continueAnnounced = false

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setAudioAttributes(audioAttributes)
                tts?.setOnUtteranceProgressListener(utteranceListener)
                ttsReady = true
            } else {
                Log.w(TAG, "TextToSpeech init failed: status=$status")
            }
        }
    }

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            if (utteranceId != null && utteranceId == activeFocusUtteranceId) {
                abandonAudioFocus()
                activeFocusUtteranceId = null
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            onDone(utteranceId)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            onDone(utteranceId)
        }
    }

    fun onNavigationDrivingStarted(firstStep: NavStepPhrase, trafficPhrase: String? = null) {
        if (!enabled || !ttsReady) return
        if (navStartSpoken) return
        navStartSpoken = true
        trackedStepIndex = 0
        resetManeuverFlags()
        speak("Starting navigation.", flush = true)
        trafficPhrase?.takeIf { it.isNotBlank() }?.let { phrase ->
            speak(phrase, flush = false)
        }
        NavTtsPhrases.buildContinuePhrase(firstStep)?.let { phrase ->
            speak(phrase, flush = false)
            continueAnnounced = true
        }
    }

    fun onNavTick(context: NavTickContext) {
        if (!enabled || !ttsReady) return
        if (context.isRouteOverviewActive) return
        if (context.steps.isEmpty()) return

        if (trackedStepIndex != context.currentStepIndex) {
            trackedStepIndex = context.currentStepIndex
            resetManeuverFlags()
        }

        val nextIdx = context.currentStepIndex + 1
        if (nextIdx >= context.steps.size) return

        val upcoming = context.steps[nextIdx]
        if (upcoming.maneuverType == "arrive") {
            lastDistToManeuverM = context.distToNextManeuverM
            lastTimeToTurnSec = timeToTurnSec(context.distToNextManeuverM, context.speedMps)
            return
        }

        val distM = context.distToNextManeuverM
        val timeToTurn = timeToTurnSec(distM, context.speedMps)
        val lastDist = lastDistToManeuverM
        val lastTime = lastTimeToTurnSec

        val phase = selectManeuverPhaseToAnnounce(
            distM = distM,
            timeToTurnSec = timeToTurn,
            lastDistM = lastDist,
            lastTimeToTurnSec = lastTime,
        )

        when (phase) {
            ManeuverPhase.TURN -> {
                turnSpoken = true
                prepareSpoken = true
                aheadSpoken = true
                speak(NavTtsPhrases.buildTurnCue(upcoming.shortInstruction), flush = false)
            }
            ManeuverPhase.PREPARE -> {
                prepareSpoken = true
                markAheadSkipped(timeToTurn)
                speak(NavTtsPhrases.buildPrepareCue(upcoming.shortInstruction), flush = false)
            }
            ManeuverPhase.AHEAD -> {
                aheadSpoken = true
                speak(
                    NavTtsPhrases.buildAheadCue(
                        upcoming.shortInstruction,
                        upcoming.streetName,
                        upcoming.maneuverType,
                    ),
                    flush = false,
                )
            }
            null -> Unit
        }

        lastDistToManeuverM = distM
        lastTimeToTurnSec = timeToTurn
    }

    fun onStepAdvanced(stepIndex: Int, newStep: NavStepPhrase) {
        if (!enabled || !ttsReady) return
        trackedStepIndex = stepIndex
        resetManeuverFlags()

        NavTtsPhrases.buildContinuePhrase(newStep)?.let { phrase ->
            continueAnnounced = true
            speak(phrase, flush = false)
        }
    }

    fun onRerouteStarted() {
        if (!enabled) return
        stopSpeaking()
        resetManeuverFlags()
        if (ttsReady) {
            speak("Recalculating route.", flush = true)
        }
    }

    fun onArrival() {
        if (!enabled || !ttsReady) return
        stopSpeaking()
        speak("You have arrived at your destination.", flush = true)
    }

    /** Preview phrase for map settings; ignores [enabled] so volume can be tested while voice nav is off. */
    fun speakPreview(text: String) {
        if (!ttsReady || text.isBlank()) return
        speak(text, flush = true, force = true)
    }

    fun onNavigationEnded() {
        stopSpeaking()
        navStartSpoken = false
        trackedStepIndex = -1
        resetManeuverFlags()
    }

    fun shutdown() {
        onNavigationEnded()
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    private enum class ManeuverPhase {
        AHEAD,
        PREPARE,
        TURN,
    }

    private fun timeToTurnSec(distM: Float, speedMps: Float): Float {
        val effectiveSpeed = maxOf(speedMps, MIN_SPEED_MPS)
        return distM / effectiveSpeed
    }

    private fun isTurnDue(distM: Float, timeToTurnSec: Float): Boolean =
        distM <= TURN_DISTANCE_M || timeToTurnSec <= TURN_TIME_SEC

    private fun isPrepareDue(distM: Float, timeToTurnSec: Float): Boolean =
        distM <= PREPARE_DISTANCE_M || timeToTurnSec <= PREPARE_TIME_SEC

    private fun isAheadDue(timeToTurnSec: Float): Boolean =
        timeToTurnSec <= EARLY_WARNING_SEC

    /**
     * At most one maneuver cue per GPS tick. Uses threshold crossing when prior sample exists;
     * on first sample or GPS jump, speaks only the most urgent applicable phase (turn > prepare > ahead).
     */
    private fun selectManeuverPhaseToAnnounce(
        distM: Float,
        timeToTurnSec: Float,
        lastDistM: Float?,
        lastTimeToTurnSec: Float?,
    ): ManeuverPhase? {
        if (lastDistM != null && lastTimeToTurnSec != null) {
            if (
                !turnSpoken &&
                (lastDistM > TURN_DISTANCE_M && distM <= TURN_DISTANCE_M ||
                    lastTimeToTurnSec > TURN_TIME_SEC && timeToTurnSec <= TURN_TIME_SEC)
            ) {
                return ManeuverPhase.TURN
            }
            if (
                !prepareSpoken &&
                (lastDistM > PREPARE_DISTANCE_M && distM <= PREPARE_DISTANCE_M ||
                    lastTimeToTurnSec > PREPARE_TIME_SEC && timeToTurnSec <= PREPARE_TIME_SEC)
            ) {
                return ManeuverPhase.PREPARE
            }
            if (
                !aheadSpoken &&
                lastTimeToTurnSec > EARLY_WARNING_SEC &&
                timeToTurnSec <= EARLY_WARNING_SEC
            ) {
                return ManeuverPhase.AHEAD
            }
            return null
        }

        if (isTurnDue(distM, timeToTurnSec) && !turnSpoken) return ManeuverPhase.TURN
        if (isPrepareDue(distM, timeToTurnSec) && !prepareSpoken) return ManeuverPhase.PREPARE
        if (isAheadDue(timeToTurnSec) && !aheadSpoken) return ManeuverPhase.AHEAD
        return null
    }

    private fun markAheadSkipped(timeToTurnSec: Float) {
        if (timeToTurnSec <= EARLY_WARNING_SEC) {
            aheadSpoken = true
        }
    }

    private fun resetManeuverFlags() {
        aheadSpoken = false
        prepareSpoken = false
        turnSpoken = false
        lastDistToManeuverM = null
        lastTimeToTurnSec = null
        continueAnnounced = false
    }

    private fun speak(text: String, flush: Boolean, force: Boolean = false) {
        if ((!enabled && !force) || !ttsReady || text.isBlank()) return
        requestAudioFocus()
        val utteranceId = "nav-${utteranceCounter.incrementAndGet()}"
        activeFocusUtteranceId = utteranceId
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }
        tts?.speak(text, queueMode, params, utteranceId)
    }

    private fun stopSpeaking() {
        tts?.stop()
        abandonAudioFocus()
        activeFocusUtteranceId = null
    }

    private fun requestAudioFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest == null) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(audioAttributes)
                    .build()
            }
            manager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            )
        }
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(null)
        }
    }

    companion object {
        private const val TAG = "NavigationVoiceController"
        private const val EARLY_WARNING_SEC = 35f
        private const val PREPARE_TIME_SEC = 12f
        private const val PREPARE_DISTANCE_M = 200f
        private const val TURN_TIME_SEC = 3f
        private const val TURN_DISTANCE_M = 40f
        private const val MIN_SPEED_MPS = 1.4f
        const val MIN_VOLUME = 0.5f
        const val MAX_VOLUME = 1.0f
        const val DEFAULT_VOLUME = 1.0f
    }
}
