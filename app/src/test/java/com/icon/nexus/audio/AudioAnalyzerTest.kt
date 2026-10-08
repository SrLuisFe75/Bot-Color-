package com.icon.nexus.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioAnalyzerTest {
    @Test
    fun attackRisesQuickly() {
        val analyzer = AudioAnalyzer()
        val first = analyzer.next(1f)
        val second = analyzer.next(1f)
        assertEquals(AudioAnalyzer.DEFAULT_ATTACK, first, 0.0001f)
        assertTrue(second > first)
        assertTrue(first > 0.4f)
    }

    @Test
    fun releaseFallsSlowerThanAttack() {
        val rising = AudioAnalyzer()
        val rose = rising.next(1f)

        val falling = AudioAnalyzer()
        repeat(40) { falling.next(1f) }
        val high = falling.next(1f)
        val dropped = falling.next(0f)
        val fall = high - dropped

        assertTrue(rose > fall)
        assertTrue(fall < AudioAnalyzer.DEFAULT_RELEASE + 0.02f)
        assertTrue(dropped < high)
        assertTrue(dropped > 0f)
    }

    @Test
    fun outputStaysInsideZeroToOne() {
        val analyzer = AudioAnalyzer()
        val samples = floatArrayOf(-5f, 0f, 0.2f, 3f, 1f, 0f, 100f, -0.01f)
        samples.forEach { sample ->
            val level = analyzer.next(sample)
            assertTrue(level in 0f..1f)
        }
        assertEquals(0f, AudioAnalyzer().next(-2f), 0.0001f)
        val pinned = AudioAnalyzer()
        val overshot = pinned.next(50f)
        assertTrue(overshot <= 1f)
        assertEquals(AudioAnalyzer.DEFAULT_ATTACK, overshot, 0.0001f)
    }

    @Test
    fun releaseTakesMoreStepsThanAttackToCoverTheSameSpan() {
        val attackSteps = stepsUntil(AudioAnalyzer()) { analyzer ->
            analyzer.next(1f) >= 0.9f
        }
        val releaseAnalyzer = AudioAnalyzer()
        repeat(80) { releaseAnalyzer.next(1f) }
        val releaseSteps = stepsUntil(releaseAnalyzer) { analyzer ->
            analyzer.next(0f) <= 0.1f
        }
        assertTrue(attackSteps < releaseSteps)
    }

    private fun stepsUntil(analyzer: AudioAnalyzer, done: (AudioAnalyzer) -> Boolean): Int {
        var steps = 0
        while (steps < 100 && !done(analyzer)) {
            steps += 1
        }
        return steps
    }
}
