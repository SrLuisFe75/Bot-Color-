package com.icon.nexus.data

import com.icon.nexus.domain.Conversation

/**
 * Chat history only. Separate from [com.icon.nexus.memory.MemoryRepository].
 * Live turns use the Room implementation. Demo sessions are not saved.
 */
interface ConversationRepository {
    suspend fun list(): List<Conversation>

    suspend fun get(id: String): Conversation?

    suspend fun save(conversation: Conversation)

    suspend fun delete(id: String)
}
