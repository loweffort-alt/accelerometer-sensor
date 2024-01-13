package com.loweffort.copy_accelerometer

import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.Viewport
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries

class MainActivity : AppCompatActivity() {

    private lateinit var txtAccelerationX: TextView
    private lateinit var txtAccelerationY: TextView
    private lateinit var txtAccelerationZ: TextView

    private var mSensorManager: SensorManager? = null
    private var mAccelerometer: Sensor? = null

    private var accelerationCurrentValueX: Double = 0.0

    private var accelerationCurrentValueY: Double = 0.0

    private var accelerationCurrentValueZ: Double = 0.0

    private var pointsPlotted: Double = 5.0

    private val seriesX = LineGraphSeries(
        arrayOf(
            DataPoint(0.0, 1.0),
            DataPoint(1.0, 5.0),
            DataPoint(2.0, 3.0),
            DataPoint(3.0, 2.0),
            DataPoint(4.0, 6.0)
        )
    )

    private val seriesY = LineGraphSeries(
        arrayOf(
            DataPoint(0.0, 1.0),
            DataPoint(1.0, 5.0),
            DataPoint(2.0, 3.0),
            DataPoint(3.0, 2.0),
            DataPoint(4.0, 6.0)
        )
    )

    private val seriesZ = LineGraphSeries(
        arrayOf(
            DataPoint(0.0, 1.0),
            DataPoint(1.0, 5.0),
            DataPoint(2.0, 3.0),
            DataPoint(3.0, 2.0),
            DataPoint(4.0, 6.0)
        )
    )

    private lateinit var viewportX: Viewport
    private lateinit var viewportY: Viewport
    private lateinit var viewportZ: Viewport

    private lateinit var graphX: GraphView
    private lateinit var graphY: GraphView
    private lateinit var graphZ: GraphView

    private val handler = Handler()
    private val updateGraphRunnable: Runnable = object : Runnable {
        override fun run() {
            // Define axis values
            val x: Double = accelerationCurrentValueX
            val y: Double = accelerationCurrentValueY
            val z: Double = accelerationCurrentValueZ

            // Title of each graph
            txtAccelerationX.text = "X Acceleration = $x"
            txtAccelerationY.text = "Y Acceleration = $y"
            txtAccelerationZ.text = "Z Acceleration = $z"

            // Customize Graph
            seriesX.setTitle("Axis X")
            seriesX.setColor(Color.GREEN)

            seriesY.setTitle("Axis Y")
            seriesY.setColor(Color.RED)

            seriesZ.setTitle("Axis Z")
            seriesZ.setColor(Color.BLUE)

            // Add new data to each series
            pointsPlotted++
            seriesX.appendData(DataPoint(pointsPlotted, x), true, pointsPlotted.toInt())
            seriesY.appendData(DataPoint(pointsPlotted, y), true, pointsPlotted.toInt())
            seriesZ.appendData(DataPoint(pointsPlotted, z), true, pointsPlotted.toInt())

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

    private val sensorEventListener: SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(sensorEvent: SensorEvent) {
            val x: Float = sensorEvent.values[0]
            val y: Float = sensorEvent.values[1]
            val z: Float = sensorEvent.values[2]

            accelerationCurrentValueX = x.toDouble()
            accelerationCurrentValueY = y.toDouble()
            accelerationCurrentValueZ = z.toDouble()
        }

        override fun onAccuracyChanged(sensor: Sensor, i: Int) {
            //this field is for improve the precision and make low-effort if is needed
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtAccelerationX = findViewById(R.id.txt_accelX)
        txtAccelerationY = findViewById(R.id.txt_accelY)
        txtAccelerationZ = findViewById(R.id.txt_accelZ)

        mSensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
        mAccelerometer = mSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        graphX = findViewById(R.id.graphX)
        viewportX = graphX.viewport
        viewportX.setScrollable(true)
        viewportX.setXAxisBoundsManual(true)
        graphX.addSeries(seriesX)

        graphY = findViewById(R.id.graphY)
        viewportY = graphY.viewport
        viewportY.setScrollable(true)
        viewportY.setXAxisBoundsManual(true)
        graphY.addSeries(seriesY)

        graphZ = findViewById(R.id.graphZ)
        viewportZ = graphZ.viewport
        viewportZ.setScrollable(true)
        viewportZ.setXAxisBoundsManual(true)
        graphZ.addSeries(seriesZ)
    }

    override fun onResume() {
        super.onResume()
        mSensorManager?.registerListener(sensorEventListener, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        handler.postDelayed(updateGraphRunnable, 1000)
    }

    override fun onPause() {
        super.onPause()
        mSensorManager?.unregisterListener(sensorEventListener)
        handler.removeCallbacks(updateGraphRunnable)
    }
}
