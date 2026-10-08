package com.icon.nexus.visualizer

import com.icon.nexus.domain.AppState
import com.icon.nexus.domain.VisualThemeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class IconCoreThemeTest {
    @Test
    fun everyThemeIdResolvesToIconCore() {
        val ids = VisualThemeId.entries
        assertTrue(ids.contains(VisualThemeId.Core))
        var index = 0
        while (index < ids.size) {
            val theme = VisualizerThemes.forId(ids[index])
            assertSame(IconCoreTheme, theme)
            assertEquals(VisualThemeId.Core, theme.id)
            assertEquals("ICON CORE", theme.label)
            index += 1
        }
    }

    @Test
    fun moteFieldIsCappedAtFortyEight() {
        val field = CoreMotes()
        assertEquals(48, CoreMotes.CAP)
        assertEquals(CoreMotes.CAP, field.motes.size)
        assertTrue(field.motes.size <= 48)
        var index = 0
        while (index < field.motes.size) {
            assertTrue(field.motes[index].homeRadius > 0f)
            assertTrue(field.motes[index].size > 0f)
            index += 1
        }
    }

    @Test
    fun eachAppStateMapsToADistinctCoreMotion() {
        val states = listOf(
            AppState.Idle,
            AppState.Listening(1L),
            AppState.Thinking(1L),
            AppState.Speaking(1L),
            AppState.Alert("notice"),
        )
        val motions = states.map { state ->
            CoreMotion(
                orbit = coreOrbit(state),
                inward = coreInward(state),
                moteEnergy = coreMoteEnergy(state, 0f),
                alert = coreUsesAlertPalette(state),
            )
        }
        assertEquals(states.size, motions.toSet().size)
        assertEquals(coreOrbit(AppState.Listening(1L)), coreOrbit(AppState.Listening(9L)))
        assertEquals(coreInward(AppState.Thinking(1L)), coreInward(AppState.Thinking(9L)))
        assertTrue(coreInward(AppState.Listening(1L)) > 0f)
        assertEquals(0f, coreInward(AppState.Idle), 0f)
        assertTrue(coreUsesAlertPalette(AppState.Alert("notice")))
        assertTrue(!coreUsesAlertPalette(AppState.Idle))
        assertTrue(coreOrbit(AppState.Thinking(1L)) > coreOrbit(AppState.Speaking(1L)))
        assertTrue(coreOrbit(AppState.Speaking(1L)) > coreOrbit(AppState.Idle))
    }

    @Test
    fun audioLevelScalesTheSpeakingNucleus() {
        val breath = 0.5f
        val quiet = speakingNucleusScale(0f, breath)
        val underFloor = speakingNucleusScale(0.04f, breath)
        val atFloor = speakingNucleusScale(0.05f, breath)
        val mid = speakingNucleusScale(0.5f, breath)
        val loud = speakingNucleusScale(1f, breath)
        assertEquals(quiet, underFloor, 0.0001f)
        assertTrue(loud > mid)
        assertTrue(mid > atFloor)
        assertEquals(1.32f, loud, 0.0001f)
        assertEquals(0.96f + 0.08f * breath, quiet, 0.0001f)
    }
}

private data class CoreMotion(
    val orbit: Float,
    val inward: Float,
    val moteEnergy: Float,
    val alert: Boolean,
)
