package com.icon.nexus.data

import com.icon.nexus.domain.Author
import com.icon.nexus.domain.Conversation
import com.icon.nexus.domain.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class ConversationHistoryTest {
    @Test
    fun rolesMapOnlyUserAndIcon() {
        assertEquals(ConversationHistory.ROLE_USER, ConversationHistory.roleName(Author.User))
        assertEquals(ConversationHistory.ROLE_ICON, ConversationHistory.roleName(Author.Assistant))
        assertNull(ConversationHistory.roleName(Author.System))
        assertEquals(Author.User, ConversationHistory.authorOf(ConversationHistory.ROLE_USER))
        assertEquals(Author.Assistant, ConversationHistory.authorOf(ConversationHistory.ROLE_ICON))
        assertNull(ConversationHistory.authorOf("fact"))
    }

    @Test
    fun summaryUsesTheFirstLineAndStartTime() {
        assertEquals("New conversation", ConversationHistory.firstLine(emptyList()))
        val messages = listOf(
            Message(
                id = "1",
                author = Author.User,
                text = "Hello\nthere",
                turnId = 1L,
                timestamp = 5L,
            ),
        )
        assertEquals("Hello", ConversationHistory.firstLine(messages))
        val longLine = "a".repeat(80)
        val trimmed = ConversationHistory.firstLine(
            listOf(Message("1", Author.User, longLine, 1L)),
        )
        assertTrue(trimmed.endsWith("…"))
        assertTrue(trimmed.length < longLine.length)

        val summary = ConversationHistory.summaryOf(
            Conversation(id = "thread", startedAt = 50L, messages = messages),
        )
        assertEquals("thread", summary.id)
        assertEquals("Hello", summary.firstLine)
        assertEquals(50L, summary.startedAt)
        assertEquals(
            "Oct 8, 12:51 AM",
            ConversationHistory.formatStartedAt(
                Instant.parse("2026-10-08T00:51:00Z").toEpochMilli(),
                ZoneOffset.UTC,
            ),
        )
    }
}
