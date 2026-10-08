package com.icon.nexus.audio

/**
 * Queue that satisfies [SpeechSynthesizer] without speaking. [stop] clears
 * every queued sentence immediately. Each queued sentence also reports
 * completion so a caller can advance without a real engine.
 */
class QueuedSpeechSynthesizer : SpeechSynthesizer {
    private val queue = ArrayDeque<Pair<Long, String>>()
    private var listener: SpeechPlaybackListener? = null

    val pending: List<Pair<Long, String>>
        get() = queue.toList()

    override fun setPlaybackListener(listener: SpeechPlaybackListener?) {
        this.listener = listener
    }

    override fun enqueue(turnId: Long, sentences: List<String>) {
        val playback = listener
        sentences.forEach { sentence ->
            queue.addLast(turnId to sentence)
            playback?.onUtteranceFinished(turnId)
        }
    }

    override fun stop() {
        queue.clear()
    }
}
