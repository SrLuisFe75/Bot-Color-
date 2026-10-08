package com.icon.nexus.memory

/**
 * One fact the user typed. This is not a chat message.
 */
data class UserMemory(
    val id: String,
    val text: String,
    val timestamp: Long,
)

/**
 * Facts sent with a prompt. Remembering off yields none, even if rows exist.
 */
fun rememberedFacts(enabled: Boolean, memories: List<UserMemory>): List<String> {
    if (!enabled) return emptyList()
    return memories.map { it.text.trim() }.filter { it.isNotEmpty() }
}
