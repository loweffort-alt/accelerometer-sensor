package com.loweffort.quakesense.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accelEntity")
data class AccelEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val x: Double,
    val y: Double,
    val z: Double,
    val systemTimeOnSensor: Long,
    val timestamp: Long = System.currentTimeMillis()
)
