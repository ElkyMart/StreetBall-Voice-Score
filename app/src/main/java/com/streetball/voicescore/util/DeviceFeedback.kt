package com.streetball.voicescore.util

import android.media.AudioManager
import android.media.ToneGenerator

object DeviceFeedback {

    private val toneLock = Any()
    private var toneGenerator: ToneGenerator? = null

    fun playScoreAcceptedBeep() {
        synchronized(toneLock) {
            val tone = toneGenerator ?: ToneGenerator(AudioManager.STREAM_MUSIC, 65).also {
                toneGenerator = it
            }
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 110)
        }
    }

    fun release() {
        synchronized(toneLock) {
            toneGenerator?.release()
            toneGenerator = null
        }
    }
}
