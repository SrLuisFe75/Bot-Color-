package com.icon.nexus.viewmodel

import com.icon.nexus.ai.AIEvent
import com.icon.nexus.ai.AIManager
import com.icon.nexus.ai.AIProvider
import com.icon.nexus.ai.AIRequest
import com.icon.nexus.ai.DemoProvider
import com.icon.nexus.audio.MicrophonePermission
import com.icon.nexus.audio.SpeechInputGate
import com.icon.nexus.audio.SpeechMessages
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
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TtsPlaybackTest {
    @After
    fun resetMainDispatcher() {
        runCatching { Dispatchers.resetMain() }
    }

    @Test
    fun firstSentenceEntersSpeakingBeforeTheReplyFinishes() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val synth = FakeSynthesizer()
        val release = CompletableDeferred<Unit>()
        val viewModel = speechViewModel(
            synth = synth,
            gemini = object : AIProvider {
                override val id: String = "gemini"
                override fun streamReply(request: AIRequest): Flow<AIEvent> = flow {
                    emit(AIEvent.Token("Hello. Still talking"))
                    release.await()
                    emit(AIEvent.Completed(request.turnId))
                }
            },
        )
        viewModel.sendText("Hi")

        val speaking = viewModel.appState.value as AppState.Speaking
        assertEquals(listOf(speaking.turnId to "Hello."), synth.spoken)
        assertEquals("Hello. Still talking", viewModel.iconLine.value)
        assertEquals(0, synth.stopCount)
        release.complete(Unit)
        synth.complete(speaking.turnId)
        synth.complete(speaking.turnId)
        assertEquals(AppState.Idle, viewModel.appState.value)
        assertTrue(synth.spoken.any { it.second == "Still talking" })
    }

    @Test
    fun interruptClearsTheQueueAndIgnoresALateCompletion() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val synth = FakeSynthesizer()
        val viewModel = speechViewModel(synth = synth, gemini = scripted("Hello there."))
        viewModel.sendText("Hi")
        val speaking = viewModel.appState.value as AppState.Speaking
        assertEquals("Hello there.", synth.spoken.single().second)

        viewModel.onMicLongPress()

        val listening = viewModel.appState.value as AppState.Listening
        assertTrue(listening.turnId != speaking.turnId)
        assertTrue(synth.stopCount >= 1)
        assertTrue(synth.queued.isEmpty())
        synth.complete(speaking.turnId)
        assertEquals(listening, viewModel.appState.value)
        assertEquals("Hello there.", viewModel.iconLine.value)
    }

    @Test
    fun unavailableSpeechEntersAlertWithTheText() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val synth = FakeSynthesizer(usable = false)
        val viewModel = speechViewModel(synth = synth, gemini = scripted("Hello there."))
        viewModel.sendText("Hi")

        val alert = viewModel.appState.value as AppState.Alert
        assertEquals(SpeechMessages.UNAVAILABLE, alert.message)
        assertEquals("Hello there.", viewModel.iconLine.value)
        assertTrue(synth.spoken.isEmpty())
        assertEquals("Alert", statusLabel(viewModel.appState.value))
    }

    @Test
    fun staleTurnCompletionDoesNotLeaveSpeaking() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val synth = FakeSynthesizer()
        val viewModel = speechViewModel(synth = synth, gemini = scripted("Hello there."))
        viewModel.sendText("Hi")
        val speaking = viewModel.appState.value as AppState.Speaking

        synth.complete(speaking.turnId + 50)

        assertEquals(speaking, viewModel.appState.value)
        assertEquals(1, synth.queued.size)
        synth.complete(speaking.turnId)
        assertEquals(AppState.Idle, viewModel.appState.value)
    }

    @Test
    fun demoModeDoesNotSpeakTheScript() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val synth = FakeSynthesizer()
        var nextId = 0L
        val initial = AppSettings.defaults()
        val repository = TtsSettingsRepository(initial)
        val viewModel = MainViewModel(
            machine = AppStateMachine { ++nextId },
            speechInput = SpeechInputGate(),
            synthesizer = synth,
            aiManager = AIManager(repository, DemoProvider(), gemini = scripted("unused")),
            settings = repository,
            initialSettings = initial,
        )
        viewModel.onMicClicked()
        runCurrent()
        elapse(DEMO_LISTEN_MILLIS + DEMO_THINK_MILLIS)
        assertTrue(viewModel.appState.value is AppState.Speaking)
        elapse(speakingMillis(demoTranscript[0].second))
        advanceUntilIdle()
        assertEquals(AppState.Idle, viewModel.appState.value)
        assertTrue(synth.spoken.isEmpty())
    }

    private fun TestScope.elapse(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    private fun scripted(text: String): AIProvider = object : AIProvider {
        override val id: String = "gemini"
        override fun streamReply(request: AIRequest): Flow<AIEvent> = flow {
            emit(AIEvent.Token(text))
            emit(AIEvent.Completed(request.turnId))
        }
    }

    private fun speechViewModel(
        synth: FakeSynthesizer,
        gemini: AIProvider,
    ): MainViewModel {
        var nextId = 0L
        val initial = AppSettings.defaults().copy(demoMode = false, apiKey = "test-key")
        val repository = TtsSettingsRepository(initial)
        return MainViewModel(
            machine = AppStateMachine { ++nextId },
            speechInput = SpeechInputGate(),
            synthesizer = synth,
            aiManager = AIManager(repository, DemoProvider(), gemini),
            settings = repository,
            initialSettings = initial,
            gemini = gemini,
            conversations = InMemoryConversationRepository(),
            microphone = MicrophonePermission { true },
        )
    }
}

private class FakeSynthesizer(
    private val usable: Boolean = true,
) : SpeechSynthesizer {
    override val available: Boolean = usable
    val spoken = mutableListOf<Pair<Long, String>>()
    val queued = ArrayDeque<Pair<Long, String>>()
    var stopCount = 0
    private var listener: SpeechPlaybackListener? = null

    override fun setPlaybackListener(listener: SpeechPlaybackListener?) {
        this.listener = listener
    }

    override fun enqueue(turnId: Long, sentences: List<String>) {
        if (!available) {
            listener?.onSpeechUnavailable()
            return
        }
        sentences.forEach { sentence ->
            spoken += turnId to sentence
            queued.addLast(turnId to sentence)
        }
    }

    override fun stop() {
        stopCount += 1
        queued.clear()
    }

    fun complete(turnId: Long) {
        val next = queued.indexOfFirst { it.first == turnId }
        if (next >= 0) queued.removeAt(next)
        listener?.onUtteranceFinished(turnId)
    }
}

private class TtsSettingsRepository(
    initial: AppSettings,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<AppSettings> = state.asStateFlow()

    override suspend fun get(): AppSettings = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}
