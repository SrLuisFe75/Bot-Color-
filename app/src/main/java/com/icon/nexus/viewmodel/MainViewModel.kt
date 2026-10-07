package com.icon.nexus.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.icon.nexus.ai.AIManager
import com.icon.nexus.ai.DemoProvider
import com.icon.nexus.ai.GeminiProvider
import com.icon.nexus.audio.AudioAnalyzer
import com.icon.nexus.audio.QueuedSpeechSynthesizer
import com.icon.nexus.audio.SpeechInput
import com.icon.nexus.audio.SpeechInputGate
import com.icon.nexus.audio.SpeechSynthesizer
import com.icon.nexus.data.AppSettings
import com.icon.nexus.data.EncryptedSettingsRepository
import com.icon.nexus.data.SettingsRepository
import com.icon.nexus.domain.AppState
import com.icon.nexus.domain.AppStateMachine
import com.icon.nexus.domain.StateTransition
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * Demo mode runs a timed local session on a mic tap. The job is tied to the
 * current turn id: a newer turn cancels stale level and transcript writes.
 * [aiManager] is not called.
 *
 * [onAppBackgrounded] is the background hook. The activity calls it from
 * `onStop` so leaving the foreground returns the app to Idle.
 */
class MainViewModel(
    private val machine: AppStateMachine,
    private val speechInput: SpeechInput,
    private val synthesizer: SpeechSynthesizer,
    val aiManager: AIManager,
    private val settings: SettingsRepository,
    initialSettings: AppSettings = AppSettings.defaults(),
    private val analyzer: AudioAnalyzer = AudioAnalyzer(),
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

    private var scriptIndex = 0
    private var levelGeneration = 0
    private var demoJob: Job? = null

    fun onMicClicked() {
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
     * Idle previews Alert, then returns to Idle.
     * Speaking interrupts: stop the demo job, bump the turn id, enter Listening.
     * The previous turn can no longer write level or transcript.
     */
    fun onMicLongPress() {
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
        if (appStateInternal.value is AppState.Idle) {
            return Result.success(AppState.Idle)
        }
        return dispatch(StateTransition.ToIdle)
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
        settings.update { current -> current.copy(demoMode = enabled) }
        demoModeInternal.value = enabled
    }

    suspend fun setTranscriptStartsVisible(visible: Boolean) {
        settings.update { current -> current.copy(showTranscript = visible) }
        transcriptStartsVisibleInternal.value = visible
        transcriptVisibleInternal.value = visible
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
            when (next) {
                is AppState.Listening -> speechInput.startListening(next.turnId)
                else -> speechInput.stopListening()
            }
            when (next) {
                is AppState.Thinking -> userLineInternal.value = demoTranscript[scriptIndex].first
                is AppState.Speaking -> {
                    iconLineInternal.value = demoTranscript[scriptIndex].second
                    scriptIndex = (scriptIndex + 1) % demoTranscript.size
                }
                else -> Unit
            }
            appStateInternal.value = next
            rejectionInternal.value = null
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
        fun factory(context: Context): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val turns = AtomicLong(0)
                    val settings = EncryptedSettingsRepository(context.applicationContext)
                    val initial = runBlocking { settings.get() }
                    val manager = AIManager(
                        settings = settings,
                        demo = DemoProvider(),
                        gemini = GeminiProvider(settings),
                    )
                    val model = MainViewModel(
                        machine = AppStateMachine { turns.incrementAndGet() },
                        speechInput = SpeechInputGate(),
                        synthesizer = QueuedSpeechSynthesizer(),
                        aiManager = manager,
                        settings = settings,
                        initialSettings = initial,
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
