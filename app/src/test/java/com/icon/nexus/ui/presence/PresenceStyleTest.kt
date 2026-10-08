package com.icon.nexus.ui.presence

import com.icon.nexus.domain.AppState
import com.icon.nexus.ui.theme.IconPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceStyleTest {
    @Test
    fun idleBreathesSlowlyInANarrowCyanRange() {
        val idle = presenceStyleFor(AppState.Idle)
        assertEquals(0.96f, idle.scaleMin, 0.0001f)
        assertEquals(1.04f, idle.scaleMax, 0.0001f)
        assertFalse(idle.alert)
        assertEquals(IconPalette.CORE, idle.coreColor)
        assertTrue(idle.breathPeriodMillis > presenceStyleFor(AppState.Listening(1L)).breathPeriodMillis)
        assertTrue(idle.breathPeriodMillis > presenceStyleFor(AppState.Thinking(1L)).breathPeriodMillis)
        assertTrue(idle.breathPeriodMillis > presenceStyleFor(AppState.Speaking(1L)).breathPeriodMillis)
        assertTrue(idle.pulse < presenceStyleFor(AppState.Speaking(1L)).pulse)
    }

    @Test
    fun listeningPullsInwardAndThinkingIsBusier() {
        val listening = presenceStyleFor(AppState.Listening(1L))
        val thinking = presenceStyleFor(AppState.Thinking(1L))
        val speaking = presenceStyleFor(AppState.Speaking(1L))
        assertTrue(listening.inward > thinking.inward)
        assertTrue(thinking.inward > speaking.inward)
        assertTrue(thinking.ringCycles > speaking.ringCycles)
        assertTrue(speaking.ringCycles > listening.ringCycles)
        assertTrue(thinking.breathPeriodMillis < listening.breathPeriodMillis)
    }

    @Test
    fun speakingHasTheStrongestPulseAndAWiderScale() {
        val idle = presenceStyleFor(AppState.Idle)
        val speaking = presenceStyleFor(AppState.Speaking(4L))
        val thinking = presenceStyleFor(AppState.Thinking(4L))
        assertTrue(speaking.pulse > thinking.pulse)
        assertTrue(speaking.pulse > presenceStyleFor(AppState.Listening(4L)).pulse)
        assertTrue((speaking.scaleMax - speaking.scaleMin) > (idle.scaleMax - idle.scaleMin))
        assertFalse(speaking.alert)
    }

    @Test
    fun alertUsesVioletAndIsDistinctFromTheCyanCore() {
        val alert = presenceStyleFor(AppState.Alert("notice"))
        assertTrue(alert.alert)
        assertEquals(IconPalette.VIOLET and 0xFFFFFF, alert.ringColor and 0xFFFFFF)
        assertTrue((alert.ringColor and 0xFFFFFF) != 0xC6A36A)
        assertTrue((alert.coreColor and 0xFFFFFF) != 0xC6A36A)
        assertTrue((alert.glowColor and 0xFFFFFF) != 0xC6A36A)
        assertTrue(alert.coreColor != IconPalette.CORE)
        assertTrue(alert.inward == 0f)
        assertFalse(presenceStyleFor(AppState.Idle).alert)
    }

    @Test
    fun turnIdDoesNotChangeThePalette() {
        assertEquals(
            presenceStyleFor(AppState.Listening(1L)),
            presenceStyleFor(AppState.Listening(99L)),
        )
        assertEquals(
            presenceStyleFor(AppState.Thinking(1L)),
            presenceStyleFor(AppState.Thinking(8L)),
        )
        assertEquals(
            presenceStyleFor(AppState.Speaking(1L)),
            presenceStyleFor(AppState.Speaking(8L)),
        )
    }
}
