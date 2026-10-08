package com.icon.nexus.audio

/**
 * Fallback samples when the output-mix visualizer cannot be created.
 * [onStart] and [onRangeStart] hold a full-scale target. [onDone] releases.
 */
object UtteranceEnergy {
    const val ACTIVE = 1f
    const val RELEASED = 0f

    fun onStart(): Float = ACTIVE

    fun onRangeStart(): Float = ACTIVE

    fun onDone(): Float = RELEASED
}
