package com.icon.nexus.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val text: String,
    val timestamp: Long,
)

@Entity(tableName = "memory_switch")
data class MemorySwitchEntity(
    @PrimaryKey val id: Int = SWITCH_ID,
    val enabled: Boolean,
) {
    companion object {
        const val SWITCH_ID = 0
    }
}
