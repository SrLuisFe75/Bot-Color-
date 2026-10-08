package com.icon.nexus.data.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "messages",
    primaryKeys = ["conversation_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversation_id")],
)
data class MessageEntity(
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    val id: String,
    val role: String,
    val text: String,
    val timestamp: Long,
    val position: Int,
    @ColumnInfo(name = "turn_id") val turnId: Long,
)
