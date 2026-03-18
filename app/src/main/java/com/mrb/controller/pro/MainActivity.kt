package com.mrb.controller.pro

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

// Implement SensorEventListener to listen for sensor changes
class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    
    private lateinit var textX: TextView
    private lateinit var textY: TextView
    private lateinit var textZ: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI components
        textX = findViewById(R.id.textX)
        textY = findViewById(R.id.textY)
        textZ = findViewById(R.id.textZ)

        // Initialize SensorManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            textTitle.text = "Accelerometer Not Found!"
        }
    }

    override fun onResume() {
        super.onResume()
        // Register listener when app is active
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        // Unregister to save battery
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0] // Tilt Left/Right
            val y = event.values[1] // Tilt Up/Down
            val z = event.values[2] // Z Axis

            // Update UI with raw data
            textX.text = "X Axis (Left/Right): ${"%.2f".format(x)}"
            textY.text = "Y Axis (Up/Down): ${"%.2f".format(y)}"
            textZ.text = "Z Axis: ${"%.2f".format(z)}"

            // FUTURE LOGIC: Send A/D or Keypress based on 'x' value to PC
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for basic tilt
    }
}
