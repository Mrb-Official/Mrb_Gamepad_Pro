package com.mrb.controller.pro

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.hardware.*
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), SensorEventListener {

    private var hidDevice: BluetoothHidDevice? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var sensorManager: SensorManager
    private lateinit var statusText: TextView

    // HID Report Descriptor: Batta hai ki hum Keyboard/Gamepad hain
    private val HID_REPORT_DESC = byteArrayOf(
        0x05, 0x01, 0x09, 0x06, 0xa1, 0x01, 0x85, 0x01, 0x05, 0x07, 
        0x19, 0xe0.toByte(), 0x29, 0xe7.toByte(), 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 
        0x95, 0x08, 0x81, 0x02, 0x95, 0x01, 0x75, 0x08, 0x81, 0x01, 
        0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65, 0x19, 0x00, 
        0x29, 0x65, 0x81, 0x00, 0xc0
    )

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        statusText = TextView(this).apply { 
            text = "Initializing MRB Gamepad..."; textSize = 24f; setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
        }
        setContentView(statusText)
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)

        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = btManager.adapter

        bluetoothAdapter?.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = proxy as BluetoothHidDevice
                    registerHidApp()
                }
            }
            override fun onServiceDisconnected(profile: Int) {}
        }, BluetoothProfile.HID_DEVICE)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    @SuppressLint("MissingPermission")
    private fun registerHidApp() {
        val sdp = BluetoothHidDeviceAppSdpSettings("MRB_Gamepad", "Remote", "MeetDev", BluetoothHidDevice.SUBCLASS1_COMBO, HID_REPORT_DESC)
        hidDevice?.registerApp(sdp, null, null, { it?.run() }, object : BluetoothHidDevice.Callback() {
            override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                runOnUiThread {
                    statusText.text = if (state == BluetoothProfile.STATE_CONNECTED) "Connected to iQOO!" else "Pair with iQOO in BT Settings"
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val keyByte = when {
                x > 3.0f -> 0x04.toByte() // 'A' key (Left)
                x < -3.0f -> 0x07.toByte() // 'D' key (Right)
                else -> 0x00.toByte()      // Release
            }
            // Report bhejo: [Modifier, Reserved, Key1, Key2, Key3, Key4, Key5, Key6]
            val report = byteArrayOf(0, 0, keyByte, 0, 0, 0, 0, 0)
            hidDevice?.getConnectedDevices()?.forEach { device ->
                hidDevice?.sendReport(device, 1, report)
            }
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

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}
