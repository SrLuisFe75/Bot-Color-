package com.icon.nexus.viewmodel

import com.icon.nexus.ai.AIManager
import com.icon.nexus.ai.DemoProvider
import com.icon.nexus.ai.GeminiProvider
import com.icon.nexus.audio.QueuedSpeechSynthesizer
import com.icon.nexus.audio.SpeechInputGate
import com.icon.nexus.data.AppSettings
import com.icon.nexus.data.SettingsRepository
import com.icon.nexus.domain.AppState
import com.icon.nexus.domain.AppStateMachine
import com.icon.nexus.domain.StateTransition
import com.icon.nexus.settings.ModelProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @After
    fun resetMainDispatcher() {
        runCatching { Dispatchers.resetMain() }
    }

    @Test
    fun demoSessionAdvancesThenReturnsToIdle() = runTest {
        val speech = SpeechInputGate()
        val viewModel = timedViewModel(speech = speech)
        viewModel.onMicClicked()
        runCurrent()

        assertEquals(AppState.Listening(1L), viewModel.appState.value)
        assertEquals(1L, speech.activeTurnId)
        assertEquals("", viewModel.userLine.value)

        elapse(DEMO_LISTEN_MILLIS)
        val thinking = viewModel.appState.value as AppState.Thinking
        assertEquals(2L, thinking.turnId)
        assertNull(speech.activeTurnId)
        assertEquals(demoTranscript[0].first, viewModel.userLine.value)
        assertEquals("", viewModel.iconLine.value)

        elapse(DEMO_THINK_MILLIS)
        val speaking = viewModel.appState.value as AppState.Speaking
        assertEquals(thinking.turnId, speaking.turnId)
        assertEquals(demoTranscript[0].second, viewModel.iconLine.value)
        assertEquals(0f, viewModel.audioLevel.value, 0.0001f)

        elapse(DEMO_LEVEL_FRAME_MILLIS)
        assertTrue(viewModel.appState.value is AppState.Speaking)
        assertTrue(viewModel.audioLevel.value > 0.05f)
        assertTrue(viewModel.audioLevel.value <= 1f)

        elapse(speakingMillis(demoTranscript[0].second) - DEMO_LEVEL_FRAME_MILLIS)
        assertEquals(AppState.Idle, viewModel.appState.value)
        assertNull(speech.activeTurnId)

        advanceUntilIdle()
        assertEquals(0f, viewModel.audioLevel.value, 0.0001f)
        assertEquals(AppState.Idle, viewModel.appState.value)
    }

    @Test
    fun newerTurnIgnoresStaleLevelAndTranscriptUpdates() = runTest {
        val speech = SpeechInputGate()
        val synthesizer = QueuedSpeechSynthesizer()
        val viewModel = timedViewModel(speech = speech, synthesizer = synthesizer)
        viewModel.onMicClicked()
        runCurrent()
        elapse(DEMO_LISTEN_MILLIS + DEMO_THINK_MILLIS + DEMO_LEVEL_FRAME_MILLIS)

        val speaking = viewModel.appState.value as AppState.Speaking
        synthesizer.enqueue(speaking.turnId, listOf("Hello.", "Again."))
        val levelWhileSpeaking = viewModel.audioLevel.value
        assertTrue(levelWhileSpeaking > 0f)
        val userLine = viewModel.userLine.value
        val iconLine = viewModel.iconLine.value

        viewModel.onMicLongPress()
        runCurrent()

        val listening = viewModel.appState.value as AppState.Listening
        assertTrue(listening.turnId > speaking.turnId)
        assertEquals(listening.turnId, speech.activeTurnId)
        assertTrue(synthesizer.pending.isEmpty())
        assertEquals(0f, viewModel.audioLevel.value, 0.0001f)
        assertEquals(userLine, viewModel.userLine.value)
        assertEquals(iconLine, viewModel.iconLine.value)

        viewModel.pushAmplitude(speaking.turnId, 1f)
        assertEquals(0f, viewModel.audioLevel.value, 0.0001f)

        elapse(DEMO_LISTEN_MILLIS - 1)
        assertEquals(listening, viewModel.appState.value)
        assertEquals(userLine, viewModel.userLine.value)
        assertEquals(iconLine, viewModel.iconLine.value)
    }

    @Test
    fun alertPreviewReturnsToIdle() = runTest {
        val viewModel = timedViewModel()
        viewModel.onMicLongPress()
        runCurrent()
        assertTrue(viewModel.appState.value is AppState.Alert)
        assertEquals("Alert", statusLabel(viewModel.appState.value))

        elapse(DEMO_ALERT_MILLIS - 1)
        assertTrue(viewModel.appState.value is AppState.Alert)

        elapse(1)
        assertEquals(AppState.Idle, viewModel.appState.value)

        elapse(5_000)
        assertEquals(AppState.Idle, viewModel.appState.value)
    }

    @Test
    fun transcriptFollowsTimedLapsAndToggle() = runTest {
        val viewModel = timedViewModel()
        assertFalse(viewModel.transcriptVisible.value)
        assertEquals("Ready", statusLabel(viewModel.appState.value))

        viewModel.onMicClicked()
        runCurrent()
        elapse(DEMO_LISTEN_MILLIS)
        assertEquals(demoTranscript[0].first, viewModel.userLine.value)
        assertEquals("", viewModel.iconLine.value)

        elapse(DEMO_THINK_MILLIS)
        assertEquals(demoTranscript[0].second, viewModel.iconLine.value)

        viewModel.toggleTranscript()
        assertTrue(viewModel.transcriptVisible.value)
        viewModel.toggleTranscript()
        assertFalse(viewModel.transcriptVisible.value)

        elapse(speakingMillis(demoTranscript[0].second))
        advanceUntilIdle()
        assertEquals(AppState.Idle, viewModel.appState.value)

        viewModel.onMicClicked()
        runCurrent()
        elapse(DEMO_LISTEN_MILLIS)
        assertEquals(demoTranscript[1].first, viewModel.userLine.value)
        elapse(DEMO_THINK_MILLIS)
        assertEquals(demoTranscript[1].second, viewModel.iconLine.value)
    }

    @Test
    fun backgroundCancelsTheDemoAndReturnsToIdle() = runTest {
        val speech = SpeechInputGate()
        val viewModel = timedViewModel(speech = speech)
        viewModel.onMicClicked()
        runCurrent()
        assertTrue(viewModel.appState.value is AppState.Listening)

        assertEquals(AppState.Idle, viewModel.onAppBackgrounded().getOrThrow())
        assertNull(speech.activeTurnId)
        elapse(10_000)
        assertEquals(AppState.Idle, viewModel.appState.value)
        assertEquals("", viewModel.userLine.value)
        assertEquals(AppState.Idle, viewModel.onAppBackgrounded().getOrThrow())
    }

    @Test
    fun cinematicHidesChromeUntilTheFieldShowsItAgain() {
        val viewModel = viewModel()
        viewModel.toggleTranscript()
        viewModel.toggleCinematic()
        assertFalse(viewModel.chromeVisible.value)
        assertTrue(viewModel.transcriptVisible.value)
        viewModel.showChrome()
        assertTrue(viewModel.chromeVisible.value)
    }

    @Test
    fun settingsPersistDemoModeAndTranscriptStart() = runBlocking {
        val initial = AppSettings.defaults()
        val settings = FakeSettingsRepository(initial)
        val viewModel = viewModel(settings = settings, initial = initial)

        viewModel.setDemoMode(false)
        viewModel.setTranscriptStartsVisible(true)

        assertFalse(settings.get().demoMode)
        assertTrue(settings.get().showTranscript)
        assertFalse(viewModel.demoMode.value)
        assertTrue(viewModel.transcriptStartsVisible.value)
        assertTrue(viewModel.transcriptVisible.value)
    }

    @Test
    fun statusLabelsMatchTheDemoStates() {
        assertEquals("Ready", statusLabel(AppState.Idle))
        assertEquals("Listening", statusLabel(AppState.Listening(1L)))
        assertEquals("Thinking", statusLabel(AppState.Thinking(1L)))
        assertEquals("Speaking", statusLabel(AppState.Speaking(1L)))
        assertEquals("Alert", statusLabel(AppState.Alert("notice")))
    }

    @Test
    fun interruptOutsideSpeakingIsRejected() {
        val viewModel = viewModel()
        val result = viewModel.onUserInterrupt()
        assertTrue(result.isFailure)
        assertEquals(AppState.Idle, viewModel.appState.value)
    }

    @Test
    fun illegalDispatchDoesNotChangeState() {
        val viewModel = viewModel()
        val result = viewModel.dispatch(StateTransition.ToSpeaking)
        assertTrue(result.isFailure)
        assertEquals(AppState.Idle, viewModel.appState.value)
        assertTrue(viewModel.rejection.value?.contains("Illegal transition") == true)
    }

    @Test
    fun demoModeSelectsDemoProviderEvenWhenGeminiIsConfigured() = runBlocking {
        val settings = FakeSettingsRepository(
            AppSettings.defaults().copy(
                demoMode = true,
                provider = ModelProviderId.GEMINI,
                apiKey = "present-but-unused",
            ),
        )
        val manager = AIManager(
            settings = settings,
            demo = DemoProvider(),
            gemini = GeminiProvider(settings),
        )
        assertEquals("demo", manager.select().id)
    }

    private fun TestScope.timedViewModel(
        speech: SpeechInputGate = SpeechInputGate(),
        synthesizer: QueuedSpeechSynthesizer = QueuedSpeechSynthesizer(),
    ): MainViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        return viewModel(speech = speech, synthesizer = synthesizer)
    }

    private fun TestScope.elapse(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    private fun viewModel(
        speech: SpeechInputGate = SpeechInputGate(),
        synthesizer: QueuedSpeechSynthesizer = QueuedSpeechSynthesizer(),
        settings: SettingsRepository? = null,
        initial: AppSettings = AppSettings.defaults(),
    ): MainViewModel {
        var nextId = 0L
        val repository = settings ?: FakeSettingsRepository(initial)
        return MainViewModel(
            machine = AppStateMachine { ++nextId },
            speechInput = speech,
            synthesizer = synthesizer,
            aiManager = AIManager(
                settings = repository,
                demo = DemoProvider(),
                gemini = GeminiProvider(repository),
            ),
            settings = repository,
            initialSettings = initial,
        )
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
