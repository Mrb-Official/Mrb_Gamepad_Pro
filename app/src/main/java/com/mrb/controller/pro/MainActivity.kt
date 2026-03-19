package com.mrb.controller.pro

import android.annotation.SuppressLint
import android.bluetooth.*
import android.companion.*
import android.content.*
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.hardware.*
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : AppCompatActivity(), SensorEventListener {

    private var hidDevice: BluetoothHidDevice? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var connectedDevice: BluetoothDevice? = null
    private lateinit var sensorManager: SensorManager

    private lateinit var txtStatus: TextView
    private lateinit var txtTilt: TextView
    private lateinit var tiltBar: ProgressBar
    private lateinit var wheelView: WheelView

    private var gasOn    = false
    private var brakeOn  = false
    private var gearUp   = false
    private var gearDown = false
    private var tiltByte: Byte = 0
    private var filtX = 0f
    private val alpha = 0.15f
    private var lastSend = 0L

    private val SELECT_DEVICE = 42

    private val HID_DESC = intArrayOf(
        0x05,0x01, 0x09,0x05, 0xa1,0x01, 0x85,0x01,
        0x05,0x09, 0x19,0x01, 0x29,0x08,
        0x15,0x00, 0x25,0x01, 0x75,0x01, 0x95,0x08, 0x81,0x02,
        0x05,0x01, 0x09,0x30, 0x09,0x31,
        0x15,0x81.toByte().toInt(), 0x25,0x7f,
        0x75,0x08, 0x95,0x02, 0x81,0x02,
        0xc0
    ).map { it.toByte() }.toByteArray()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Global crash handler
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            android.util.Log.e("MRB_CRASH", e.stackTraceToString())
            runOnUiThread {
                Toast.makeText(this,
                    "Crash: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(arrayOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_ADVERTISE
            ), 99)
        }

        // Runtime BT permissions Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(arrayOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_ADVERTISE
            ), 99)
        }

        try {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            window.decorView.windowInsetsController?.hide(
                WindowInsets.Type.statusBars() or
                WindowInsets.Type.navigationBars())

            setContentView(R.layout.activity_main)

            txtStatus = findViewById(R.id.txt_status)
            txtTilt   = findViewById(R.id.txt_tilt)
            tiltBar   = findViewById(R.id.tilt_bar)
            wheelView = findViewById(R.id.lay_steering)

            sensorManager    = getSystemService(
                Context.SENSOR_SERVICE) as SensorManager
            bluetoothAdapter = (getSystemService(
                Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

            wheelView.setOnClickListener { pairDevice() }

            setupTouchBtn(R.id.lay_gas,
                "#1A2A1A", "#111111") { gasOn = it }
            setupTouchBtn(R.id.lay_brake,
                "#2A1A1A", "#111111") { brakeOn = it }
            setupTouchBtn(R.id.lay_gear_up,
                "#1A2A1A", "#111111") { gearUp = it }
            setupTouchBtn(R.id.lay_gear_down,
                "#2A1A1A", "#111111") { gearDown = it }

            setupHid()

        } catch (e: Exception) {
            android.util.Log.e("MRB_CRASH", e.stackTraceToString())
            Toast.makeText(this,
                "Init Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchBtn(
        id: Int,
        pressColor: String,
        normalColor: String,
        onPress: (Boolean) -> Unit
    ) {
        findViewById<View>(id)?.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    onPress(true)
                    v.setBackgroundColor(Color.parseColor(pressColor))
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    onPress(false)
                    v.setBackgroundColor(Color.parseColor(normalColor))
                    true
                }
                else -> false
            }
        }
    }

    private fun pairDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(arrayOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_ADVERTISE
            ), 99)
        }

        try {
            val dm = getSystemService(
                Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
            val req = AssociationRequest.Builder()
                .addDeviceFilter(BluetoothDeviceFilter.Builder().build())
                .setSingleDevice(false).build()
            dm.associate(req, object : CompanionDeviceManager.Callback() {
                override fun onDeviceFound(sender: IntentSender) {
                    startIntentSenderForResult(
                        sender, SELECT_DEVICE, null, 0, 0, 0)
                }
                override fun onFailure(e: CharSequence?) {
                    startActivity(Intent(
                        android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                }
            }, null)
        } catch (e: Exception) {
            Toast.makeText(this,
                "BT Error: ${e.message}", Toast.LENGTH_LONG).show()
            startActivity(Intent(
                android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
        }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == SELECT_DEVICE && res == RESULT_OK) {
            connectedDevice = data?.getParcelableExtra(
                CompanionDeviceManager.EXTRA_DEVICE)
            txtStatus.text = "Ready — Tap wheel"
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupHid() {
        bluetoothAdapter?.getProfileProxy(this,
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(
                    p: Int, proxy: BluetoothProfile?) {
                    hidDevice = proxy as BluetoothHidDevice
                    val sdp = BluetoothHidDeviceAppSdpSettings(
                        "MRB Gamepad Pro",
                        "Tilt Controller",
                        "MeetDev",
                        0x08.toByte(),
                        HID_DESC)
                    hidDevice?.registerApp(sdp, null, null,
                        { it?.run() },
                        object : BluetoothHidDevice.Callback() {
                            override fun onConnectionStateChanged(
                                device: BluetoothDevice?, state: Int) {
                                runOnUiThread {
                                    when (state) {
                                        BluetoothProfile.STATE_CONNECTED -> {
                                            connectedDevice = device
                                            txtStatus.text =
                                                "● ${device?.name ?: "Connected"}"
                                            txtStatus.setTextColor(
                                                Color.parseColor("#00FF88"))
                                        }
                                        BluetoothProfile.STATE_DISCONNECTED -> {
                                            connectedDevice = null
                                            txtStatus.text = "Tap wheel to pair"
                                            txtStatus.setTextColor(
                                                Color.argb(128,255,255,255))
                                        }
                                        BluetoothProfile.STATE_CONNECTING -> {
                                            txtStatus.text = "Ready — Tap wheel"
                                            txtStatus.setTextColor(
                                                Color.parseColor("#FFD700"))
                                        }
                                    }
                                }
                            }
                            override fun onAppStatusChanged(
                                d: BluetoothDevice?,
                                registered: Boolean) {
                                if (registered) runOnUiThread {
                                    txtStatus.text =
                                        "Visible as 'MRB Gamepad Pro'"
                                    txtStatus.setTextColor(
                                        Color.argb(180,255,255,255))
                                }
                            }
                        })
                }
                override fun onServiceDisconnected(p: Int) {
                    hidDevice = null
                }
            }, BluetoothProfile.HID_DEVICE)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        filtX = alpha * event.values[0] + (1 - alpha) * filtX
        tiltByte = (filtX / 10f * 127f).toInt()
            .coerceIn(-127, 127).toByte()

        val now = System.currentTimeMillis()
        if (now - lastSend > 40) {
            sendReport()
            lastSend = now
        }

        runOnUiThread {
            wheelView.angle = (filtX / 10f * 90f).coerceIn(-90f, 90f)
            txtTilt.text = "%.1f°".format(filtX * 9f)
            tiltBar.progress =
                (100 + (filtX / 10f * 100).toInt()).coerceIn(0, 200)
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendReport() {
        val device = connectedDevice ?: return
        val hid    = hidDevice ?: return
        var btns   = 0
        if (gasOn)    btns = btns or 0x01
        if (brakeOn)  btns = btns or 0x02
        if (gearUp)   btns = btns or 0x04
        if (gearDown) btns = btns or 0x08
        hid.sendReport(device, 1,
            byteArrayOf(btns.toByte(), tiltByte, 0x00))
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
}

class WheelView(
    context: Context,
    attrs: android.util.AttributeSet? = null
) : View(context, attrs) {

    var angle: Float = 0f
        set(v) { field = v; invalidate() }

    private val pRing = android.graphics.Paint(
        android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 14f
    }
    private val pHub = android.graphics.Paint(
        android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 8f
    }
    private val pSpoke = android.graphics.Paint(
        android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180,255,255,255)
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    private val pDot = android.graphics.Paint(
        android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = android.graphics.Paint.Style.FILL
    }
    private val pArc = android.graphics.Paint(
        android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(50,255,255,255)
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = android.graphics.Paint.Cap.ROUND
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        val cx = width/2f
        val cy = height/2f
        val r  = minOf(width,height)/2f - 14f

        canvas.drawCircle(cx, cy, r, pRing)

        val arc = android.graphics.RectF(
            cx-r*0.6f, cy-r*0.6f, cx+r*0.6f, cy+r*0.6f)
        canvas.drawArc(arc, -90f, -angle, false, pArc)

        canvas.save()
        canvas.rotate(-angle, cx, cy)
        canvas.drawCircle(cx, cy, r*0.24f, pHub)
        for (i in 0..2) {
            val a = Math.toRadians(i*120.0 - 90.0)
            canvas.drawLine(
                cx+(r*0.24f*cos(a)).toFloat(),
                cy+(r*0.24f*sin(a)).toFloat(),
                cx+(r*cos(a)).toFloat(),
                cy+(r*sin(a)).toFloat(),
                pSpoke)
        }
        canvas.drawCircle(cx, cy-r+10f, 8f, pDot)
        canvas.drawCircle(cx, cy, 5f, pDot)
        canvas.restore()
    }
}

// Permission result - restart init
