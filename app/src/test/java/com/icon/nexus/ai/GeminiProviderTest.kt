package com.icon.nexus.ai

import com.icon.nexus.data.AppSettings
import com.icon.nexus.data.SettingsRepository
import com.icon.nexus.domain.AssistantPersona
import com.icon.nexus.settings.SettingsDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiProviderTest {
    @Test
    fun blankKeyFailsWithoutOpeningAConnection() = runBlocking {
        val settings = FakeSettingsRepository(AppSettings.defaults())
        val provider = GeminiProvider(settings)
        val events = provider.streamReply(
            AIRequest(
                turnId = 1L,
                persona = AssistantPersona(
                    name = SettingsDefaults.ASSISTANT_NAME,
                    personality = SettingsDefaults.PERSONALITY,
                ),
                history = emptyList(),
                userText = "Hello",
            ),
        ).toList()

        assertEquals(1, events.size)
        val failure = events.single() as AIEvent.Failed
        assertTrue(failure.message.contains("blank"))
    }
}

private class FakeSettingsRepository(
    initial: AppSettings,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<AppSettings> = state.asStateFlow()

    override suspend fun get(): AppSettings = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}
