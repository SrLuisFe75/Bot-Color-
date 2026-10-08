package com.icon.nexus.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceSplitterTest {
    @Test
    fun splitsPeriodQuestionAndExclamation() {
        val split = splitCompletedSentences("Hello. How are you? Great!")
        assertEquals(listOf("Hello.", "How are you?", "Great!"), split.sentences)
        assertEquals("", split.remainder)
    }

    @Test
    fun keepsAnUnfinishedTail() {
        val split = splitCompletedSentences("Hello. How are")
        assertEquals(listOf("Hello."), split.sentences)
        assertEquals(" How are", split.remainder)
    }

    @Test
    fun keepsTextWithNoTerminator() {
        val split = splitCompletedSentences("Not yet")
        assertEquals(emptyList<String>(), split.sentences)
        assertEquals("Not yet", split.remainder)
    }

    @Test
    fun groupsRepeatedMarksIntoOneSentence() {
        val split = splitCompletedSentences("Really?! Yes.")
        assertEquals(listOf("Really?!", "Yes."), split.sentences)
        assertEquals("", split.remainder)
    }
}
