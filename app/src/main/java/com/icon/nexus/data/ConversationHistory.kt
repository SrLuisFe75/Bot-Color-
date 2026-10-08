package com.icon.nexus.data

import com.icon.nexus.domain.Author
import com.icon.nexus.domain.Conversation
import com.icon.nexus.domain.Message
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure mapping for chat history. Roles are only `user` and `ICON`.
 * This is not the opt-in memory store.
 */
object ConversationHistory {
    const val ROLE_USER = "user"
    const val ROLE_ICON = "ICON"
    private const val EMPTY_LINE = "New conversation"
    private const val LINE_LIMIT = 72

    fun roleName(author: Author): String? = when (author) {
        Author.User -> ROLE_USER
        Author.Assistant -> ROLE_ICON
        Author.System -> null
    }

    fun authorOf(role: String): Author? = when (role) {
        ROLE_USER -> Author.User
        ROLE_ICON -> Author.Assistant
        else -> null
    }

    fun firstLine(messages: List<Message>): String {
        val raw = messages.firstOrNull { it.text.isNotBlank() }?.text ?: return EMPTY_LINE
        val line = raw.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return EMPTY_LINE
        return if (line.length <= LINE_LIMIT) line else line.take(LINE_LIMIT).trimEnd() + "…"
    }

    fun summaryOf(conversation: Conversation): ConversationSummary {
        return ConversationSummary(
            id = conversation.id,
            firstLine = firstLine(conversation.messages),
            startedAt = conversation.startedAt,
        )
    }

    fun formatStartedAt(
        startedAtMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        if (startedAtMillis <= 0L) return ""
        val local = Instant.ofEpochMilli(startedAtMillis).atZone(zone)
        return TIME.format(local)
    }

    private val TIME: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US)
}

data class ConversationSummary(
    val id: String,
    val firstLine: String,
    val startedAt: Long,
)
