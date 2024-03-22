package com.loweffort.quakesense.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AccelDao {
    @Insert
    suspend fun insertReading(lectura: AccelEntity)

    @Query("SELECT * FROM accelEntity ORDER BY timestamp DESC")
    suspend fun getAllReadings(): List<AccelEntity>

    @Query("DELETE FROM accelEntity WHERE id NOT IN (SELECT id from accelEntity ORDER BY timestamp DESC LIMIT 500)")
    suspend fun keepMaxNumberOfData()
}