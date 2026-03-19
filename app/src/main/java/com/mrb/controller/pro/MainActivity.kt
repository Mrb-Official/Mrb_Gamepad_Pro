package com.mrb.controller.pro

import android.annotation.SuppressLint
import android.bluetooth.*
import android.companion.*
import android.content.*
import android.content.pm.ActivityInfo
import android.graphics.*
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
    private var btnA     = false
    private var btnB     = false
    private var btnX     = false
    private var btnY     = false
    private var tiltByte: Byte = 0
    private var filtX    = 0f
    private val alpha    = 0.15f
    private var lastSend = 0L
    private val SELECT_DEVICE = 42

    private val HID_DESC = byteArrayOf(
        0x05, 0x01,
        0x09, 0x05,
        0xa1.toByte(), 0x01,
        0x85.toByte(), 0x01,
        // 8 Buttons
        0x05, 0x09,
        0x19, 0x01,
        0x29, 0x08,
        0x15, 0x00,
        0x25, 0x01,
        0x75, 0x01,
        0x95.toByte(), 0x08,
        0x81.toByte(), 0x02,
        // Padding
        0x75, 0x08,
        0x95.toByte(), 0x01,
        0x81.toByte(), 0x01,
        // X Axis steering
        0x05, 0x01,
        0x09, 0x30,
        0x15, 0x81.toByte(),
        0x25, 0x7f,
        0x75, 0x08,
        0x95.toByte(), 0x01,
        0x81.toByte(), 0x02,
        // Y Axis
        0x09, 0x31,
        0x15, 0x81.toByte(),
        0x25, 0x7f,
        0x75, 0x08,
        0x95.toByte(), 0x01,
        0x81.toByte(), 0x02,
        // Z = LT Brake
        0x09, 0x32,
        0x15, 0x00,
        0x25, 0x7f,
        0x75, 0x08,
        0x95.toByte(), 0x01,
        0x81.toByte(), 0x02,
        // RZ = RT Gas
        0x09, 0x35,
        0x15, 0x00,
        0x25, 0x7f,
        0x75, 0x08,
        0x95.toByte(), 0x01,
        0x81.toByte(), 0x02,
        0xc0.toByte()
    )

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val perms = arrayOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_ADVERTISE)
            val missing = perms.filter {
                checkSelfPermission(it) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 99)
        }

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.decorView.windowInsetsController?.hide(
            WindowInsets.Type.statusBars() or
            WindowInsets.Type.navigationBars())

        setContentView(R.layout.activity_main)

        txtStatus = findViewById(R.id.txt_status)
        txtTilt   = findViewById(R.id.txt_tilt)
        tiltBar   = findViewById(R.id.tilt_bar)
        wheelView = findViewById(R.id.lay_steering)

        sensorManager    = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        bluetoothAdapter = (getSystemService(Context.BLUETOOTH_SERVICE)
            as BluetoothManager).adapter

        // Brake - red border on press
        setupTouch(R.id.lay_brake,
            R.drawable.btn_normal_r12, R.drawable.btn_press_red,
            R.id.ic_brake, 0xFFFF4B4B.toInt()) { brakeOn = it }

        // Gas - green border on press
        setupTouch(R.id.lay_gas,
            R.drawable.btn_normal_r12, R.drawable.btn_press_green,
            R.id.ic_gas, 0xFF3CFF6B.toInt()) { gasOn = it }

        // Gear up - orange
        setupTouch(R.id.lay_gear_up,
            R.drawable.btn_normal_r12, R.drawable.btn_press_orange,
            null, 0) { gearUp = it }

        // Gear down - blue
        setupTouch(R.id.lay_gear_down,
            R.drawable.btn_normal_r12, R.drawable.btn_press_blue,
            null, 0) { gearDown = it }

        // Xbox buttons
        setupTouch(R.id.btn_a,
            R.drawable.btn_xbox_green, R.drawable.btn_xbox_green_press,
            null, 0) { btnA = it }
        setupTouch(R.id.btn_b,
            R.drawable.btn_xbox_red, R.drawable.btn_xbox_red_press,
            null, 0) { btnB = it }
        setupTouch(R.id.btn_x,
            R.drawable.btn_xbox_blue, R.drawable.btn_xbox_blue_press,
            null, 0) { btnX = it }
        setupTouch(R.id.btn_y,
            R.drawable.btn_xbox_yellow, R.drawable.btn_xbox_yellow_press,
            null, 0) { btnY = it }

        setupHid()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouch(
        id: Int,
        normalRes: Int,
        pressRes: Int,
        iconId: Int?,
        pressIconColor: Int,
        onPress: (Boolean) -> Unit
    ) {
        val view = findViewById<View>(id) ?: return
        val icon = iconId?.let { findViewById<ImageView>(it) }
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    onPress(true)
                    view.setBackgroundResource(pressRes)
                    if (pressIconColor != 0) icon?.setColorFilter(pressIconColor)
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    onPress(false)
                    view.setBackgroundResource(normalRes)
                    icon?.setColorFilter(Color.parseColor("#888888"))
                    true
                }
                else -> false
            }
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
                                            txtStatus.text = "● ${device?.name}"
                                            txtStatus.setTextColor(
                                                Color.parseColor("#00FF88"))
                                        }
                                        BluetoothProfile.STATE_DISCONNECTED -> {
                                            connectedDevice = null
                                            txtStatus.text = "Tap wheel to pair"
                                            txtStatus.setTextColor(
                                                Color.argb(100,255,255,255))
                                        }
                                        BluetoothProfile.STATE_CONNECTING -> {
                                            txtStatus.text = "Connecting..."
                                            txtStatus.setTextColor(
                                                Color.parseColor("#FFD700"))
                                        }
                                    }
                                }
                            }
                            override fun onAppStatusChanged(
                                d: BluetoothDevice?, registered: Boolean) {
                                runOnUiThread {
                                    if (registered) {
                                        txtStatus.text = "Visible — Tap wheel to pair"
                                        txtStatus.setTextColor(
                                            Color.argb(150,255,255,255))
                                    }
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
        filtX = alpha * event.values[1] + (1 - alpha) * filtX
        tiltByte = (filtX / 10f * 127f).toInt().coerceIn(-127, 127).toByte()

        val now = System.currentTimeMillis()
        if (now - lastSend > 40) { sendReport(); lastSend = now }

        runOnUiThread {
            wheelView.angle = (filtX / 10f * 90f).coerceIn(-90f, 90f)
            txtTilt.text = "%.1f°".format(filtX * 9f)
            tiltBar.progress = (100 + (filtX/10f*100).toInt()).coerceIn(0, 200)
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendReport() {
        val device = connectedDevice ?: return
        val hid    = hidDevice ?: return
        var btns   = 0
        if (btnA)     btns = btns or (1 shl 0)
        if (btnB)     btns = btns or (1 shl 1)
        if (gearUp)   btns = btns or (1 shl 2)
        if (gearDown) btns = btns or (1 shl 6)
        if (btnX)     btns = btns or (1 shl 12)
        if (btnY)     btns = btns or (1 shl 13)
        val lt = if (brakeOn) 0x7f.toByte() else 0x00.toByte()
        val rt = if (gasOn)   0x7f.toByte() else 0x00.toByte()
        hid.sendReport(device, 1,
            byteArrayOf(btns.toByte(), 0x00, tiltByte, 0x00, lt, rt))
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

    override fun onRequestPermissionsResult(
        req: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(req, perms, results)
        if (req == 99) setupHid()
    }
}

class WheelView(
    context: android.content.Context,
    attrs: android.util.AttributeSet? = null
) : View(context, attrs) {

    var angle: Float = 0f
        set(v) { field = v; invalidate() }

    private val pRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 12f
    }
    private val pHub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 7f
    }
    private val pSpoke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160,255,255,255)
        style = Paint.Style.STROKE
        strokeWidth = 9f
        strokeCap = Paint.Cap.ROUND
    }
    private val pDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val pArc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40,255,255,255)
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        val cx = width/2f
        val cy = height/2f
        val r  = minOf(width,height)/2f - 12f

        canvas.drawCircle(cx, cy, r, pRing)
        val arc = android.graphics.RectF(
            cx-r*0.6f, cy-r*0.6f, cx+r*0.6f, cy+r*0.6f)
        canvas.drawArc(arc, -90f, -angle, false, pArc)
        canvas.save()
        canvas.rotate(-angle, cx, cy)
        canvas.drawCircle(cx, cy, r*0.22f, pHub)
        for (i in 0..2) {
            val a = Math.toRadians(i*120.0 - 90.0)
            canvas.drawLine(
                cx+(r*0.22f*cos(a)).toFloat(),
                cy+(r*0.22f*sin(a)).toFloat(),
                cx+(r*cos(a)).toFloat(),
                cy+(r*sin(a)).toFloat(), pSpoke)
        }
        canvas.drawCircle(cx, cy-r+9f, 7f, pDot)
        canvas.drawCircle(cx, cy, 5f, pDot)
        canvas.restore()
    }
}
