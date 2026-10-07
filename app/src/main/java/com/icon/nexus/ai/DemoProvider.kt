package com.icon.nexus.ai

import com.icon.nexus.settings.ModelProviderId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Scripted local replies. This provider never opens a socket.
 */
class DemoProvider : AIProvider {
    override val id: String = ModelProviderId.DEMO.wireName

    override fun streamReply(request: AIRequest): Flow<AIEvent> = flow {
        LOCAL_SCRIPT.forEach { sentence ->
            emit(AIEvent.Token(sentence))
        }
        emit(AIEvent.Completed(request.turnId))
    }

    private companion object {
        val LOCAL_SCRIPT = listOf(
            "ICON online.",
            "This reply stays on the device.",
        )
    }
}
