package com.icon.nexus.memory

/**
 * Opt-in facts the user asked ICON to keep. Separate from conversation
 * history. [add] stores nothing while remembering is off. The default is off.
 */
interface MemoryRepository {
    suspend fun isEnabled(): Boolean

    suspend fun setEnabled(enabled: Boolean)

    suspend fun add(text: String): UserMemory?

    suspend fun list(): List<UserMemory>

    suspend fun delete(id: String)

    suspend fun clear()
}

/**
 * Remembering stays off. Used until a persistent store is installed.
 */
object OffMemoryRepository : MemoryRepository {
    override suspend fun isEnabled(): Boolean = false

    override suspend fun setEnabled(enabled: Boolean) = Unit

    override suspend fun add(text: String): UserMemory? = null

    override suspend fun list(): List<UserMemory> = emptyList()

    override suspend fun delete(id: String) = Unit

    override suspend fun clear() = Unit
}
