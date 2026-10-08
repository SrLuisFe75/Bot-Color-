package com.icon.nexus.viewmodel

import com.icon.nexus.ai.AIEvent
import com.icon.nexus.ai.AIManager
import com.icon.nexus.ai.AIProvider
import com.icon.nexus.ai.AIRequest
import com.icon.nexus.ai.DemoProvider
import com.icon.nexus.ai.geminiSystemInstruction
import com.icon.nexus.audio.QueuedSpeechSynthesizer
import com.icon.nexus.audio.SpeechInputGate
import com.icon.nexus.data.AppSettings
import com.icon.nexus.data.InMemoryConversationRepository
import com.icon.nexus.data.SettingsRepository
import com.icon.nexus.domain.AppStateMachine
import com.icon.nexus.domain.Author
import com.icon.nexus.domain.Conversation
import com.icon.nexus.domain.Message
import com.icon.nexus.memory.MemoryRepository
import com.icon.nexus.memory.UserMemory
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
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserMemoryViewModelTest {
    @After
    fun resetMainDispatcher() {
        runCatching { Dispatchers.resetMain() }
    }

    @Test
    fun switchDefaultsOffAndMemoryStaysOutOfChat() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val conversations = InMemoryConversationRepository()
        val seed = Conversation(
            id = "thread",
            startedAt = 5L,
            messages = listOf(Message("u", Author.User, "earlier", 1L, 5L)),
        )
        conversations.save(seed)
        val memory = FakeMemoryStore()
        val gemini = MemoryGemini()
        val viewModel = viewModel(conversations, memory, gemini)

        assertFalse(viewModel.memoryEnabled.value)
        viewModel.addMemory("I like tea")
        assertTrue(viewModel.memories.value.isEmpty())
        assertEquals(seed.messages, conversations.get("thread")?.messages)

        viewModel.sendText("hello")
        val hidden = promptOf(gemini.requests.single())
        assertFalse(hidden.contains("I like tea"))
        assertFalse(hidden.contains("facts"))
        assertTrue(hidden.contains("Reply in natural spoken sentences."))
        assertFalse(gemini.requests.single().history.any { it.text == "I like tea" })

        viewModel.setMemoryEnabled(true)
        assertTrue(viewModel.memoryEnabled.value)
        viewModel.addMemory("I like tea")
        assertEquals(listOf("I like tea"), viewModel.memories.value.map { it.text })
        viewModel.sendText("again")
        val shown = promptOf(gemini.requests.last())
        assertTrue(shown.contains("The user asked you to keep these facts: I like tea."))
        assertTrue(shown.contains("Do not use Markdown."))
        assertFalse(gemini.requests.last().history.any { it.text == "I like tea" })

        val id = viewModel.memories.value.single().id
        viewModel.deleteMemory(id)
        assertTrue(viewModel.memories.value.isEmpty())
        viewModel.sendText("after delete")
        assertFalse(promptOf(gemini.requests.last()).contains("I like tea"))

        viewModel.addMemory("I like tea")
        viewModel.clearMemories()
        assertTrue(viewModel.memories.value.isEmpty())
        viewModel.sendText("after clear")
        assertFalse(promptOf(gemini.requests.last()).contains("I like tea"))

        val texts = conversations.get("thread")?.messages.orEmpty().map { it.text }
        assertTrue(texts.contains("earlier"))
        assertTrue(texts.contains("hello"))
        assertFalse(texts.contains("I like tea"))
    }

    private fun promptOf(request: AIRequest): String {
        return geminiSystemInstruction(
            name = request.persona.name,
            personality = request.persona.personality,
            memories = request.memories,
        )
    }

    private fun viewModel(
        conversations: InMemoryConversationRepository,
        memory: MemoryRepository,
        gemini: AIProvider,
    ): MainViewModel {
        var nextId = 0L
        val initial = AppSettings.defaults().copy(demoMode = false, apiKey = "test-key")
        val settings = MemorySettingsRepository(initial)
        return MainViewModel(
            machine = AppStateMachine { ++nextId },
            speechInput = SpeechInputGate(),
            synthesizer = QueuedSpeechSynthesizer(),
            aiManager = AIManager(settings, DemoProvider(), gemini),
            settings = settings,
            initialSettings = initial,
            gemini = gemini,
            conversations = conversations,
            memory = memory,
        )
    }
}

private class MemoryGemini : AIProvider {
    val requests = mutableListOf<AIRequest>()

    override val id: String = "gemini"

    override fun streamReply(request: AIRequest): Flow<AIEvent> = flow {
        requests += request
        emit(AIEvent.Token("Reply."))
        emit(AIEvent.Completed(request.turnId))
    }
}

private class FakeMemoryStore : MemoryRepository {
    private var enabled = false
    private val items = LinkedHashMap<String, UserMemory>()
    private var next = 0

    override suspend fun isEnabled(): Boolean = enabled

    override suspend fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override suspend fun add(text: String): UserMemory? {
        val trimmed = text.trim()
        if (!enabled || trimmed.isEmpty()) return null
        next += 1
        val memory = UserMemory(id = "m$next", text = trimmed, timestamp = next.toLong())
        items[memory.id] = memory
        return memory
    }

    override suspend fun list(): List<UserMemory> = items.values.toList()

    override suspend fun delete(id: String) {
        items.remove(id)
    }

    override suspend fun clear() {
        items.clear()
    }
}

private class MemorySettingsRepository(
    initial: AppSettings,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<AppSettings> = state.asStateFlow()

    override suspend fun get(): AppSettings = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}
