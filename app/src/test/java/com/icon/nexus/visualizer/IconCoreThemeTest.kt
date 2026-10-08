package com.icon.nexus.visualizer

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
}
