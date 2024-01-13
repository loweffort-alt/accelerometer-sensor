package com.loweffort.copy_accelerometer

import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.Viewport
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import kotlin.math.abs
import kotlin.math.sqrt


class MainActivity : AppCompatActivity() {
    // Define the View Elements variables
    private lateinit var progressBar: ProgressBar
    private lateinit var txtCurrentAccel: TextView
    private lateinit var txtPrevAccel: TextView
    private lateinit var txtAcceleration: TextView

    // Define the sensor variables
    private var mSensorManager: SensorManager? = null
    private var mAccelerometer: Sensor? = null

    private var accelerationCurrentValue: Double = 0.0
    private var accelerationPreviousValue: Double = 0.0

    private var pointsPlotted: Int = 5
    private var graphIntervalCounter: Int = 0

    private val series = LineGraphSeries(
        arrayOf(
            DataPoint(0.0, 1.0),
            DataPoint(1.0, 5.0),
            DataPoint(2.0, 3.0),
            DataPoint(3.0, 2.0),
            DataPoint(4.0, 6.0)
        )
    )

    // Define viewport variables
    private lateinit var viewport : Viewport

    private val sensorEventListener: SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(sensorEvent: SensorEvent) {
            val x : Float = sensorEvent.values[0]
            val y : Float = sensorEvent.values[1]
            val z : Float = sensorEvent.values[2]

            accelerationCurrentValue = sqrt(x * x + y * y + z * z).toDouble()
            val changeInAcceleration : Double = abs(accelerationPreviousValue - accelerationCurrentValue)
            accelerationPreviousValue =  accelerationCurrentValue

            //update text views
            txtCurrentAccel.text = "Current = ${accelerationCurrentValue.toInt()}"
            txtPrevAccel.text = "Previous = ${accelerationPreviousValue.toInt()}"
            txtAcceleration.text = "Acceleration Change = $changeInAcceleration"
            progressBar.progress = changeInAcceleration.toInt()

            if (changeInAcceleration > 13) {
                txtAcceleration.setBackgroundColor(Color.RED)
            } else if (changeInAcceleration > 8) {
                txtAcceleration.setBackgroundColor(Color.YELLOW)
            } else if (changeInAcceleration > 3.5) {
                txtAcceleration.setBackgroundColor(Color.GREEN)
            } else {
                txtAcceleration.setBackgroundColor(resources.getColor(com.google.android.material.R.color.design_default_color_background))
            }

            // Update the graph
            pointsPlotted++

            series.appendData(DataPoint(pointsPlotted.toDouble(), changeInAcceleration), true, pointsPlotted)

            // Viewport
            viewport.setMaxX(pointsPlotted.toDouble())
            viewport.setMinX(pointsPlotted.toDouble() - 200)
        }

        override fun onAccuracyChanged(sensor: Sensor, i: Int) {
            // Implementation for accuracy changes if needed
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.prog_shakeMeter)
        txtCurrentAccel = findViewById(R.id.txt_currentAccel)
        txtPrevAccel = findViewById(R.id.txt_prevAccel)
        txtAcceleration = findViewById(R.id.txt_accel)

        // Initialize sensor objects
        mSensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
        mAccelerometer = mSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Sample graph code
        val graph = findViewById<View>(R.id.graph) as GraphView
        viewport = graph.getViewport()
        viewport.setScrollable(true)
        viewport.setXAxisBoundsManual(true)

        graph.addSeries(series)
    }

    override fun onResume() {
        super.onResume()
        mSensorManager?.registerListener(sensorEventListener, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onPause() {
        super.onPause()
        mSensorManager?.unregisterListener(sensorEventListener)
    }
}