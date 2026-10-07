package com.icon.nexus.domain

sealed interface AppState {
    val name: String

    data object Idle : AppState {
        override val name: String = "Idle"
    }

    data class Listening(val turnId: Long) : AppState {
        override val name: String = "Listening"
    }

    data class Thinking(val turnId: Long) : AppState {
        override val name: String = "Thinking"
    }

    data class Speaking(val turnId: Long) : AppState {
        override val name: String = "Speaking"
    }

    data class Alert(val message: String) : AppState {
        override val name: String = "Alert"
    }
}
