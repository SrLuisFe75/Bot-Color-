package com.icon.nexus.data

import com.icon.nexus.domain.Conversation

/**
 * Conversation history. Separate from [com.icon.nexus.memory.MemoryRepository].
 */
interface ConversationRepository {
    suspend fun list(): List<Conversation>

    suspend fun get(id: String): Conversation?

    suspend fun save(conversation: Conversation)

    suspend fun delete(id: String)
}
