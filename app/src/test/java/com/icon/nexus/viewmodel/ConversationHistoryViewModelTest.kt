package com.icon.nexus.viewmodel

import com.icon.nexus.ai.AIEvent
import com.icon.nexus.ai.AIManager
import com.icon.nexus.ai.AIProvider
import com.icon.nexus.ai.AIRequest
import com.icon.nexus.ai.DemoProvider
import com.icon.nexus.audio.QueuedSpeechSynthesizer
import com.icon.nexus.audio.SpeechInputGate
import com.icon.nexus.data.AppSettings
import com.icon.nexus.data.ConversationRepository
import com.icon.nexus.data.SettingsRepository
import com.icon.nexus.domain.AppState
import com.icon.nexus.domain.AppStateMachine
import com.icon.nexus.domain.Author
import com.icon.nexus.domain.Conversation
import com.icon.nexus.domain.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationHistoryViewModelTest {
    @After
    fun resetMainDispatcher() {
        runCatching { Dispatchers.resetMain() }
    }

    @Test
    fun newConversationClearsContext() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val repo = RecordingConversationRepository(
            listOf(thread("old", startedAt = 1L, user = "remember this", icon = "old answer")),
        )
        val gemini = RecordingGemini()
        val viewModel = liveViewModel(repo, gemini)
        assertEquals("remember this", viewModel.userLine.value)
        viewModel.sendText("next")
        assertEquals(listOf("remember this", "old answer"), gemini.histories.single().map { it.text })
        viewModel.newConversation()
        assertEquals("", viewModel.userLine.value)
        assertEquals("", viewModel.iconLine.value)
        assertEquals(AppState.Idle, viewModel.appState.value)
        viewModel.sendText("fresh")
        assertTrue(gemini.histories.last().isEmpty())
        assertEquals("fresh", viewModel.userLine.value)
        assertEquals("Reply.", viewModel.iconLine.value)
    }

    @Test
    fun continueLoadsTheSelectedThread() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val repo = RecordingConversationRepository(
            listOf(
                thread("older", startedAt = 1L, user = "alpha question", icon = "alpha answer"),
                thread("newer", startedAt = 2L, user = "beta question", icon = null),
            ),
        )
        val gemini = RecordingGemini()
        val viewModel = liveViewModel(repo, gemini)
        assertEquals("beta question", viewModel.userLine.value)
        assertEquals("", viewModel.iconLine.value)
        viewModel.continueConversation("older")
        assertEquals("alpha question", viewModel.userLine.value)
        assertEquals("alpha answer", viewModel.iconLine.value)
        viewModel.sendText("go")
        assertEquals(listOf("alpha question", "alpha answer"), gemini.histories.single().map { it.text })
        assertEquals("older", repo.get("older")?.id)
    }

    @Test
    fun deleteDropsTheThreadAndReplacesTheOpenOne() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val repo = RecordingConversationRepository(
            listOf(
                thread("older", startedAt = 1L, user = "alpha question", icon = "alpha answer"),
                thread("newer", startedAt = 2L, user = "beta question", icon = null),
            ),
        )
        val gemini = RecordingGemini()
        val viewModel = liveViewModel(repo, gemini)
        viewModel.deleteConversation("older")
        assertNull(repo.get("older"))
        assertEquals("beta question", viewModel.userLine.value)
        viewModel.deleteConversation("newer")
        assertNull(repo.get("newer"))
        assertEquals("", viewModel.userLine.value)
        assertEquals("", viewModel.iconLine.value)
        viewModel.sendText("after")
        assertTrue(gemini.histories.single().isEmpty())
        assertTrue(repo.list().none { it.id == "newer" || it.id == "older" })
    }

    @Test
    fun twoTurnsInOneConversationKeepOrder() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val repo = RecordingConversationRepository()
        val gemini = OrderedGemini()
        val viewModel = liveViewModel(repo, gemini)
        viewModel.sendText("one")
        viewModel.sendText("two")
        val messages = repo.list().single().messages
        assertEquals(listOf("one", "First.", "two", "Second."), messages.map { it.text })
        assertEquals(
            listOf(Author.User, Author.Assistant, Author.User, Author.Assistant),
            messages.map { it.author },
        )
        assertEquals(emptyList<String>(), gemini.histories[0])
        assertEquals(listOf("one", "First."), gemini.histories[1])
        assertEquals(AppState.Idle, viewModel.appState.value)
    }

    @Test
    fun partialIconTextUpdatesTheSameRow() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val repo = RecordingConversationRepository()
        val gemini = RecordingGemini(parts = listOf("Hel", "lo."), repo = repo)
        val viewModel = liveViewModel(repo, gemini)
        viewModel.sendText("hello")
        val stored = repo.list().single()
        val icon = stored.messages.filter { it.author == Author.Assistant }
        assertEquals(1, icon.size)
        assertEquals("icon-1", icon.single().id)
        assertEquals("Hello.", icon.single().text)
        assertEquals("hello", stored.messages.first { it.author == Author.User }.text)
        assertEquals(listOf("hello"), gemini.storedAtRequest.single())
    }

    @Test
    fun demoWritesNothing() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = RecordingConversationRepository()
        var nextId = 0L
        val initial = AppSettings.defaults()
        val repository = HistorySettingsRepository(initial)
        val viewModel = MainViewModel(
            machine = AppStateMachine { ++nextId },
            speechInput = SpeechInputGate(),
            synthesizer = QueuedSpeechSynthesizer(),
            aiManager = AIManager(repository, DemoProvider(), gemini = RecordingGemini()),
            settings = repository,
            initialSettings = initial,
            conversations = repo,
        )
        viewModel.onMicClicked()
        runCurrent()
        elapse(DEMO_LISTEN_MILLIS + DEMO_THINK_MILLIS + speakingMillis(demoTranscript[0].second))
        advanceUntilIdle()
        viewModel.sendText("hello")
        viewModel.newConversation()
        viewModel.deleteConversation("unused")
        assertEquals(0, repo.saves)
        assertEquals(0, repo.deletes)
        assertTrue(repo.list().isEmpty())
    }

    private fun TestScope.elapse(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    private fun liveViewModel(
        conversations: ConversationRepository,
        gemini: AIProvider,
    ): MainViewModel {
        var nextId = 0L
        val initial = AppSettings.defaults().copy(demoMode = false, apiKey = "test-key")
        val repository = HistorySettingsRepository(initial)
        val ids = ArrayDeque(listOf("created-1", "created-2"))
        return MainViewModel(
            machine = AppStateMachine { ++nextId },
            speechInput = SpeechInputGate(),
            synthesizer = QueuedSpeechSynthesizer(),
            aiManager = AIManager(repository, DemoProvider(), gemini),
            settings = repository,
            initialSettings = initial,
            gemini = gemini,
            conversations = conversations,
            conversationIds = { ids.removeFirst() },
            clock = { 1_000L },
        )
    }

    private fun thread(
        id: String,
        startedAt: Long,
        user: String,
        icon: String?,
    ): Conversation {
        val messages = mutableListOf(
            Message("user-$id", Author.User, user, 1L, startedAt),
        )
        if (icon != null) {
            messages += Message("icon-$id", Author.Assistant, icon, 1L, startedAt + 1)
        }
        return Conversation(id = id, startedAt = startedAt, messages = messages)
    }
}

private class OrderedGemini : AIProvider {
    val histories = mutableListOf<List<String>>()
    private var calls = 0

    override val id: String = "gemini"

    override fun streamReply(request: AIRequest): Flow<AIEvent> = flow {
        calls += 1
        histories += request.history.map { it.text }
        val reply = if (calls == 1) "First." else "Second."
        emit(AIEvent.Token(reply))
        emit(AIEvent.Completed(request.turnId))
    }
}

private class RecordingGemini(
    private val parts: List<String> = listOf("Reply."),
    private val repo: RecordingConversationRepository? = null,
) : AIProvider {
    val histories = mutableListOf<List<Message>>()
    val storedAtRequest = mutableListOf<List<String>>()

    override val id: String = "gemini"

    override fun streamReply(request: AIRequest): Flow<AIEvent> = flow {
        histories += request.history
        storedAtRequest += repo?.list()?.flatMap { thread -> thread.messages.map { it.text } }.orEmpty()
        parts.forEach { emit(AIEvent.Token(it)) }
        emit(AIEvent.Completed(request.turnId))
    }
}

private class RecordingConversationRepository(
    initial: List<Conversation> = emptyList(),
) : ConversationRepository {
    private val items = LinkedHashMap<String, Conversation>()
    var saves = 0
        private set
    var deletes = 0
        private set

    init {
        initial.forEach { items[it.id] = it }
    }

    override suspend fun list(): List<Conversation> = items.values.toList()

    override suspend fun get(id: String): Conversation? = items[id]

    override suspend fun save(conversation: Conversation) {
        saves += 1
        items[conversation.id] = conversation
    }

    override suspend fun delete(id: String) {
        deletes += 1
        items.remove(id)
    }
}

private class HistorySettingsRepository(
    initial: AppSettings,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<AppSettings> = state.asStateFlow()

    override suspend fun get(): AppSettings = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}
