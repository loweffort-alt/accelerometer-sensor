package com.loweffort.quakesense

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.Viewport
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    // Config to show notifications:
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            // FCM SDK (and your app) can post notifications.
            Toast.makeText(this, "FCM SDK (and your app) can post notifications", Toast.LENGTH_SHORT).show()
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

    private lateinit var txtAccelerationX: TextView
    private lateinit var txtAccelerationY: TextView
    private lateinit var txtAccelerationZ: TextView

    //Buttons
    private var saveCount = 0
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
    private lateinit var viewportY: Viewport
    private lateinit var viewportZ: Viewport

    private lateinit var graphX: GraphView
    private lateinit var graphY: GraphView
    private lateinit var graphZ: GraphView

    private val handler = Handler()

    // Referencias del Firebase RealtimeDatabase
    private var firebaseRef: FirebaseDatabase = FirebaseDatabase.getInstance()
    private lateinit var firebaseAccelDataRef: DatabaseReference
    private var firebaseInfoDeviceRef: DatabaseReference = firebaseRef.getReference("infoDevice")

    private lateinit var deviceId: String

    private val sensorEventListener: SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(sensorEvent: SensorEvent) {
            val x: Float = sensorEvent.values[0]
            val y: Float = sensorEvent.values[1]
            val z: Float = sensorEvent.values[2] - 9.81f

            accelerationCurrentValueX = String.format("%.5f", x).toDouble()
            accelerationCurrentValueY = String.format("%.5f", y).toDouble()
            accelerationCurrentValueZ = String.format("%.5f", z).toDouble()

            // Title of each graph
            /*val accelerationXText = resources.getString(R.string.accelX, accelerationCurrentValueX)
            val accelerationYText = resources.getString(R.string.accelY, accelerationCurrentValueY)
            val accelerationZText = resources.getString(R.string.accelZ, accelerationCurrentValueZ)
            txtAccelerationX.text = accelerationXText
            txtAccelerationY.text = accelerationYText
            txtAccelerationZ.text = accelerationZText*/
        }

        override fun onAccuracyChanged(sensor: Sensor, i: Int) {
            //this field is for improve the precision and make low-effort if is needed
        }
    }

    private val sendDataRunnable: Runnable = object : Runnable {
        override fun run() {
            saveData(
                accelerationCurrentValueX,
                accelerationCurrentValueY,
                accelerationCurrentValueZ
            )
            // Esto controla la velocidad de muestreo
            handler.postDelayed(this, 20)
        }
    }

    private fun getCurrentDate(): String {
        val calendar = Calendar.getInstance()
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val month = calendar.get(Calendar.MONTH) + 1 // Los meses comienzan desde 0
        val year = calendar.get(Calendar.YEAR)

        return "$day/$month/$year"
    }

    private fun getCurrentTime(): String {
        val calendar = Calendar.getInstance()
        var hour = calendar.get(Calendar.HOUR_OF_DAY)
        var minute = calendar.get(Calendar.MINUTE)
        var second = calendar.get(Calendar.SECOND)
        var milisecond = calendar.get(Calendar.MILLISECOND)

        if (hour < 10) {
            hour = "0$hour".toInt()
        } else if (minute < 10) {
            minute = "0$minute".toInt()
        } else if (second < 10) {
            second = "0$second".toInt()
        } else if (milisecond < 100) {
            milisecond = "0$milisecond".toInt()
        }

        return "$hour:$minute:$second.$milisecond"
    }

    private fun getDataTimeTest(): String {
        val calendar = Calendar.getInstance()
        var second = calendar.get(Calendar.SECOND).toString()
        val milisecond = calendar.get(Calendar.MILLISECOND).toString()

        if (second.toInt() < 10) {
            second = "0$second"
        }

        return "$second,$milisecond"
    }

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
            viewportY.setMaxX(pointsPlotted)
            viewportY.setMinX(kotlin.math.max(0.0, pointsPlotted - maxDataPoints))
            viewportZ.setMaxX(pointsPlotted)
            viewportZ.setMinX(kotlin.math.max(0.0, pointsPlotted - maxDataPoints))

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        firebaseAccelDataRef = firebaseRef.getReference(deviceId)

        // Registra esta clase como un suscriptor de EventBus
        EventBus.getDefault().register(this)

        askNotificationPermission()
        getToken()
        initializeViews()
        initializeGraphs()
    }

    private fun getToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val tokenFCM = task.result
                sendDeviceInfo(tokenFCM)
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "Error al obtener el token FCM",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun sendDeviceInfo(tokensito: String) {
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
            tokensito,
        )

        firebaseInfoDeviceRef.child(deviceId).setValue(deviceInfo)
            .addOnFailureListener { exception ->
                Toast.makeText(
                    this,
                    "Error al enviar la información: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    // Método para manejar el evento de recepción de notificación
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onNotificationReceived(event: NotificationReceivedEvent) {
        Log.d("jhnf", "Notificación recibida!")
        // Esto envía los datos cuando una notificación es recibida
        handler.postDelayed(sendDataRunnable, 0)
        handler.postDelayed({handler.removeCallbacks(sendDataRunnable)}, 30000)
    }

    private fun initializeViews() {
        txtAccelerationX = findViewById(R.id.txt_accelX)
        txtAccelerationY = findViewById(R.id.txt_accelY)
        txtAccelerationZ = findViewById(R.id.txt_accelZ)
        btnSendData = findViewById(R.id.btnSendData)
        btnDeleteData = findViewById(R.id.btnDeleteData)
        progressBarSendData = findViewById(R.id.progressBarSendData)
        progressBarSendData.max = maxSaveCount
        progressBarSendData.progress = 0

        btnSendData.setOnClickListener {
            saveCount = 0
            // Esto envía los datos cuando le das click al boton de enviar
            handler.postDelayed(sendDataRunnable, 0)
            handler.postDelayed({handler.removeCallbacks(sendDataRunnable)}, 30000)
            saveCount = 0
        }
        btnDeleteData.setOnClickListener {
            progressBarSendData.progress = 0
            firebaseAccelDataRef.setValue(null)
            firebaseInfoDeviceRef.setValue(null)
        }
    }

    private fun initializeGraphs() {
        graphX = findViewById(R.id.graphX)
        viewportX = graphX.viewport
        seriesX = LineGraphSeries(
            arrayOf(
                DataPoint(1.0, 0.0),
                DataPoint(2.0, 0.0),
                DataPoint(3.0, 0.0),
            )
        )
        graphX.addSeries(seriesX)
        graphX.viewport.isXAxisBoundsManual = true

        graphY = findViewById(R.id.graphY)
        viewportY = graphY.viewport
        seriesY = LineGraphSeries(
            arrayOf(
                DataPoint(1.0, 0.0),
                DataPoint(2.0, 0.0),
                DataPoint(3.0, 0.0),
            )
        )
        graphY.addSeries(seriesY)
        graphY.viewport.isXAxisBoundsManual = true

        graphZ = findViewById(R.id.graphZ)
        viewportZ = graphZ.viewport
        seriesZ = LineGraphSeries(
            arrayOf(
                DataPoint(1.0, 0.0),
                DataPoint(2.0, 0.0),
                DataPoint(3.0, 0.0),
            )
        )
        graphZ.addSeries(seriesZ)
        graphZ.viewport.isXAxisBoundsManual = true

        // Customize Graph
        seriesX.title = "Axis X"
        seriesX.color = Color.GREEN

        seriesY.title = "Axis Y"
        seriesY.color = Color.RED

        seriesZ.title = "Axis Z"
        seriesZ.color = Color.BLUE
    }

    private fun registerSensor() {
        mSensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
        mAccelerometer = mSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mSensorManager?.registerListener(
            sensorEventListener,
            mAccelerometer,
            SensorManager.SENSOR_DELAY_FASTEST
        )
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

    private fun saveData(
        accelerationCurrentValueX: Double,
        accelerationCurrentValueY: Double,
        accelerationCurrentValueZ: Double
    ) {
        val accelDataId = firebaseAccelDataRef.push().key!!
        val dataX = accelerationCurrentValueX.toString()
        val dataY = accelerationCurrentValueY.toString()
        val dataZ = accelerationCurrentValueZ.toString()
        val currentDate = getCurrentDate()
        val currentTime = getCurrentTime()
        val getTimeTest = getDataTimeTest()
        val accelData = AccelData(
            accelDataId,
            dataX,
            dataY,
            dataZ,
            currentDate,
            currentTime,
            getTimeTest
        )
        firebaseAccelDataRef.child(accelDataId).setValue(accelData)
            .addOnFailureListener {
                Toast.makeText(this, "error ${it.message}", Toast.LENGTH_SHORT).show()
            }
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
}
