package com.icon.nexus.domain

enum class Author {
    User,
    Assistant,
    System,
}

data class Message(
    val id: String,
    val author: Author,
    val text: String,
    val turnId: Long,
    val timestamp: Long = 0L,
)
