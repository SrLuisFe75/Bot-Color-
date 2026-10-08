package com.icon.nexus.domain

import com.icon.nexus.settings.SettingsDefaults

data class AssistantPersona(
    val name: String,
    val personality: String,
)

/**
 * Blank name or personality uses the built-in defaults. Stored settings may
 * be empty; the system instruction still receives a name and a personality.
 */
fun resolvedPersona(name: String, personality: String): AssistantPersona {
    return AssistantPersona(
        name = name.trim().ifEmpty { SettingsDefaults.ASSISTANT_NAME },
        personality = personality.trim().ifEmpty { SettingsDefaults.PERSONALITY },
    )
}
