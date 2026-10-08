package com.icon.nexus.audio

/**
 * Playback energy for one Speaking turn. [start] returns false when the
 * platform capture cannot be created. [release] is safe to call more than once.
 */
interface PlaybackCapture {
    fun start(): Boolean

    fun release()

    fun setLevelListener(listener: ((Float) -> Unit)?) {}
}

/**
 * Used when no platform capture is installed. Speaking then follows the
 * utterance envelope instead.
 */
object UnavailablePlaybackCapture : PlaybackCapture {
    override fun start(): Boolean = false

    override fun release() = Unit
}
