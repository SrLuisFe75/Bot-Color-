package com.icon.nexus.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.icon.nexus.ai.AIManager
import com.icon.nexus.ai.DemoProvider
import com.icon.nexus.ai.GeminiProvider
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicLong

/**
 * Local demo shell. The mic cycles Idle → Listening → Thinking → Speaking → Idle
 * and does not call [aiManager]. Each lap writes a fixed user sentence and an
 * ICON sentence into the transcript. No network.
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

    private var scriptIndex = 0

    fun onMicClicked(): Result<AppState> {
        val target = when (appStateInternal.value) {
            AppState.Idle -> StateTransition.ToListening
            is AppState.Listening -> StateTransition.ToThinking
            is AppState.Thinking -> StateTransition.ToSpeaking
            is AppState.Speaking -> StateTransition.ToIdle
            is AppState.Alert -> return reject("Mic does not leave Alert")
        }
        return dispatch(target)
    }

    /**
     * Interrupt while speaking: stop speech first, then enter Listening.
     * The Listening entry allocates a new turn id inside [AppStateMachine].
     * The latest transcript lines stay until the next lap replaces them.
     */
    fun onUserInterrupt(): Result<AppState> {
        if (appStateInternal.value !is AppState.Speaking) {
            return reject("Interrupt is only legal while Speaking")
        }
        return dispatch(StateTransition.ToListening)
    }

    /**
     * Returns to Idle from any other state. Idle stays Idle.
     * Call this when the app goes to the background.
     */
    fun onAppBackgrounded(): Result<AppState> {
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
