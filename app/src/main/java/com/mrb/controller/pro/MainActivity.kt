package com.mrb.controller.pro

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
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

    // ── Bluetooth HID ──────────────────────────
    private var hidDevice: BluetoothHidDevice? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var connectedDevice: BluetoothDevice? = null

    // ── Sensor ─────────────────────────────────
    private lateinit var sensorManager: SensorManager
    private var tiltX = 0f
    private val alpha = 0.15f 
    private var filtX = 0f

    // ── State (Buttons) ────────────────────────
    private var gasOn    = false
    private var brakeOn  = false
    private var gearUp   = false
    private var gearDown = false

    // ── UI ─────────────────────────────────────
    private lateinit var page1: View
    private lateinit var page2: View
    private lateinit var tvBtStatus: TextView
    private lateinit var tvTopStatus: TextView
    private lateinit var wheelView: WheelView
    private lateinit var tiltBar: ProgressBar
    private var onPage2 = false
    private val cornerRadius = 24.dpToPx().toFloat()

    // ── TRUE GAMEPAD LOGIC (8 Buttons + X/Y Axes) ──
    private val HID_DESC = byteArrayOf(
        0x05.toByte(), 0x01.toByte(), // Usage Page: Generic Desktop
        0x09.toByte(), 0x05.toByte(), // Usage: Gamepad
        0xA1.toByte(), 0x01.toByte(), // Collection: Application
        0x85.toByte(), 0x01.toByte(), // Report ID: 1
        
        // 8 Buttons (1 Byte)
        0x05.toByte(), 0x09.toByte(), // Usage Page: Buttons
        0x19.toByte(), 0x01.toByte(), // Usage Min: 1
        0x29.toByte(), 0x08.toByte(), // Usage Max: 8
        0x15.toByte(), 0x00.toByte(), // Logical Min: 0
        0x25.toByte(), 0x01.toByte(), // Logical Max: 1
        0x75.toByte(), 0x01.toByte(), // Report Size: 1
        0x95.toByte(), 0x08.toByte(), // Report Count: 8
        0x81.toByte(), 0x02.toByte(), // Input: Data,Variable,Absolute
        
        // Analog Stick X & Y (2 Bytes)
        0x05.toByte(), 0x01.toByte(), // Usage Page: Generic Desktop
        0x09.toByte(), 0x30.toByte(), // Usage: X
        0x09.toByte(), 0x31.toByte(), // Usage: Y
        0x15.toByte(), 0x81.toByte(), // Logical Min: -127
        0x25.toByte(), 0x7F.toByte(), // Logical Max: 127
        0x75.toByte(), 0x08.toByte(), // Report Size: 8
        0x95.toByte(), 0x02.toByte(), // Report Count: 2
        0x81.toByte(), 0x02.toByte(), // Input: Data,Variable,Absolute
        
        0xC0.toByte()                 // End Collection
    )

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == BluetoothDevice.ACTION_ACL_CONNECTED) {
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

        // UI Setup
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

        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#0A0A0A")) }

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

    @SuppressLint("MissingPermission")
    private fun startHid() {
        if (bluetoothAdapter == null) return
        bluetoothAdapter?.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
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
                            if (!onPage2) switchToPage2()
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            connectedDevice = null
                            tvBtStatus.text = "○ Waiting to pair..."
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

    // ── DITTO PAGE 1 ──
    private fun buildPage1(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            setPadding(80.dpToPx(), 60.dpToPx(), 80.dpToPx(), 60.dpToPx())
        }

        val tvTitle = TextView(this).apply {
            text = "MRB Controller"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 4.dpToPx() }
        }

        tvBtStatus = TextView(this).apply {
            text = "○ Waiting to pair..."
            textSize = 14f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 40.dpToPx() }
        }

        val ivCog = TextView(this).apply {
            text = "⚙️"; textSize = 30f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20.dpToPx() }
        }

        val btnConnect = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = getRoundedRectDrawable(Color.WHITE, 16.dpToPx())
            setPadding(40.dpToPx(), 16.dpToPx(), 40.dpToPx(), 16.dpToPx())
            setOnClickListener {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                startActivity(intent)
            }
        }
        val ivLink = TextView(this).apply { text = "🔗"; textSize = 20f; gravity = Gravity.CENTER }
        val tvConnect = TextView(this).apply {
            text = " Connect"
            textSize = 18f; setTextColor(Color.BLACK); typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); gravity = Gravity.CENTER
        }
        btnConnect.addView(ivLink); btnConnect.addView(tvConnect)

        root.addView(tvTitle); root.addView(tvBtStatus); root.addView(ivCog); root.addView(btnConnect)
        return root
    }

    // ── DITTO PAGE 2 ──
    private fun buildPage2(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#0A0A0A")) }

        tvTopStatus = TextView(this).apply {
            text = "● TX: X=50, B=0"
            textSize = 12f; setTextColor(Color.WHITE); alpha = 0.5f; gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.TOP; topMargin = 10.dpToPx() }
        }

        wheelView = WheelView(this).apply {
            layoutParams = FrameLayout.LayoutParams(300.dpToPx(), 300.dpToPx()).apply { gravity = Gravity.CENTER }
        }

        val brakePedal = buildBtn("BRAKE ✋", Color.RED, 180.dpToPx(), 280.dpToPx()) { on -> brakeOn = on; sendHIDReport() }
        val brakeParams = FrameLayout.LayoutParams(180.dpToPx(), 280.dpToPx()).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL; setMargins(80.dpToPx(), 0, 0, 0) }
        brakePedal.layoutParams = brakeParams

        val gearsColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(180.dpToPx(), FrameLayout.LayoutParams.MATCH_PARENT).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL; setMargins(300.dpToPx(), 0, 0, 0) }
        }
        val btnGearDown = buildBtn("REVERSE ▼", Color.parseColor("#D500F9"), 180.dpToPx(), 120.dpToPx(), isGear = true) { on -> gearDown = on; sendHIDReport() }
        val btnGearUp = buildBtn("FRONT ▲", Color.parseColor("#00B0FF"), 180.dpToPx(), 120.dpToPx(), isGear = true) { on -> gearUp = on; sendHIDReport() }
        gearsColumn.addView(btnGearDown)
        gearsColumn.addView(Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, 20.dpToPx()) })
        gearsColumn.addView(btnGearUp)

        val gasPedal = buildBtn("GAS ⚡", Color.GREEN, 180.dpToPx(), 280.dpToPx()) { on -> gasOn = on; sendHIDReport() }
        val gasParams = FrameLayout.LayoutParams(180.dpToPx(), 280.dpToPx()).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; setMargins(0, 0, 80.dpToPx(), 0) }
        gasPedal.layoutParams = gasParams

        val bottomParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.BOTTOM; setPadding(100.dpToPx(), 0, 100.dpToPx(), 20.dpToPx()) }
        val bottomRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; layoutParams = bottomParams }
        tiltBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 50 
            val pParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 8.dpToPx())
            pParams.bottomMargin = 8.dpToPx()
            layoutParams = pParams
        }
        bottomRoot.addView(tiltBar)

        root.addView(tvTopStatus); root.addView(wheelView); root.addView(brakePedal); root.addView(gearsColumn); root.addView(gasPedal); root.addView(bottomRoot)
        return root
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildBtn(label: String, pressColor: Int, w: Int, h: Int, isGear: Boolean = false, onPress: (Boolean) -> Unit): View {
        val btn = FrameLayout(this)
        val text = TextView(this).apply {
            text = label
            textSize = if (isGear) 14f else 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER; letterSpacing = 0.1f
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        btn.background = getRoundedRectDrawable(Color.parseColor("#121212"), cornerRadius)
        btn.setPadding(20.dpToPx(), 10.dpToPx(), 20.dpToPx(), 10.dpToPx())
        btn.addView(text)

        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    btn.background = getRoundedPeddlePressDrawable(pressColor, cornerRadius)
                    text.setTextColor(Color.BLACK)
                    onPress(true)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    btn.background = getRoundedRectDrawable(Color.parseColor("#121212"), cornerRadius)
                    text.setTextColor(Color.WHITE)
                    onPress(false)
                    true
                }
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

    // ── GAMEPAD LOGIC ──
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER && onPage2) {
            val x = event.values[0] 
            filtX = alpha * x + (1 - alpha) * filtX
            tiltX = filtX

            val rotationAngle = tiltX * -10f
            wheelView.rotation = rotationAngle
            tiltBar.progress = (50 + (tiltX * 5)).toInt().coerceIn(0, 100)

            sendHIDReport()
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendHIDReport() {
        if (hidDevice == null || connectedDevice == null) return

        val joyX = (tiltX * 15f).toInt().coerceIn(-127, 127).toByte()
        
        var buttons: Byte = 0x00 
        if (gasOn)    buttons = (buttons.toInt() or 0x01).toByte() // Btn 1 (A)
        if (brakeOn)  buttons = (buttons.toInt() or 0x02).toByte() // Btn 2 (B)
        if (gearUp)   buttons = (buttons.toInt() or 0x04).toByte() // Btn 3 (X)
        if (gearDown) buttons = (buttons.toInt() or 0x08).toByte() // Btn 4 (Y)

        // Structure: Buttons(1 Byte), X(1 Byte), Y(1 Byte - Always 0)
        val report = byteArrayOf(buttons, joyX, 0x00.toByte())
        
        hidDevice?.sendReport(connectedDevice, 1, report)
        
        runOnUiThread { tvTopStatus.text = "● TX: X=$joyX, B=$buttons" }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onResume() { super.onResume()
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
    }
    override fun onPause() { super.onPause()
        sensorManager.unregisterListener(this)
    }
}

class WheelView(context: Context) : View(context) {
    private val paintWheel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 20f; color = Color.WHITE
    }
    private val paintSpokes = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.GRAY
    }
    private val paintInnerHub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.parseColor("#121212")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val outerRadius = min(cx, cy) - 20f
        val innerHubRadius = outerRadius * 0.2f

        canvas.drawCircle(cx, cy, innerHubRadius, paintInnerHub)

        for (i in 0..2) {
            canvas.drawRect(
                cx - innerHubRadius * 0.15f, cy - outerRadius * 1.0f,
                cx + innerHubRadius * 0.15f, cy + outerRadius * 1.0f,
                paintSpokes
            )
            canvas.save(); canvas.rotate(120f, cx, cy); canvas.restore()
        }

        canvas.drawCircle(cx, cy, outerRadius, paintWheel)
        
        val paintHubDetail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; color = Color.argb(100, 255, 255, 255)
        }
        canvas.drawCircle(cx, cy, innerHubRadius * 0.4f, paintHubDetail)
    }
}

fun Int.dpToPx(): Int = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()

fun getRoundedRectDrawable(color: Int, radius: Float): GradientDrawable {
    val gd = GradientDrawable()
    gd.setColor(color)
    gd.cornerRadius = radius
    return gd
}

fun getRoundedPeddlePressDrawable(color: Int, radius: Float): GradientDrawable {
    val gd = GradientDrawable()
    gd.setColor(color)
    gd.cornerRadius = radius
    gd.setStroke(4.dpToPx(), Color.argb(100, Color.red(color), Color.green(color), Color.blue(color)))
    return gd
}
