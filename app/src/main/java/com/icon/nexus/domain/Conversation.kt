package com.icon.nexus.domain

data class Conversation(
    val id: String,
    val messages: List<Message> = emptyList(),
)
