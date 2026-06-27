package com.kyuusanq3.mixauto.data.navigation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
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

    private var navStartSpoken = false
    private var trackedStepIndex = -1
    private var announced800 = false
    private var announced500 = false
    private var announced200 = false
    private var lastDistToManeuverM: Float? = null
    private var continueAnnounced = false
    private var countdownIntroSpoken = false
    private var lastCountdownSecond: Int? = null
    private var immediateSpoken = false

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

    fun onNavigationDrivingStarted(firstStep: NavStepPhrase) {
        if (!enabled || !ttsReady) return
        if (navStartSpoken) return
        navStartSpoken = true
        trackedStepIndex = 0
        resetManeuverFlags()
        speak("Starting navigation.", flush = true)
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
        val distM = context.distToNextManeuverM

        maybeAnnounceDistanceWarning(distM, upcoming.shortInstruction)

        if (distM <= IMMEDIATE_DISTANCE_M && !immediateSpoken) {
            immediateSpoken = true
            speak(NavTtsPhrases.buildImmediate(upcoming.shortInstruction), flush = true)
        }

        val speedMps = context.speedMps
        val speedKmh = speedMps * 3.6f
        if (speedMps < MIN_COUNTDOWN_SPEED_MPS || speedKmh >= COUNTDOWN_MAX_SPEED_KMH) {
            return
        }

        val effectiveSpeed = maxOf(speedMps, MIN_COUNTDOWN_SPEED_MPS)
        val timeToTurnSec = distM / effectiveSpeed
        if (timeToTurnSec > COUNTDOWN_ENTRY_SEC || distM <= COUNTDOWN_MIN_DISTANCE_M) {
            return
        }

        if (!countdownIntroSpoken) {
            countdownIntroSpoken = true
            speak(NavTtsPhrases.buildCountdownIntro(upcoming.shortInstruction), flush = true)
        }

        val secondsLeft = timeToTurnSec.toInt().coerceIn(1, COUNTDOWN_SECONDS)
        if (secondsLeft <= COUNTDOWN_SECONDS && lastCountdownSecond != secondsLeft) {
            lastCountdownSecond = secondsLeft
            speak(secondsLeft.toString(), flush = false)
        }
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

    fun onNavigationEnded() {
        stopSpeaking()
        navStartSpoken = false
        trackedStepIndex = -1
        announced800 = false
        announced500 = false
        announced200 = false
        lastDistToManeuverM = null
        continueAnnounced = false
        countdownIntroSpoken = false
        lastCountdownSecond = null
        immediateSpoken = false
    }

    fun shutdown() {
        onNavigationEnded()
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    private fun maybeAnnounceDistanceWarning(distM: Float, shortInstruction: String) {
        if (distM <= IMMEDIATE_DISTANCE_M) {
            lastDistToManeuverM = distM
            return
        }
        val tierToSpeak = selectDistanceTierToAnnounce(distM) ?: run {
            lastDistToManeuverM = distM
            return
        }
        markDistanceTierAnnounced(tierToSpeak, distM)
        speak(
            NavTtsPhrases.buildDistanceWarning(tierToSpeak, shortInstruction),
            flush = false,
        )
        lastDistToManeuverM = distM
    }

    /**
     * At most one distance cue per GPS tick. Uses threshold crossing when [lastDistToManeuverM]
     * is known; on first sample or after a GPS jump, speaks only the most urgent applicable tier
     * and marks skipped outer tiers so they are not read back-to-back.
     */
    private fun selectDistanceTierToAnnounce(distM: Float): Int? {
        val lastDist = lastDistToManeuverM

        if (lastDist != null) {
            DISTANCE_WARNING_TIERS.firstOrNull { tier ->
                lastDist > tier && distM <= tier && !isDistanceTierAnnounced(tier)
            }?.let { return it }
        }

        val stillRelevant = DISTANCE_WARNING_TIERS.filter { tier ->
            distM <= tier &&
                !isDistanceTierAnnounced(tier) &&
                isDistanceTierStillRelevant(tier, distM)
        }
        if (stillRelevant.isEmpty()) return null

        return stillRelevant.minOrNull()
    }

    /** Do not catch up a far tier when already deep inside it (e.g. skip 800 m at 400 m out). */
    private fun isDistanceTierStillRelevant(tierM: Int, distM: Float): Boolean {
        return distM > tierM * DISTANCE_TIER_RELEVANCE_FRACTION
    }

    private fun markDistanceTierAnnounced(spokenTier: Int, distM: Float) {
        DISTANCE_WARNING_TIERS
            .filter { tier -> distM <= tier && tier >= spokenTier }
            .forEach { setDistanceTierAnnounced(it, true) }
    }

    private fun isDistanceTierAnnounced(tierM: Int): Boolean = when (tierM) {
        800 -> announced800
        500 -> announced500
        200 -> announced200
        else -> false
    }

    private fun setDistanceTierAnnounced(tierM: Int, announced: Boolean) {
        when (tierM) {
            800 -> announced800 = announced
            500 -> announced500 = announced
            200 -> announced200 = announced
        }
    }

    private fun resetManeuverFlags() {
        announced800 = false
        announced500 = false
        announced200 = false
        lastDistToManeuverM = null
        continueAnnounced = false
        countdownIntroSpoken = false
        lastCountdownSecond = null
        immediateSpoken = false
    }

    private fun speak(text: String, flush: Boolean) {
        if (!enabled || !ttsReady || text.isBlank()) return
        requestAudioFocus()
        val utteranceId = "nav-${utteranceCounter.incrementAndGet()}"
        activeFocusUtteranceId = utteranceId
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(text, queueMode, null, utteranceId)
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
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(audioAttributes)
                    .build()
            }
            manager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
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
        private const val IMMEDIATE_DISTANCE_M = 50f
        private const val COUNTDOWN_SECONDS = 5
        private const val COUNTDOWN_ENTRY_SEC = 5.5f
        private const val COUNTDOWN_MIN_DISTANCE_M = 30f
        private const val COUNTDOWN_MAX_SPEED_KMH = 70f
        private const val MIN_COUNTDOWN_SPEED_MPS = 1.4f
        private val DISTANCE_WARNING_TIERS = listOf(800, 500, 200)
        /** Below this fraction of a tier, a missed outer warning is skipped on catch-up. */
        private const val DISTANCE_TIER_RELEVANCE_FRACTION = 0.6f
    }
}
