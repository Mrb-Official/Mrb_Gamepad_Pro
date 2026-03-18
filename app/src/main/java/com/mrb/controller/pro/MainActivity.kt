package com.mrb.controller.pro

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.hardware.*
import android.os.Bundle
import android.widget.TextView
import android.widget.LinearLayout
import android.view.Gravity
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), SensorEventListener {

    private var hidDevice: BluetoothHidDevice? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var sensorManager: SensorManager
    private lateinit var statusText: TextView
    private lateinit var dataText: TextView

    // Har ek literal ko .toByte() kiya hai taaki compiler error na de
    private val HID_REPORT_DESC = byteArrayOf(
        0x05.toByte(), 0x01.toByte(), 0x09.toByte(), 0x06.toByte(), 0xA1.toByte(), 0x01.toByte(), 
        0x85.toByte(), 0x01.toByte(), 0x05.toByte(), 0x07.toByte(), 0x19.toByte(), 0xE0.toByte(), 
        0x29.toByte(), 0xE7.toByte(), 0x15.toByte(), 0x00.toByte(), 0x25.toByte(), 0x01.toByte(), 
        0x75.toByte(), 0x01.toByte(), 0x95.toByte(), 0x08.toByte(), 0x81.toByte(), 0x02.toByte(), 
        0x95.toByte(), 0x01.toByte(), 0x75.toByte(), 0x08.toByte(), 0x81.toByte(), 0x01.toByte(), 
        0x95.toByte(), 0x06.toByte(), 0x75.toByte(), 0x08.toByte(), 0x15.toByte(), 0x00.toByte(), 
        0x25.toByte(), 0x65.toByte(), 0x19.toByte(), 0x00.toByte(), 0x29.toByte(), 0x65.toByte(), 
        0x81.toByte(), 0x00.toByte(), 0xC0.toByte()
    )

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212"))
        }

        statusText = TextView(this).apply { 
            text = "Initializing MRB..."; textSize = 20f; setTextColor(Color.GREEN)
            setPadding(0, 0, 0, 50)
        }
        dataText = TextView(this).apply { 
            text = "Tilt Phone to Start"; textSize = 18f; setTextColor(Color.WHITE)
        }

        layout.addView(statusText); layout.addView(dataText)
        setContentView(layout)

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
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "MRB_Gamepad", "Remote", "MeetDev", 
            BluetoothHidDevice.SUBCLASS1_COMBO, HID_REPORT_DESC
        )
        hidDevice?.registerApp(sdp, null, null, { it?.run() }, object : BluetoothHidDevice.Callback() {
            override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                runOnUiThread {
                    statusText.text = if (state == BluetoothProfile.STATE_CONNECTED) "CONNECTED TO iQOO" else "PAIR iQOO IN SETTINGS"
                    statusText.setTextColor(if (state == BluetoothProfile.STATE_CONNECTED) Color.CYAN else Color.RED)
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0] 
            val y = event.values[1] 

            runOnUiThread { dataText.text = "X (Steer): ${"%.1f".format(x)} | Y (Gas): ${"%.1f".format(y)}" }

            var keyByte: Byte = 0x00

            // X-Axis: Left (A) / Right (D)
            if (x > 3.5f) keyByte = 0x04.toByte()
            else if (x < -3.5f) keyByte = 0x07.toByte()
            
            // Y-Axis: Gas (W) / Brake (S)
            if (keyByte == 0x00.toByte()) {
                if (y < -3.0f) keyByte = 0x1A.toByte()
                else if (y > 3.0f) keyByte = 0x16.toByte()
            }

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
