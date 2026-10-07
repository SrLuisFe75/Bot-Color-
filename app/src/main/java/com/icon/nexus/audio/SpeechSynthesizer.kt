package com.icon.nexus.audio

/**
 * The only text-to-speech boundary. [enqueue] queues sentences for [turnId]
 * in order. [stop] halts playback immediately and drops the queue.
 */
interface SpeechSynthesizer {
    fun enqueue(turnId: Long, sentences: List<String>)

    fun stop()
}
