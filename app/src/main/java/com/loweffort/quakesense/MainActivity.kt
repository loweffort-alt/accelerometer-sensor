package com.loweffort.quakesense


import android.content.ContentValues.TAG
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Parcel
import android.os.Parcelable
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.Viewport
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import com.loweffort.quakesense.room.AccelDatabase
import com.loweffort.quakesense.room.AccelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

interface MainExecution : Parcelable {
    fun getToken()
    fun registerSensor()
}

class MainActivity() : AppCompatActivity(), MainExecution {

    // Config to show notifications:
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            // FCM SDK (and your app) can post notifications.
            Toast.makeText(
                this,
                "FCM SDK (and your app) can post notifications",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            // Inform user that that your app will not show notifications.
            Toast.makeText(this, "app will not show notifications.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun askNotificationPermission() {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // FCM SDK (and your app) can post notifications.
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // TODO: display an educational UI explaining to the user the features that will be enabled
                //       by them granting the POST_NOTIFICATION permission. This UI should provide the user
                //       "OK" and "No thanks" buttons. If the user selects "OK," directly request the permission.
                //       If the user selects "No thanks," allow the user to continue without notifications.
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private lateinit var dataCounter: TextView

    //Buttons
    private lateinit var btnSendData: Button
    private lateinit var btnDeleteData: Button
    private lateinit var progressBarSendData: ProgressBar
    private val maxSaveCount = 500 //Máx datos grabados en 5 min: 30000

    //Sensors
    private var mSensorManager: SensorManager? = null
    private var mAccelerometer: Sensor? = null

    private var accelerationCurrentValueX: Double = 0.0
    private var accelerationCurrentValueY: Double = 0.0
    private var accelerationCurrentValueZ: Double = 0.0
    private var pointsPlotted: Double = 5.0

    private lateinit var seriesX: LineGraphSeries<DataPoint>
    private lateinit var seriesY: LineGraphSeries<DataPoint>
    private lateinit var seriesZ: LineGraphSeries<DataPoint>
    private val maxDataPoints = 100

    private lateinit var viewportX: Viewport
    /*private lateinit var viewportY: Viewport
    private lateinit var viewportZ: Viewport*/

    private lateinit var graphX: GraphView
    /*private lateinit var graphY: GraphView
    private lateinit var graphZ: GraphView*/

    private val handler = Handler()

    // Referencias del Firebase RealtimeDatabase
    private var firebaseRef: FirebaseDatabase = FirebaseDatabase.getInstance()
    private lateinit var firebaseAccelDataRef: DatabaseReference
    private var firebaseInfoDeviceRef: DatabaseReference = firebaseRef.getReference("infoDevice")

    private lateinit var deviceId: String

    // Guardado en DB local
    private lateinit var database: AccelDatabase

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
                //saveReading(reading)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, i: Int) {
            //this field is for improve the precision and make low-effort if is needed
        }
    }

    // Obtener ubicación desde GPS
    private val locationService: LocationService = LocationService()

    private val updateGraphRunnable: Runnable = object : Runnable {
        override fun run() {
            // Add one new data to each series
            pointsPlotted++
            seriesX.appendData(
                DataPoint(pointsPlotted, accelerationCurrentValueX),
                true,
                maxDataPoints
            )
            seriesY.appendData(
                DataPoint(pointsPlotted, accelerationCurrentValueY),
                true,
                maxDataPoints
            )
            seriesZ.appendData(
                DataPoint(pointsPlotted, accelerationCurrentValueZ),
                true,
                maxDataPoints
            )

            // Auto rescaling viewport
            viewportX.setMaxX(pointsPlotted)
            viewportX.setMinX(kotlin.math.max(0.0, pointsPlotted - maxDataPoints))

            // Reset data if necessary to remove invisible points
            if (seriesX.highestValueX - seriesX.lowestValueX > maxDataPoints) {
                seriesX.resetData(arrayOf())
            }
            if (seriesY.highestValueX - seriesY.lowestValueX > maxDataPoints) {
                seriesY.resetData(arrayOf())
            }
            if (seriesZ.highestValueX - seriesZ.lowestValueX > maxDataPoints) {
                seriesZ.resetData(arrayOf())
            }

            // Exec this code 100 times per second
            handler.postDelayed(this, 100)
        }
    }

    constructor(parcel: Parcel) : this() {
        accelerationCurrentValueX = parcel.readDouble()
        accelerationCurrentValueY = parcel.readDouble()
        accelerationCurrentValueZ = parcel.readDouble()
        pointsPlotted = parcel.readDouble()
        deviceId = parcel.readString().toString()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        SingletonContextProvider.setContext(this)
        deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        firebaseAccelDataRef = firebaseRef.getReference(deviceId)

        database = AccelDatabase.getDatabase(this)

        lifecycleScope.launch(Dispatchers.IO) {
            Log.d("Runnable", "Runnable Corriendo")
            // Borrar la base de datos al abrir la aplicación
            database.accelReadingDao().resetDatabase()
        }

        // Registra esta clase como un suscriptor de EventBus
        EventBus.getDefault().register(this)

        askNotificationPermission()
        getToken()
        //registerSensor()

        val intent = Intent(this, ForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("SDK_VERSIONForegroundService", Build.VERSION.SDK_INT.toString())
            fun doSomething() {
                // Función que deseas ejecutar desde ForegroundService
                // Por ejemplo:
                // actualizarUI()
            }
            startForegroundService(intent)
        } else {
            Log.d("SDK_VERSIONService", Build.VERSION.SDK_INT.toString())
            startService(intent)
        }

        initializeViews()
        initializeGraphs()
        updateTextEachSecond()
    }

    private fun updateTextEachSecond() {
        val handler = Handler()
        handler.postDelayed(object : Runnable {
            override fun run() {
                lifecycleScope.launch(Dispatchers.IO) {
                    // Devuelve la cantidad total de datos
                    val dataAmount = database.accelReadingDao().getCount()
                    withContext(Dispatchers.Main) {
                        val showDataAmount = resources.getString(R.string.dataAmount, dataAmount)
                        dataCounter.text = showDataAmount
                    }
                }
                handler.postDelayed(this, 1000)
            }
        }, 1000) // 1000 milisegundos = 1 segundo
    }

    private fun saveReading(reading: AccelEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            Log.d("Runnable", "Runnable Corriendo")
            // Aquí iría el código para insertar la lectura en la base de datos
            // y para mantener el máximo de datos a 500
            database.accelReadingDao().insertReading(reading)
            // Después de cada inserción, asegurarse de no superar los 90000 registros.
            // Actualmente hace 50 registros cada segundo y con un máximo de 90000 registros
            // significa que registra 1800 segundos (30 min) en Local
            database.accelReadingDao().keepMaxNumberOfData()
        }
    }

    override fun getToken() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = locationService.getUserLocation(this@MainActivity)
            var latitude: String? = null
            var longitude: String? = null
            var altitude: String? = null

            if (result != null) {
                latitude =
                    result.latitude.toString() // Latitude of this location Value is between -90.0 and 90.0 inclusive
                longitude =
                    result.longitude.toString() // Longitude of this location Value is between -180.0 and 180.0 inclusive
                altitude =
                    result.altitude.toString() //The altitude of this location in meters above the WGS84 reference ellipsoid.
            }

            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val tokenFCM = task.result
                    sendDeviceInfo(tokenFCM, latitude, longitude, altitude)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Error al obtener el token FCM",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun sendDeviceInfo(
        tokenFCM: String,
        latitude: String?,
        longitude: String?,
        altitude: String?
    ) {
        val deviceInfoId = firebaseInfoDeviceRef.push().key!!
        val deviceModel = Build.MODEL
        val deviceManufacturer = Build.MANUFACTURER
        val deviceSDKVersion = Build.VERSION.SDK_INT.toString()
        val packageManager = applicationContext.packageManager
        val packageName = applicationContext.packageName
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val appVersion = packageInfo.versionName
        val deviceInfo = DeviceInfo(
            deviceInfoId,
            deviceModel,
            deviceManufacturer,
            deviceSDKVersion,
            appVersion,
            tokenFCM,
        )
        val currentLocation = CurrentLocation(
            latitude,
            longitude,
            altitude
        )

        firebaseInfoDeviceRef.child(deviceId).setValue(deviceInfo)
        firebaseInfoDeviceRef.child(deviceId).child("currentLocation").setValue(currentLocation)
    }

    // Método para manejar el evento de recepción de notificación
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onNotificationReceived(event: NotificationReceivedEvent) {
        // Esto envía los datos cuando una notificación es recibida
        //handler.postDelayed(sendDataRunnable, 0)
        Log.d(TAG, event.seismTime)
        /*lifecycleScope.launch(Dispatchers.IO) {
            val timestampFromServer = event.seismTime.toLong()
            val firstData = database.accelReadingDao().getInitialData(timestampFromServer)
            firebaseAccelDataRef.child("localData").setValue(firstData)
            //val weaData = database.accelReadingDao().getAllReadings()
            //firebaseAccelDataRef.child("timestamp").setValue(weaData)
        }*/
        //handler.postDelayed({handler.removeCallbacks(sendDataRunnable)}, 30000)
    }

    private fun initializeViews() {
        dataCounter = findViewById(R.id.txt_accelX)
        btnSendData = findViewById(R.id.btnSendData)
        btnDeleteData = findViewById(R.id.btnDeleteData)
        progressBarSendData = findViewById(R.id.progressBarSendData)
        progressBarSendData.max = maxSaveCount
        progressBarSendData.progress = 0

        btnSendData.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val allReadings = database.accelReadingDao().getAllReadings()
                firebaseAccelDataRef.child("sismicData").setValue(allReadings)
            }
        }
        btnDeleteData.setOnClickListener {
            progressBarSendData.progress = 0
            // Borrar TODA la base de datos
            //firebaseRef.reference.setValue(null)
            // Borrar los datos de aceleración guardados desde el dispositivo
            firebaseAccelDataRef.setValue(null)
            // Borrar los datos propios de TODOS los dispositivos
            //firebaseInfoDeviceRef.setValue(null)
        }
    }

    private fun initializeGraphs() {
        graphX = findViewById(R.id.graphX)
        viewportX = graphX.viewport
        seriesX = LineGraphSeries()
        seriesY = LineGraphSeries()
        seriesZ = LineGraphSeries()
        graphX.addSeries(seriesX)
        graphX.addSeries(seriesY)
        graphX.addSeries(seriesZ)
        graphX.viewport.isXAxisBoundsManual = true

        // Customize Graph
        seriesX.title = "Axis X"
        seriesX.color = Color.GREEN

        seriesY.title = "Axis Y"
        seriesY.color = Color.RED

        seriesZ.title = "Axis Z"
        seriesZ.color = Color.BLUE
    }

    override fun registerSensor() {
        /*mSensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
        mAccelerometer = mSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)*/

        // Calcular el intervalo de tiempo en milisegundos
        //val intervalMillis = 20

       /* mSensorManager?.registerListener(
            sensorEventListener,
            mAccelerometer,
            intervalMillis * 1000 // Actualmente lee 50 datos por segundo
        )*/
    }

    override fun describeContents(): Int {
        TODO("Not yet implemented")
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        TODO("Not yet implemented")
    }

    private fun unregisterSensor() {
        mSensorManager?.unregisterListener(sensorEventListener)
    }

    private fun startUpdateGraphRunnable() {
        handler.postDelayed(updateGraphRunnable, 0)
    }

    private fun stopUpdateGraphRunnable() {
        handler.removeCallbacks(updateGraphRunnable)
    }

    override fun onResume() {
        super.onResume()
        registerSensor()
        startUpdateGraphRunnable()
    }

    override fun onPause() {
        super.onPause()
        //unregisterSensor()
        stopUpdateGraphRunnable()
    }

    override fun onDestroy() {
        // Desregistra esta clase como suscriptor de EventBus
        EventBus.getDefault().unregister(this)
        super.onDestroy()
        unregisterSensor()
        stopUpdateGraphRunnable()
    }

    companion object CREATOR : Parcelable.Creator<MainActivity> {
        override fun createFromParcel(parcel: Parcel): MainActivity {
            return MainActivity(parcel)
        }

        override fun newArray(size: Int): Array<MainActivity?> {
            return arrayOfNulls(size)
        }
    }
}
