package com.icon.nexus.settings

import com.icon.nexus.domain.VisualThemeId

object SettingsDefaults {
    const val ASSISTANT_NAME = "ICON"
    const val PERSONALITY =
        "ICON is a concise voice assistant. Answer directly, stay calm, and keep turns short."
    const val DEMO_MODE = true
    const val VOICE_RATE = 1.0f
    const val SHOW_TRANSCRIPT = true
    const val API_KEY = ""
    val THEME = VisualThemeId.Nexus
    val PROVIDER = ModelProviderId.DEMO
}
