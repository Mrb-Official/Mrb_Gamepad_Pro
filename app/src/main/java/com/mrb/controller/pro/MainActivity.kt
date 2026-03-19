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
    private var lastSendTime = 0L

    // ── BULLETPROOF DESCRIPTOR: intArray mapped to ByteArray ──
    private val HID_DESC = intArrayOf(
        0x05, 0x01, 0x09, 0x04, 0xa1, 0x01, 0x85, 0x01,
        0x05, 0x09, 0x19, 0x01, 0x29, 0x04, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x04, 0x81, 0x02,
        0x75, 0x04, 0x95, 0x01, 0x81, 0x03, // <-- PADDING FIX
        0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x15, 0x81, 0x25, 0x7f, 0x75, 0x08, 0x95, 0x02, 0x81, 0x02,
        0xc0
    ).map { it.toByte() }.toByteArray()

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        
        tvStatus = TextView(this).apply { 
            text = "MRB PRO: WAITING"; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 20f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 50, 0, 0) }
        }

        val btnGas = Button(this).apply {
            text = "GAS"; setBackgroundColor(Color.DKGRAY); setTextColor(Color.WHITE); textSize = 24f
            layoutParams = FrameLayout.LayoutParams(350, 350).apply { 
                gravity = Gravity.END or Gravity.CENTER_VERTICAL; setMargins(0,0,100,0) 
            }
            setOnTouchListener { v, event ->
                when(event.action) {
                    MotionEvent.ACTION_DOWN -> { gasOn = true; v.setBackgroundColor(Color.GREEN); sendHIDReport(); true }
                    MotionEvent.ACTION_UP -> { gasOn = false; v.setBackgroundColor(Color.DKGRAY); sendHIDReport(); true }
                    else -> false
                }
            }
        }

        val btnBrake = Button(this).apply {
            text = "BRAKE"; setBackgroundColor(Color.DKGRAY); setTextColor(Color.WHITE); textSize = 24f
            layoutParams = FrameLayout.LayoutParams(350, 350).apply { 
                gravity = Gravity.START or Gravity.CENTER_VERTICAL; setMargins(100,0,0,0) 
            }
            setOnTouchListener { v, event ->
                when(event.action) {
                    MotionEvent.ACTION_DOWN -> { brakeOn = true; v.setBackgroundColor(Color.RED); sendHIDReport(); true }
                    MotionEvent.ACTION_UP -> { brakeOn = false; v.setBackgroundColor(Color.DKGRAY); sendHIDReport(); true }
                    else -> false
                }
            }
        }

        root.addView(tvStatus)
        root.addView(btnBrake)
        root.addView(btnGas)
        setContentView(root)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        bluetoothAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        
        setupHid()
    }

    @SuppressLint("MissingPermission")
    private fun setupHid() {
        bluetoothAdapter?.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                hidDevice = proxy as BluetoothHidDevice
                val sdp = BluetoothHidDeviceAppSdpSettings("Mrb Pad", "Gamepad", "Meet", 0x40.toByte(), HID_DESC)
                hidDevice?.registerApp(sdp, null, null, { it?.run() }, object : BluetoothHidDevice.Callback() {
                    override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                        if (state == BluetoothProfile.STATE_CONNECTED) {
                            connectedDevice = device
                            runOnUiThread { tvStatus.text = "CONNECTED" }
                        } else {
                            connectedDevice = null
                            runOnUiThread { tvStatus.text = "DISCONNECTED" }
                        }
                    }
                })
            }
            override fun onServiceDisconnected(p0: Int) {}
        }, BluetoothProfile.HID_DEVICE)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            tiltValue = (event.values[1] * 12).toInt().coerceIn(-127, 127).toByte()
            val now = System.currentTimeMillis()
            if (now - lastSendTime > 40) {
                sendHIDReport()
                lastSendTime = now
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendHIDReport() {
        val device = connectedDevice ?: return
        var buttons = 0
        if (gasOn) buttons = buttons or 0x01
        if (brakeOn) buttons = buttons or 0x02
        
        hidDevice?.sendReport(device, 1, byteArrayOf(buttons.toByte(), tiltValue, 0x00))
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}
    override fun onResume() { 
        super.onResume()
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
    }
}
