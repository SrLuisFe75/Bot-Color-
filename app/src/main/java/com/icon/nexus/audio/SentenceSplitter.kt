package com.icon.nexus.audio

data class SentenceSplit(
    val sentences: List<String>,
    val remainder: String,
)

/**
 * Pulls finished sentences out of [text]. A sentence ends at `.`, `?`, or `!`,
 * including a run of those marks such as `?!`. The unfinished tail is [remainder].
 */
fun splitCompletedSentences(text: String): SentenceSplit {
    val sentences = mutableListOf<String>()
    val current = StringBuilder()
    var index = 0
    while (index < text.length) {
        val character = text[index]
        current.append(character)
        if (character == '.' || character == '?' || character == '!') {
            while (index + 1 < text.length && text[index + 1].isSentenceMark()) {
                index += 1
                current.append(text[index])
            }
            val sentence = current.toString().trim()
            if (sentence.any { it.isLetterOrDigit() }) {
                sentences += sentence
            }
            current.clear()
        }
        index += 1
    }
    return SentenceSplit(sentences, current.toString())
}

private fun Char.isSentenceMark(): Boolean = this == '.' || this == '?' || this == '!'
