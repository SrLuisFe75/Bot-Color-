package com.icon.nexus.audio

/**
 * Recognizer contract. The microphone may open only while the app is in
 * Listening. Speaking blocks the mic unless the user interrupts: stop speech
 * first, allocate a new turn id, then enter Listening. [startListening] does
 * not capture audio.
 */
interface SpeechInput {
    val isActive: Boolean

    fun startListening(turnId: Long)

    fun stopListening()
}
