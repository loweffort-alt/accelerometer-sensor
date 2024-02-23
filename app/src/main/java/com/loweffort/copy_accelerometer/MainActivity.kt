package com.loweffort.copy_accelerometer

import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.Manifest
import android.content.ContentValues.TAG
import android.os.Handler
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.Viewport
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import java.util.Calendar
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MainActivity : AppCompatActivity() {

    // Config to show notifications:
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            // FCM SDK (and your app) can post notifications.
        } else {
            // TODO: Inform user that that your app will not show notifications.
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

    private lateinit var recordToggle: ToggleButton
    private var isSavingData = false
    private var saveCount = 0
    private val maxSaveCount = 500 //Máx datos grabados en 5 min: 30000

    private var mSensorManager: SensorManager? = null
    private var mAccelerometer: Sensor? = null

    private var accelerationCurrentValueX: Double = 0.0
    private var accelerationCurrentValueY: Double = 0.0
    private var accelerationCurrentValueZ: Double = 0.0
    private var pointsPlotted: Double = 5.0

    private lateinit var seriesX: LineGraphSeries<DataPoint>
    private lateinit var seriesY: LineGraphSeries<DataPoint>
    private lateinit var seriesZ: LineGraphSeries<DataPoint>

    private lateinit var viewportX: Viewport
    private lateinit var viewportY: Viewport
    private lateinit var viewportZ: Viewport

    private lateinit var graphX: GraphView
    private lateinit var graphY: GraphView
    private lateinit var graphZ: GraphView

    private val handler = Handler()

    private lateinit var firebaseRef: DatabaseReference

    private val sensorEventListener: SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(sensorEvent: SensorEvent) {
            val x: Float = sensorEvent.values[0]
            val y: Float = sensorEvent.values[1]
            val z: Float = sensorEvent.values[2]

            accelerationCurrentValueX = x.toDouble()
            accelerationCurrentValueY = y.toDouble()
            accelerationCurrentValueZ = z.toDouble()

            // Title of each graph
            val accelerationXText = resources.getString(R.string.accelX, accelerationCurrentValueX)
            val accelerationYText = resources.getString(R.string.accelY, accelerationCurrentValueY)
            val accelerationZText = resources.getString(R.string.accelZ, accelerationCurrentValueZ)
            txtAccelerationX.text = accelerationXText
            txtAccelerationY.text = accelerationYText
            txtAccelerationZ.text = accelerationZText
        }

        override fun onAccuracyChanged(sensor: Sensor, i: Int) {
            //this field is for improve the precision and make low-effort if is needed
        }
    }

    // Lógica de envío de datos
    private val sendDataRunnable : Runnable = object : Runnable {
        override fun run() {
            if (isSavingData && saveCount < maxSaveCount) {
                saveData(accelerationCurrentValueX, accelerationCurrentValueY, accelerationCurrentValueZ)
                saveCount++
            }
            handler.postDelayed(this, 10)
        }
    }

    private val updateGraphRunnable: Runnable = object : Runnable {
        override fun run() {
            // Add one new data to each series
            pointsPlotted++
            seriesX.appendData(DataPoint(pointsPlotted, accelerationCurrentValueX), true, pointsPlotted.toInt())
            seriesY.appendData(DataPoint(pointsPlotted, accelerationCurrentValueY), true, pointsPlotted.toInt())
            seriesZ.appendData(DataPoint(pointsPlotted, accelerationCurrentValueZ), true, pointsPlotted.toInt())

            // Auto rescaling viewport
            viewportX.setMaxX(pointsPlotted)
            viewportX.setMinX(pointsPlotted - 200)
            viewportY.setMaxX(pointsPlotted)
            viewportY.setMinX(pointsPlotted - 200)
            viewportZ.setMaxX(pointsPlotted)
            viewportZ.setMinX(pointsPlotted - 200)

            // Exec this code 100 times per second
            handler.postDelayed(this, 10)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        firebaseRef = FirebaseDatabase.getInstance().getReference("data")
        firebaseRef.setValue(null)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Registra esta clase como un suscriptor de EventBus
        EventBus.getDefault().register(this)

        askNotificationPermission()
        notifications()
        initializeViews()
        initializeGraphs()
        initializeListeners()
    }

    // Método para manejar el evento de recepción de notificación
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onNotificationReceived(event: NotificationReceivedEvent) {
        // Cambiar el estado del recordToggle
        recordToggle.isChecked = true
    }

    private fun notifications() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failes", task.exception)
                return@OnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result

            // Log and toast
            val msg = getString(R.string.msg_token_fmt, token)
            Log.d(TAG, msg)
            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
        })
    }

    private fun initializeViews() {
        txtAccelerationX = findViewById(R.id.txt_accelX)
        txtAccelerationY = findViewById(R.id.txt_accelY)
        txtAccelerationZ = findViewById(R.id.txt_accelZ)
        recordToggle = findViewById(R.id.recordToggle)
    }

    private fun initializeGraphs() {
        graphX = findViewById(R.id.graphX)
        viewportX = graphX.viewport
        viewportX.isScrollable = true
        viewportX.isXAxisBoundsManual = true
        seriesX = LineGraphSeries(arrayOf(
            DataPoint(0.0, 1.0),
            DataPoint(1.0, 5.0),
            DataPoint(2.0, 3.0),
            DataPoint(3.0, 2.0),
            DataPoint(4.0, 6.0)
        ))
        graphX.addSeries(seriesX)

        graphY = findViewById(R.id.graphY)
        viewportY = graphY.viewport
        viewportY.isScrollable = true
        viewportY.isXAxisBoundsManual = true
        seriesY = LineGraphSeries(arrayOf(
            DataPoint(0.0, 1.0),
            DataPoint(1.0, 5.0),
            DataPoint(2.0, 3.0),
            DataPoint(3.0, 2.0),
            DataPoint(4.0, 6.0)
        ))
        graphY.addSeries(seriesY)

        graphZ = findViewById(R.id.graphZ)
        viewportZ = graphZ.viewport
        viewportZ.isScrollable = true
        viewportZ.isXAxisBoundsManual = true
        seriesZ = LineGraphSeries(arrayOf(
            DataPoint(0.0, 1.0),
            DataPoint(1.0, 5.0),
            DataPoint(2.0, 3.0),
            DataPoint(3.0, 2.0),
            DataPoint(4.0, 6.0)
        ))
        graphZ.addSeries(seriesZ)

        // Customize Graph
        seriesX.title = "Axis X"
        seriesX.color = Color.GREEN

        seriesY.title = "Axis Y"
        seriesY.color = Color.RED

        seriesZ.title = "Axis Z"
        seriesZ.color = Color.BLUE
    }

    private fun initializeListeners() {
        recordToggle.setOnCheckedChangeListener { _, isChecked ->
            isSavingData = isChecked
            if (!isChecked) {
                saveCount = 0
            }
        }
    }

    private fun registerSensor() {
        mSensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
        mAccelerometer = mSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mSensorManager?.registerListener(sensorEventListener, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST)
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

    private fun startSendDataRunnable() {
        handler.postDelayed(sendDataRunnable, 0)
    }

    private fun stopSendDataRunnable() {
        handler.removeCallbacks(sendDataRunnable)
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
        } else if (minute < 10){
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
        var milisecond = calendar.get(Calendar.MILLISECOND).toString()

        if (second.toInt() < 10) {
            second = "0$second"
        }

        return "$second,$milisecond"
    }

    private fun saveData(
        accelerationCurrentValueX: Double,
        accelerationCurrentValueY: Double,
        accelerationCurrentValueZ: Double
    ) {
        val dataX = accelerationCurrentValueX.toString()
        val dataY = accelerationCurrentValueY.toString()
        val dataZ = accelerationCurrentValueZ.toString()

        val dataId = firebaseRef.push().key!!
        val deviceModel = Build.MODEL
        val deviceManufacturer = Build.MANUFACTURER
        val deviceVersion = Build.VERSION.SDK_INT.toString()
        val packageManager = applicationContext.packageManager
        val packageName = applicationContext.packageName
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val appVersion = packageInfo.versionName
        val currentDate = getCurrentDate()
        val currentTime = getCurrentTime()
        val getTimeTest = getDataTimeTest()
        val data = AllData(dataId, dataX, dataY, dataZ, deviceModel, deviceManufacturer, deviceVersion, appVersion, currentDate, currentTime, getTimeTest)

        firebaseRef.child(dataId).setValue(data)
            .addOnFailureListener {
                Toast.makeText(this, "error ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onResume() {
        super.onResume()
        registerSensor()
        startUpdateGraphRunnable()
        startSendDataRunnable()
    }

    override fun onPause() {
        super.onPause()
        unregisterSensor()
        stopUpdateGraphRunnable()
    }

    override fun onDestroy() {
        // Desregistra esta clase como suscriptor de EventBus
        EventBus.getDefault().unregister(this)
        super.onDestroy()
        unregisterSensor()
        stopUpdateGraphRunnable()
        stopSendDataRunnable()
    }
}
