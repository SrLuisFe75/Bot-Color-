package com.icon.nexus.viewmodel

import com.icon.nexus.ai.AIManager
import com.icon.nexus.ai.AIProvider
import com.icon.nexus.ai.DemoProvider
import com.icon.nexus.ai.GeminiMessages
import com.icon.nexus.ai.GeminiProvider
import com.icon.nexus.audio.QueuedSpeechSynthesizer
import com.icon.nexus.audio.SpeechInputGate
import com.icon.nexus.data.AppSettings
import com.icon.nexus.data.InMemoryConversationRepository
import com.icon.nexus.data.SettingsRepository
import com.icon.nexus.domain.AppState
import com.icon.nexus.domain.AppStateMachine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class GeminiChatTest {
    @After
    fun resetMainDispatcher() {
        runCatching { Dispatchers.resetMain() }
    }

    @Test
    fun micTapDoesNotStartDemoWhenDemoModeIsOff() {
        val viewModel = chatViewModel(apiKey = "")
        viewModel.onMicClicked()
        assertTrue(viewModel.appState.value is AppState.Listening)
        assertEquals("", viewModel.userLine.value)
        assertEquals("", viewModel.iconLine.value)
        viewModel.onMicClicked()
        assertEquals(AppState.Idle, viewModel.appState.value)
        viewModel.onMicLongPress()
        assertEquals(AppState.Idle, viewModel.appState.value)
        assertTrue(viewModel.onUserInterrupt().isFailure)
    }

    @Test
    fun demoModeIgnoresTypedSend() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = chatViewModel(
            apiKey = "test-key",
            initial = AppSettings.defaults().copy(demoMode = true, apiKey = "test-key"),
        )
        viewModel.sendText("hello from the field")
        assertEquals(AppState.Idle, viewModel.appState.value)
        assertEquals("", viewModel.userLine.value)
    }

    @Test
    fun blankKeyMakesNoRequestAndEntersAlert() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val server = MockWebServer()
        server.start()
        try {
            val viewModel = chatViewModel(server = server, apiKey = "   ")
            viewModel.sendText("Hello")
            awaitCondition { viewModel.appState.value is AppState.Alert }
            assertEquals(0, server.requestCount)
            val alert = viewModel.appState.value as AppState.Alert
            assertEquals(GeminiMessages.BLANK_KEY, alert.message)
            assertEquals("Hello", viewModel.userLine.value)
            assertEquals("Alert", statusLabel(viewModel.appState.value))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun twoChunksAppendInOrderThenReturnToIdle() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val server = MockWebServer()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                started.countDown()
                release.await(5, TimeUnit.SECONDS)
                return sse("Hello ", "there.")
            }
        }
        server.start()
        try {
            val viewModel = chatViewModel(server = server, apiKey = "test-key")
            viewModel.sendText("Hello")
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertTrue(viewModel.appState.value is AppState.Thinking)
            assertEquals("Hello", viewModel.userLine.value)
            assertEquals("", viewModel.iconLine.value)
            release.countDown()
            awaitCondition {
                viewModel.appState.value == AppState.Idle && viewModel.iconLine.value == "Hello there."
            }
            assertEquals("Hello there.", viewModel.iconLine.value)
            assertEquals("Ready", statusLabel(viewModel.appState.value))
        } finally {
            release.countDown()
            server.shutdown()
        }
    }

    @Test
    fun unauthorizedAndRateLimitEnterAlertWithMessages() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        assertEquals(GeminiMessages.INVALID_KEY, chatHttpError(401))
        assertEquals(GeminiMessages.RATE_LIMIT, chatHttpError(429))
    }

    @Test
    fun cancelledTurnIgnoresALateChunk() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val server = MockWebServer()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val index = calls.incrementAndGet()
                if (index == 1) {
                    started.countDown()
                    release.await(5, TimeUnit.SECONDS)
                    return sse("late")
                }
                return sse("fresh")
            }
        }
        server.start()
        try {
            val viewModel = chatViewModel(server = server, apiKey = "test-key")
            viewModel.sendText("first")
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertTrue(viewModel.appState.value is AppState.Thinking)
            viewModel.sendText("second")
            awaitCondition {
                viewModel.appState.value == AppState.Idle && viewModel.iconLine.value == "fresh"
            }
            assertEquals("second", viewModel.userLine.value)
            assertFalse(viewModel.iconLine.value.contains("late"))
            release.countDown()
            awaitCondition { calls.get() >= 1 }
            kotlinx.coroutines.delay(200)
            assertEquals("fresh", viewModel.iconLine.value)
            assertEquals(AppState.Idle, viewModel.appState.value)
        } finally {
            release.countDown()
            server.shutdown()
        }
    }

    @Test
    fun interruptCancelsTheCallAndIgnoresALateChunk() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val server = MockWebServer()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                started.countDown()
                release.await(5, TimeUnit.SECONDS)
                return sse("late")
            }
        }
        server.start()
        try {
            val viewModel = chatViewModel(server = server, apiKey = "test-key")
            viewModel.sendText("hello")
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val thinking = viewModel.appState.value as AppState.Thinking
            viewModel.onMicLongPress()
            val listening = viewModel.appState.value as AppState.Listening
            assertTrue(listening.turnId != thinking.turnId)
            release.countDown()
            kotlinx.coroutines.delay(200)
            assertEquals(listening, viewModel.appState.value)
            assertFalse(viewModel.iconLine.value.contains("late"))
            assertEquals("hello", viewModel.userLine.value)
        } finally {
            release.countDown()
            server.shutdown()
        }
    }

    @Test
    fun newConversationClearsTheThread() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val server = MockWebServer()
        val bodies = java.util.Collections.synchronizedList(mutableListOf<String>())
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                bodies.add(request.body.readUtf8())
                val text = if (bodies.size == 1) "First reply." else "Second reply."
                return sse(text)
            }
        }
        server.start()
        try {
            val viewModel = chatViewModel(server = server, apiKey = "test-key")
            viewModel.sendText("hello one")
            awaitCondition { viewModel.iconLine.value == "First reply." }
            viewModel.newConversation()
            assertEquals("", viewModel.userLine.value)
            assertEquals("", viewModel.iconLine.value)
            assertEquals(AppState.Idle, viewModel.appState.value)
            viewModel.sendText("hello two")
            awaitCondition { viewModel.iconLine.value == "Second reply." }
            assertEquals("hello two", viewModel.userLine.value)
            assertTrue(bodies[0].contains("hello one"))
            assertTrue(bodies[1].contains("hello two"))
            assertFalse(bodies[1].contains("hello one"))
        } finally {
            server.shutdown()
        }
    }

    private suspend fun chatHttpError(code: Int): String {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(code).setBody("secret-test-key"))
        server.start()
        try {
            val viewModel = chatViewModel(server = server, apiKey = "secret-test-key")
            viewModel.sendText("Hi")
            awaitCondition { viewModel.appState.value is AppState.Alert }
            val alert = viewModel.appState.value as AppState.Alert
            assertFalse(alert.message.contains("secret-test-key"))
            return alert.message
        } finally {
            server.shutdown()
        }
    }

    private fun chatViewModel(
        server: MockWebServer? = null,
        apiKey: String,
        initial: AppSettings? = null,
    ): MainViewModel {
        var nextId = 0L
        val resolved = initial ?: AppSettings.defaults().copy(demoMode = false, apiKey = apiKey)
        val repository = ChatSettingsRepository(resolved)
        val provider: AIProvider = if (server == null) {
            UnusedGemini()
        } else {
            GeminiProvider(
                settings = repository,
                endpointRoot = server.url("v1beta").toString(),
            )
        }
        return MainViewModel(
            machine = AppStateMachine { ++nextId },
            speechInput = SpeechInputGate(),
            synthesizer = QueuedSpeechSynthesizer(),
            aiManager = AIManager(
                settings = repository,
                demo = DemoProvider(),
                gemini = provider,
            ),
            settings = repository,
            initialSettings = resolved,
            gemini = provider,
            conversations = InMemoryConversationRepository(),
        )
    }

    private suspend fun awaitCondition(predicate: () -> Boolean) {
        withTimeout(5_000) {
            while (!predicate()) {
                kotlinx.coroutines.delay(10)
            }
        }
    }

    private fun sse(vararg parts: String): MockResponse {
        val body = parts.joinToString(separator = "\n") { text ->
            "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"$text\"}]}}]}\n"
        }
        return MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "text/event-stream")
            .setBody(body)
    }
}

private class UnusedGemini : AIProvider {
    override val id: String = "gemini"

    override fun streamReply(request: com.icon.nexus.ai.AIRequest): Flow<com.icon.nexus.ai.AIEvent> {
        error("Gemini must not be called")
    }
}

private class ChatSettingsRepository(
    initial: AppSettings,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<AppSettings> = state.asStateFlow()

    override suspend fun get(): AppSettings = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}
