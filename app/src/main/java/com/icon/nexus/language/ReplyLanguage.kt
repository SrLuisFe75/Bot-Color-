package com.icon.nexus.language

import java.util.Locale

/**
 * Spanish and English are the two pipeline languages. AUTO leaves the
 * recognizer unpinned and picks one of these from the latest text.
 */
enum class ReplyLanguage(val tag: String) {
    Spanish("es"),
    English("en"),
}

enum class LanguageChoice {
    Auto,
    Spanish,
    English,
    Custom,
}

const val LANGUAGE_SUPPORT = "Español and English are both supported."

const val SPANISH_TAG = "es"
const val ENGLISH_TAG = "en"

/**
 * A blank tag is AUTO, the default. Español and English are the pinned
 * modes. Any other tag stays a custom pin so an existing language tag is
 * still what recognition and speech are asked to use.
 */
fun languageChoice(tag: String): LanguageChoice {
    val trimmed = tag.trim()
    if (trimmed.isEmpty() || trimmed.equals("auto", ignoreCase = true)) {
        return LanguageChoice.Auto
    }
    val primary = trimmed.substringBefore('-').lowercase(Locale.ROOT)
    return when (primary) {
        "es" -> LanguageChoice.Spanish
        "en" -> LanguageChoice.English
        else -> LanguageChoice.Custom
    }
}

/**
 * What the recognizer should be asked. AUTO does not pin [pinLanguage].
 * [detectLanguage] is true only for AUTO, where the platform extra
 * `RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION` is set.
 */
data class RecognitionPlan(
    val pinLanguage: String?,
    val detectLanguage: Boolean,
)

fun recognitionPlan(tag: String): RecognitionPlan {
    val trimmed = tag.trim()
    if (languageChoice(trimmed) == LanguageChoice.Auto) {
        return RecognitionPlan(pinLanguage = null, detectLanguage = true)
    }
    return RecognitionPlan(pinLanguage = trimmed, detectLanguage = false)
}

/**
 * Pinned Español and English ignore the text. AUTO uses [detectReplyLanguage].
 * A custom tag does not add a Spanish or English instruction.
 */
fun replyLanguageFor(
    tag: String,
    text: String,
    previous: ReplyLanguage = ReplyLanguage.English,
): ReplyLanguage? {
    return when (languageChoice(tag)) {
        LanguageChoice.Auto -> detectReplyLanguage(text, previous)
        LanguageChoice.Spanish -> ReplyLanguage.Spanish
        LanguageChoice.English -> ReplyLanguage.English
        LanguageChoice.Custom -> null
    }
}

/**
 * Spanish or English from [text]. A later clause wins when both languages
 * are present. Text with no signal keeps [previous].
 */
fun detectReplyLanguage(
    text: String,
    previous: ReplyLanguage = ReplyLanguage.English,
): ReplyLanguage {
    var start = 0
    var decided: ReplyLanguage? = null
    val length = text.length
    var index = 0
    while (index <= length) {
        val boundary = index == length || text[index] == '.' || text[index] == '?' || text[index] == '!' || text[index] == '\n'
        if (boundary) {
            if (index > start) {
                val winner = scoreWinner(text, start, index)
                if (winner != null) decided = winner
            }
            start = index + 1
        }
        index += 1
    }
    return decided ?: previous
}

private fun scoreWinner(text: String, start: Int, end: Int): ReplyLanguage? {
    var spanish = 0
    var english = 0
    val word = StringBuilder()
    var index = start
    while (index < end) {
        val character = text[index]
        if (character.isLetter()) {
            word.append(character.lowercaseChar())
        } else {
            val scored = scoreWord(word)
            spanish += scored ushr 16
            english += scored and 0xFFFF
            if (character == '¿' || character == '¡') spanish += 2
        }
        index += 1
    }
    val scored = scoreWord(word)
    spanish += scored ushr 16
    english += scored and 0xFFFF
    return when {
        spanish > english -> ReplyLanguage.Spanish
        english > spanish -> ReplyLanguage.English
        else -> null
    }
}

private fun scoreWord(word: StringBuilder): Int {
    if (word.isEmpty()) return 0
    val token = word.toString()
    word.clear()
    var spanish = 0
    var english = 0
    if (SPANISH_WORDS.contains(token)) spanish += 1
    if (ENGLISH_WORDS.contains(token)) english += 1
    if (hasSpanishMark(token)) spanish += 2
    return (spanish shl 16) or english
}

private fun hasSpanishMark(token: String): Boolean {
    var index = 0
    while (index < token.length) {
        when (token[index]) {
            'á', 'é', 'í', 'ó', 'ú', 'ü', 'ñ' -> return true
        }
        index += 1
    }
    return false
}

private val SPANISH_WORDS = hashSetOf(
    "hola", "gracias", "por", "para", "porque", "qué", "que", "cómo", "como",
    "dónde", "donde", "cuándo", "cuando", "estoy", "está", "estás", "esta",
    "este", "esto", "necesito", "quiero", "puedes", "puedo", "ayuda",
    "ayúdame", "ayudarme", "buenos", "días", "dia", "noche", "también", "ahora", "aquí",
    "favor", "el", "la", "los", "las", "una", "uno", "del", "al", "muy",
    "bien", "tengo", "tiene", "tienes", "hay", "con", "pero", "más", "mas",
    "ya", "lo", "le", "se", "te", "mi", "tu", "su", "es", "soy", "eres",
    "somos", "un", "unos", "unas", "de", "en", "y", "sí", "español",
)

private val ENGLISH_WORDS = hashSetOf(
    "the", "hello", "please", "what", "how", "can", "you", "your", "are",
    "is", "am", "dont", "don't", "its", "it's", "this", "that", "want",
    "need", "help", "thanks", "thank", "good", "morning", "yes", "have",
    "with", "from", "they", "we", "was", "were", "not", "and", "of", "to",
    "for", "my", "it", "do", "be", "i", "i'm", "im", "hi", "hey", "english",
)
