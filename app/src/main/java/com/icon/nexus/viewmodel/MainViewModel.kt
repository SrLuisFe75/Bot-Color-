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
import com.icon.nexus.data.EncryptedSettingsRepository
import com.icon.nexus.domain.AppState
import com.icon.nexus.domain.AppStateMachine
import com.icon.nexus.domain.StateTransition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Holds the app state machine. The mic button drives a local cycle only:
 * Idle → Listening → Thinking → Speaking → Idle. It does not call [aiManager].
 *
 * [onAppBackgrounded] is the background hook. The activity calls it from
 * `onStop` so leaving the foreground returns the app to Idle.
 */
class MainViewModel(
    private val machine: AppStateMachine,
    private val speechInput: SpeechInput,
    private val synthesizer: SpeechSynthesizer,
    val aiManager: AIManager,
) : ViewModel() {
    private val appStateInternal = MutableStateFlow(machine.current)
    val appState: StateFlow<AppState> = appStateInternal.asStateFlow()

    private val rejectionInternal = MutableStateFlow<String?>(null)
    val rejection: StateFlow<String?> = rejectionInternal.asStateFlow()

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
                    )
                    @Suppress("UNCHECKED_CAST")
                    return model as T
                }
            }
        }
    }
}
