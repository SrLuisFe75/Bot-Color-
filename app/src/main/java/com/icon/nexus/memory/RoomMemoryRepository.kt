package com.icon.nexus.memory

import android.content.Context
import androidx.room.Room
import java.util.UUID

/**
 * Persistent memory in its own database. Chat tables are not written here.
 */
class RoomMemoryRepository(
    private val database: MemoryDatabase,
    private val ids: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = { System.currentTimeMillis() },
) : MemoryRepository {
    private val dao = database.memories()

    override suspend fun isEnabled(): Boolean = dao.enabled() ?: false

    override suspend fun setEnabled(enabled: Boolean) {
        dao.setSwitch(MemorySwitchEntity(enabled = enabled))
    }

    override suspend fun add(text: String): UserMemory? {
        val trimmed = text.trim()
        if (!isEnabled() || trimmed.isEmpty()) return null
        val row = MemoryEntity(
            id = ids(),
            text = trimmed,
            timestamp = clock(),
        )
        dao.insert(row)
        return UserMemory(id = row.id, text = row.text, timestamp = row.timestamp)
    }

    override suspend fun list(): List<UserMemory> {
        return dao.list().map { UserMemory(id = it.id, text = it.text, timestamp = it.timestamp) }
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    override suspend fun clear() {
        dao.clear()
    }

    companion object {
        fun create(context: Context): RoomMemoryRepository {
            val database = Room.databaseBuilder(
                context.applicationContext,
                MemoryDatabase::class.java,
                DATABASE_NAME,
            ).build()
            return RoomMemoryRepository(database)
        }

        private const val DATABASE_NAME = "icon-memory.db"
    }
}
