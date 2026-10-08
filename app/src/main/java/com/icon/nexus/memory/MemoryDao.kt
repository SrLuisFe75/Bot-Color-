package com.icon.nexus.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MemoryDao {
    @Query("SELECT enabled FROM memory_switch WHERE id = :id")
    suspend fun enabled(id: Int = MemorySwitchEntity.SWITCH_ID): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSwitch(row: MemorySwitchEntity)

    @Query("SELECT * FROM memories ORDER BY timestamp ASC")
    suspend fun list(): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM memories")
    suspend fun clear()
}
