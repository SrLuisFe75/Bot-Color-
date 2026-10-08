package com.icon.nexus.viewmodel

import com.icon.nexus.ai.AIEvent
import com.icon.nexus.ai.AIManager
import com.icon.nexus.ai.AIProvider
import com.icon.nexus.ai.AIRequest
import com.icon.nexus.ai.DemoProvider
import com.icon.nexus.audio.AudioAnalyzer
import com.icon.nexus.audio.MicrophonePermission
import com.icon.nexus.audio.PlaybackCapture
import com.icon.nexus.audio.SpeechInputGate
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackCaptureTest {
    @After
    fun resetMainDispatcher() {
        runCatching { Dispatchers.resetMain() }
    }

    @Test
    fun demoSpeakingDoesNotRequestACapture() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val capture = RecordingPlaybackCapture()
        val viewModel = demoViewModel(capture)
        viewModel.onMicClicked()
        runCurrent()
        elapse(DEMO_LISTEN_MILLIS + DEMO_THINK_MILLIS)
        assertTrue(viewModel.appState.value is AppState.Speaking)
        assertEquals(0, capture.starts)
        assertEquals(0, capture.releases)
        elapse(DEMO_LEVEL_FRAME_MILLIS)
        assertTrue(viewModel.audioLevel.value > 0.05f)
        assertTrue(viewModel.audioLevel.value <= 1f)
        assertEquals(0, capture.starts)
    }

    @Test
    fun unavailableCaptureFollowsTheUtteranceEnvelope() = runBlockingMain {
        val capture = RecordingPlaybackCapture(available = false)
        val synth = EnvelopeSynthesizer()
        val release = CompletableDeferred<Unit>()
        val viewModel = liveViewModel(capture, synth) { request ->
            emit(AIEvent.Token("Hello."))
            release.await()
            emit(AIEvent.Completed(request.turnId))
        }
        viewModel.sendText("Hi")
        val speaking = viewModel.appState.value as AppState.Speaking
        assertEquals(1, capture.starts)
        assertEquals(0f, viewModel.audioLevel.value, 0.0001f)

        synth.start(speaking.turnId)
        val risen = viewModel.audioLevel.value
        assertEquals(AudioAnalyzer.DEFAULT_ATTACK, risen, 0.0001f)
        synth.range(speaking.turnId)
        val held = viewModel.audioLevel.value
        assertTrue(held >= risen)

        synth.finish(speaking.turnId)
        assertTrue(viewModel.appState.value is AppState.Speaking)
        assertTrue(viewModel.audioLevel.value < held)

        release.complete(Unit)
        assertEquals(AppState.Idle, viewModel.appState.value)
        assertEquals(1, capture.releases)
        assertEquals(0f, viewModel.audioLevel.value, 0.0001f)
    }

    @Test
    fun throwingCaptureDoesNotStickInSpeaking() = runBlockingMain {
        val capture = ThrowingPlaybackCapture()
        val synth = EnvelopeSynthesizer()
        val viewModel = liveViewModel(capture, synth) { request ->
            emit(AIEvent.Token("Hello."))
            emit(AIEvent.Completed(request.turnId))
        }
        viewModel.sendText("Hi")
        val speaking = viewModel.appState.value as AppState.Speaking
        assertEquals(1, capture.starts)
        synth.start(speaking.turnId)
        assertTrue(viewModel.audioLevel.value > 0.05f)
        synth.finish(speaking.turnId)
        assertEquals(AppState.Idle, viewModel.appState.value)
        assertEquals(1, capture.releases)
    }

    @Test
    fun captureReleasesOnInterruptBackgroundAndIdle() = runBlockingMain {
        val capture = RecordingPlaybackCapture(available = true)
        val synth = EnvelopeSynthesizer()
        val hold = CompletableDeferred<Unit>()
        val viewModel = liveViewModel(capture, synth) { request ->
            emit(AIEvent.Token("Hello."))
            hold.await()
            emit(AIEvent.Completed(request.turnId))
        }
        viewModel.sendText("Hi")
        val speaking = viewModel.appState.value as AppState.Speaking
        capture.emit(1f)
        assertEquals(AudioAnalyzer.DEFAULT_ATTACK, viewModel.audioLevel.value, 0.0001f)

        viewModel.onMicLongPress()
        assertTrue(viewModel.appState.value is AppState.Listening)
        assertEquals(1, capture.releases)
        assertTrue(synth.stops >= 1)
        assertEquals(0f, viewModel.audioLevel.value, 0.0001f)

        viewModel.sendText("Again")
        assertTrue(viewModel.appState.value is AppState.Speaking)
        assertEquals(2, capture.starts)
        assertEquals(AppState.Idle, viewModel.onAppBackgrounded().getOrThrow())
        assertEquals(2, capture.releases)
        assertEquals(0f, viewModel.audioLevel.value, 0.0001f)

        val resumed = CompletableDeferred<Unit>()
        val second = liveViewModel(capture, synth) { request ->
            emit(AIEvent.Token("Hello."))
            resumed.await()
            emit(AIEvent.Completed(request.turnId))
        }
        second.sendText("Once more")
        val again = second.appState.value as AppState.Speaking
        resumed.complete(Unit)
        synth.finish(again.turnId)
        assertEquals(AppState.Idle, second.appState.value)
        assertEquals(3, capture.releases)
    }

    private fun runBlockingMain(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
        kotlinx.coroutines.runBlocking {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            block()
        }
    }

    private fun TestScope.elapse(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    private fun demoViewModel(capture: PlaybackCapture): MainViewModel {
        var nextId = 0L
        val initial = AppSettings.defaults()
        val repository = CaptureSettingsRepository(initial)
        return MainViewModel(
            machine = AppStateMachine { ++nextId },
            speechInput = SpeechInputGate(),
            synthesizer = EnvelopeSynthesizer(),
            aiManager = AIManager(repository, DemoProvider(), gemini = silentGemini()),
            settings = repository,
            initialSettings = initial,
            playback = capture,
        )
    }

    private fun liveViewModel(
        capture: PlaybackCapture,
        synth: EnvelopeSynthesizer,
        reply: suspend kotlinx.coroutines.flow.FlowCollector<AIEvent>.(AIRequest) -> Unit,
    ): MainViewModel {
        var nextId = 0L
        val initial = AppSettings.defaults().copy(demoMode = false, apiKey = "test-key")
        val repository = CaptureSettingsRepository(initial)
        val gemini = object : AIProvider {
            override val id: String = "gemini"
            override fun streamReply(request: AIRequest): Flow<AIEvent> = flow { reply(request) }
        }
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
            playback = capture,
        )
    }

    private fun silentGemini(): AIProvider = object : AIProvider {
        override val id: String = "gemini"
        override fun streamReply(request: AIRequest): Flow<AIEvent> = flow { }
    }
}

private class RecordingPlaybackCapture(
    private val available: Boolean = true,
) : PlaybackCapture {
    var starts = 0
        private set
    var releases = 0
        private set
    private var listener: ((Float) -> Unit)? = null

    override fun setLevelListener(listener: ((Float) -> Unit)?) {
        this.listener = listener
    }

    override fun start(): Boolean {
        starts += 1
        return available
    }

    override fun release() {
        releases += 1
    }

    fun emit(level: Float) {
        listener?.invoke(level)
    }
}

private class ThrowingPlaybackCapture : PlaybackCapture {
    var starts = 0
        private set
    var releases = 0
        private set

    override fun start(): Boolean {
        starts += 1
        throw IllegalStateException("visualizer unavailable")
    }

    override fun release() {
        releases += 1
    }
}

private class EnvelopeSynthesizer : SpeechSynthesizer {
    private var listener: SpeechPlaybackListener? = null
    var stops = 0
        private set

    override fun setPlaybackListener(listener: SpeechPlaybackListener?) {
        this.listener = listener
    }

    override fun enqueue(turnId: Long, sentences: List<String>) = Unit

    override fun stop() {
        stops += 1
    }

    fun start(turnId: Long) {
        listener?.onUtteranceStarted(turnId)
    }

    fun range(turnId: Long) {
        listener?.onUtteranceRange(turnId)
    }

    fun finish(turnId: Long) {
        listener?.onUtteranceFinished(turnId)
    }
}

private class CaptureSettingsRepository(
    initial: AppSettings,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<AppSettings> = state.asStateFlow()

    override suspend fun get(): AppSettings = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}
