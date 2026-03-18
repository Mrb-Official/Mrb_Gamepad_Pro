package com.mrb.controller.pro

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.*
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.*

class MainActivity : AppCompatActivity(), SensorEventListener {

    private var hidDevice: BluetoothHidDevice? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var connectedDevice: BluetoothDevice? = null

    private lateinit var sensorManager: SensorManager
    private var tiltX = 0f
    private val alpha = 0.15f
    private var filtX = 0f

    private var gasOn    = false
    private var brakeOn  = false
    private var gearUp   = false
    private var gearDown = false

    private lateinit var page1: View
    private lateinit var page2: View
    private lateinit var tvBtStatus: TextView
    private lateinit var wheelView: WheelView
    private lateinit var tvTiltVal: TextView
    private lateinit var tiltBar: ProgressBar
    private lateinit var tvConnected: TextView
    private var onPage2 = false

    // ── 100% PURE GAMEPAD DESCRIPTOR (ONLY X & Y AXIS) ──
    private val HID_DESC = byteArrayOf(
        0x05.toByte(), 0x01.toByte(), 0x09.toByte(), 0x05.toByte(), 0xA1.toByte(), 0x01.toByte(),
        0x85.toByte(), 0x01.toByte(), 0x05.toByte(), 0x09.toByte(), 0x19.toByte(), 0x01.toByte(),
        0x29.toByte(), 0x10.toByte(), 0x15.toByte(), 0x00.toByte(), 0x25.toByte(), 0x01.toByte(),
        0x75.toByte(), 0x01.toByte(), 0x95.toByte(), 0x10.toByte(), 0x81.toByte(), 0x02.toByte(),
        0x05.toByte(), 0x01.toByte(), 0x09.toByte(), 0x30.toByte(), 0x09.toByte(), 0x31.toByte(),
        0x15.toByte(), 0x81.toByte(), 0x25.toByte(), 0x7F.toByte(), 0x75.toByte(), 0x08.toByte(),
        0x95.toByte(), 0x02.toByte(), 0x81.toByte(), 0x02.toByte(), 0xC0.toByte()
    )

    // ── BLUETOOTH CONNECTION RECEIVER (AUTO-SWITCH FIX) ──
    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                connectedDevice = device
                runOnUiThread {
                    tvBtStatus.text = "● Auto-Connected!"
                    tvBtStatus.setTextColor(Color.GREEN)
                    if (!onPage2) switchToPage2()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
        
        window.decorView.setBackgroundColor(Color.parseColor("#0A0A0A"))
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        val filter = IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED)
        registerReceiver(btReceiver, filter)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0A0A0A"))
        }

        page1 = buildPage1()
        page2 = buildPage2()
        page2.alpha = 0f
        page2.visibility = View.GONE

        root.addView(page1, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(page2, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        setContentView(root)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = btManager.adapter

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_ADVERTISE
            )
            val needed = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
            if (needed.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1)
            } else {
                startHid()
            }
        } else {
            startHid()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(btReceiver)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startHid() 
        } else {
            tvBtStatus.text = "Permission Denied! Cannot start Gamepad."
            tvBtStatus.setTextColor(Color.RED)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startHid() {
        if (bluetoothAdapter == null) return
        
        val proxySuccess = bluetoothAdapter?.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = proxy as BluetoothHidDevice
                    registerHid()
                }
            }
            override fun onServiceDisconnected(profile: Int) {
                hidDevice = null
            }
        }, BluetoothProfile.HID_DEVICE)

        if (proxySuccess == false) {
            tvBtStatus.text = "Hardware Error: Kernel blocks HID!"
            tvBtStatus.setTextColor(Color.RED)
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerHid() {
        val sdp = BluetoothHidDeviceAppSdpSettings("MRB Gamepad Pro", "MRB Tilt Controller", "MeetDev", BluetoothHidDevice.SUBCLASS1_COMBO, HID_DESC)
        
        hidDevice?.registerApp(sdp, null, null, { it?.run() }, object : BluetoothHidDevice.Callback() {
            override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                runOnUiThread {
                    when (state) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            connectedDevice = device
                            tvBtStatus.text = "● Connected: ${device?.name}"
                            tvBtStatus.setTextColor(Color.GREEN)
                            tvConnected.text = "● Connected to ${device?.name}"
                            if (!onPage2) switchToPage2()
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            connectedDevice = null
                            tvBtStatus.text = "○ Disconnected"
                            tvBtStatus.setTextColor(Color.GRAY)
                            if (onPage2) {
                                onPage2 = false
                                page1.visibility = View.VISIBLE
                                page1.animate().alpha(1f).setDuration(500).start()
                                page2.animate().alpha(0f).setDuration(300).withEndAction { page2.visibility = View.GONE }.start()
                            }
                        }
                    }
                }
            }
        })
    }

    private fun buildPage1(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0A0A0A"))
        }

        val tvTitle = TextView(this).apply {
            text = "MRB Gamepad Pro"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        tvBtStatus = TextView(this).apply {
            text = "\nReady to Pair!"
            textSize = 16f
            setTextColor(Color.CYAN)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 40)
        }

        // ── BUTTON 1: BLUETOOTH SETTINGS ──
        val btnConnect = Button(this).apply {
            text = "OPEN BLUETOOTH SETTINGS"
            setBackgroundColor(Color.parseColor("#00C853"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                startActivity(intent)
            }
        }

        // ── BUTTON 2: MANUAL START (FAILSAFE) ──
        val btnForceStart = Button(this).apply {
            text = "START CONTROLLER (Manual)"
            setBackgroundColor(Color.parseColor("#333333"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 30 }
            setOnClickListener { 
                switchToPage2() 
            }
        }

        root.addView(tvTitle)
        root.addView(tvBtStatus)
        root.addView(btnConnect)
        root.addView(btnForceStart) // Added Manual Button!
        return root
    }

    private fun buildPage2(): View {
        val root = FrameLayout(this)

        wheelView = WheelView(this).apply {
            layoutParams = FrameLayout.LayoutParams(300.dpToPx(), 300.dpToPx()).apply { gravity = Gravity.CENTER }
        }

        val topBar = LinearLayout(this).apply { setPadding(20, 20, 20, 20) }
        tvConnected = TextView(this).apply { text = "● Controller Active"; setTextColor(Color.GREEN) }
        topBar.addView(tvConnected)

        val leftCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(150.dpToPx(), FrameLayout.LayoutParams.MATCH_PARENT).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }
            gravity = Gravity.CENTER
        }
        val btnBrake = buildBtn("BRAKE", Color.RED) { on -> brakeOn = on; sendHIDReport() }
        val btnGearDown = buildBtn("GEAR-", Color.MAGENTA) { on -> gearDown = on; sendHIDReport() }
        leftCol.addView(btnGearDown); leftCol.addView(btnBrake)

        val rightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(150.dpToPx(), FrameLayout.LayoutParams.MATCH_PARENT).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
            gravity = Gravity.CENTER
        }
        val btnGas = buildBtn("GAS", Color.GREEN) { on -> gasOn = on; sendHIDReport() }
        val btnGearUp = buildBtn("GEAR+", Color.CYAN) { on -> gearUp = on; sendHIDReport() }
        rightCol.addView(btnGearUp); rightCol.addView(btnGas)

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.BOTTOM }
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }
        tvTiltVal = TextView(this).apply { text = "0.0°"; setTextColor(Color.WHITE); textSize = 18f; gravity = Gravity.CENTER }
        tiltBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 50 }
        bottom.addView(tvTiltVal); bottom.addView(tiltBar)

        root.addView(wheelView); root.addView(topBar); root.addView(leftCol); root.addView(rightCol); root.addView(bottom)
        return root
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildBtn(label: String, color: Int, onPress: (Boolean) -> Unit): View {
        val btn = Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#222222"))
            layoutParams = LinearLayout.LayoutParams(120.dpToPx(), 100.dpToPx()).apply { setMargins(10, 10, 10, 10) }
        }
        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { btn.setBackgroundColor(color); onPress(true); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { btn.setBackgroundColor(Color.parseColor("#222222")); onPress(false); true }
                else -> false
            }
        }
        return btn
    }

    private fun switchToPage2() {
        if (onPage2) return
        onPage2 = true
        page2.visibility = View.VISIBLE
        page2.animate().alpha(1f).setDuration(500).start()
        page1.animate().alpha(0f).setDuration(300).withEndAction { page1.visibility = View.GONE }.start()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER && onPage2) {
            val x = event.values[0] 
            
            filtX = alpha * x + (1 - alpha) * filtX
            tiltX = filtX

            val rotationAngle = tiltX * -10f
            wheelView.rotation = rotationAngle
            tvTiltVal.text = "Analog Steering: ${"%.1f".format(rotationAngle)}°"
            tiltBar.progress = (50 + (tiltX * 5)).toInt().coerceIn(0, 100)

            sendHIDReport()
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendHIDReport() {
        if (hidDevice == null) return

        val joyX = (tiltX * 15f).toInt().coerceIn(-127, 127).toByte()
        
        var buttons1: Byte = 0x00 
        var buttons2: Byte = 0x00 

        if (gasOn)    buttons1 = (buttons1.toInt() or 0x01).toByte() 
        if (brakeOn)  buttons1 = (buttons1.toInt() or 0x02).toByte() 
        if (gearUp)   buttons1 = (buttons1.toInt() or 0x04).toByte() 
        if (gearDown) buttons1 = (buttons1.toInt() or 0x08).toByte() 

        val report = byteArrayOf(
            buttons1, 
            buttons2, 
            joyX,          
            0x00.toByte()  
        )
        
        // Failsafe: Agar specific device miss ho gaya, toh connect hue kisi bhi device ko bhej do
        val devices = hidDevice?.connectedDevices
        if (!devices.isNullOrEmpty()) {
            hidDevice?.sendReport(devices[0], 1, report)
        } else if (connectedDevice != null) {
            hidDevice?.sendReport(connectedDevice, 1, report)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
        
        // Auto-switch agar pehle se connected hai
        if (hidDevice?.connectedDevices?.isNotEmpty() == true && !onPage2) {
            switchToPage2()
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
}

class WheelView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 20f
        color = Color.parseColor("#444444")
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.RED
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) - 20f
        
        canvas.drawCircle(cx, cy, radius, paint)
        canvas.drawCircle(cx, cy, 30f, linePaint)
        
        canvas.drawLine(cx, cy, cx - radius, cy, paint)
        canvas.drawLine(cx, cy, cx + radius, cy, paint)
        canvas.drawLine(cx, cy, cx, cy + radius, paint)
    }
}

fun Int.dpToPx(): Int {
    val density = android.content.res.Resources.getSystem().displayMetrics.density
    return (this * density).toInt()
}
