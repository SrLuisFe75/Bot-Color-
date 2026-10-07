package com.icon.nexus.domain

/**
 * Legal edges only:
 * Idle → Listening, Idle → Alert
 * Listening → Thinking, Listening → Idle, Listening → Alert
 * Thinking → Speaking, Thinking → Idle, Thinking → Alert, Thinking → Listening
 * Speaking → Idle, Speaking → Listening, Speaking → Alert
 * Alert → Idle
 *
 * Each successful entry into Listening or Thinking calls [allocateTurnId].
 * Speaking keeps the turn id of the Thinking state it leaves.
 * An illegal edge returns [Result.failure] and leaves [current] unchanged.
 */
class AppStateMachine(
    initial: AppState = AppState.Idle,
    private val allocateTurnId: () -> Long = { error("turn id was not expected") },
) {
    var current: AppState = initial
        private set

    fun transition(target: StateTransition): Result<AppState> {
        if (!isLegal(current, target)) {
            return Result.failure(
                IllegalStateException("Illegal transition from ${current.name} to ${target.name}"),
            )
        }
        val next = when (target) {
            StateTransition.ToIdle -> AppState.Idle
            StateTransition.ToListening -> AppState.Listening(allocateTurnId())
            StateTransition.ToThinking -> AppState.Thinking(allocateTurnId())
            StateTransition.ToSpeaking -> AppState.Speaking((current as AppState.Thinking).turnId)
            is StateTransition.ToAlert -> AppState.Alert(target.message)
        }
        current = next
        return Result.success(next)
    }

    companion object {
        fun isLegal(from: AppState, target: StateTransition): Boolean = when (from) {
            AppState.Idle ->
                target is StateTransition.ToListening || target is StateTransition.ToAlert
            is AppState.Listening ->
                target is StateTransition.ToThinking ||
                    target is StateTransition.ToIdle ||
                    target is StateTransition.ToAlert
            is AppState.Thinking ->
                target is StateTransition.ToSpeaking ||
                    target is StateTransition.ToIdle ||
                    target is StateTransition.ToAlert ||
                    target is StateTransition.ToListening
            is AppState.Speaking ->
                target is StateTransition.ToIdle ||
                    target is StateTransition.ToListening ||
                    target is StateTransition.ToAlert
            is AppState.Alert -> target is StateTransition.ToIdle
        }
    }
}
