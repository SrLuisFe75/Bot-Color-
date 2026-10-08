package com.icon.nexus.memory

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Opt-in user memory. Not the conversation database.
 */
@Database(
    entities = [MemoryEntity::class, MemorySwitchEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun memories(): MemoryDao
}
