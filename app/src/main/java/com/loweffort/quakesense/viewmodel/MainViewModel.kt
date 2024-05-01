package com.loweffort.quakesense.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.loweffort.quakesense.room.AccelDatabase
import com.loweffort.quakesense.room.AccelEntity
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AccelDatabase.getDatabase(application)

    // LiveData que observa los primeros datos de la base de datos
    val firstAccelerometerData: LiveData<List<AccelEntity>> = database.accelReadingDao().observeFirstData()
    val lastAccelerometerData: LiveData<List<AccelEntity>> = database.accelReadingDao().observeLastData()

    // Método para cargar los primeros datos desde la base de datos
    fun fetchFirstData() {
        viewModelScope.launch {
            val firstData = database.accelReadingDao().getFirstData()
        }
    }
}