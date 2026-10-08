package com.icon.nexus.verification

import androidx.lifecycle.ViewModelStore
import com.icon.nexus.ai.AIEvent
import com.icon.nexus.ai.AIManager
import com.icon.nexus.ai.AIProvider
import com.icon.nexus.ai.AIRequest
import com.icon.nexus.ai.DemoProvider
import com.icon.nexus.audio.PlaybackCapture
import com.icon.nexus.audio.SpeechInput
import com.icon.nexus.audio.SpeechPlaybackListener
import com.icon.nexus.audio.SpeechSynthesizer
import com.icon.nexus.data.AppSettings
import com.icon.nexus.data.InMemoryConversationRepository
import com.icon.nexus.data.SettingsRepository
import com.icon.nexus.domain.AppState
import com.icon.nexus.domain.AppStateMachine
import com.icon.nexus.viewmodel.MainViewModel
import kotlinx.coroutines.CompletableDeferred
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
class SessionReleaseTest {
    @After
    fun resetMainDispatcher() {
        runCatching { Dispatchers.resetMain() }
    }

    @Test
    fun recognizerVisualizerAndSpeechReleaseOnClearAndBackground() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val speech = ReleaseSpeech()
        val capture = ReleaseCapture()
        val synth = ReleaseSynth()
        val hold = CompletableDeferred<Unit>()
        val viewModel = releaseViewModel(speech, capture, synth, hold)

        viewModel.onMicClicked()
        assertTrue(viewModel.appState.value is AppState.Listening)
        assertTrue(speech.isActive)
        assertEquals(AppState.Idle, viewModel.onAppBackgrounded().getOrThrow())
        assertFalse(speech.isActive)
        assertTrue(speech.stops >= 1)
        assertEquals(0, capture.releases)
        assertEquals(0, synth.releases)

        viewModel.sendText("Hi")
        assertTrue(viewModel.appState.value is AppState.Speaking)
        assertEquals(1, capture.starts)
        assertTrue(synth.queued >= 1)
        val stopsAfterSpeak = synth.stops
        val captureReleasesAfterSpeak = capture.releases
        assertEquals(AppState.Idle, viewModel.onAppBackgrounded().getOrThrow())
        assertFalse(speech.isActive)
        assertTrue(synth.stops > stopsAfterSpeak)
        assertTrue(capture.releases > captureReleasesAfterSpeak)
        assertEquals(0, synth.releases)

        viewModel.sendText("Again")
        assertTrue(viewModel.appState.value is AppState.Speaking)
        assertEquals(2, capture.starts)
        val store = ViewModelStore()
        store.put("icon", viewModel)
        store.clear()
        assertFalse(speech.isActive)
        assertTrue(speech.stops >= 1)
        assertEquals(2, capture.releases)
        assertTrue(synth.stops >= 2)
        assertEquals(1, synth.releases)
    }

    private fun releaseViewModel(
        speech: ReleaseSpeech,
        capture: ReleaseCapture,
        synth: ReleaseSynth,
        hold: CompletableDeferred<Unit>,
    ): MainViewModel {
        var nextId = 0L
        val initial = AppSettings.defaults().copy(demoMode = false, apiKey = "test-key")
        val settings = ReleaseSettings(initial)
        val gemini = object : AIProvider {
            override val id: String = "gemini"
            override fun streamReply(request: AIRequest): Flow<AIEvent> = flow {
                emit(AIEvent.Token("Hello."))
                hold.await()
                emit(AIEvent.Completed(request.turnId))
            }
        }
        return MainViewModel(
            machine = AppStateMachine { ++nextId },
            speechInput = speech,
            synthesizer = synth,
            aiManager = AIManager(settings, DemoProvider(), gemini),
            settings = settings,
            initialSettings = initial,
            gemini = gemini,
            conversations = InMemoryConversationRepository(),
            liveSpeech = speech,
            playback = capture,
        )
    }
}

private class ReleaseSpeech : SpeechInput {
    var stops = 0
        private set
    private var activeTurnId: Long? = null

    override val isActive: Boolean
        get() = activeTurnId != null

    override fun startListening(turnId: Long) {
        activeTurnId = turnId
    }

    override fun stopListening() {
        stops += 1
        activeTurnId = null
    }
}

private class ReleaseCapture : PlaybackCapture {
    var starts = 0
        private set
    var releases = 0
        private set

    override fun start(): Boolean {
        starts += 1
        return true
    }

    override fun release() {
        releases += 1
    }
}

private class ReleaseSynth : SpeechSynthesizer {
    var stops = 0
        private set
    var releases = 0
        private set
    var queued = 0
        private set

    override fun enqueue(turnId: Long, sentences: List<String>) {
        queued += sentences.size
    }

    override fun stop() {
        stops += 1
    }

    override fun release() {
        releases += 1
    }

    override fun setPlaybackListener(listener: SpeechPlaybackListener?) = Unit
}

private class ReleaseSettings(
    initial: AppSettings,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<AppSettings> = state.asStateFlow()

    override suspend fun get(): AppSettings = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}
