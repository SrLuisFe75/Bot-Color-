package com.icon.nexus.domain

sealed interface StateTransition {
    val name: String

    data object ToIdle : StateTransition {
        override val name: String = "Idle"
    }

    data object ToListening : StateTransition {
        override val name: String = "Listening"
    }

    data object ToThinking : StateTransition {
        override val name: String = "Thinking"
    }

    data object ToSpeaking : StateTransition {
        override val name: String = "Speaking"
    }

    data class ToAlert(val message: String) : StateTransition {
        override val name: String = "Alert"
    }
}
