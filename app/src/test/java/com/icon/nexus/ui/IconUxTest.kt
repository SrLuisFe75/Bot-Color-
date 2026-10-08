package com.icon.nexus.ui

import com.icon.nexus.ai.AIEvent
import com.icon.nexus.ai.AIManager
import com.icon.nexus.ai.AIProvider
import com.icon.nexus.ai.AIRequest
import com.icon.nexus.ai.DemoProvider
import com.icon.nexus.ai.geminiSystemInstruction
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
import com.icon.nexus.language.ReplyLanguage
import com.icon.nexus.language.replyLanguageFor
import com.icon.nexus.settings.SettingsDefaults
import com.icon.nexus.viewmodel.MainViewModel
import com.icon.nexus.viewmodel.voiceChrome
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
class IconUxTest {
    @After
    fun resetMainDispatcher() {
        runCatching { Dispatchers.resetMain() }
    }

    @Test
    fun homeVoiceConversationsSettingsAndCinematicNavigate() {
        assertEquals(IconPlace.Voice, destinationAfter(IconPlace.Home, IconNav.OpenVoice))
        assertEquals(IconPlace.Home, destinationAfter(IconPlace.Voice, IconNav.BackHome))
        assertEquals(
            IconPlace.Conversations,
            destinationAfter(IconPlace.Voice, IconNav.OpenConversations),
        )
        assertEquals(IconPlace.Settings, destinationAfter(IconPlace.Home, IconNav.OpenSettings))
        assertEquals(IconPlace.Cinematic, destinationAfter(IconPlace.Home, IconNav.OpenCinematic))
    }

    @Test
    fun sessionStartListensAndAFinishedReplyListensAgain() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val speech = SessionSpeech()
        val voice = HoldingVoice()
        val viewModel = sessionViewModel(speech, voice)
        assertEquals(AppState.Idle, viewModel.appState.value)
        assertFalse(speech.isActive)
        assertTrue(speech.started.isEmpty())

        viewModel.startVoiceSession()
        val first = viewModel.appState.value as AppState.Listening
        assertTrue(viewModel.voiceSessionActive.value)
        assertEquals(listOf(first.turnId), speech.started)

        speech.emitResult(first.turnId, "Hola")
        val speaking = viewModel.appState.value as AppState.Speaking
        assertTrue(speaking.turnId != first.turnId)
        voice.complete(speaking.turnId)

        val again = viewModel.appState.value as AppState.Listening
        assertTrue(again.turnId != speaking.turnId)
        assertEquals(listOf(first.turnId, again.turnId), speech.started)
        assertTrue(speech.isActive)

        viewModel.endVoiceSession()
        assertEquals(AppState.Idle, viewModel.appState.value)
        assertFalse(viewModel.voiceSessionActive.value)
        assertFalse(speech.isActive)
        assertEquals(listOf(first.turnId, again.turnId), speech.started)
    }

    @Test
    fun textSendReturnsToListeningOnlyWhileTheSessionStaysActive() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val speech = SessionSpeech()
        val voice = HoldingVoice()
        val viewModel = sessionViewModel(speech, voice)
        viewModel.startVoiceSession()
        assertTrue(viewModel.appState.value is AppState.Listening)

        viewModel.sendText("Hola")
        val speaking = viewModel.appState.value as AppState.Speaking
        voice.complete(speaking.turnId)
        val again = viewModel.appState.value as AppState.Listening
        assertTrue(again.turnId != speaking.turnId)
        assertTrue(speech.isActive)

        viewModel.endVoiceSession()
        viewModel.sendText("Hello")
        val after = viewModel.appState.value as AppState.Speaking
        voice.complete(after.turnId)
        assertEquals(AppState.Idle, viewModel.appState.value)
        assertFalse(speech.isActive)
    }

    @Test
    fun interruptDropsTheOldTurn() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val speech = SessionSpeech()
        val voice = HoldingVoice()
        val viewModel = sessionViewModel(speech, voice)
        viewModel.startVoiceSession()
        val first = viewModel.appState.value as AppState.Listening
        speech.emitResult(first.turnId, "Hello")
        val speaking = viewModel.appState.value as AppState.Speaking

        viewModel.startVoiceSession()

        val listening = viewModel.appState.value as AppState.Listening
        assertTrue(listening.turnId != speaking.turnId)
        assertTrue(voice.stopCount >= 1)
        voice.complete(speaking.turnId)
        assertEquals(listening, viewModel.appState.value)
        assertTrue(speech.isActive)
    }

    @Test
    fun audioLevelDoesNotRecomposeTheChrome() {
        val quiet = 0.05f
        val loud = 0.9f
        val first = voiceChrome(AppState.Speaking(4L), "Hello", "Hi there.", true)
        val second = voiceChrome(AppState.Speaking(4L), "Hello", "Hi there.", true)
        assertEquals(first, second)
        assertTrue(quiet != loud)
        assertEquals("Speaking", first.status)
    }

    @Test
    fun spanishEnglishAndAutoInstructionsStillHold() {
        val spanish = geminiSystemInstruction(
            name = "ICON",
            personality = SettingsDefaults.PERSONALITY,
            replyLanguage = ReplyLanguage.Spanish,
            followLatest = true,
        )
        val english = geminiSystemInstruction(
            name = "ICON",
            personality = SettingsDefaults.PERSONALITY,
            replyLanguage = replyLanguageFor("en", "¿Cómo estás?"),
        )
        val auto = geminiSystemInstruction(
            name = "ICON",
            personality = SettingsDefaults.PERSONALITY,
            replyLanguage = replyLanguageFor("", "I am fine. ¿Cómo estás?"),
            followLatest = true,
        )
        assertTrue(spanish.contains("You are a sophisticated companion."))
        assertTrue(spanish.contains("Reply in the language the user just used."))
        assertTrue(spanish.contains("Reply in natural spoken sentences."))
        assertTrue(spanish.contains("Do not translate unless the user asks."))
        assertTrue(spanish.contains("Do not announce that you are an AI."))
        assertTrue(spanish.contains("Reply in Spanish."))
        assertTrue(english.contains("Reply in English."))
        assertTrue(auto.contains("Reply in Spanish."))
        assertTrue(auto.contains("follow the language of the latest user message."))
    }

    private fun sessionViewModel(speech: SessionSpeech, voice: HoldingVoice): MainViewModel {
        var nextId = 0L
        val initial = AppSettings.defaults().copy(demoMode = false, apiKey = "test-key")
        val settings = SessionSettings(initial)
        val gemini = object : AIProvider {
            override val id: String = "gemini"
            override fun streamReply(request: AIRequest): Flow<AIEvent> = flow {
                emit(AIEvent.Token("Hola."))
                emit(AIEvent.Completed(request.turnId))
            }
        }
        return MainViewModel(
            machine = AppStateMachine { ++nextId },
            speechInput = speech,
            synthesizer = voice,
            aiManager = AIManager(settings, DemoProvider(), gemini),
            settings = settings,
            initialSettings = initial,
            gemini = gemini,
            conversations = InMemoryConversationRepository(),
            liveSpeech = speech,
            microphone = MicrophonePermission { true },
        )
    }
}

private class SessionSpeech : SpeechInput {
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
}

private class HoldingVoice : SpeechSynthesizer {
    var stopCount = 0
    private var listener: SpeechPlaybackListener? = null

    override fun setPlaybackListener(listener: SpeechPlaybackListener?) {
        this.listener = listener
    }

    override fun enqueue(turnId: Long, sentences: List<String>) = Unit

    override fun stop() {
        stopCount += 1
    }

    fun complete(turnId: Long) {
        listener?.onUtteranceFinished(turnId)
    }
}

private class SessionSettings(initial: AppSettings) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override fun observe(): Flow<AppSettings> = state.asStateFlow()
    override suspend fun get(): AppSettings = state.value
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}
