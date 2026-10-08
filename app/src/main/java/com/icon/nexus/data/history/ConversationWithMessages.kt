package com.icon.nexus.data.history

import androidx.room.Embedded
import androidx.room.Relation
import com.icon.nexus.data.ConversationHistory
import com.icon.nexus.domain.Conversation
import com.icon.nexus.domain.Message

data class ConversationWithMessages(
    @Embedded val conversation: ConversationEntity,
    @Relation(parentColumn = "id", entityColumn = "conversation_id")
    val messages: List<MessageEntity>,
) {
    fun toDomain(): Conversation {
        val ordered = messages.sortedBy { it.position }
        return Conversation(
            id = conversation.id,
            startedAt = conversation.startedAt,
            messages = ordered.mapNotNull { row ->
                val author = ConversationHistory.authorOf(row.role) ?: return@mapNotNull null
                Message(
                    id = row.id,
                    author = author,
                    text = row.text,
                    turnId = row.turnId,
                    timestamp = row.timestamp,
                )
            },
        )
    }
}
