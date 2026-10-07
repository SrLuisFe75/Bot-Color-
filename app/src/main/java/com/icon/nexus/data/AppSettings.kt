package com.icon.nexus.data

import com.icon.nexus.domain.VisualThemeId
import com.icon.nexus.settings.ModelProviderId
import com.icon.nexus.settings.SettingsDefaults

data class AppSettings(
    val apiKey: String,
    val geminiModel: String,
    val provider: ModelProviderId,
    val personaName: String,
    val personality: String,
    val voiceRate: Float,
    val theme: VisualThemeId,
    val demoMode: Boolean,
    val showTranscript: Boolean,
) {
    companion object {
        fun defaults(): AppSettings = AppSettings(
            apiKey = SettingsDefaults.API_KEY,
            geminiModel = SettingsDefaults.GEMINI_MODEL,
            provider = SettingsDefaults.PROVIDER,
            personaName = SettingsDefaults.ASSISTANT_NAME,
            personality = SettingsDefaults.PERSONALITY,
            voiceRate = SettingsDefaults.VOICE_RATE,
            theme = SettingsDefaults.THEME,
            demoMode = SettingsDefaults.DEMO_MODE,
            showTranscript = SettingsDefaults.SHOW_TRANSCRIPT,
        )
    }
}
