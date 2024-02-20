package com.loweffort.copy_accelerometer

import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.Viewport
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var txtAccelerationX: TextView
    private lateinit var txtAccelerationY: TextView
    private lateinit var txtAccelerationZ: TextView

    private lateinit var recordToggle: ToggleButton
    private var isSavingData = false
    private var saveCount = 0
    private val maxSaveCount = 180000 //Datos máximos en 30 min

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
            txtAccelerationX.text = "X Acceleration = $accelerationCurrentValueX"
            txtAccelerationY.text = "Y Acceleration = $accelerationCurrentValueY"
            txtAccelerationZ.text = "Z Acceleration = $accelerationCurrentValueZ"
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
            // Add new data to each series
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

        initializeViews()
        initializeGraphs()
        initializeListeners()
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
        viewportX.setScrollable(true)
        viewportX.setXAxisBoundsManual(true)
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
        viewportY.setScrollable(true)
        viewportY.setXAxisBoundsManual(true)
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
        viewportZ.setScrollable(true)
        viewportZ.setXAxisBoundsManual(true)
        seriesZ = LineGraphSeries(arrayOf(
            DataPoint(0.0, 1.0),
            DataPoint(1.0, 5.0),
            DataPoint(2.0, 3.0),
            DataPoint(3.0, 2.0),
            DataPoint(4.0, 6.0)
        ))
        graphZ.addSeries(seriesZ)

        // Customize Graph
        seriesX.setTitle("Axis X")
        seriesX.setColor(Color.GREEN)

        seriesY.setTitle("Axis Y")
        seriesY.setColor(Color.RED)

        seriesZ.setTitle("Axis Z")
        seriesZ.setColor(Color.BLUE)
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
        mSensorManager?.registerListener(sensorEventListener, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun unregisterSensor() {
        mSensorManager?.unregisterListener(sensorEventListener)
    }

    private fun startUpdateGraphRunnable() {
        handler.postDelayed(updateGraphRunnable, 1000)
    }

    private fun stopUpdateGraphRunnable() {
        handler.removeCallbacks(updateGraphRunnable)
    }

    private fun startSendDataRunnable() {
        handler.postDelayed(sendDataRunnable, 1000)
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
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)

        return "$hour:$minute:$second"
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
        val data = AllData(dataId, dataX, dataY, dataZ, deviceModel, deviceManufacturer, deviceVersion, appVersion, currentDate, currentTime)

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
        super.onDestroy()
        unregisterSensor()
        stopUpdateGraphRunnable()
    }
}
