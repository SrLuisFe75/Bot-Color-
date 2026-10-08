package com.icon.nexus.language

import com.icon.nexus.ai.geminiSystemInstruction
import com.icon.nexus.data.AppSettings
import com.icon.nexus.settings.SettingsDefaults
import com.icon.nexus.ui.presence.presenceStyleFor
import com.icon.nexus.ui.theme.IconPalette
import com.icon.nexus.visualizer.CoreLooks
import com.icon.nexus.visualizer.LISTENING_CORE_SCALE
import com.icon.nexus.domain.AppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ReplyLanguageTest {
    @Test
    fun spanishEnglishAndMixedTextResolve() {
        assertEquals(ReplyLanguage.Spanish, detectReplyLanguage("¿Cómo estás?"))
        assertEquals(ReplyLanguage.Spanish, detectReplyLanguage("Hola, necesito ayuda con esto"))
        assertEquals(ReplyLanguage.English, detectReplyLanguage("Hello, how are you?"))
        assertEquals(ReplyLanguage.English, detectReplyLanguage("I need help with this"))
        assertEquals(ReplyLanguage.English, detectReplyLanguage("Estoy bien. How are you?"))
        assertEquals(ReplyLanguage.Spanish, detectReplyLanguage("I am fine. ¿Cómo estás?"))
        assertEquals(ReplyLanguage.Spanish, detectReplyLanguage("Hello ¿cómo estás?"))
        assertEquals(
            ReplyLanguage.Spanish,
            detectReplyLanguage("ok", previous = ReplyLanguage.Spanish),
        )
        assertEquals(
            ReplyLanguage.English,
            detectReplyLanguage("   ", previous = ReplyLanguage.English),
        )
    }

    @Test
    fun autoIsTheDefaultAndDoesNotPinTheRecognizer() {
        assertEquals("", SettingsDefaults.LANGUAGE_TAG)
        assertEquals("", AppSettings.defaults().languageTag)
        assertEquals(LanguageChoice.Auto, languageChoice(SettingsDefaults.LANGUAGE_TAG))
        assertEquals(LanguageChoice.Auto, languageChoice("auto"))
        val auto = recognitionPlan("")
        assertNull(auto.pinLanguage)
        assertTrue(auto.detectLanguage)
        val spanish = recognitionPlan(SPANISH_TAG)
        assertEquals(SPANISH_TAG, spanish.pinLanguage)
        assertFalse(spanish.detectLanguage)
        val english = recognitionPlan(ENGLISH_TAG)
        assertEquals(ENGLISH_TAG, english.pinLanguage)
        assertFalse(english.detectLanguage)
        assertEquals("es-MX", recognitionPlan("es-MX").pinLanguage)
        assertEquals("ja-JP", recognitionPlan("ja-JP").pinLanguage)
        assertFalse(recognitionPlan("ja-JP").detectLanguage)
        assertEquals("es", Locale.forLanguageTag(SPANISH_TAG).toLanguageTag())
        assertEquals("en", Locale.ENGLISH.toLanguageTag())
    }

    @Test
    fun systemInstructionFollowsEachMode() {
        val spanishText = "¿Cómo estás?"
        val englishText = "Hello, how are you?"
        assertEquals(ReplyLanguage.Spanish, replyLanguageFor("", spanishText))
        assertEquals(ReplyLanguage.English, replyLanguageFor("", englishText))
        assertEquals(ReplyLanguage.Spanish, replyLanguageFor("es", englishText))
        assertEquals(ReplyLanguage.English, replyLanguageFor("en", spanishText))
        assertNull(replyLanguageFor("ja-JP", englishText))

        val spanish = geminiSystemInstruction(
            name = "ICON",
            personality = "Calm",
            replyLanguage = ReplyLanguage.Spanish,
        )
        assertTrue(spanish.contains("Reply in natural spoken sentences."))
        assertTrue(spanish.contains("Reply in Spanish."))
        assertTrue(spanish.contains("Do not translate unless the user asks."))
        assertFalse(spanish.contains("Reply in English."))

        val english = geminiSystemInstruction(
            name = "ICON",
            personality = "Calm",
            replyLanguage = ReplyLanguage.English,
        )
        assertTrue(english.contains("Reply in English."))
        assertFalse(english.contains("Reply in Spanish."))

        val auto = geminiSystemInstruction(
            name = "ICON",
            personality = "Calm",
            replyLanguage = replyLanguageFor("", spanishText),
            followLatest = true,
        )
        assertTrue(auto.contains("Reply in Spanish."))
        assertTrue(auto.contains("follow the language of the latest user message."))
        assertTrue(auto.contains("Do not translate unless the user asks."))

        val pinnedEnglish = geminiSystemInstruction(
            name = "ICON",
            personality = "Calm",
            replyLanguage = replyLanguageFor("en", spanishText),
            followLatest = false,
        )
        assertTrue(pinnedEnglish.contains("Reply in English."))
        assertFalse(pinnedEnglish.contains("latest user message"))

        val untouched = geminiSystemInstruction(name = "ICON", personality = "Calm")
        assertFalse(untouched.contains("Reply in Spanish."))
        assertFalse(untouched.contains("Reply in English."))
    }

    @Test
    fun alertNoLongerUsesAmber() {
        assertFalse(CoreLooks.alertUsesAmber())
        assertFalse(CoreLooks.anyAmber())
        val alert = presenceStyleFor(AppState.Alert("notice"))
        assertEquals(IconPalette.VIOLET and 0xFFFFFF, alert.ringColor and 0xFFFFFF)
        assertFalse(CoreLooks.usesAmber(alert.ringColor))
        assertFalse(CoreLooks.usesAmber(alert.coreColor))
        assertFalse(CoreLooks.usesAmber(alert.glowColor))
        assertFalse(CoreLooks.usesAmber(IconPalette.VIOLET))
        assertTrue(LISTENING_CORE_SCALE > 1.04f)
    }
}
