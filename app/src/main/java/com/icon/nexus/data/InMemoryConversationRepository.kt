package com.icon.nexus.data

import com.icon.nexus.domain.Conversation
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-local stand-in for tests. Live turns use Room.
 */
class InMemoryConversationRepository : ConversationRepository {
    private val conversations = ConcurrentHashMap<String, Conversation>()

    override suspend fun list(): List<Conversation> = conversations.values.toList()

    override suspend fun get(id: String): Conversation? = conversations[id]

    override suspend fun save(conversation: Conversation) {
        conversations[conversation.id] = conversation
    }

    override suspend fun delete(id: String) {
        conversations.remove(id)
    }
}
