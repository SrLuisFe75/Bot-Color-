package com.icon.nexus.ai

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

internal object GeminiMessages {
    const val BLANK_KEY = "Add a Gemini API key in Settings."
    const val INVALID_KEY = "That API key was rejected."
    const val RATE_LIMIT = "Gemini is rate limited. Try again in a moment."
    const val OFFLINE = "You appear to be offline."
    const val TIMEOUT = "Gemini timed out."
    const val EMPTY = "Gemini returned an empty response."
    const val UNAVAILABLE = "Gemini could not answer."
}

internal fun geminiSystemInstruction(name: String, personality: String): String {
    val resolvedName = name.trim().ifEmpty { "ICON" }
    val trait = personality.trim()
    return buildString {
        append("Your name is ")
        append(resolvedName)
        append(". ")
        if (trait.isNotEmpty()) {
            append(trait)
            if (!trait.endsWith('.')) append('.')
            append(' ')
        }
        append("Reply in natural spoken sentences. Do not use Markdown. Do not use symbol lists.")
    }
}

internal fun geminiFailureMessage(httpCode: Int?, error: Throwable?): String {
    return when (httpCode) {
        401, 403 -> GeminiMessages.INVALID_KEY
        429 -> GeminiMessages.RATE_LIMIT
        null -> when {
            error.isGeminiTimeout() -> GeminiMessages.TIMEOUT
            error is IOException -> GeminiMessages.OFFLINE
            else -> GeminiMessages.UNAVAILABLE
        }
        else -> GeminiMessages.UNAVAILABLE
    }
}

private fun Throwable?.isGeminiTimeout(): Boolean {
    var current = this
    while (current != null) {
        if (current is SocketTimeoutException) return true
        if (current is InterruptedIOException &&
            current.message?.contains("timeout", ignoreCase = true) == true
        ) {
            return true
        }
        current = current.cause
    }
    return false
}
