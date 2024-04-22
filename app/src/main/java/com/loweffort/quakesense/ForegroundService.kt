package com.loweffort.quakesense

import android.content.ContentValues.TAG
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import com.loweffort.quakesense.room.AccelDatabase
import com.loweffort.quakesense.room.AccelEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class ForegroundService : Service() {
    private val FOREGROUND_SERVICE_ID = 101
    private lateinit var handler: Handler
    private lateinit var database: AccelDatabase
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var accelerationCurrentValueX: Double = 0.0
    private var accelerationCurrentValueY: Double = 0.0
    private var accelerationCurrentValueZ: Double = 0.0

    private val serviceScope = CoroutineScope(Dispatchers.Main)

    private val sensorEventListener: SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(sensorEvent: SensorEvent) {
            val x: Float = sensorEvent.values[0]
            val y: Float = sensorEvent.values[1]
            val z: Float = sensorEvent.values[2] - 9.81f

            accelerationCurrentValueX = String.format("%.5f", x).toDouble()
            accelerationCurrentValueY = String.format("%.5f", y).toDouble()
            accelerationCurrentValueZ = String.format("%.5f", z).toDouble()

            sensorEvent.let {
                val reading = AccelEntity(
                    x = accelerationCurrentValueX,
                    y = accelerationCurrentValueY,
                    z = accelerationCurrentValueZ
                )
                Log.d(TAG, reading.toString())
                saveReading(reading)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, i: Int) {
            //this field is for improve the precision and make low-effort if is needed
        }
    }

    private fun saveReading(reading: AccelEntity) {
        // Ejecutar el bloque de código dentro de una corrutina
        serviceScope.launch {
            Log.d("Runnable", "Runnable Corriendo")
            database.accelReadingDao().insertReading(reading)
            database.accelReadingDao().keepMaxNumberOfData()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate called")
        handler = Handler() // Inicializar el handler aquí
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val mainActivityCntxt: Context? = SingletonContextProvider.getContext()
        if (mainActivityCntxt == null) {
            Log.e(TAG, "Context is null")
            // Handle error if context is null
            return
        }
        database = AccelDatabase.getDatabase(mainActivityCntxt)
    }
    //TODO: Comprobar si la base de datos también es leida y escribida en segundo plano.

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")
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
        // Calcular el intervalo de tiempo en milisegundos
        val intervalMillis = 20

        sensorManager.registerListener(sensorEventListener, accelerometer, intervalMillis * 1000)
        //handler.postDelayed(logRunnable, 2000)
    }

    private val logRunnable = object : Runnable {
        override fun run() {
            // Ejecutar el bloque de código dentro de una corrutina
            serviceScope.launch {
                val firstData = database.accelReadingDao().getFirstData()
                val numberData = database.accelReadingDao().getCount()
                Log.d(TAG, "$numberData $firstData")
            }
            handler.postDelayed(this, 2000)
        }
    }

    // Este método es opcional, pero puede ser útil si necesitas detener el servicio en algún momento.
    fun stopForegroundService() {
        stopForeground(true)
        stopSelf()
    }
}