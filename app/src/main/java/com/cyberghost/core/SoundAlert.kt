package com.cyberghost.core

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class SoundAlert(context: Context) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()

    // use system notification sound
    private val hookSoundId = pool.load(context, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI.hashCode(), 1)
    var enabled = true

    fun playHookAlert() {
        if (enabled) {
            try { pool.play(hookSoundId, 0.5f, 0.5f, 1, 0, 1f) } catch (_: Exception) {}
        }
    }

    fun release() { pool.release() }
}
