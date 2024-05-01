package com.loweffort.quakesense


import android.content.ContentValues.TAG
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Parcel
import android.os.Parcelable
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
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
import com.loweffort.quakesense.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.text.SimpleDateFormat
import java.util.Date

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

    private lateinit var dataAmount: TextView
    private lateinit var lastSism: TextView
    private lateinit var firstData: TextView
    private lateinit var lastData: TextView
    private lateinit var recordTime: TextView

    //Buttons
    private lateinit var btnSendData: Button
    private lateinit var btnDeleteData: Button

    private var newPointGraphX: Double? = 0.0
    private var newPointGraphY: Double? = 0.0
    private var newPointGraphZ: Double? = 0.0
    private var pointsPlotted: Double = 5.0

    private lateinit var seriesX: LineGraphSeries<DataPoint>
    private lateinit var seriesY: LineGraphSeries<DataPoint>
    private lateinit var seriesZ: LineGraphSeries<DataPoint>
    private val maxDataPoints = 100

    private lateinit var viewportX: Viewport

    private lateinit var graphX: GraphView

    private val handler = Handler()

    // Referencias del Firebase RealtimeDatabase
    private var firebaseRef: FirebaseDatabase = FirebaseDatabase.getInstance()
    private lateinit var firebaseAccelDataRef: DatabaseReference
    private var firebaseInfoDeviceRef: DatabaseReference = firebaseRef.getReference("infoDevice")

    private lateinit var deviceId: String

    // Guardado en DB local
    private lateinit var database: AccelDatabase

    // Obtener ubicación desde GPS
    private val locationService: LocationService = LocationService()
    private var initialTime = 0

    private val updateGraphRunnable: Runnable = object : Runnable {
        override fun run() {
            lifecycleScope.launch(Dispatchers.IO) {
                val firstData = database.accelReadingDao().getFirstData()
                newPointGraphX = firstData.getOrNull(0)?.x
                newPointGraphY = firstData.getOrNull(0)?.y
                newPointGraphZ = firstData.getOrNull(0)?.z

                // Add one new data to each series
                pointsPlotted++
                if (newPointGraphX != null) {
                    // Add one new data to seriesY if newPointGraphY is not null
                    seriesX.appendData(
                        DataPoint(pointsPlotted, newPointGraphX!!),
                        true,
                        maxDataPoints
                    )
                }
                if (newPointGraphY != null) {
                    // Add one new data to seriesY if newPointGraphY is not null
                    seriesY.appendData(
                        DataPoint(pointsPlotted, newPointGraphY!!),
                        true,
                        maxDataPoints
                    )
                }
                if (newPointGraphZ != null) {
                    // Add one new data to seriesY if newPointGraphY is not null
                    seriesZ.appendData(
                        DataPoint(pointsPlotted, newPointGraphZ!!),
                        true,
                        maxDataPoints
                    )
                }

                withContext(Dispatchers.Main) {
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
                }
            }

            // Exec this code 100 times per second
            handler.postDelayed(this, 100)
        }
    }

    constructor(parcel: Parcel) : this() {
        newPointGraphX = parcel.readDouble()
        newPointGraphY = parcel.readDouble()
        newPointGraphZ = parcel.readDouble()
        pointsPlotted = parcel.readDouble()
        deviceId = parcel.readString().toString()
    }

    private lateinit var viewModel: MainViewModel
    private lateinit var firstAccelerometerDataObserver: Observer<List<AccelEntity>>
    private lateinit var lastAccelerometerDataObserver: Observer<List<AccelEntity>>

    @SuppressLint("HardwareIds")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        SingletonContextProvider.setContext(this)
        deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        firebaseAccelDataRef = firebaseRef.getReference(deviceId)

        database = AccelDatabase.getDatabase(this)

        lifecycleScope.launch(Dispatchers.IO) {
            // Borrar la base de datos al abrir la aplicación
            database.accelReadingDao().resetDatabase()
        }

        //*************************************************************

        // Inicializar el ViewModel
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)

        // Inicializar el Observer para observar los cambios en los datos del acelerómetro
        firstAccelerometerDataObserver = Observer { accelerometerData ->
            // Actualizar la interfaz de usuario con los nuevos datos del acelerómetro
            updateFirstDataUI(accelerometerData)
        }

        // Observar el LiveData en el ViewModel
        viewModel.firstAccelerometerData.observe(this, firstAccelerometerDataObserver)

        lastAccelerometerDataObserver = Observer { accelerometerData ->
            // Actualizar la interfaz de usuario con los últimos datos del acelerómetro
            updateLastDataUI(accelerometerData)
        }

        // Observar el LiveData para los últimos datos en el ViewModel
        viewModel.lastAccelerometerData.observe(this, lastAccelerometerDataObserver)

        // Llamar al método fetchFirstData() para cargar los primeros datos del acelerómetro
        //viewModel.fetchFirstData()

        //*************************************************************

        // Registra esta clase como un suscriptor de EventBus
        EventBus.getDefault().register(this)

        askNotificationPermission()
        getToken()

        val intent = Intent(this, ForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("SDK_VERSIONForegroundService", Build.VERSION.SDK_INT.toString())
            startForegroundService(intent)
        } else {
            Log.d("SDK_VERSIONService", Build.VERSION.SDK_INT.toString())
            startService(intent)
        }

        initializeViews()
        initializeGraphs()
        updateTextEachSecond()
    }

    private fun updateFirstDataUI(accelerometerData: List<AccelEntity>) {
        if (accelerometerData.isNotEmpty()) {
            val firstDataAccelerometer = accelerometerData[0]
            val id = firstDataAccelerometer.id
            val timestamp = firstDataAccelerometer.timestamp
            firstData = findViewById(R.id.firstData)
            firstData.text = "Newest Data: ID: $id, Timestamp: $timestamp"
        }
    }

    private fun updateLastDataUI(accelerometerData: List<AccelEntity>) {
        if (accelerometerData.isNotEmpty()) {
            val firstDataAccelerometer = accelerometerData[0]
            val id = firstDataAccelerometer.id
            val timestamp = firstDataAccelerometer.timestamp
            lastData = findViewById(R.id.lastData)
            lastData.text = "Oldest Data: ID: $id, Timestamp: $timestamp"
        }
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
                        this@MainActivity.dataAmount.text = showDataAmount
                    }
                    initialTime += 1
                    withContext(Dispatchers.Main) {
                        val showRecordTime = resources.getString(R.string.recordTime, initialTime)
                        this@MainActivity.recordTime.text = showRecordTime
                    }
                }
                handler.postDelayed(this, 1000)
            }
        }, 1000) // 1000 milisegundos = 1 segundo
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
        Log.d(TAG, event.seismTime)
        val epochTime = Date(event.seismTime.toLong() * 1000)
        val formatDate = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
        lastSism.text = getString(R.string.lastSism, formatDate.format(epochTime))
    }

    private fun initializeViews() {
        dataAmount = findViewById(R.id.dataAmount)
        recordTime = findViewById(R.id.recordTime)
        btnSendData = findViewById(R.id.btnSendData)
        btnDeleteData = findViewById(R.id.btnDeleteData)
        lastSism = findViewById(R.id.lastSism)
        lastSism.text = "Last sism = No data"

        btnSendData.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val allReadings = database.accelReadingDao().getAllReadings()
                firebaseAccelDataRef.child("sismicData").setValue(allReadings)
            }
        }
        btnDeleteData.setOnClickListener {
            // Borrar TODA la base de datos
            firebaseRef.reference.setValue(null)
            // Borrar los datos de aceleración guardados desde el dispositivo
            //firebaseAccelDataRef.setValue(null)
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

    override fun registerSensor() {}

    override fun describeContents(): Int {
        TODO("Not yet implemented")
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        TODO("Not yet implemented")
    }

    private fun startUpdateGraphRunnable() {
        handler.postDelayed(updateGraphRunnable, 0)
    }

    private fun stopUpdateGraphRunnable() {
        handler.removeCallbacks(updateGraphRunnable)
    }

    override fun onResume() {
        super.onResume()
        startUpdateGraphRunnable()
    }

    override fun onPause() {
        super.onPause()
        stopUpdateGraphRunnable()
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        super.onDestroy()
        stopUpdateGraphRunnable()
        viewModel.firstAccelerometerData.removeObserver(firstAccelerometerDataObserver)
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
