package com.mrb.controller.pro

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.*
import android.graphics.Color
import android.hardware.*
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), SensorEventListener {

    private var hidDevice: BluetoothHidDevice? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var connectedDevice: BluetoothDevice? = null
    private lateinit var sensorManager: SensorManager
    private lateinit var tvStatus: TextView
    private var gasOn = false
    private var brakeOn = false
    private var tiltValue: Byte = 0

    // Standard Gamepad Descriptor with strict Byte casting
    private val HID_DESC = byteArrayOf(
        0x05.toByte(), 0x01.toByte(), 0x09.toByte(), 0x04.toByte(), 0xa1.toByte(), 0x01.toByte(), 
        0x85.toByte(), 0x01.toByte(), 0x05.toByte(), 0x09.toByte(), 0x19.toByte(), 0x01.toByte(), 
        0x29.toByte(), 0x04.toByte(), 0x15.toByte(), 0x00.toByte(), 0x25.toByte(), 0x01.toByte(), 
        0x75.toByte(), 0x01.toByte(), 0x95.toByte(), 0x04.toByte(), 0x81.toByte(), 0x02.toByte(),
        0x05.toByte(), 0x01.toByte(), 0x09.toByte(), 0x30.toByte(), 0x09.toByte(), 0x31.toByte(), 
        0x15.toByte(), 0x81.toByte(), 0x25.toByte(), 0x7f.toByte(), 0x75.toByte(), 0x08.toByte(), 
        0x95.toByte(), 0x02.toByte(), 0x81.toByte(), 0x02.toByte(), 0xc0.toByte()
    )

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        tvStatus = TextView(this).apply { 
            text = "INITIALIZING..."; setTextColor(Color.WHITE); gravity = Gravity.CENTER 
        }
        
        val btnGas = Button(this).apply {
            text = "GAS"; setBackgroundColor(Color.DKGRAY); setTextColor(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(300, 500).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; setMargins(0,0,100,0) }
            setOnTouchListener { v, e ->
                if(e.action == MotionEvent.ACTION_DOWN) { gasOn = true; v.setBackgroundColor(Color.GREEN) }
                if(e.action == MotionEvent.ACTION_UP) { gasOn = false; v.setBackgroundColor(Color.DKGRAY) }
                sendReport(); true
            }
        }

        root.addView(tvStatus); root.addView(btnGas)
        setContentView(root)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        bluetoothAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        setupHid()
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(device: BluetoothDevice?, registered: Boolean) {
            runOnUiThread { tvStatus.text = if(registered) "READY TO PAIR" else "ERROR" }
        }
        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                connectedDevice = device
                runOnUiThread { tvStatus.text = "CONNECTED" }
            } else {
                connectedDevice = null
                runOnUiThread { tvStatus.text = "DISCONNECTED" }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupHid() {
        bluetoothAdapter?.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(p: Int, proxy: BluetoothProfile?) {
                hidDevice = proxy as BluetoothHidDevice
                val sdp = BluetoothHidDeviceAppSdpSettings("Mrb Pad", "Gamepad", "Meet", 0x40.toByte(), HID_DESC)
                hidDevice?.registerApp(sdp, null, null, { it?.run() }, hidCallback)
            }
            override fun onServiceDisconnected(p: Int) {}
        }, BluetoothProfile.HID_DEVICE)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            tiltValue = (event.values[1] * 12).toInt().coerceIn(-127, 127).toByte()
            sendReport()
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendReport() {
        val dev = connectedDevice ?: return
        var b = 0
        if (gasOn) b = b or 0x01
        if (brakeOn) b = b or 0x02
        hidDevice?.sendReport(dev, 1, byteArrayOf(b.toByte(), tiltValue, 0x00))
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    override fun onResume() { 
        super.onResume()
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
    }
}
