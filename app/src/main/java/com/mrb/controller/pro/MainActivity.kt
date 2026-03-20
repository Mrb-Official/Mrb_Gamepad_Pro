package com.mrb.controller.pro

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.*
import android.content.pm.ActivityInfo
import android.graphics.*
import android.hardware.*
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.*

class MainActivity : AppCompatActivity(), SensorEventListener {

    private var connectedDevice: BluetoothDevice? = null
    private lateinit var sensorManager: SensorManager

    private lateinit var txtStatus: TextView
    private lateinit var txtTilt: TextView
    private lateinit var tiltBar: ProgressBar
    private lateinit var wheelView: WheelView

    private var gasOn     = false
    private var brakeOn   = false
    private var gearUp    = false
    private var gearDown  = false
    private var btnA      = false
    private var btnB      = false
    private var btnX      = false
    private var btnY      = false
    private var dpadUp    = false
    private var dpadDown  = false
    private var dpadLeft  = false
    private var dpadRight = false
    private var tiltByte: Byte = 0
    private var filtX     = 0f
    private val alpha     = 0.15f
    private var lastSend  = 0L
    private var connectedAnimDone = false

    private val handler = Handler(Looper.getMainLooper())

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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.windowInsetsController?.apply {
            hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContentView(R.layout.activity_main)

        txtStatus = findViewById(R.id.txt_status)
        txtTilt   = findViewById(R.id.txt_tilt)
        tiltBar   = findViewById(R.id.tilt_bar)
        wheelView = findViewById(R.id.lay_steering)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        setupTouch(R.id.lay_brake, R.drawable.btn_normal_r12, R.drawable.btn_press_red,
            R.id.ic_brake, 0xFFFF4B4B.toInt()) { brakeOn = it }
        setupTouch(R.id.lay_gas, R.drawable.btn_normal_r12, R.drawable.btn_press_green,
            R.id.ic_gas, 0xFF3CFF6B.toInt()) { gasOn = it }
        setupTouch(R.id.lay_gear_up, R.drawable.btn_gear_normal, R.drawable.btn_press_orange,
            R.id.ic_gear_up, 0xFFFF6D00.toInt()) { gearUp = it }
        setupTouch(R.id.lay_gear_down, R.drawable.btn_gear_normal, R.drawable.btn_press_blue,
            R.id.ic_gear_down, 0xFF00B4D8.toInt()) { gearDown = it }
        setupTouch(R.id.btn_a, R.drawable.btn_xbox_green, R.drawable.btn_xbox_green_press,
            null, 0) { btnA = it }
        setupTouch(R.id.btn_b, R.drawable.btn_xbox_red, R.drawable.btn_xbox_red_press,
            null, 0) { btnB = it }
        setupTouch(R.id.btn_x, R.drawable.btn_xbox_blue, R.drawable.btn_xbox_blue_press,
            null, 0) { btnX = it }
        setupTouch(R.id.btn_y, R.drawable.btn_xbox_yellow, R.drawable.btn_xbox_yellow_press,
            null, 0) { btnY = it }
        setupTouch(R.id.btn_dpad_up, R.drawable.btn_normal_r12, R.drawable.btn_press_white,
            null, 0) { dpadUp = it }
        setupTouch(R.id.btn_dpad_down, R.drawable.btn_normal_r12, R.drawable.btn_press_white,
            null, 0) { dpadDown = it }
        setupTouch(R.id.btn_dpad_left, R.drawable.btn_normal_r12, R.drawable.btn_press_white,
            null, 0) { dpadLeft = it }
        setupTouch(R.id.btn_dpad_right, R.drawable.btn_normal_r12, R.drawable.btn_press_white,
            null, 0) { dpadRight = it }

        setupHid()
    }

    private fun setupHid() {
        connectedDevice = HidService.connectedDevice
        if (connectedDevice != null) {
            txtStatus.text = "● ${connectedDevice?.name}"
            txtStatus.setTextColor(Color.parseColor("#00FF88"))
            if (!connectedAnimDone) playConnectedAnim()
        } else {
            txtStatus.text = "Waiting for device..."
            txtStatus.setTextColor(Color.argb(100,255,255,255))
        }
        HidService.onDisconnected = {
            runOnUiThread {
                connectedDevice = null
                connectedAnimDone = false
                txtStatus.text = "Disconnected"
                txtStatus.setTextColor(Color.argb(100,255,255,255))
            }
        }
        HidService.onConnected = { device ->
            runOnUiThread {
                connectedDevice = device
                txtStatus.text = "● ${device.name}"
                txtStatus.setTextColor(Color.parseColor("#00FF88"))
                if (!connectedAnimDone) playConnectedAnim()
            }
        }
    }

    private fun playConnectedAnim() {
        connectedAnimDone = true
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val phase1Steps = 50
        val phase2Steps = 50
        val phase3Steps = 25
        var step = 0

        fun haptic(ms: Long) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(
                    ms, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }

        val r = object : Runnable {
            override fun run() {
                val total = phase1Steps + phase2Steps + phase3Steps
                when {
                    step < phase1Steps -> {
                        val p = step.toFloat() / phase1Steps
                        val eased = if (p < 0.5f) 2f*p*p else 1f-2f*(1f-p)*(1f-p)
                        wheelView.angle = -(eased * 360f)
                        if (step == 0 || step == phase1Steps/2) haptic(40)
                    }
                    step < phase1Steps + phase2Steps -> {
                        val p = (step - phase1Steps).toFloat() / phase2Steps
                        val eased = if (p < 0.5f) 2f*p*p else 1f-2f*(1f-p)*(1f-p)
                        wheelView.angle = (eased * 360f)
                        if (step == phase1Steps || step == phase1Steps + phase2Steps/2) haptic(40)
                    }
                    step < total -> {
                        val p = (step - phase1Steps - phase2Steps).toFloat() / phase3Steps
                        val eased = 1f - (1f-p)*(1f-p)
                        wheelView.angle = 360f * (1f - eased)
                    }
                    else -> {
                        wheelView.angle = 0f
                        haptic(80)
                        return
                    }
                }
                step++
                handler.postDelayed(this, 25)
            }
        }
        handler.post(r)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouch(
        id: Int, normalRes: Int, pressRes: Int,
        iconId: Int?, pressIconColor: Int,
        onPress: (Boolean) -> Unit
    ) {
        val view = findViewById<View>(id) ?: return
        val icon = iconId?.let { findViewById<ImageView>(it) }
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    onPress(true)
                    view.setBackgroundResource(pressRes)
                    if (pressIconColor != 0) icon?.setColorFilter(pressIconColor)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(
                            30, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(30)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    onPress(false)
                    view.setBackgroundResource(normalRes)
                    icon?.setColorFilter(Color.parseColor("#888888"))
                    true
                }
                else -> false
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        filtX = alpha * event.values[1] + (1 - alpha) * filtX
        tiltByte = (filtX / 10f * 127f).toInt().coerceIn(-127, 127).toByte()
        val now = System.currentTimeMillis()
        if (now - lastSend > 40) { sendReport(); lastSend = now }
        runOnUiThread {
            wheelView.angle = -(filtX / 10f * 90f).coerceIn(-90f, 90f)
            txtTilt.text = "%.1f°".format(filtX * 9f)
            tiltBar.progress = (100 + (filtX/10f*100).toInt()).coerceIn(0, 200)
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendReport() {
        val device = HidService.connectedDevice ?: return
        val hid    = HidService.hidDevice ?: return

        var btnByte1 = 0
        var btnByte2 = 0

        if (btnA)     btnByte1 = btnByte1 or (1 shl 0)
        if (btnB)     btnByte1 = btnByte1 or (1 shl 1)
        if (gearDown) btnByte1 = btnByte1 or (1 shl 6)
        if (gearUp)   btnByte1 = btnByte1 or (1 shl 7)
        if (btnY)     btnByte1 = btnByte1 or (1 shl 4)
        if (btnX)     btnByte2 = btnByte2 or (1 shl 6)
        if (dpadUp)   btnByte2 = btnByte2 or (1 shl 2)
        if (dpadDown) btnByte2 = btnByte2 or (1 shl 3)
        if (dpadLeft) btnByte2 = btnByte2 or (1 shl 6)
        if (dpadRight)btnByte2 = btnByte2 or (1 shl 5)

        val gas   = if (gasOn)   0xFF.toByte() else 0x00.toByte()
        val brake = if (brakeOn) 0xFF.toByte() else 0x00.toByte()

        hid.sendReport(device, 1,
            byteArrayOf(btnByte1.toByte(), btnByte2.toByte(), tiltByte, 0x00, gas, brake))
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_GAME)
        connectedDevice = HidService.connectedDevice
        if (connectedDevice != null) {
            txtStatus.text = "● ${connectedDevice?.name}"
            txtStatus.setTextColor(Color.parseColor("#00FF88"))
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.decorView.windowInsetsController?.apply {
            hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}

class WheelView(
    context: android.content.Context,
    attrs: android.util.AttributeSet? = null
) : View(context, attrs) {

    var angle: Float = 0f
        set(v) { field = v; invalidate() }

    // Outer ring
    private val pRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A2A2A")
        style = Paint.Style.STROKE
        strokeWidth = 28f
    }
    // Outer ring highlight
    private val pRingHL = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#444444")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    // Spokes
    private val pSpoke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#222222")
        style = Paint.Style.STROKE
        strokeWidth = 18f
        strokeCap = Paint.Cap.ROUND
    }
    private val pSpokeHL = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }
    // Center hub
    private val pHub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A1A")
        style = Paint.Style.FILL
    }
    private val pHubBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    // Center circle
    private val pCenter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111111")
        style = Paint.Style.FILL
    }
    // Grip texture on ring
    private val pGrip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A1A")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r  = minOf(width, height) / 2f - 20f

        canvas.save()
        canvas.rotate(angle, cx, cy)

        // Outer ring shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0A0A0A")
            style = Paint.Style.STROKE
            strokeWidth = 32f
        }
        canvas.drawCircle(cx, cy + 3f, r, shadowPaint)

        // Main outer ring
        canvas.drawCircle(cx, cy, r, pRing)

        // Ring outer highlight
        val pRingOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#555555")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawCircle(cx, cy, r + 12f, pRingOuter)

        // Ring inner highlight
        canvas.drawCircle(cx, cy, r - 12f, pRingHL)

        // Grip lines on ring (3 groups)
        for (g in 0..2) {
            val baseAngle = g * 120.0
            for (i in -2..2) {
                val a = Math.toRadians(baseAngle + i * 8.0)
                val innerR = r - 14f
                val outerR = r + 10f
                canvas.drawLine(
                    cx + (innerR * sin(a)).toFloat(),
                    cy - (innerR * cos(a)).toFloat(),
                    cx + (outerR * sin(a)).toFloat(),
                    cy - (outerR * cos(a)).toFloat(),
                    pGrip)
            }
        }

        // 3 Spokes
        val hubR = r * 0.28f
        val spokeAngles = listOf(-90.0, 30.0, 150.0)
        for (sa in spokeAngles) {
            val a = Math.toRadians(sa)
            val startX = cx + (hubR * cos(a)).toFloat()
            val startY = cy + (hubR * sin(a)).toFloat()
            val endX   = cx + (r * 0.72f * cos(a)).toFloat()
            val endY   = cy + (r * 0.72f * sin(a)).toFloat()

            // Spoke shadow
            val pSpokeShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0A0A0A")
                style = Paint.Style.STROKE
                strokeWidth = 22f
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(startX, startY + 2f, endX, endY + 2f, pSpokeShadow)

            // Main spoke
            canvas.drawLine(startX, startY, endX, endY, pSpoke)
            // Spoke highlight
            canvas.drawLine(startX, startY, endX, endY, pSpokeHL)
        }

        // Hub background
        canvas.drawCircle(cx, cy, hubR + 8f, pHub)
        canvas.drawCircle(cx, cy, hubR + 8f, pHubBorder)

        // Center circle
        canvas.drawCircle(cx, cy, hubR, pCenter)

        // Center ring detail
        val pCenterRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2A2A2A")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawCircle(cx, cy, hubR * 0.7f, pCenterRing)
        canvas.drawCircle(cx, cy, hubR * 0.4f, pCenterRing)

        canvas.restore()
    }
}
