package com.icon.nexus.ai

import com.icon.nexus.data.SettingsRepository
import com.icon.nexus.settings.ModelProviderId

/**
 * Picks the active [AIProvider]. Demo mode, a blank key, or any provider
 * other than Gemini stays on [DemoProvider], so the app runs with no key.
 */
class AIManager(
    private val settings: SettingsRepository,
    private val demo: AIProvider,
    private val gemini: AIProvider,
) {
    suspend fun select(): AIProvider {
        val current = settings.get()
        val useGemini = !current.demoMode &&
            current.provider == ModelProviderId.GEMINI &&
            current.apiKey.isNotBlank()
        return if (useGemini) gemini else demo
    }
}
