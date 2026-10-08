package com.icon.nexus.settings

import com.icon.nexus.ai.AIEvent
import com.icon.nexus.ai.AIManager
import com.icon.nexus.ai.AIProvider
import com.icon.nexus.ai.AIRequest
import com.icon.nexus.ai.DemoProvider
import com.icon.nexus.ai.geminiSystemInstruction
import com.icon.nexus.audio.QueuedSpeechSynthesizer
import com.icon.nexus.audio.SpeechInput
import com.icon.nexus.audio.SpeechSynthesizer
import com.icon.nexus.data.AppSettings
import com.icon.nexus.data.InMemoryConversationRepository
import com.icon.nexus.data.SettingsRepository
import com.icon.nexus.domain.AppState
import com.icon.nexus.domain.AppStateMachine
import com.icon.nexus.domain.resolvedPersona
import com.icon.nexus.audio.resolvedLanguageTag
import com.icon.nexus.audio.ttsLanguage
import com.icon.nexus.viewmodel.MainViewModel
import com.icon.nexus.visualizer.visualLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsBehaviorTest {
    @After
    fun resetMainDispatcher() {
        runCatching { Dispatchers.resetMain() }
    }

    @Test
    fun nameAndPersonalityReachTheSystemInstruction() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val gemini = CapturingGemini()
        val viewModel = settingsViewModel(
            initial = AppSettings.defaults().copy(
                demoMode = false,
                apiKey = "test-key",
                personaName = "Nova",
                personality = "Keep answers brief",
            ),
            gemini = gemini,
            synthesizer = QueuedSpeechSynthesizer(),
        )
        viewModel.sendText("Hello")
        val first = gemini.requests.single()
        val spoken = geminiSystemInstruction(first.persona.name, first.persona.personality)
        assertEquals("Nova", first.persona.name)
        assertEquals("Keep answers brief", first.persona.personality)
        assertTrue(spoken.contains("Your name is Nova"))
        assertTrue(spoken.contains("Keep answers brief."))
        assertTrue(spoken.contains("Reply in natural spoken sentences."))

        viewModel.setPersonaName("  ")
        viewModel.setPersonality("")
        viewModel.sendText("Again")
        val fallback = gemini.requests.last().persona
        val fallbackText = geminiSystemInstruction(fallback.name, fallback.personality)
        assertEquals(SettingsDefaults.ASSISTANT_NAME, fallback.name)
        assertEquals(SettingsDefaults.PERSONALITY, fallback.personality)
        assertTrue(fallbackText.contains("Your name is ICON"))
        assertTrue(fallbackText.contains(SettingsDefaults.PERSONALITY))
    }

    @Test
    fun sensitivityScalesALevel() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val stored = SettingsBehaviorRepository(AppSettings.defaults())
        val viewModel = settingsViewModel(initial = stored.get(), settings = stored)
        assertEquals(1f, viewModel.visualSensitivity.value, 0.0001f)
        assertEquals(0.8f, visualLevel(0.8f, viewModel.visualSensitivity.value), 0.0001f)
        viewModel.setVisualSensitivity(0.5f)
        assertEquals(0.5f, stored.get().visualSensitivity, 0.0001f)
        assertEquals(0.4f, visualLevel(0.8f, viewModel.visualSensitivity.value), 0.0001f)
        assertEquals(1f, visualLevel(0.8f, 2f), 0.0001f)
    }

    @Test
    fun demoProviderNeverCallsGemini() = runBlocking {
        val gemini = CountingGemini()
        val settings = SettingsBehaviorRepository(
            AppSettings.defaults().copy(
                demoMode = false,
                provider = ModelProviderId.DEMO,
                apiKey = "present",
            ),
        )
        val selected = AIManager(settings, DemoProvider(), gemini).select()
        assertEquals(ModelProviderId.DEMO.wireName, selected.id)
        selected.streamReply(
            AIRequest(
                turnId = 1L,
                persona = resolvedPersona("ICON", SettingsDefaults.PERSONALITY),
                history = emptyList(),
                userText = "Hello",
            ),
        ).toList()
        assertEquals(0, gemini.calls)
    }

    @Test
    fun demoTimelineDoesNotCallGemini() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val gemini = CountingGemini()
        val viewModel = settingsViewModel(
            initial = AppSettings.defaults().copy(
                demoMode = false,
                provider = ModelProviderId.GEMINI,
                apiKey = "present",
            ),
            gemini = gemini,
        )
        viewModel.setProvider(ModelProviderId.DEMO)
        runCurrent()
        assertTrue(viewModel.demoMode.value)
        assertEquals(ModelProviderId.DEMO, viewModel.provider.value)
        viewModel.sendText("Hello")
        viewModel.onMicClicked()
        runCurrent()
        assertTrue(viewModel.appState.value is AppState.Listening)
        assertEquals(0, gemini.calls)
    }

    @Test
    fun languageTagIsWhatRecognizerAndTtsAreAskedToUse() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val speech = RecordingSpeechInput()
        val voice = RecordingSynthesizer()
        val stored = SettingsBehaviorRepository(
            AppSettings.defaults().copy(
                languageTag = "es-MX",
                voiceRate = 1.2f,
                voiceVolume = 0.4f,
            ),
        )
        val viewModel = settingsViewModel(
            initial = stored.get(),
            settings = stored,
            speech = speech,
            synthesizer = voice,
        )
        assertEquals("es-MX", speech.languageTag)
        assertEquals("es-MX", voice.languageTag)
        assertEquals("es-MX", resolvedLanguageTag(speech.languageTag, "en-US"))
        assertEquals("es-MX", ttsLanguage(voice.languageTag, "en-US").toLanguageTag())
        assertEquals(1.2f, voice.rate, 0.0001f)
        assertEquals(0.4f, voice.volume, 0.0001f)

        viewModel.setLanguageTag("ja-JP")
        assertEquals("ja-JP", stored.get().languageTag)
        assertEquals("ja-JP", speech.languageTag)
        assertEquals("ja-JP", voice.languageTag)
        assertEquals("ja-JP", resolvedLanguageTag(speech.languageTag, "en-US"))
        assertEquals("ja-JP", ttsLanguage(voice.languageTag, "en-US").toLanguageTag())
        assertEquals("en-US", resolvedLanguageTag("  ", "en-US"))
    }

    private fun settingsViewModel(
        initial: AppSettings,
        gemini: AIProvider = CountingGemini(),
        settings: SettingsRepository = SettingsBehaviorRepository(initial),
        speech: SpeechInput = RecordingSpeechInput(),
        synthesizer: SpeechSynthesizer = RecordingSynthesizer(),
    ): MainViewModel {
        var nextId = 0L
        return MainViewModel(
            machine = AppStateMachine { ++nextId },
            speechInput = speech,
            synthesizer = synthesizer,
            aiManager = AIManager(settings, DemoProvider(), gemini),
            settings = settings,
            initialSettings = initial,
            gemini = gemini,
            conversations = InMemoryConversationRepository(),
            liveSpeech = speech,
        )
    }
}

private class SettingsBehaviorRepository(
    initial: AppSettings,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<AppSettings> = state.asStateFlow()

    override suspend fun get(): AppSettings = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

private class CapturingGemini : AIProvider {
    val requests = mutableListOf<AIRequest>()

    override val id: String = ModelProviderId.GEMINI.wireName

    override fun streamReply(request: AIRequest): Flow<AIEvent> = flow {
        requests += request
        emit(AIEvent.Token("Hello."))
        emit(AIEvent.Completed(request.turnId))
    }
}

private class CountingGemini : AIProvider {
    var calls = 0

    override val id: String = ModelProviderId.GEMINI.wireName

    override fun streamReply(request: AIRequest): Flow<AIEvent> {
        calls += 1
        return flow { emit(AIEvent.Failed("Gemini was called")) }
    }
}

private class RecordingSpeechInput : SpeechInput {
    var languageTag: String = ""

    override val isActive: Boolean = false

    override fun startListening(turnId: Long) = Unit

    override fun stopListening() = Unit

    override fun setLanguageTag(tag: String) {
        languageTag = tag
    }
}

private class RecordingSynthesizer : SpeechSynthesizer {
    var rate = 0f
    var volume = 0f
    var languageTag = ""

    override fun enqueue(turnId: Long, sentences: List<String>) = Unit

    override fun applyVoice(rate: Float, volume: Float, languageTag: String) {
        this.rate = rate
        this.volume = volume
        this.languageTag = languageTag
    }

    override fun stop() = Unit
}
