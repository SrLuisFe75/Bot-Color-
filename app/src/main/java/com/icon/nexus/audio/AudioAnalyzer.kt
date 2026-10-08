package com.icon.nexus.audio

/**
 * Attack/release envelope. Output stays in 0..1. Rising input uses [attack]
 * (fast). Falling input uses [release] (slower).
 */
class AudioAnalyzer(
    private val attack: Float = DEFAULT_ATTACK,
    private val release: Float = DEFAULT_RELEASE,
) {
    private var envelope: Float = 0f

    init {
        require(attack in 0f..1f) { "attack must be inside 0..1" }
        require(release in 0f..1f) { "release must be inside 0..1" }
        require(attack > release) { "attack must be faster than release" }
    }

    fun next(sample: Float): Float {
        val target = sample.coerceIn(0f, 1f)
        val coefficient = if (target > envelope) attack else release
        envelope = (envelope + (target - envelope) * coefficient).coerceIn(0f, 1f)
        return envelope
    }

    fun reset() {
        envelope = 0f
    }

    companion object {
        const val DEFAULT_ATTACK = 0.62f
        const val DEFAULT_RELEASE = 0.12f
    }
}
