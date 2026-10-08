package com.icon.nexus.viewmodel

import com.icon.nexus.ai.AIEvent
import com.icon.nexus.ai.AIManager
import com.icon.nexus.ai.AIProvider
import com.icon.nexus.ai.AIRequest
import com.icon.nexus.ai.DemoProvider
import com.icon.nexus.ai.GeminiProvider
import com.icon.nexus.audio.MicrophonePermission
import com.icon.nexus.audio.QueuedSpeechSynthesizer
import com.icon.nexus.audio.SpeechEvent
import com.icon.nexus.audio.SpeechEventListener
import com.icon.nexus.audio.SpeechFailure
import com.icon.nexus.audio.SpeechInput
import com.icon.nexus.audio.SpeechMessages
import com.icon.nexus.audio.speechErrorMessage
import com.icon.nexus.audio.speechFailureForCode
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
import kotlinx.coroutines.flow.flow
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
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class SpeechRecognitionTest {
    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        runCatching { Dispatchers.resetMain() }
    }

    @Test
    fun permissionDeniedStaysIdle() {
        val speech = FakeSpeechInput()
        val viewModel = speechViewModel(speech = speech, granted = false)
        viewModel.onMicClicked()

        assertEquals(AppState.Idle, viewModel.appState.value)
        assertTrue(viewModel.microphoneExplanation.value)
        assertEquals(
            "ICON needs the microphone to hear one utterance.",
            SpeechMessages.EXPLANATION,
        )
        assertFalse(speech.isActive)
        assertTrue(speech.started.isEmpty())

        viewModel.onMicrophonePermissionResult(false)

        assertEquals(AppState.Idle, viewModel.appState.value)
        assertEquals(SpeechMessages.PERMISSION, viewModel.chatError.value)
        assertFalse(viewModel.microphoneExplanation.value)
        assertFalse(speech.isActive)
        assertEquals("Ready", statusLabel(viewModel.appState.value))
    }

    @Test
    fun emptyResultReturnsToIdleWithoutCallingGemini() {
        val speech = FakeSpeechInput()
        var calls = 0
        val viewModel = speechViewModel(
            speech = speech,
            gemini = object : AIProvider {
                override val id: String = "gemini"
                override fun streamReply(request: AIRequest): Flow<AIEvent> = flow {
                    calls += 1
                    emit(AIEvent.Completed(request.turnId))
                }
            },
        )
        viewModel.onMicClicked()
        val listening = viewModel.appState.value as AppState.Listening
        speech.emitResult(listening.turnId, "   ")

        assertEquals(AppState.Idle, viewModel.appState.value)
        assertEquals(SpeechMessages.EMPTY, viewModel.chatError.value)
        assertEquals("", viewModel.userLine.value)
        assertEquals(0, calls)
        assertFalse(speech.isActive)
        assertEquals(1, speech.started.size)
    }

    @Test
    fun recognizerErrorsReturnToIdle() {
        assertEquals(SpeechMessages.EMPTY, leaveWith(SpeechFailure.NoMatch))
        assertEquals(SpeechMessages.BUSY, leaveWith(SpeechFailure.Busy))
        assertEquals(SpeechMessages.NETWORK, leaveWith(SpeechFailure.Network))
        assertEquals(SpeechMessages.PERMISSION, leaveWith(SpeechFailure.Permission))
        assertEquals(SpeechFailure.NoMatch, speechFailureForCode(6))
        assertEquals(SpeechFailure.NoMatch, speechFailureForCode(7))
        assertEquals(SpeechFailure.Busy, speechFailureForCode(8))
        assertEquals(SpeechFailure.Network, speechFailureForCode(1))
        assertEquals(SpeechFailure.Network, speechFailureForCode(2))
        assertEquals(SpeechFailure.Permission, speechFailureForCode(9))
        assertEquals(SpeechMessages.EMPTY, speechErrorMessage(SpeechFailure.NoMatch))
    }

    @Test
    fun staleTurnIsIgnored() {
        val speech = FakeSpeechInput()
        val viewModel = speechViewModel(speech = speech)
        viewModel.onMicClicked()
        val first = viewModel.appState.value as AppState.Listening
        viewModel.onMicClicked()
        assertEquals(AppState.Idle, viewModel.appState.value)
        viewModel.onMicClicked()
        val second = viewModel.appState.value as AppState.Listening
        assertTrue(second.turnId != first.turnId)

        speech.emitResult(first.turnId, "from the old turn")

        assertEquals(second, viewModel.appState.value)
        assertEquals("", viewModel.userLine.value)
        assertEquals("", viewModel.iconLine.value)
        assertEquals(null, viewModel.chatError.value)
        assertTrue(speech.isActive)
    }

    @Test
    fun backgroundStopsListening() {
        val speech = FakeSpeechInput()
        val viewModel = speechViewModel(speech = speech)
        viewModel.onMicClicked()
        assertTrue(viewModel.appState.value is AppState.Listening)
        assertTrue(speech.isActive)

        assertEquals(AppState.Idle, viewModel.onAppBackgrounded().getOrThrow())

        assertEquals(AppState.Idle, viewModel.appState.value)
        assertFalse(speech.isActive)
        assertFalse(viewModel.microphoneExplanation.value)
    }

    @Test
    fun secondTapCancelsListening() {
        val speech = FakeSpeechInput()
        val viewModel = speechViewModel(speech = speech)
        viewModel.onMicClicked()
        assertTrue(speech.isActive)
        viewModel.onMicClicked()
        assertEquals(AppState.Idle, viewModel.appState.value)
        assertFalse(speech.isActive)
        assertEquals(null, viewModel.chatError.value)
    }

    @Test
    fun micStaysBlockedWhileThinking() = runBlocking {
        val speech = FakeSpeechInput()
        val server = MockWebServer()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                started.countDown()
                release.await(5, TimeUnit.SECONDS)
                return sse("later")
            }
        }
        server.start()
        try {
            val viewModel = speechViewModel(speech = speech, server = server, apiKey = "test-key")
            viewModel.sendText("hold")
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val thinking = viewModel.appState.value as AppState.Thinking
            viewModel.onMicClicked()
            assertEquals(thinking, viewModel.appState.value)
            assertFalse(speech.isActive)
        } finally {
            release.countDown()
            server.shutdown()
        }
    }

    @Test
    fun interruptCancelsGeminiBeforeListening() = runBlocking {
        val speech = FakeSpeechInput()
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
            val viewModel = speechViewModel(speech = speech, server = server, apiKey = "test-key")
            viewModel.sendText("hello")
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val thinking = viewModel.appState.value as AppState.Thinking
            assertFalse(speech.isActive)

            viewModel.onMicLongPress()

            val listening = viewModel.appState.value as AppState.Listening
            assertTrue(listening.turnId != thinking.turnId)
            assertEquals(listening.turnId, speech.activeTurnId)
            assertTrue(speech.started.last() == listening.turnId)
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
    fun recognizedTextUsesTheGeminiSendPath() = runBlocking {
        val speech = FakeSpeechInput()
        val server = MockWebServer()
        server.enqueue(sse("On it."))
        server.start()
        try {
            val viewModel = speechViewModel(speech = speech, server = server, apiKey = "test-key")
            viewModel.onMicClicked()
            val listening = viewModel.appState.value as AppState.Listening
            speech.emitResult(listening.turnId, "What time is it")
            awaitCondition {
                viewModel.appState.value == AppState.Idle && viewModel.iconLine.value == "On it."
            }
            assertEquals("What time is it", viewModel.userLine.value)
            assertEquals(null, viewModel.chatError.value)
            assertFalse(speech.isActive)
            val body = server.takeRequest().body.readUtf8()
            assertTrue(body.contains("What time is it"))
        } finally {
            server.shutdown()
        }
    }

    private fun leaveWith(reason: SpeechFailure): String {
        val speech = FakeSpeechInput()
        val viewModel = speechViewModel(speech = speech)
        viewModel.onMicClicked()
        val listening = viewModel.appState.value as AppState.Listening
        speech.emitFailure(listening.turnId, reason)
        assertEquals(AppState.Idle, viewModel.appState.value)
        assertFalse(speech.isActive)
        return viewModel.chatError.value.orEmpty()
    }

    private fun speechViewModel(
        speech: FakeSpeechInput,
        granted: Boolean = true,
        server: MockWebServer? = null,
        apiKey: String = "",
        gemini: AIProvider? = null,
    ): MainViewModel {
        var nextId = 0L
        val initial = AppSettings.defaults().copy(demoMode = false, apiKey = apiKey)
        val repository = SpeechSettingsRepository(initial)
        val provider = gemini ?: if (server == null) {
            object : AIProvider {
                override val id: String = "gemini"
                override fun streamReply(request: AIRequest): Flow<AIEvent> {
                    error("Gemini must not be called")
                }
            }
        } else {
            GeminiProvider(
                settings = repository,
                endpointRoot = server.url("v1beta").toString(),
            )
        }
        return MainViewModel(
            machine = AppStateMachine { ++nextId },
            speechInput = speech,
            synthesizer = QueuedSpeechSynthesizer(),
            aiManager = AIManager(
                settings = repository,
                demo = DemoProvider(),
                gemini = provider,
            ),
            settings = repository,
            initialSettings = initial,
            gemini = provider,
            conversations = InMemoryConversationRepository(),
            liveSpeech = speech,
            microphone = MicrophonePermission { granted },
        )
    }

    private suspend fun awaitCondition(predicate: () -> Boolean) {
        withTimeout(5_000) {
            while (!predicate()) {
                kotlinx.coroutines.delay(10)
            }
        }
    }

    private fun sse(text: String): MockResponse {
        val body = "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"$text\"}]}}]}\n"
        return MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "text/event-stream")
            .setBody(body)
    }
}

private class FakeSpeechInput : SpeechInput {
    var activeTurnId: Long? = null
    private var events: SpeechEventListener? = null
    val started = mutableListOf<Long>()

    override val isActive: Boolean
        get() = activeTurnId != null

    override fun startListening(turnId: Long) {
        activeTurnId = turnId
        started += turnId
    }

    override fun stopListening() {
        activeTurnId = null
    }

    override fun setListener(listener: SpeechEventListener?) {
        events = listener
    }

    fun emitResult(turnId: Long, text: String) {
        events?.onSpeechEvent(SpeechEvent.Result(turnId, text))
    }

    fun emitFailure(turnId: Long, reason: SpeechFailure) {
        events?.onSpeechEvent(SpeechEvent.Failure(turnId, reason))
    }
}

private class SpeechSettingsRepository(
    initial: AppSettings,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<AppSettings> = state.asStateFlow()

    override suspend fun get(): AppSettings = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}
