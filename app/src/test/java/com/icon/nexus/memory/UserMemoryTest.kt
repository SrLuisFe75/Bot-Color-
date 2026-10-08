package com.icon.nexus.memory

import com.icon.nexus.ai.geminiSystemInstruction
import com.icon.nexus.settings.SettingsDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserMemoryTest {
    @Test
    fun offOmitsFactsAndOnKeepsPlainSentences() {
        val stored = listOf(UserMemory(id = "1", text = "I like tea", timestamp = 1L))
        assertEquals(emptyList<String>(), rememberedFacts(enabled = false, memories = stored))
        assertEquals(listOf("I like tea"), rememberedFacts(enabled = true, memories = stored))

        val plain = geminiSystemInstruction(
            name = SettingsDefaults.ASSISTANT_NAME,
            personality = SettingsDefaults.PERSONALITY,
            memories = rememberedFacts(false, stored),
        )
        assertFalse(plain.contains("I like tea"))
        assertFalse(plain.contains("facts"))
        assertTrue(plain.contains("Reply in natural spoken sentences."))
        assertTrue(plain.contains("Do not use Markdown."))
        assertTrue(plain.contains("Do not use symbol lists."))

        val withFacts = geminiSystemInstruction(
            name = SettingsDefaults.ASSISTANT_NAME,
            personality = SettingsDefaults.PERSONALITY,
            memories = rememberedFacts(true, stored),
        )
        assertTrue(withFacts.contains("The user asked you to keep these facts: I like tea."))
        assertTrue(withFacts.contains("Reply in natural spoken sentences."))
        assertFalse(withFacts.contains("- "))
    }
}
