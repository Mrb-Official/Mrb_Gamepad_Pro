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
    
    private var tiltAngle = 0f
    private var gasOn = false
    private var brakeOn = false
    private var lastSendTime = 0L

    // ── XBOX 360 STANDARD DESCRIPTOR (Android Games Friendly) ──
    private val HID_DESC = byteArrayOf(
        0x05, 0x01, 0x09, 0x05, 0xa1, 0x01, 0x85, 0x01,
        0x05, 0x09, 0x19, 0x01, 0x29, 0x0a, 0x15, 0x00,
        0x25, 0x01, 0x95, 0x0a, 0x75, 0x01, 0x81, 0x02,
        0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x15, 0x81,
        0x25, 0x7f, 0x75, 0x08, 0x95, 0x02, 0x81, 0x02,
        0xc0
    ).map { it.toByte() }.toByteArray()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#080808")) }
        val tvStatus = TextView(this).apply { 
            text = "MRB PRO: READY"; setTextColor(Color.WHITE); gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(0, 50, 0, 0)
        }

        // GAS BUTTON
        val btnGas = Button(this).apply {
            text = "GAS"; setBackgroundColor(Color.DARK_GRAY); setTextColor(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(300, 500).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; setMargins(0,0,100,0) }
            setOnTouchListener { _, event ->
                when(event.action) {
                    MotionEvent.ACTION_DOWN -> { gasOn = true; setBackgroundColor(Color.GREEN); sendHIDReport(); true }
                    MotionEvent.ACTION_UP -> { gasOn = false; setBackgroundColor(Color.DARK_GRAY); sendHIDReport(); true }
                    else -> false
                }
            }
        }

        // BRAKE BUTTON
        val btnBrake = Button(this).apply {
            text = "BRAKE"; setBackgroundColor(Color.DARK_GRAY); setTextColor(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(300, 500).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL; setMargins(100,0,0,0) }
            setOnTouchListener { _, event ->
                when(event.action) {
                    MotionEvent.ACTION_DOWN -> { brakeOn = true; setBackgroundColor(Color.RED); sendHIDReport(); true }
                    MotionEvent.ACTION_UP -> { brakeOn = false; setBackgroundColor(Color.DARK_GRAY); sendHIDReport(); true }
                    else -> false
                }
            }
        }

        root.addView(tvStatus); root.addView(btnGas); root.addView(btnBrake)
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
                val sdp = BluetoothHidDeviceAppSdpSettings("Mrb Pad", "Gamepad", "Meet", BluetoothHidDevice.SUBCLASS1_COMBO, HID_DESC)
                hidDevice?.registerApp(sdp, null, null, { it?.run() }, object : BluetoothHidDevice.Callback() {
                    override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                        if (state == BluetoothProfile.STATE_CONNECTED) connectedDevice = device
                    }
                })
            }
            override fun onServiceDisconnected(p0: Int) {}
        }, BluetoothProfile.HID_DEVICE)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            tiltAngle = event.values[1]
            val now = System.currentTimeMillis()
            if (now - lastSendTime > 30) { // Stable 33fps
                sendHIDReport()
                lastSendTime = now
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendHIDReport() {
        val device = connectedDevice ?: return
        val joyX = (tiltAngle * 15f).toInt().coerceIn(-127, 127).toByte()
        
        var b1 = 0
        if (gasOn) b1 = b1 or 0x01
        if (brakeOn) b1 = b1 or 0x02

        // Report Structure: [Buttons, AxisX, AxisY]
        val report = byteArrayOf(b1.toByte(), joyX, 0x00)
        hidDevice?.sendReport(device, 1, report)
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}
    override fun onResume() { 
        super.onResume()
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
    }
}
