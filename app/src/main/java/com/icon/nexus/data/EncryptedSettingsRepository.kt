package com.icon.nexus.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.icon.nexus.domain.VisualThemeId
import com.icon.nexus.settings.ModelProviderId
import com.icon.nexus.settings.SettingsDefaults
import com.icon.nexus.settings.SettingsKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Stores settings in [EncryptedSharedPreferences], including the Gemini API
 * key. The key defaults to empty; nothing in source supplies a credential.
 * Voice rate, volume, language, and visual sensitivity are stored here too.
 */
class EncryptedSettingsRepository(
    context: Context,
) : SettingsRepository {
    private val preferences: SharedPreferences = open(context.applicationContext)
    private val state = MutableStateFlow(read())

    override fun observe(): Flow<AppSettings> = state.asStateFlow()

    override suspend fun get(): AppSettings = withContext(Dispatchers.IO) {
        val loaded = read()
        state.value = loaded
        loaded
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        withContext(Dispatchers.IO) {
            val next = transform(read())
            preferences.edit()
                .putString(SettingsKeys.API_KEY, next.apiKey)
                .putString(SettingsKeys.GEMINI_MODEL, next.geminiModel)
                .putString(SettingsKeys.PROVIDER, next.provider.wireName)
                .putString(SettingsKeys.PERSONA_NAME, next.personaName)
                .putString(SettingsKeys.PERSONALITY, next.personality)
                .putFloat(SettingsKeys.VOICE_RATE, next.voiceRate)
                .putFloat(SettingsKeys.VOICE_VOLUME, next.voiceVolume)
                .putString(SettingsKeys.LANGUAGE_TAG, next.languageTag)
                .putString(SettingsKeys.THEME, next.theme.name)
                .putBoolean(SettingsKeys.DEMO_MODE, next.demoMode)
                .putBoolean(SettingsKeys.SHOW_TRANSCRIPT, next.showTranscript)
                .putFloat(SettingsKeys.VISUAL_SENSITIVITY, next.visualSensitivity)
                .commit()
            state.value = next
        }
    }

    private fun read(): AppSettings {
        val defaults = AppSettings.defaults()
        return defaults.copy(
            apiKey = preferences.getString(SettingsKeys.API_KEY, defaults.apiKey) ?: defaults.apiKey,
            geminiModel = preferences.getString(SettingsKeys.GEMINI_MODEL, defaults.geminiModel)
                ?: defaults.geminiModel,
            provider = ModelProviderId.fromWire(
                preferences.getString(SettingsKeys.PROVIDER, defaults.provider.wireName),
            ),
            personaName = preferences.getString(SettingsKeys.PERSONA_NAME, defaults.personaName)
                ?: defaults.personaName,
            personality = preferences.getString(SettingsKeys.PERSONALITY, defaults.personality)
                ?: defaults.personality,
            voiceRate = preferences.getFloat(SettingsKeys.VOICE_RATE, defaults.voiceRate),
            voiceVolume = preferences.getFloat(SettingsKeys.VOICE_VOLUME, defaults.voiceVolume),
            languageTag = preferences.getString(SettingsKeys.LANGUAGE_TAG, defaults.languageTag)
                ?: defaults.languageTag,
            theme = themeOrDefault(preferences.getString(SettingsKeys.THEME, defaults.theme.name)),
            demoMode = preferences.getBoolean(SettingsKeys.DEMO_MODE, defaults.demoMode),
            showTranscript = preferences.getBoolean(
                SettingsKeys.SHOW_TRANSCRIPT,
                defaults.showTranscript,
            ),
            visualSensitivity = preferences.getFloat(
                SettingsKeys.VISUAL_SENSITIVITY,
                defaults.visualSensitivity,
            ),
        )
    }

    private fun themeOrDefault(raw: String?): VisualThemeId =
        VisualThemeId.entries.firstOrNull { it.name == raw } ?: SettingsDefaults.THEME

    companion object {
        private const val FILE_NAME = "icon_secure_settings"

        private fun open(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
