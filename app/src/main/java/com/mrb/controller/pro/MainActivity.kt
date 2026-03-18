package com.mrb.controller.pro

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    
    private lateinit var imageSteering: ImageView
    private lateinit var textStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Binding UI
        imageSteering = findViewById(R.id.imageSteering)
        textStatus = findViewById(R.id.textStatus)

        // Sensor Setup
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            textStatus.text = "Error: Accelerometer missing!"
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val xTilt = event.values[0] // X-axis Left/Right tilt

            // UI LOGIC: Rotate the steering wheel image
            // Phone landscape me hai, xTilt ~9.8 max hai.
            // Hum usko -90 se +90 degree rotation me convert karte hain.
            val rotationAngle = xTilt * -10f // Simple mapping for rotation
            imageSteering.rotation = rotationAngle

            // FUTURE BLUETOOTH LOGIC: Convert rotationAngle to PC command (A/D keys)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
