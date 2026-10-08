package com.icon.nexus.data

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observe(): Flow<AppSettings>

    suspend fun get(): AppSettings

    suspend fun update(transform: (AppSettings) -> AppSettings)
}
