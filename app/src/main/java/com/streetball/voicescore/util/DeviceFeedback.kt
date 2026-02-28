package com.streetball.voicescore.util

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context

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

    fun vibrateShort(context: Context) {
        val durationMs = 80L

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            val vibrator = manager?.defaultVibrator
            vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            return
        }

        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        @Suppress("DEPRECATION")
        vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun release() {
        synchronized(toneLock) {
            toneGenerator?.release()
            toneGenerator = null
        }
    }
}
