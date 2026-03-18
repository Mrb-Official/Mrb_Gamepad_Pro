package com.mrb.controller.pro // Isko apne package name se replace kar lena

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.*
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity(), SensorEventListener {

    private var hidDevice: BluetoothHidDevice? = null
    private var targetDevice: BluetoothDevice? = null
    private lateinit var sensorManager: SensorManager
    private var accelSensor: Sensor? = null

    // Controller State (Byte 1 = Steering, Byte 2 = Buttons)
    private var currentSteering: Byte = 0x00
    private var currentButtons: Byte = 0x00

    // PRO GAMEPAD DESCRIPTOR: 1 X-Axis (Steering) + 8 Buttons
    private val HID_REPORT_DESC = byteArrayOf(
        0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
        0x09.toByte(), 0x05.toByte(), // Usage (Gamepad)
        0xa1.toByte(), 0x01.toByte(), // Collection (Application)
        0xa1.toByte(), 0x00.toByte(), //   Collection (Physical)
        // --- 1 Axis (Steering X) ---
        0x05.toByte(), 0x01.toByte(), //     Usage Page (Generic Desktop)
        0x09.toByte(), 0x30.toByte(), //     Usage (X)
        0x15.toByte(), 0x81.toByte(), //     Logical Minimum (-127)
        0x25.toByte(), 0x7f.toByte(), //     Logical Maximum (127)
        0x75.toByte(), 0x08.toByte(), //     Report Size (8)
        0x95.toByte(), 0x01.toByte(), //     Report Count (1)
        0x81.toByte(), 0x02.toByte(), //     Input (Data, Variable, Absolute)
        // --- 8 Buttons ---
        0x05.toByte(), 0x09.toByte(), //     Usage Page (Button)
        0x19.toByte(), 0x01.toByte(), //     Usage Minimum (1)
        0x29.toByte(), 0x08.toByte(), //     Usage Maximum (8)
        0x15.toByte(), 0x00.toByte(), //     Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(), //     Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(), //     Report Size (1)
        0x95.toByte(), 0x08.toByte(), //     Report Count (8)
        0x81.toByte(), 0x02.toByte(), //     Input (Data, Variable, Absolute)
        0xc0.toByte(),                //   End Collection
        0xc0.toByte()                 // End Collection
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Sensor Setup
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Bluetooth Setup
        setupBluetoothProfile()

        // Button Mapping
        setupButtonListeners()
    }

    private fun setupBluetoothProfile() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        btManager.adapter?.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                hidDevice = proxy as BluetoothHidDevice
                val sdp = BluetoothHidDeviceAppSdpSettings("MRB-Pro", "Gamepad", "Meet", 0xC0.toByte(), HID_REPORT_DESC)
                hidDevice?.registerApp(sdp, null, null, { it.run() }, object : BluetoothHidDevice.Callback() {
                    override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
                        if (state == BluetoothProfile.STATE_CONNECTED) {
                            targetDevice = device
                            runOnUiThread { Toast.makeText(this@MainActivity, "Connected to ${device.name}", Toast.LENGTH_SHORT).show() }
                        } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                            targetDevice = null
                        }
                    }
                })
            }
            override fun onServiceDisconnected(profile: Int) { hidDevice = null }
        }, BluetoothProfile.HID_DEVICE)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupButtonListeners() {
        // Bit Mapping: Gas=Bit0, Brake=Bit1, A=Bit2, B=Bit3, X=Bit4, Y=Bit5, Front=Bit6, Reverse=Bit7
        setTouchListener(findViewById<MaterialCardView>(R.id.btnGas), 0)
        setTouchListener(findViewById<MaterialCardView>(R.id.btnBrake), 1)
        setTouchListener(findViewById<MaterialButton>(R.id.btnA), 2)
        setTouchListener(findViewById<MaterialButton>(R.id.btnB), 3)
        setTouchListener(findViewById<MaterialButton>(R.id.btnX), 4)
        setTouchListener(findViewById<MaterialButton>(R.id.btnY), 5)
        setTouchListener(findViewById<MaterialButton>(R.id.btnFront), 6)
        setTouchListener(findViewById<MaterialButton>(R.id.btnReverse), 7)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setTouchListener(view: android.view.View, bitIndex: Int) {
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    currentButtons = (currentButtons.toInt() or (1 shl bitIndex)).toByte()
                    sendHidReport()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    currentButtons = (currentButtons.toInt() and (1 shl bitIndex).inv()).toByte()
                    sendHidReport()
                    true
                }
                else -> false
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            // Y-axis in landscape determines steering (-90 to +90 mapping)
            val tilt = event.values[1] 
            val rawSteering = (tilt * 14.0).toInt().coerceIn(-127, 127) // Multiplier adjusted for sensitivity
            
            val newSteering = rawSteering.toByte()
            // Send report ONLY if steering has changed to save battery & bluetooth bandwidth
            if (newSteering != currentSteering) {
                currentSteering = newSteering
                sendHidReport()
            }
        }
    }

    private fun sendHidReport() {
        targetDevice?.let { device ->
            // Report Size: 2 Bytes (Byte 0: X-Axis Steering, Byte 1: 8 Buttons State)
            val report = byteArrayOf(currentSteering, currentButtons)
            hidDevice?.sendReport(device, 0, report)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onResume() { super.onResume(); accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) } }
    override fun onPause() { super.onPause(); sensorManager.unregisterListener(this) }
}