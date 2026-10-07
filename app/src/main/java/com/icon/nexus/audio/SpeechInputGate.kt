package com.icon.nexus.audio

/**
 * In-memory stand-in for [SpeechInput]. It records the active turn and does
 * not open a microphone.
 */
class SpeechInputGate : SpeechInput {
    var activeTurnId: Long? = null
        private set

    override val isActive: Boolean
        get() = activeTurnId != null

    override fun startListening(turnId: Long) {
        activeTurnId = turnId
    }

    override fun stopListening() {
        activeTurnId = null
    }
}
