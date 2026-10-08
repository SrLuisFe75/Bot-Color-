package com.icon.nexus.audio

/**
 * Recognizer contract. The microphone may open only while the app is in
 * Listening, and only for one utterance. Leaving Listening must stop it.
 * A result or error is delivered once, tagged with the turn id passed to
 * [startListening]. [SpeechInputGate] records the turn and does not capture.
 */
interface SpeechInput {
    val isActive: Boolean

    fun startListening(turnId: Long)

    fun stopListening()

    fun setListener(listener: SpeechEventListener?) {}
}

fun interface SpeechEventListener {
    fun onSpeechEvent(event: SpeechEvent)
}

sealed interface SpeechEvent {
    val turnId: Long

    data class Result(override val turnId: Long, val text: String) : SpeechEvent

    data class Failure(override val turnId: Long, val reason: SpeechFailure) : SpeechEvent
}

enum class SpeechFailure {
    NoMatch,
    Busy,
    Network,
    Permission,
    Unavailable,
}
