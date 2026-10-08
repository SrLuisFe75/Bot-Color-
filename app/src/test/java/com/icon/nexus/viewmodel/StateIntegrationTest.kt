package com.icon.nexus.viewmodel

import com.icon.nexus.ai.AIEvent
import com.icon.nexus.ai.AIManager
import com.icon.nexus.ai.AIProvider
import com.icon.nexus.ai.AIRequest
import com.icon.nexus.ai.DemoProvider
import com.icon.nexus.ai.GeminiMessages
import com.icon.nexus.audio.MicrophonePermission
import com.icon.nexus.audio.SpeechEvent
import com.icon.nexus.audio.SpeechEventListener
import com.icon.nexus.audio.SpeechInput
import com.icon.nexus.audio.SpeechPlaybackListener
import com.icon.nexus.audio.SpeechSynthesizer
import com.icon.nexus.data.AppSettings
import com.icon.nexus.data.InMemoryConversationRepository
import com.icon.nexus.data.SettingsRepository
import com.icon.nexus.domain.AppState
import com.icon.nexus.domain.AppStateMachine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StateIntegrationTest {
    @After
    fun resetMainDispatcher() {
        runCatching { Dispatchers.resetMain() }
    }

    @Test
    fun turnWalksIdleListeningThinkingSpeakingIdle() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val speech = IntegrationSpeech()
        val synth = IntegrationSynth()
        val gate = CompletableDeferred<Unit>()
        val viewModel = integrationViewModel(
            speech = speech,
            synth = synth,
            gemini = object : AIProvider {
                override val id: String = "gemini"
                override fun streamReply(request: AIRequest): Flow<AIEvent> = flow {
                    gate.await()
                    emit(AIEvent.Token("Hello there."))
                    emit(AIEvent.Completed(request.turnId))
                }
            },
        )
        val phases = mutableListOf(viewModel.appState.value.name)

        viewModel.onMicClicked()
        phases += viewModel.appState.value.name
        val listening = viewModel.appState.value as AppState.Listening
        speech.emitResult(listening.turnId, "What time is it")
        phases += viewModel.appState.value.name
        assertTrue(viewModel.appState.value is AppState.Thinking)

        gate.complete(Unit)
        val speaking = viewModel.appState.value as AppState.Speaking
        phases += speaking.name
        synth.complete(speaking.turnId)
        phases += viewModel.appState.value.name

        assertEquals(AppState.Idle, viewModel.appState.value)
        assertEquals(
            listOf("Idle", "Listening", "Thinking", "Speaking", "Idle"),
            phases,
        )
        assertEquals("What time is it", viewModel.userLine.value)
        assertEquals("Hello there.", viewModel.iconLine.value)
        assertFalse(speech.isActive)
    }

    @Test
    fun interruptThenSecondReplySpeaksOnlyTheNewTurn() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val speech = IntegrationSpeech()
        val synth = IntegrationSynth()
        var script = "Alpha. Beta."
        val viewModel = integrationViewModel(
            speech = speech,
            synth = synth,
            gemini = object : AIProvider {
                override val id: String = "gemini"
                override fun streamReply(request: AIRequest): Flow<AIEvent> = flow {
                    emit(AIEvent.Token(script))
                    emit(AIEvent.Completed(request.turnId))
                }
            },
        )

        viewModel.sendText("first")
        val first = viewModel.appState.value as AppState.Speaking
        assertTrue(synth.queued.any { it.first == first.turnId })

        viewModel.onMicLongPress()
        val listening = viewModel.appState.value as AppState.Listening
        assertTrue(listening.turnId != first.turnId)
        assertTrue(synth.queued.isEmpty())
        synth.complete(first.turnId)
        assertEquals(listening, viewModel.appState.value)
        assertTrue(synth.spoken.none { it.first == first.turnId })

        script = "Gamma."
        viewModel.sendText("second")
        val second = viewModel.appState.value as AppState.Speaking
        assertTrue(second.turnId != first.turnId)
        synth.complete(second.turnId)

        assertEquals(AppState.Idle, viewModel.appState.value)
        assertEquals(listOf(second.turnId to "Gamma."), synth.spoken)
        assertTrue(synth.spoken.none { it.first == first.turnId })
    }

    @Test
    fun failureDuringThinkingEntersAlertThenIdle() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = integrationViewModel(
            speech = IntegrationSpeech(),
            synth = IntegrationSynth(),
            gemini = object : AIProvider {
                override val id: String = "gemini"
                override fun streamReply(request: AIRequest): Flow<AIEvent> = flow {
                    emit(AIEvent.Failed(GeminiMessages.INVALID_KEY))
                }
            },
        )

        viewModel.sendText("Hi")
        runCurrent()
        val alert = viewModel.appState.value as AppState.Alert
        assertEquals(GeminiMessages.INVALID_KEY, alert.message)
        assertEquals("Hi", viewModel.userLine.value)

        advanceTimeBy(FAILURE_ALERT_MILLIS - 1)
        runCurrent()
        assertTrue(viewModel.appState.value is AppState.Alert)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(AppState.Idle, viewModel.appState.value)

        viewModel.sendText("Hi")
        runCurrent()
        assertTrue(viewModel.appState.value is AppState.Alert)
        viewModel.onPresenceTapped()
        assertEquals(AppState.Idle, viewModel.appState.value)
        advanceTimeBy(FAILURE_ALERT_MILLIS)
        runCurrent()
        assertEquals(AppState.Idle, viewModel.appState.value)
    }

    @Test
    fun offlineFailureEntersAlertThenIdle() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = integrationViewModel(
            speech = IntegrationSpeech(),
            synth = IntegrationSynth(),
            gemini = object : AIProvider {
                override val id: String = "gemini"
                override fun streamReply(request: AIRequest): Flow<AIEvent> = flow {
                    emit(AIEvent.Failed(GeminiMessages.OFFLINE))
                }
            },
        )
        viewModel.sendText("Hi")
        runCurrent()
        val alert = viewModel.appState.value as AppState.Alert
        assertEquals(GeminiMessages.OFFLINE, alert.message)
        advanceTimeBy(FAILURE_ALERT_MILLIS - 1)
        runCurrent()
        assertEquals(alert, viewModel.appState.value)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(AppState.Idle, viewModel.appState.value)
    }

    @Test
    fun backgroundDuringListeningAndSpeakingReturnsToIdle() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val speech = IntegrationSpeech()
        val synth = IntegrationSynth()
        val viewModel = integrationViewModel(
            speech = speech,
            synth = synth,
            gemini = scripted("Hello there."),
        )

        viewModel.onMicClicked()
        assertTrue(viewModel.appState.value is AppState.Listening)
        assertTrue(speech.isActive)
        val stopsAfterListenStart = speech.stopCalls

        assertEquals(AppState.Idle, viewModel.onAppBackgrounded().getOrThrow())
        assertFalse(speech.isActive)
        assertTrue(speech.stopCalls > stopsAfterListenStart)

        viewModel.sendText("Hi")
        assertTrue(viewModel.appState.value is AppState.Speaking)
        val synthStops = synth.stopCount
        val speechStops = speech.stopCalls

        assertEquals(AppState.Idle, viewModel.onAppBackgrounded().getOrThrow())
        assertFalse(speech.isActive)
        assertTrue(speech.stopCalls > speechStops)
        assertTrue(synth.stopCount > synthStops)
        assertTrue(synth.queued.isEmpty())
    }

    private fun scripted(text: String): AIProvider = object : AIProvider {
        override val id: String = "gemini"
        override fun streamReply(request: AIRequest): Flow<AIEvent> = flow {
            emit(AIEvent.Token(text))
            emit(AIEvent.Completed(request.turnId))
        }
    }

    private fun integrationViewModel(
        speech: IntegrationSpeech,
        synth: IntegrationSynth,
        gemini: AIProvider,
    ): MainViewModel {
        var nextId = 0L
        val initial = AppSettings.defaults().copy(demoMode = false, apiKey = "test-key")
        val repository = IntegrationSettings(initial)
        return MainViewModel(
            machine = AppStateMachine { ++nextId },
            speechInput = speech,
            synthesizer = synth,
            aiManager = AIManager(repository, DemoProvider(), gemini),
            settings = repository,
            initialSettings = initial,
            gemini = gemini,
            conversations = InMemoryConversationRepository(),
            liveSpeech = speech,
            microphone = MicrophonePermission { true },
        )
    }
}

private class IntegrationSpeech : SpeechInput {
    var activeTurnId: Long? = null
    var stopCalls = 0
    private var events: SpeechEventListener? = null

    override val isActive: Boolean
        get() = activeTurnId != null

    override fun startListening(turnId: Long) {
        activeTurnId = turnId
    }

    override fun stopListening() {
        stopCalls += 1
        activeTurnId = null
    }

    override fun setListener(listener: SpeechEventListener?) {
        events = listener
    }

    fun emitResult(turnId: Long, text: String) {
        events?.onSpeechEvent(SpeechEvent.Result(turnId, text))
    }
}

private class IntegrationSynth : SpeechSynthesizer {
    val spoken = mutableListOf<Pair<Long, String>>()
    val queued = ArrayDeque<Pair<Long, String>>()
    var stopCount = 0
    private var listener: SpeechPlaybackListener? = null

    override fun setPlaybackListener(listener: SpeechPlaybackListener?) {
        this.listener = listener
    }

    override fun enqueue(turnId: Long, sentences: List<String>) {
        sentences.forEach { sentence -> queued.addLast(turnId to sentence) }
    }

    override fun stop() {
        stopCount += 1
        queued.clear()
    }

    fun complete(turnId: Long) {
        val next = queued.indexOfFirst { it.first == turnId }
        if (next >= 0) spoken += queued.removeAt(next)
        listener?.onUtteranceFinished(turnId)
    }
}

private class IntegrationSettings(
    initial: AppSettings,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<AppSettings> = state.asStateFlow()

    override suspend fun get(): AppSettings = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}
