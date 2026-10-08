package com.icon.nexus.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class PlaybackEnergyTest {
    @Test
    fun rmsOfAKnownWaveform() {
        assertEquals(0f, WaveformEnergy.rms(byteArrayOf()), 0f)
        assertEquals(0f, WaveformEnergy.rms(byteArrayOf(128.toByte(), 128.toByte())), 0.0001f)
        assertEquals(1f, WaveformEnergy.rms(byteArrayOf(0, 0)), 0.0001f)
        val mixed = byteArrayOf(0, 128.toByte(), 0, 128.toByte())
        assertEquals(sqrt(0.5).toFloat(), WaveformEnergy.rms(mixed), 0.0001f)
        val longer = byteArrayOf(0, 128.toByte(), 0)
        assertEquals(sqrt(0.5).toFloat(), WaveformEnergy.rms(longer, length = 2), 0.0001f)
    }

    @Test
    fun waveformEnergyStaysClampedThroughAttackAndRelease() {
        val analyzer = AudioAnalyzer()
        val loud = WaveformEnergy.rms(byteArrayOf(0, 0))
        val first = analyzer.next(loud)
        assertEquals(AudioAnalyzer.DEFAULT_ATTACK, first, 0.0001f)
        assertTrue(first in 0f..1f)
        repeat(40) { analyzer.next(loud) }
        val high = analyzer.next(loud)
        val dropped = analyzer.next(WaveformEnergy.rms(byteArrayOf(128.toByte())))
        val fall = high - dropped
        assertTrue(dropped in 0f..1f)
        assertTrue(fall < AudioAnalyzer.DEFAULT_RELEASE + 0.02f)
        assertTrue(dropped < high)
        assertTrue(AudioAnalyzer.DEFAULT_ATTACK > AudioAnalyzer.DEFAULT_RELEASE)
    }

    @Test
    fun fallbackLevelRisesOnStartHoldsOnRangeAndReleasesOnDone() {
        val analyzer = AudioAnalyzer()
        assertEquals(1f, UtteranceEnergy.onStart(), 0f)
        assertEquals(UtteranceEnergy.ACTIVE, UtteranceEnergy.onRangeStart(), 0f)
        assertEquals(0f, UtteranceEnergy.onDone(), 0f)
        val started = analyzer.next(UtteranceEnergy.onStart())
        val held = analyzer.next(UtteranceEnergy.onRangeStart())
        assertTrue(held >= started)
        assertTrue(held <= 1f)
        val released = analyzer.next(UtteranceEnergy.onDone())
        assertTrue(released < held)
        assertTrue(released >= 0f)
    }
}
