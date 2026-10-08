package com.icon.nexus.audio

import java.util.Locale

/**
 * A blank tag keeps the device locale. A set tag is the language both
 * recognition and speech are asked to use.
 */
fun resolvedLanguageTag(
    tag: String,
    deviceTag: String = Locale.getDefault().toLanguageTag(),
): String {
    return tag.trim().ifEmpty { deviceTag }
}

fun ttsLanguage(
    tag: String,
    deviceTag: String = Locale.getDefault().toLanguageTag(),
): Locale {
    return Locale.forLanguageTag(resolvedLanguageTag(tag, deviceTag))
}
