package com.icon.nexus.audio

/**
 * The only text-to-speech boundary. [enqueue] queues sentences for [turnId]
 * in order. [stop] halts playback immediately and drops the queue.
 * [available] is false when the engine is missing or failed to start.
 */
interface SpeechSynthesizer {
    val available: Boolean
        get() = true

    fun enqueue(turnId: Long, sentences: List<String>)

    fun stop()

    fun setPlaybackListener(listener: SpeechPlaybackListener?) {}

    fun release() {}
}

interface SpeechPlaybackListener {
    fun onUtteranceFinished(turnId: Long)

    fun onSpeechUnavailable() {}

    fun onUtteranceStarted(turnId: Long) {}

    fun onUtteranceRange(turnId: Long) {}
}
