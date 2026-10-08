package com.icon.nexus.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.icon.nexus.ai.AIEvent
import com.icon.nexus.ai.AIManager
import com.icon.nexus.ai.AIProvider
import com.icon.nexus.ai.AIRequest
import com.icon.nexus.ai.DemoProvider
import com.icon.nexus.ai.GeminiMessages
import com.icon.nexus.ai.GeminiProvider
import com.icon.nexus.audio.AndroidMicrophonePermission
import com.icon.nexus.audio.AudioAnalyzer
import com.icon.nexus.audio.MicrophonePermission
import com.icon.nexus.audio.AndroidSpeechSynthesizer
import com.icon.nexus.audio.RecognizerSpeechInput
import com.icon.nexus.audio.SpeechEvent
import com.icon.nexus.audio.SpeechInput
import com.icon.nexus.audio.SpeechInputGate
import com.icon.nexus.audio.SpeechMessages
import com.icon.nexus.audio.SpeechPlaybackListener
import com.icon.nexus.audio.SpeechSynthesizer
import com.icon.nexus.audio.speechErrorMessage
import com.icon.nexus.audio.splitCompletedSentences
import com.icon.nexus.data.AppSettings
import com.icon.nexus.data.ConversationRepository
import com.icon.nexus.data.EncryptedSettingsRepository
import com.icon.nexus.data.InMemoryConversationRepository
import com.icon.nexus.data.SettingsRepository
import com.icon.nexus.domain.AppState
import com.icon.nexus.domain.AppStateMachine
import com.icon.nexus.domain.AssistantPersona
import com.icon.nexus.domain.Author
import com.icon.nexus.domain.Conversation
import com.icon.nexus.domain.Message
import com.icon.nexus.domain.StateTransition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * Demo mode runs a timed local session on a mic tap. The job is tied to the
 * current turn id: a newer turn cancels stale level and transcript writes.
 * With demo mode off, [sendText] and a one-shot microphone utterance stream
 * a Gemini reply into the transcript. Finished sentences are spoken with
 * Android text-to-speech, then the app returns to Idle. Demo mode keeps the
 * timed local timeline and does not speak those sentences.
 *
 * [appState] is the only phase SpeechInput, Gemini, text-to-speech, the
 * transcript, and the presence follow. Exceptional failures enter Alert
 * with their message, then return to Idle.
 *
 * [onAppBackgrounded] is the background hook. The activity calls it from
 * `onStop` so leaving the foreground returns the app to Idle and cancels
 * an in-flight Gemini call.
 */
class MainViewModel(
    private val machine: AppStateMachine,
    private val speechInput: SpeechInput,
    private val synthesizer: SpeechSynthesizer,
    val aiManager: AIManager,
    private val settings: SettingsRepository,
    initialSettings: AppSettings = AppSettings.defaults(),
    private val analyzer: AudioAnalyzer = AudioAnalyzer(),
    private val gemini: AIProvider = GeminiProvider(settings),
    private val conversations: ConversationRepository = InMemoryConversationRepository(),
    private val liveSpeech: SpeechInput = speechInput,
    private val microphone: MicrophonePermission = MicrophonePermission { true },
) : ViewModel() {
    private val appStateInternal = MutableStateFlow(machine.current)
    val appState: StateFlow<AppState> = appStateInternal.asStateFlow()

    private val rejectionInternal = MutableStateFlow<String?>(null)
    val rejection: StateFlow<String?> = rejectionInternal.asStateFlow()

    private val transcriptVisibleInternal = MutableStateFlow(initialSettings.showTranscript)
    val transcriptVisible: StateFlow<Boolean> = transcriptVisibleInternal.asStateFlow()

    private val transcriptStartsVisibleInternal = MutableStateFlow(initialSettings.showTranscript)
    val transcriptStartsVisible: StateFlow<Boolean> = transcriptStartsVisibleInternal.asStateFlow()

    private val chromeVisibleInternal = MutableStateFlow(true)
    val chromeVisible: StateFlow<Boolean> = chromeVisibleInternal.asStateFlow()

    private val demoModeInternal = MutableStateFlow(initialSettings.demoMode)
    val demoMode: StateFlow<Boolean> = demoModeInternal.asStateFlow()

    private val userLineInternal = MutableStateFlow("")
    val userLine: StateFlow<String> = userLineInternal.asStateFlow()

    private val iconLineInternal = MutableStateFlow("")
    val iconLine: StateFlow<String> = iconLineInternal.asStateFlow()

    private val audioLevelInternal = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = audioLevelInternal.asStateFlow()

    private val apiKeyInternal = MutableStateFlow(initialSettings.apiKey)
    val apiKey: StateFlow<String> = apiKeyInternal.asStateFlow()

    private val geminiModelInternal = MutableStateFlow(initialSettings.geminiModel)
    val geminiModel: StateFlow<String> = geminiModelInternal.asStateFlow()

    private val microphoneExplanationInternal = MutableStateFlow(false)
    val microphoneExplanation: StateFlow<Boolean> = microphoneExplanationInternal.asStateFlow()

    private var speechTurnId: Long? = null
    private var pendingUtterances = 0
    private var replyFinished = false
    private val sentenceBuffer = StringBuilder()
    private var alertJob: Job? = null

    init {
        liveSpeech.setListener { event -> onSpeechEvent(event) }
        synthesizer.setPlaybackListener(object : SpeechPlaybackListener {
            override fun onUtteranceFinished(turnId: Long) {
                onSpeechFinished(turnId)
            }

            override fun onSpeechUnavailable() {
                val turnId = speechTurnId ?: return
                markSpeechUnavailable(turnId)
            }
        })
    }

    private val settingsMutex = Mutex()
    private val conversationMutex = Mutex()
    private val chatGeneration = AtomicInteger(0)

    private var scriptIndex = 0
    private var levelGeneration = 0
    private var demoJob: Job? = null
    private var chatJob: Job? = null

    fun onMicClicked() {
        if (!demoModeInternal.value) {
            when (appStateInternal.value) {
                AppState.Idle -> requestLiveListening()
                is AppState.Listening -> dispatch(StateTransition.ToIdle)
                else -> Unit
            }
            return
        }
        when (val state = appStateInternal.value) {
            AppState.Idle -> startDemoSession()
            is AppState.Listening -> {
                if (demoJob?.isActive == true) return
                val generation = newGeneration()
                demoJob = viewModelScope.launch {
                    continueAfterListening(generation, state.turnId)
                }
            }
            else -> Unit
        }
    }

    /**
     * Demo mode: Idle previews Alert, then returns to Idle. Speaking
     * interrupts into Listening. Text chat: a long press while Thinking or
     * Speaking cancels that turn and only then starts one-shot Listening.
     */
    fun onMicLongPress() {
        if (!demoModeInternal.value) {
            when (appStateInternal.value) {
                is AppState.Thinking, is AppState.Speaking -> interruptToListening()
                else -> Unit
            }
            return
        }
        when (appStateInternal.value) {
            AppState.Idle -> startAlertPreview()
            is AppState.Speaking -> interruptSpeaking()
            else -> rejectionInternal.value = "Long press is the idle alert or the speaking interrupt"
        }
    }

    fun onUserInterrupt(): Result<AppState> {
        if (appStateInternal.value !is AppState.Speaking) {
            return reject("Interrupt is only legal while Speaking")
        }
        return interruptSpeaking()
    }

    /**
     * Returns to Idle from any other state. Idle stays Idle.
     * Call this when the app goes to the background.
     */
    fun onAppBackgrounded(): Result<AppState> {
        stopDemoWork()
        cancelChat()
        cancelAlertTimer()
        abandonSpeech()
        microphoneExplanationInternal.value = false
        liveSpeech.stopListening()
        if (appStateInternal.value is AppState.Idle) {
            speechInput.stopListening()
            return Result.success(AppState.Idle)
        }
        return dispatch(StateTransition.ToIdle)
    }

    override fun onCleared() {
        abandonSpeech()
        synthesizer.release()
        liveSpeech.stopListening()
        super.onCleared()
    }

    fun acceptMicrophoneExplanation() {
        microphoneExplanationInternal.value = false
    }

    fun dismissMicrophoneExplanation() {
        microphoneExplanationInternal.value = false
    }

    fun onMicrophonePermissionResult(granted: Boolean) {
        microphoneExplanationInternal.value = false
        if (demoModeInternal.value) return
        if (!granted) {
            enterAlert(SpeechMessages.PERMISSION)
            return
        }
        if (appStateInternal.value is AppState.Idle) {
            dispatch(StateTransition.ToListening)
        }
    }

    fun sendText(raw: String) {
        if (demoModeInternal.value) return
        val text = raw.trim()
        if (text.isEmpty()) return
        val state = appStateInternal.value
        if (state !is AppState.Idle && state !is AppState.Thinking && state !is AppState.Listening) return
        val generation = beginChatTurn()
        chatJob = viewModelScope.launch {
            runChatTurn(generation, text)
        }
    }

    fun newConversation() {
        if (demoModeInternal.value) return
        cancelChat()
        userLineInternal.value = ""
        iconLineInternal.value = ""
        if (appStateInternal.value !is AppState.Idle) {
            dispatch(StateTransition.ToIdle)
        }
        viewModelScope.launch {
            conversationMutex.withLock {
                conversations.delete(CURRENT_CONVERSATION)
            }
        }
    }

    fun toggleTranscript() {
        transcriptVisibleInternal.value = !transcriptVisibleInternal.value
    }

    fun toggleCinematic() {
        chromeVisibleInternal.value = !chromeVisibleInternal.value
    }

    fun showChrome() {
        chromeVisibleInternal.value = true
    }

    suspend fun setDemoMode(enabled: Boolean) {
        settingsMutex.withLock {
            settings.update { current -> current.copy(demoMode = enabled) }
        }
        demoModeInternal.value = enabled
    }

    suspend fun setTranscriptStartsVisible(visible: Boolean) {
        settingsMutex.withLock {
            settings.update { current -> current.copy(showTranscript = visible) }
        }
        transcriptStartsVisibleInternal.value = visible
        transcriptVisibleInternal.value = visible
    }

    suspend fun setApiKey(value: String) {
        apiKeyInternal.value = value
        settingsMutex.withLock {
            settings.update { current -> current.copy(apiKey = value) }
        }
    }

    suspend fun setGeminiModel(value: String) {
        geminiModelInternal.value = value
        settingsMutex.withLock {
            settings.update { current -> current.copy(geminiModel = value) }
        }
    }

    /**
     * Applies one raw sample for [turnId]. A turn that is no longer Speaking
     * is ignored, so a stale job cannot move the envelope.
     */
    fun pushAmplitude(turnId: Long, raw: Float) {
        val speaking = appStateInternal.value as? AppState.Speaking ?: return
        if (speaking.turnId != turnId) return
        audioLevelInternal.value = analyzer.next(raw)
    }

    fun dispatch(target: StateTransition): Result<AppState> {
        val current = appStateInternal.value
        if (current is AppState.Speaking && AppStateMachine.isLegal(current, target)) {
            synthesizer.stop()
        }
        val result = machine.transition(target)
        result.onSuccess { next ->
            if (current is AppState.Alert && next !is AppState.Alert) {
                cancelAlertTimer()
            }
            when (next) {
                is AppState.Thinking -> if (demoModeInternal.value) {
                    userLineInternal.value = demoTranscript[scriptIndex].first
                }
                is AppState.Speaking -> if (demoModeInternal.value) {
                    iconLineInternal.value = demoTranscript[scriptIndex].second
                    scriptIndex = (scriptIndex + 1) % demoTranscript.size
                }
                else -> Unit
            }
            appStateInternal.value = next
            rejectionInternal.value = null
            routeSpeech(next)
        }
        result.onFailure { error ->
            rejectionInternal.value = error.message
        }
        return result
    }

    private fun startDemoSession() {
        val generation = newGeneration()
        demoJob?.cancel()
        analyzer.reset()
        audioLevelInternal.value = 0f
        demoJob = viewModelScope.launch {
            val listening = dispatch(StateTransition.ToListening).getOrNull() as? AppState.Listening
                ?: return@launch
            continueAfterListening(generation, listening.turnId)
        }
    }

    private suspend fun continueAfterListening(generation: Int, listenTurnId: Long) {
        delay(DEMO_LISTEN_MILLIS)
        if (!still(generation, listenTurnId)) return
        val thinking = dispatch(StateTransition.ToThinking).getOrNull() as? AppState.Thinking ?: return
        delay(DEMO_THINK_MILLIS)
        if (!still(generation, thinking.turnId)) return
        val speaking = dispatch(StateTransition.ToSpeaking).getOrNull() as? AppState.Speaking ?: return
        if (levelGeneration != generation) return
        speak(speaking.turnId, generation, speakingMillis(iconLineInternal.value))
        if (!still(generation, speaking.turnId)) return
        dispatch(StateTransition.ToIdle)
        releaseEnvelope(generation)
    }

    private suspend fun speak(turnId: Long, generation: Int, durationMillis: Long) {
        var elapsed = 0L
        while (elapsed < durationMillis) {
            if (!still(generation, turnId)) return
            pushAmplitude(turnId, syllableAmplitude(elapsed))
            val step = min(DEMO_LEVEL_FRAME_MILLIS, durationMillis - elapsed)
            delay(step)
            elapsed += step
        }
    }

    private suspend fun releaseEnvelope(generation: Int) {
        var guard = 0
        while (guard < 80 && levelGeneration == generation) {
            val next = analyzer.next(0f)
            if (levelGeneration != generation) return
            audioLevelInternal.value = next
            if (next < 0.02f) break
            delay(DEMO_LEVEL_FRAME_MILLIS)
            guard += 1
        }
        if (levelGeneration == generation) {
            analyzer.reset()
            audioLevelInternal.value = 0f
        }
    }

    private fun startAlertPreview() {
        demoJob?.cancel()
        demoJob = viewModelScope.launch {
            if (dispatch(StateTransition.ToAlert("Preview")).isFailure) return@launch
            delay(DEMO_ALERT_MILLIS)
            if (appStateInternal.value is AppState.Alert) {
                dispatch(StateTransition.ToIdle)
            }
        }
    }

    private fun interruptSpeaking(): Result<AppState> {
        stopDemoWork()
        return dispatch(StateTransition.ToListening)
    }

    private fun stopDemoWork() {
        newGeneration()
        demoJob?.cancel()
        demoJob = null
        analyzer.reset()
        audioLevelInternal.value = 0f
    }

    private fun requestLiveListening() {
        if (microphone.isGranted()) {
            dispatch(StateTransition.ToListening)
            return
        }
        microphoneExplanationInternal.value = true
    }

    private fun interruptToListening() {
        abandonSpeech()
        cancelChat()
        val state = appStateInternal.value
        if (state !is AppState.Thinking && state !is AppState.Speaking) return
        if (!microphone.isGranted()) {
            dispatch(StateTransition.ToIdle)
            microphoneExplanationInternal.value = true
            return
        }
        dispatch(StateTransition.ToListening)
    }

    /**
     * Alert is not sticky. A tap on the presence leaves it immediately.
     * The failure timer does the same after [FAILURE_ALERT_MILLIS].
     */
    fun onPresenceTapped() {
        if (appStateInternal.value !is AppState.Alert) return
        cancelAlertTimer()
        dispatch(StateTransition.ToIdle)
    }

    private fun onSpeechEvent(event: SpeechEvent) {
        if (demoModeInternal.value) return
        val listening = appStateInternal.value as? AppState.Listening ?: return
        if (listening.turnId != event.turnId) return
        when (event) {
            is SpeechEvent.Result -> {
                val text = event.text.trim()
                if (text.isEmpty()) {
                    enterAlert(SpeechMessages.EMPTY)
                } else {
                    val generation = beginChatTurn()
                    chatJob = viewModelScope.launch {
                        runChatTurn(generation, text)
                    }
                }
            }
            is SpeechEvent.Failure -> enterAlert(speechErrorMessage(event.reason))
        }
    }

    private fun routeSpeech(next: AppState) {
        if (demoModeInternal.value) {
            liveSpeech.stopListening()
            if (next is AppState.Listening) {
                speechInput.startListening(next.turnId)
            } else {
                speechInput.stopListening()
            }
            return
        }
        speechInput.stopListening()
        if (next is AppState.Listening) {
            liveSpeech.startListening(next.turnId)
        } else {
            liveSpeech.stopListening()
        }
    }

    private fun beginReplySpeech(turnId: Long) {
        speechTurnId = turnId
        pendingUtterances = 0
        replyFinished = false
        sentenceBuffer.clear()
    }

    private fun absorbSpeech(turnId: Long, chunk: String) {
        if (demoModeInternal.value || turnId != speechTurnId) return
        sentenceBuffer.append(chunk)
        val split = splitCompletedSentences(sentenceBuffer.toString())
        sentenceBuffer.clear()
        sentenceBuffer.append(split.remainder)
        queueSentences(turnId, split.sentences)
    }

    private fun flushSpeech(turnId: Long) {
        if (demoModeInternal.value || turnId != speechTurnId) return
        val tail = sentenceBuffer.toString().trim()
        sentenceBuffer.clear()
        if (tail.any { it.isLetterOrDigit() }) {
            queueSentences(turnId, listOf(tail))
        }
    }

    private fun queueSentences(turnId: Long, sentences: List<String>) {
        if (sentences.isEmpty() || turnId != speechTurnId) return
        if (!synthesizer.available) {
            markSpeechUnavailable(turnId)
            return
        }
        pendingUtterances += sentences.size
        if (appStateInternal.value is AppState.Thinking && ownsTurn(turnId)) {
            dispatch(StateTransition.ToSpeaking)
        }
        synthesizer.enqueue(turnId, sentences)
    }

    private fun markSpeechUnavailable(turnId: Long) {
        if (turnId != speechTurnId) return
        enterAlert(SpeechMessages.UNAVAILABLE)
    }

    private fun onSpeechFinished(turnId: Long) {
        if (demoModeInternal.value || turnId != speechTurnId) return
        val activeTurn = when (val state = appStateInternal.value) {
            is AppState.Thinking -> state.turnId
            is AppState.Speaking -> state.turnId
            else -> return
        }
        if (activeTurn != turnId) return
        if (pendingUtterances > 0) pendingUtterances -= 1
        maybeFinishSpeech(turnId)
    }

    private fun maybeFinishSpeech(turnId: Long) {
        if (turnId != speechTurnId || !replyFinished || pendingUtterances > 0) return
        if (appStateInternal.value is AppState.Speaking && ownsTurn(turnId)) {
            dispatch(StateTransition.ToIdle)
        }
    }

    private fun abandonSpeech() {
        speechTurnId = null
        pendingUtterances = 0
        replyFinished = false
        sentenceBuffer.clear()
        synthesizer.stop()
    }

    /**
     * Leaves Listening, Thinking, or Speaking for Alert. The message lives
     * on [AppState.Alert] so the presence and the transcript read one state.
     * A tap or [FAILURE_ALERT_MILLIS] returns to Idle.
     */
    private fun enterAlert(message: String) {
        if (appStateInternal.value is AppState.Alert) return
        abandonSpeech()
        if (dispatch(StateTransition.ToAlert(message)).isFailure) return
        cancelAlertTimer()
        alertJob = viewModelScope.launch {
            delay(FAILURE_ALERT_MILLIS)
            if (appStateInternal.value is AppState.Alert) {
                dispatch(StateTransition.ToIdle)
            }
        }
    }

    private fun cancelAlertTimer() {
        alertJob?.cancel()
        alertJob = null
    }

    private fun beginChatTurn(): Int {
        val generation = chatGeneration.incrementAndGet()
        chatJob?.cancel()
        chatJob = null
        if (appStateInternal.value is AppState.Thinking) {
            dispatch(StateTransition.ToIdle)
        }
        return generation
    }

    private fun cancelChat() {
        chatGeneration.incrementAndGet()
        chatJob?.cancel()
        chatJob = null
    }

    private suspend fun runChatTurn(generation: Int, text: String) {
        if (chatGeneration.get() != generation) return
        val thinking = dispatch(StateTransition.ToThinking).getOrNull() as? AppState.Thinking ?: return
        if (chatGeneration.get() != generation) return
        val turnId = thinking.turnId
        beginReplySpeech(turnId)
        userLineInternal.value = text
        iconLineInternal.value = ""
        try {
            val current = settings.get()
            if (chatGeneration.get() != generation || !ownsTurn(turnId)) return
            if (current.apiKey.isBlank()) {
                enterAlert(GeminiMessages.BLANK_KEY)
                return
            }
            val history = conversationMutex.withLock {
                if (chatGeneration.get() != generation) {
                    null
                } else {
                    val prior = conversations.get(CURRENT_CONVERSATION)?.messages.orEmpty()
                    conversations.save(
                        Conversation(
                            id = CURRENT_CONVERSATION,
                            messages = prior + Message(
                                id = "user-$turnId",
                                author = Author.User,
                                text = text,
                                turnId = turnId,
                            ),
                        ),
                    )
                    prior
                }
            } ?: return
            if (chatGeneration.get() != generation || !ownsTurn(turnId)) return
            val persona = AssistantPersona(
                name = current.personaName,
                personality = current.personality,
            )
            gemini.streamReply(
                AIRequest(
                    turnId = turnId,
                    persona = persona,
                    history = history,
                    userText = text,
                ),
            ).collect { event ->
                if (chatGeneration.get() != generation || !ownsTurn(turnId)) return@collect
                when (event) {
                    is AIEvent.Token -> {
                        iconLineInternal.value += event.text
                        absorbSpeech(turnId, event.text)
                    }
                    is AIEvent.Failed -> {
                        enterAlert(event.message.ifBlank { GeminiMessages.EMPTY })
                    }
                    is AIEvent.Completed -> {
                        val reply = iconLineInternal.value.trim()
                        if (reply.isEmpty()) {
                            enterAlert(GeminiMessages.EMPTY)
                            return@collect
                        }
                        saveAssistant(generation, turnId, reply)
                        flushSpeech(turnId)
                        if (appStateInternal.value is AppState.Alert) return@collect
                        replyFinished = true
                        maybeFinishSpeech(turnId)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            if (chatGeneration.get() == generation && ownsTurn(turnId)) {
                enterAlert(GeminiMessages.OFFLINE)
            }
        } finally {
            if (
                chatGeneration.get() == generation &&
                ownsTurn(turnId) &&
                appStateInternal.value is AppState.Thinking
            ) {
                dispatch(StateTransition.ToIdle)
            }
        }
    }

    private suspend fun saveAssistant(generation: Int, turnId: Long, reply: String) {
        conversationMutex.withLock {
            if (chatGeneration.get() != generation) return@withLock
            val prior = conversations.get(CURRENT_CONVERSATION)?.messages.orEmpty()
            if (prior.any { it.id == "icon-$turnId" }) return@withLock
            conversations.save(
                Conversation(
                    id = CURRENT_CONVERSATION,
                    messages = prior + Message(
                        id = "icon-$turnId",
                        author = Author.Assistant,
                        text = reply,
                        turnId = turnId,
                    ),
                ),
            )
        }
    }

    private fun still(generation: Int, turnId: Long): Boolean {
        if (levelGeneration != generation) return false
        return ownsTurn(turnId)
    }

    private fun ownsTurn(turnId: Long): Boolean {
        val active = when (val state = appStateInternal.value) {
            is AppState.Listening -> state.turnId
            is AppState.Thinking -> state.turnId
            is AppState.Speaking -> state.turnId
            else -> return false
        }
        return active == turnId
    }

    private fun newGeneration(): Int {
        levelGeneration += 1
        return levelGeneration
    }

    private fun reject(message: String): Result<AppState> {
        rejectionInternal.value = message
        return Result.failure(IllegalStateException(message))
    }

    companion object {
        private const val CURRENT_CONVERSATION = "current"

        fun factory(context: Context): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val turns = AtomicLong(0)
                    val settings = EncryptedSettingsRepository(context.applicationContext)
                    val initial = runBlocking { settings.get() }
                    val gemini = GeminiProvider(settings)
                    val manager = AIManager(
                        settings = settings,
                        demo = DemoProvider(),
                        gemini = gemini,
                    )
                    val model = MainViewModel(
                        machine = AppStateMachine { turns.incrementAndGet() },
                        speechInput = SpeechInputGate(),
                        synthesizer = AndroidSpeechSynthesizer(context.applicationContext),
                        aiManager = manager,
                        settings = settings,
                        initialSettings = initial,
                        gemini = gemini,
                        conversations = InMemoryConversationRepository(),
                        liveSpeech = RecognizerSpeechInput(context.applicationContext),
                        microphone = AndroidMicrophonePermission(context.applicationContext),
                    )
                    @Suppress("UNCHECKED_CAST")
                    return model as T
                }
            }
        }
    }
}

internal const val DEMO_LISTEN_MILLIS = 1_500L
internal const val DEMO_THINK_MILLIS = 1_200L
internal const val DEMO_ALERT_MILLIS = 2_000L
internal const val FAILURE_ALERT_MILLIS = 2_500L
internal const val DEMO_LEVEL_FRAME_MILLIS = 40L

internal fun speakingMillis(sentence: String): Long {
    return (sentence.length * 55L).coerceIn(800L, 8_000L)
}

internal fun syllableAmplitude(elapsedMillis: Long): Float {
    val period = 220L
    val position = (elapsedMillis % period).toFloat() / period.toFloat()
    return if (position < 0.3f) {
        position / 0.3f
    } else {
        ((1f - (position - 0.3f) / 0.7f) * 0.35f).coerceIn(0f, 1f)
    }
}

fun statusLabel(state: AppState): String = when (state) {
    AppState.Idle -> "Ready"
    is AppState.Listening -> "Listening"
    is AppState.Thinking -> "Thinking"
    is AppState.Speaking -> "Speaking"
    is AppState.Alert -> "Alert"
}

internal val demoTranscript = listOf(
    "Hey ICON, what is on my calendar today?" to "You have a clear morning and one call at three.",
    "Remind me to stretch in an hour." to "I will remind you to stretch in an hour.",
    "How is the weather outside?" to "It is cool and clear outside right now.",
)
