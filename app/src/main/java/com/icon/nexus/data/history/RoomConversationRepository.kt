package com.icon.nexus.data.history

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.icon.nexus.data.ConversationHistory
import com.icon.nexus.data.ConversationRepository
import com.icon.nexus.domain.Conversation

/**
 * Room-backed chat history. Demo sessions are not written here.
 */
class RoomConversationRepository(
    private val database: IconDatabase,
) : ConversationRepository {
    private val dao = database.conversations()

    override suspend fun list(): List<Conversation> {
        return dao.loadAll().map { it.toDomain() }
    }

    override suspend fun get(id: String): Conversation? = dao.load(id)?.toDomain()

    override suspend fun save(conversation: Conversation) {
        val rows = conversation.messages.mapIndexedNotNull { index, message ->
            val role = ConversationHistory.roleName(message.author) ?: return@mapIndexedNotNull null
            MessageEntity(
                conversationId = conversation.id,
                id = message.id,
                role = role,
                text = message.text,
                timestamp = message.timestamp,
                position = index,
                turnId = message.turnId,
            )
        }
        database.withTransaction {
            dao.upsertConversation(
                ConversationEntity(
                    id = conversation.id,
                    startedAt = conversation.startedAt,
                ),
            )
            dao.deleteMessages(conversation.id)
            if (rows.isNotEmpty()) dao.upsertMessages(rows)
        }
    }

    override suspend fun delete(id: String) {
        dao.deleteConversation(id)
    }

    companion object {
        fun create(context: Context): RoomConversationRepository {
            val database = Room.databaseBuilder(
                context.applicationContext,
                IconDatabase::class.java,
                DATABASE_NAME,
            ).build()
            return RoomConversationRepository(database)
        }

        private const val DATABASE_NAME = "icon-conversations.db"
    }
}
