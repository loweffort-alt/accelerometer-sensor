package com.loweffort.quakesense

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.util.Log
import androidx.core.app.NotificationCompat

class ForegroundService : Service() {
    private val FOREGROUND_SERVICE_ID = 101
    private lateinit var handler: Handler

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("ForegroundService", "onCreate called")
        handler = Handler() // Inicializar el handler aquí
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ForegroundService", "onStartCommand called")
        // Recuperar la interfaz del intent y ejecutar el método run()
        val executionSensorGetToken = intent?.getParcelableExtra<MainExecution>("executionSensorGetToken")
        executionSensorGetToken?.getToken()
        executionSensorGetToken?.registerSensor()

        startForegroundService()
        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = "my_service"
        val channelName = "My Background Service"
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Servicio en primer plano")
            .setContentText("Este es un ejemplo de cómo funciona un Foreground Service.")

        val notification = notificationBuilder.build()

        startForeground(FOREGROUND_SERVICE_ID, notification)

        // Iniciar el Runnable para que se ejecute periódicamente
    }

    // Este método es opcional, pero puede ser útil si necesitas detener el servicio en algún momento.
    fun stopForegroundService() {
        stopForeground(true)
        stopSelf()
    }
}