package com.streetball.voicescore.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Wrapper around SpeechRecognizer that keeps continuous listening active.
 */
class VoiceRecognitionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onTranscript: (text: String, confidence: Float) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onErrorMessage: (String) -> Unit,
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var shouldListen: Boolean = false
    private var restartJob: Job? = null
    private var isListeningSessionActive: Boolean = false

    fun start() {
        if (shouldListen) return
        shouldListen = true

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onErrorMessage("Speech recognition unavailable on this device")
            onListeningChanged(false)
            shouldListen = false
            return
        }

        startListeningInternal(delayMs = 0L)
    }

    fun stop() {
        shouldListen = false
        restartJob?.cancel()
        restartJob = null

        mainHandler.post {
            onListeningChanged(false)
            isListeningSessionActive = false
            recognizer?.stopListening()
        }
    }

    fun destroy() {
        stop()
        mainHandler.post {
            recognizer?.destroy()
            recognizer = null
        }
    }

    private fun ensureRecognizer() {
        if (recognizer != null) return

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListeningSessionActive = true
                    onListeningChanged(true)
                }

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    isListeningSessionActive = false
                    // Keep logical listening state stable; automatic restart follows.
                }

                override fun onError(error: Int) {
                    isListeningSessionActive = false
                    onErrorMessage(errorToText(error))
                    if (shouldListen) {
                        startListeningInternal(delayMs = restartDelayForError(error))
                    } else {
                        onListeningChanged(false)
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListeningSessionActive = false
                    dispatchResults(results, defaultConfidence = 0.7f)
                    if (shouldListen) {
                        startListeningInternal(delayMs = 1_100L)
                    } else {
                        onListeningChanged(false)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    // Ignore partial hypotheses for score commits to reduce noise and state churn.
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun dispatchResults(bundle: Bundle?, defaultConfidence: Float) {
        val items = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        if (items.isEmpty()) return

        val confidence = bundle.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        val bestText = items.firstOrNull()?.trim().orEmpty()
        if (bestText.isEmpty()) return
        val bestConfidence = confidence?.getOrNull(0)?.takeIf { it >= 0f } ?: defaultConfidence
        onTranscript(bestText, bestConfidence)
    }

    private fun startListeningInternal(delayMs: Long) {
        restartJob?.cancel()
        restartJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            if (!shouldListen) return@launch

            mainHandler.post {
                if (!shouldListen) return@post
                ensureRecognizer()
                if (isListeningSessionActive) return@post
                recognizer?.startListening(buildRecognizerIntent())
            }
        }
    }

    private fun buildRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Avoid forcing offline-only engines; some devices fail to emit stable results in that mode.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            // Dictation mode reduces start/stop earcons on many Android speech engines.
            putExtra("android.speech.extra.DICTATION_MODE", true)
            // Slightly longer windows reduce stop/start cycling and audible earcons.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1_500L)
        }
    }

    private fun restartDelayForError(error: Int): Long {
        return when (error) {
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 900L
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 1_800L

            else -> 350L
        }
    }

    private fun errorToText(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio error"
            SpeechRecognizer.ERROR_CLIENT -> "Client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission missing"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
            else -> "Recognizer error $error"
        }
    }
}
