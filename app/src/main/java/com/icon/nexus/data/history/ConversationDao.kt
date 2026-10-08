package com.icon.nexus.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ConversationDao {
    @Transaction
    @Query("SELECT * FROM conversations ORDER BY started_at DESC")
    suspend fun loadAll(): List<ConversationWithMessages>

    @Transaction
    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun load(id: String): ConversationWithMessages?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(entity: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE conversation_id = :id")
    suspend fun deleteMessages(id: String)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)
}
