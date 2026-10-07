package com.icon.nexus.audio

/**
 * Queue that satisfies [SpeechSynthesizer] without speaking. [stop] clears
 * every queued sentence immediately.
 */
class QueuedSpeechSynthesizer : SpeechSynthesizer {
    private val queue = ArrayDeque<Pair<Long, String>>()

    val pending: List<Pair<Long, String>>
        get() = queue.toList()

    override fun enqueue(turnId: Long, sentences: List<String>) {
        sentences.forEach { sentence ->
            queue.addLast(turnId to sentence)
        }
    }

    override fun stop() {
        queue.clear()
    }
}
