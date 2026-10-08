package com.icon.nexus.ai

import com.icon.nexus.domain.AssistantPersona
import com.icon.nexus.domain.Message
import com.icon.nexus.language.ReplyLanguage
import kotlinx.coroutines.flow.Flow

data class AIRequest(
    val turnId: Long,
    val persona: AssistantPersona,
    val history: List<Message>,
    val userText: String,
    val memories: List<String> = emptyList(),
    val replyLanguage: ReplyLanguage? = null,
    val followLatestLanguage: Boolean = false,
)

sealed interface AIEvent {
    data class Token(val text: String) : AIEvent

    data class Completed(val turnId: Long) : AIEvent

    data class Failed(val message: String) : AIEvent
}

/**
 * The only model boundary. [DemoProvider] is the default. Cloud providers
 * sit behind this same contract.
 */
interface AIProvider {
    val id: String

    fun streamReply(request: AIRequest): Flow<AIEvent>
}
