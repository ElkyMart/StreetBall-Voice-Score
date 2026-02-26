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
            recognizer?.cancel()
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
                    onListeningChanged(true)
                }

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    onListeningChanged(false)
                }

                override fun onError(error: Int) {
                    onListeningChanged(false)
                    onErrorMessage(errorToText(error))
                    if (shouldListen) {
                        startListeningInternal(delayMs = restartDelayForError(error))
                    }
                }

                override fun onResults(results: Bundle?) {
                    onListeningChanged(false)
                    dispatchResults(results, defaultConfidence = 0.7f)
                    if (shouldListen) {
                        startListeningInternal(delayMs = 120L)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    dispatchResults(partialResults, defaultConfidence = 0.6f)
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun dispatchResults(bundle: Bundle?, defaultConfidence: Float) {
        val items = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        if (items.isEmpty()) return

        val confidence = bundle.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        items.forEachIndexed { index, text ->
            val itemConfidence = confidence?.getOrNull(index)?.takeIf { it >= 0f } ?: defaultConfidence
            onTranscript(text, itemConfidence)
        }
    }

    private fun startListeningInternal(delayMs: Long) {
        restartJob?.cancel()
        restartJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            if (!shouldListen) return@launch

            mainHandler.post {
                if (!shouldListen) return@post
                ensureRecognizer()
                recognizer?.cancel()
                recognizer?.startListening(buildRecognizerIntent())
            }
        }
    }

    private fun buildRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 450L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 350L)
        }
    }

    private fun restartDelayForError(error: Int): Long {
        return when (error) {
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 250L
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 120L

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
