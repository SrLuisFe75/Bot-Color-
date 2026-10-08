package com.icon.nexus.privacy

import com.icon.nexus.ai.AIEvent
import com.icon.nexus.ai.AIManager
import com.icon.nexus.ai.AIProvider
import com.icon.nexus.ai.AIRequest
import com.icon.nexus.ai.DemoProvider
import com.icon.nexus.audio.QueuedSpeechSynthesizer
import com.icon.nexus.audio.SpeechInputGate
import com.icon.nexus.data.AppSettings
import com.icon.nexus.data.InMemoryConversationRepository
import com.icon.nexus.data.SettingsRepository
import com.icon.nexus.domain.AppState
import com.icon.nexus.domain.AppStateMachine
import com.icon.nexus.domain.Author
import com.icon.nexus.domain.Conversation
import com.icon.nexus.domain.Message
import com.icon.nexus.memory.MemoryRepository
import com.icon.nexus.memory.UserMemory
import com.icon.nexus.settings.ModelProviderId
import com.icon.nexus.viewmodel.MainViewModel
import com.icon.nexus.viewmodel.microphoneIsLive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyTest {
    @After
    fun resetMainDispatcher() {
        runCatching { Dispatchers.resetMain() }
    }

    @Test
    fun listeningShowsTheMicAsLiveAndIdleDoesNot() {
        assertTrue(microphoneIsLive(AppState.Listening(1L)))
        assertFalse(microphoneIsLive(AppState.Idle))
        assertFalse(microphoneIsLive(AppState.Thinking(1L)))
        assertFalse(microphoneIsLive(AppState.Speaking(1L)))
        assertFalse(microphoneIsLive(AppState.Alert("Preview")))
    }

    @Test
    fun deleteLocalDataRemovesHistoryMemoriesAndTheKey() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val conversations = InMemoryConversationRepository()
        conversations.save(
            Conversation(
                id = "old",
                startedAt = 10L,
                messages = listOf(
                    Message(
                        id = "m1",
                        author = Author.User,
                        text = "hello",
                        turnId = 1L,
                        timestamp = 10L,
                    ),
                ),
            ),
        )
        val memory = PrivacyMemoryStore()
        memory.setEnabled(true)
        memory.add("I like tea")
        val initial = AppSettings.defaults().copy(
            demoMode = false,
            apiKey = "secret-key",
            onboardingComplete = true,
            provider = ModelProviderId.GEMINI,
        )
        val settings = PrivacySettingsRepository(initial)
        val viewModel = privacyViewModel(
            initial = initial,
            settings = settings,
            conversations = conversations,
            memory = memory,
            speech = SpeechInputGate(),
        )
        viewModel.deleteLocalData()
        val saved = settings.get()
        assertEquals("", saved.apiKey)
        assertFalse(saved.demoMode)
        assertTrue(saved.onboardingComplete)
        assertEquals(ModelProviderId.GEMINI, saved.provider)
        assertEquals("", viewModel.apiKey.value)
        assertTrue(memory.list().isEmpty())
        assertTrue(memory.isEnabled())
        assertTrue(viewModel.memories.value.isEmpty())
        val remaining = conversations.list()
        assertEquals(1, remaining.size)
        assertEquals("fresh-thread", remaining.single().id)
        assertTrue(remaining.single().messages.isEmpty())
        assertNull(conversations.get("old"))
        assertEquals("", viewModel.userLine.value)
        assertEquals("", viewModel.iconLine.value)
    }

    @Test
    fun startupInIdleDoesNotStartTheRecognizer() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val speech = SpeechInputGate()
        val initial = AppSettings.defaults().copy(
            demoMode = false,
            apiKey = "secret-key",
            onboardingComplete = true,
            provider = ModelProviderId.GEMINI,
        )
        val viewModel = privacyViewModel(
            initial = initial,
            settings = PrivacySettingsRepository(initial),
            conversations = InMemoryConversationRepository(),
            memory = PrivacyMemoryStore(),
            speech = speech,
        )
        assertTrue(viewModel.appState.value is AppState.Idle)
        assertNull(speech.activeTurnId)
        assertFalse(speech.isActive)
    }

    private fun privacyViewModel(
        initial: AppSettings,
        settings: SettingsRepository,
        conversations: InMemoryConversationRepository,
        memory: MemoryRepository,
        speech: SpeechInputGate,
    ): MainViewModel {
        var nextId = 0L
        val gemini = PrivacyGemini()
        return MainViewModel(
            machine = AppStateMachine { ++nextId },
            speechInput = speech,
            synthesizer = QueuedSpeechSynthesizer(),
            aiManager = AIManager(settings, DemoProvider(), gemini),
            settings = settings,
            initialSettings = initial,
            gemini = gemini,
            conversations = conversations,
            liveSpeech = speech,
            memory = memory,
            clock = { 50L },
            conversationIds = { "fresh-thread" },
        )
    }
}

private class PrivacySettingsRepository(
    initial: AppSettings,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<AppSettings> = state.asStateFlow()

    override suspend fun get(): AppSettings = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

private class PrivacyMemoryStore : MemoryRepository {
    private var enabled = false
    private val rows = LinkedHashMap<String, UserMemory>()
    private var nextId = 0

    override suspend fun isEnabled(): Boolean = enabled

    override suspend fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override suspend fun add(text: String): UserMemory? {
        val trimmed = text.trim()
        if (!enabled || trimmed.isEmpty()) return null
        nextId += 1
        val memory = UserMemory(id = "mem-$nextId", text = trimmed, timestamp = nextId.toLong())
        rows[memory.id] = memory
        return memory
    }

    override suspend fun list(): List<UserMemory> = rows.values.toList()

    override suspend fun delete(id: String) {
        rows.remove(id)
    }

    override suspend fun clear() {
        rows.clear()
    }
}

private class PrivacyGemini : AIProvider {
    override val id: String = "gemini"

    override fun streamReply(request: AIRequest): Flow<AIEvent> = flow { }
}
