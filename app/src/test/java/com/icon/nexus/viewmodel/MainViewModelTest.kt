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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewModelTest {
    @Test
    fun micCyclesLocalStatesAndClosesTheMicOutsideListening() {
        val speech = SpeechInputGate()
        val viewModel = viewModel(speech = speech)

        assertEquals(AppState.Listening(1L), viewModel.onMicClicked().getOrThrow())
        assertEquals(1L, speech.activeTurnId)

        assertEquals(AppState.Thinking(2L), viewModel.onMicClicked().getOrThrow())
        assertNull(speech.activeTurnId)

        assertEquals(AppState.Speaking(2L), viewModel.onMicClicked().getOrThrow())
        assertNull(speech.activeTurnId)

        assertEquals(AppState.Idle, viewModel.onMicClicked().getOrThrow())
        assertNull(speech.activeTurnId)
    }

    @Test
    fun interruptStopsSpeechThenBumpsTurnIdIntoListening() {
        val speech = SpeechInputGate()
        val synthesizer = QueuedSpeechSynthesizer()
        val viewModel = viewModel(speech = speech, synthesizer = synthesizer)
        viewModel.onMicClicked()
        viewModel.onMicClicked()
        viewModel.onMicClicked()
        synthesizer.enqueue(2L, listOf("Hello.", "Again."))

        val interrupted = viewModel.onUserInterrupt().getOrThrow()
        assertEquals(AppState.Listening(3L), interrupted)
        assertTrue(synthesizer.pending.isEmpty())
        assertEquals(3L, speech.activeTurnId)
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
    fun backgroundReturnsToIdle() {
        val speech = SpeechInputGate()
        val viewModel = viewModel(speech = speech)
        viewModel.onMicClicked()
        assertEquals(AppState.Idle, viewModel.onAppBackgrounded().getOrThrow())
        assertNull(speech.activeTurnId)
        assertEquals(AppState.Idle, viewModel.onAppBackgrounded().getOrThrow())
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

    private fun viewModel(
        speech: SpeechInputGate = SpeechInputGate(),
        synthesizer: QueuedSpeechSynthesizer = QueuedSpeechSynthesizer(),
    ): MainViewModel {
        var nextId = 0L
        val settings = FakeSettingsRepository(AppSettings.defaults())
        return MainViewModel(
            machine = AppStateMachine { ++nextId },
            speechInput = speech,
            synthesizer = synthesizer,
            aiManager = AIManager(
                settings = settings,
                demo = DemoProvider(),
                gemini = GeminiProvider(settings),
            ),
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
