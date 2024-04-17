package com.loweffort.quakesense.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import java.sql.Timestamp

@Dao
interface AccelDao {
    @Insert
    suspend fun insertReading(lectura: AccelEntity)

    @Query("SELECT * FROM accelEntity ORDER BY id DESC LIMIT 15000")
    suspend fun getAllReadings(): List<AccelEntity>

    // Para registrar los 15k datos, necesito registrar datos por mínimos 5 minutos.
    @Query("SELECT * FROM accelEntity WHERE timestamp <= :timestampFromServer ORDER BY id DESC LIMIT 15000")
    suspend fun getInitialData(timestampFromServer: Long): List<AccelEntity>

    //Almacena 90000 datos máximo. Estos datos se registran en mínimo 30 minutos.
    @Query("DELETE FROM accelEntity WHERE id NOT IN (SELECT id from accelEntity ORDER BY id DESC LIMIT 90000)")
    suspend fun keepMaxNumberOfData()

    //Cuando abra la app, necesito borrar los datos. (En un futuro, cuando abra la app necesito
    // confirmar si borro o no los datos de la sesión anterior)
    @Query("DELETE FROM accelEntity")
    suspend fun resetDatabase()

    @Query("SELECT COUNT(*) FROM accelEntity")
    suspend fun getCount(): Int
}