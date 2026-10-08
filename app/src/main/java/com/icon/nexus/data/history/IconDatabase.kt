package com.icon.nexus.data.history

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Chat history only. Opt-in user memory is a different store.
 */
@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class IconDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
}
