package com.icon.nexus.domain

data class Conversation(
    val id: String,
    val startedAt: Long = 0L,
    val messages: List<Message> = emptyList(),
)
