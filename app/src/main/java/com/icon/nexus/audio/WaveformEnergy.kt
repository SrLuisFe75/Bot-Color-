package com.icon.nexus.audio

import kotlin.math.sqrt

/**
 * RMS of an 8-bit playback waveform. Silence is byte 128. The result is
 * clamped to 0..1 and allocates nothing.
 */
object WaveformEnergy {
    fun rms(waveform: ByteArray, length: Int = waveform.size): Float {
        val count = length.coerceIn(0, waveform.size)
        if (count == 0) return 0f
        var sum = 0.0
        var index = 0
        while (index < count) {
            val centered = (waveform[index].toInt() and 0xFF) - CENTER
            val sample = centered / CENTER.toDouble()
            sum += sample * sample
            index += 1
        }
        return sqrt(sum / count).toFloat().coerceIn(0f, 1f)
    }

    private const val CENTER = 128
}
