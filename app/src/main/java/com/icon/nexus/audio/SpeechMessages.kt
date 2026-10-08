package com.icon.nexus.audio

object SpeechMessages {
    const val EXPLANATION = "ICON needs the microphone to hear one utterance."
    const val PERMISSION = "Microphone permission is needed to listen."
    const val EMPTY = "I didn\u2019t catch that."
    const val BUSY = "The microphone is busy."
    const val NETWORK = "Speech recognition needs a network connection."
    const val FAILED = "Speech recognition failed."
}

/**
 * Android [android.speech.SpeechRecognizer] error codes:
 * 1 network timeout, 2 network, 6 speech timeout, 7 no match,
 * 8 recognizer busy, 9 insufficient permissions.
 */
internal fun speechFailureForCode(code: Int): SpeechFailure = when (code) {
    6, 7 -> SpeechFailure.NoMatch
    8 -> SpeechFailure.Busy
    1, 2 -> SpeechFailure.Network
    9 -> SpeechFailure.Permission
    else -> SpeechFailure.Unavailable
}

internal fun speechErrorMessage(failure: SpeechFailure): String = when (failure) {
    SpeechFailure.NoMatch -> SpeechMessages.EMPTY
    SpeechFailure.Busy -> SpeechMessages.BUSY
    SpeechFailure.Network -> SpeechMessages.NETWORK
    SpeechFailure.Permission -> SpeechMessages.PERMISSION
    SpeechFailure.Unavailable -> SpeechMessages.FAILED
}
