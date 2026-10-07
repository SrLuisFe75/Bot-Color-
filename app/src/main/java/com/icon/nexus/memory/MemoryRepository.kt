package com.icon.nexus.memory

/**
 * Opt-in facts about the user. This store is not conversation history.
 * [put] is meaningful only while [isEnabled] is true; callers leave it off
 * until the user opts in.
 */
interface MemoryRepository {
    suspend fun isEnabled(): Boolean

    suspend fun setEnabled(enabled: Boolean)

    suspend fun put(key: String, value: String)

    suspend fun get(key: String): String?

    suspend fun entries(): Map<String, String>

    suspend fun remove(key: String)

    suspend fun clear()
}
