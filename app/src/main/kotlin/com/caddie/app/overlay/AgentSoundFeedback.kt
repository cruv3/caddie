package com.caddie.app.overlay

import android.media.AudioManager
import android.media.ToneGenerator

/** Plays short one-shot cues for normal-agent outcomes and user decisions. */
class AgentSoundFeedback : AutoCloseable {
    private val tones = runCatching {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME_PERCENT)
    }.getOrNull()

    fun play(cue: Cue) {
        tones?.stopTone()
        tones?.startTone(cue.tone, cue.durationMs)
    }

    override fun close() {
        tones?.release()
    }

    enum class Cue(internal val tone: Int, internal val durationMs: Int) {
        SUCCESS(ToneGenerator.TONE_PROP_ACK, 120),
        ERROR(ToneGenerator.TONE_PROP_NACK, 220),
        QUESTION(ToneGenerator.TONE_PROP_PROMPT, 140),
        CONFIRMATION(ToneGenerator.TONE_PROP_BEEP2, 220),
    }

    private companion object {
        const val VOLUME_PERCENT = 65
    }
}
