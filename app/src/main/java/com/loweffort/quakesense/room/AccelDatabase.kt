package com.loweffort.quakesense.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AccelEntity::class], version = 1, exportSchema = false)
abstract class AccelDatabase: RoomDatabase() {
    abstract fun accelReadingDao(): AccelDao

    companion object {
        @Volatile
        private var INSTANCE: AccelDatabase? = null

        fun getDatabase(context: Context): AccelDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AccelDatabase::class.java,
                    "acelerometro_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}