package com.icon.nexus.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStateMachineTest {
    @Test
    fun legalPathsAllocateTurnIdsOnlyOnListenAndThink() {
        var nextId = 0L
        var allocations = 0
        val machine = AppStateMachine {
            allocations += 1
            ++nextId
        }

        val listening = machine.transition(StateTransition.ToListening).getOrThrow()
        assertEquals(AppState.Listening(1L), listening)
        assertEquals(1, allocations)

        val thinking = machine.transition(StateTransition.ToThinking).getOrThrow()
        assertEquals(AppState.Thinking(2L), thinking)
        assertEquals(2, allocations)

        val speaking = machine.transition(StateTransition.ToSpeaking).getOrThrow()
        assertEquals(AppState.Speaking(2L), speaking)
        assertEquals(2, allocations)

        val interrupted = machine.transition(StateTransition.ToListening).getOrThrow()
        assertEquals(AppState.Listening(3L), interrupted)
        assertEquals(3, allocations)

        assertEquals(AppState.Idle, machine.transition(StateTransition.ToIdle).getOrThrow())
    }

    @Test
    fun thinkingCancelAllocatesANewListeningTurn() {
        val machine = AppStateMachine(AppState.Thinking(turnId = 8L), allocateTurnId = { 9L })
        val cancelled = machine.transition(StateTransition.ToListening).getOrThrow()
        assertEquals(AppState.Listening(9L), cancelled)
    }

    @Test
    fun speakingKeepsThinkingTurnId() {
        var allocations = 0
        val machine = AppStateMachine(AppState.Thinking(turnId = 7L)) {
            allocations += 1
            99L
        }
        assertEquals(AppState.Speaking(7L), machine.transition(StateTransition.ToSpeaking).getOrThrow())
        assertEquals(0, allocations)
    }

    @Test
    fun eachListedLegalEdgeSucceeds() {
        assertEquals(
            AppState.Listening(1L),
            AppStateMachine(allocateTurnId = { 1L }).transition(StateTransition.ToListening).getOrThrow(),
        )
        assertEquals(
            AppState.Thinking(3L),
            AppStateMachine(allocateTurnId = { 3L }).transition(StateTransition.ToThinking).getOrThrow(),
        )
        assertEquals(
            AppState.Alert("notice"),
            AppStateMachine().transition(StateTransition.ToAlert("notice")).getOrThrow(),
        )
        assertEquals(
            AppState.Thinking(2L),
            AppStateMachine(AppState.Listening(1L), allocateTurnId = { 2L })
                .transition(StateTransition.ToThinking)
                .getOrThrow(),
        )
        assertEquals(
            AppState.Idle,
            AppStateMachine(AppState.Listening(1L)).transition(StateTransition.ToIdle).getOrThrow(),
        )
        assertEquals(
            AppState.Alert("listen"),
            AppStateMachine(AppState.Listening(1L))
                .transition(StateTransition.ToAlert("listen"))
                .getOrThrow(),
        )
        assertEquals(
            AppState.Idle,
            AppStateMachine(AppState.Thinking(4L)).transition(StateTransition.ToIdle).getOrThrow(),
        )
        assertEquals(
            AppState.Alert("think"),
            AppStateMachine(AppState.Thinking(4L))
                .transition(StateTransition.ToAlert("think"))
                .getOrThrow(),
        )
        assertEquals(
            AppState.Idle,
            AppStateMachine(AppState.Speaking(4L)).transition(StateTransition.ToIdle).getOrThrow(),
        )
        assertEquals(
            AppState.Listening(5L),
            AppStateMachine(AppState.Speaking(4L), allocateTurnId = { 5L })
                .transition(StateTransition.ToListening)
                .getOrThrow(),
        )
        assertEquals(
            AppState.Alert("speak"),
            AppStateMachine(AppState.Speaking(4L))
                .transition(StateTransition.ToAlert("speak"))
                .getOrThrow(),
        )
        assertEquals(
            AppState.Idle,
            AppStateMachine(AppState.Alert("x")).transition(StateTransition.ToIdle).getOrThrow(),
        )
    }

    @Test
    fun illegalPathsLeaveStateUnchangedAndDoNotAllocate() {
        val cases = listOf(
            AppState.Idle to StateTransition.ToIdle,
            AppState.Idle to StateTransition.ToSpeaking,
            AppState.Listening(1L) to StateTransition.ToListening,
            AppState.Listening(1L) to StateTransition.ToSpeaking,
            AppState.Thinking(2L) to StateTransition.ToThinking,
            AppState.Speaking(2L) to StateTransition.ToThinking,
            AppState.Speaking(2L) to StateTransition.ToSpeaking,
            AppState.Alert("blocked") to StateTransition.ToListening,
            AppState.Alert("blocked") to StateTransition.ToThinking,
            AppState.Alert("blocked") to StateTransition.ToSpeaking,
            AppState.Alert("blocked") to StateTransition.ToAlert("again"),
        )
        cases.forEach { (initial, target) ->
            var allocations = 0
            val machine = AppStateMachine(initial) {
                allocations += 1
                100L
            }
            val result = machine.transition(target)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertEquals(initial, machine.current)
            assertEquals(0, allocations)
        }
    }
}
