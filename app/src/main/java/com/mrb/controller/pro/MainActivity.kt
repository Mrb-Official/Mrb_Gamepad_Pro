package com.mrb.controller.pro

import android.annotation.SuppressLint
import android.bluetooth.*
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

    private var hidDevice: BluetoothHidDevice? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var connectedDevice: BluetoothDevice? = null

    private lateinit var sensorManager: SensorManager
    private lateinit var imageSteering: ImageView
    private lateinit var textStatus: TextView

    private val HID_REPORT_DESC = byteArrayOf(
        0x05.toByte(), 0x01.toByte(), 0x09.toByte(), 0x04.toByte(), 0xa1.toByte(), 0x01.toByte(),
        0x85.toByte(), 0x01.toByte(), 0x05.toByte(), 0x09.toByte(), 0x19.toByte(), 0x01.toByte(),
        0x29.toByte(), 0x0a.toByte(), 0x15.toByte(), 0x00.toByte(), 0x25.toByte(), 0x01.toByte(),
        0x95.toByte(), 0x0a.toByte(), 0x75.toByte(), 0x01.toByte(), 0x81.toByte(), 0x02.toByte(),
        0xc0.toByte()
    )

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageSteering = findViewById(R.id.imageSteering)
        textStatus = findViewById(R.id.textStatus)

        // Standard way to get Bluetooth Adapter
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        bluetoothAdapter?.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = proxy as BluetoothHidDevice
                    registerApp()
                }
            }
            override fun onServiceDisconnected(profile: Int) {}
        }, BluetoothProfile.HID_DEVICE)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "MRB_Gamepad", "Mobile Steering", "Meet_Dev", 
            BluetoothHidDevice.SUBCLASS1_COMBO, HID_REPORT_DESC
        )
        hidDevice?.registerApp(sdp, null, null, { it?.run() }, object : BluetoothHidDevice.Callback() {
            override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                connectedDevice = if (state == BluetoothProfile.STATE_CONNECTED) device else null
                runOnUiThread { 
                    textStatus.text = if (state == BluetoothProfile.STATE_CONNECTED) "Connected!" else "Ready to Pair..." 
                }
            }
        })
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val xTilt = event.values[0]
            imageSteering.rotation = xTilt * -10f
            
            // FUTURE: Tilt data ko HID reports (A/D keys) mein yahan convert karenge
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
