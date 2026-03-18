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
import androidx.core.graphics.PathParser
import kotlin.math.*

class MainActivity : AppCompatActivity(), SensorEventListener {

    private var hidDevice: BluetoothHidDevice? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var connectedDevice: BluetoothDevice? = null

    private lateinit var sensorManager: SensorManager
    private var tiltAngle = 0f
    private val alpha = 0.15f 
    private var filtAngle = 0f

    private var gasOn    = false
    private var brakeOn  = false
    private var gearUp   = false
    private var gearDown = false

    private lateinit var page1: View
    private lateinit var page2: View
    private lateinit var tvBtStatus: TextView
    private lateinit var tvTopStatus: TextView
    private lateinit var wheelView: WheelView
    private lateinit var tiltBar: ProgressBar
    private var onPage2 = false
    private val cornerRadius = 16.dpToPx().toFloat()

    // ── GAMEPAD DESCRIPTOR ──
    private val HID_DESC = byteArrayOf(
        0x05.toByte(), 0x01.toByte(), 0x09.toByte(), 0x05.toByte(), 0xA1.toByte(), 0x01.toByte(), 
        0x85.toByte(), 0x01.toByte(), 0x05.toByte(), 0x09.toByte(), 0x19.toByte(), 0x01.toByte(), 
        0x29.toByte(), 0x08.toByte(), 0x15.toByte(), 0x00.toByte(), 0x25.toByte(), 0x01.toByte(), 
        0x75.toByte(), 0x01.toByte(), 0x95.toByte(), 0x08.toByte(), 0x81.toByte(), 0x02.toByte(), 
        0x05.toByte(), 0x01.toByte(), 0x09.toByte(), 0x30.toByte(), 0x09.toByte(), 0x31.toByte(), 
        0x15.toByte(), 0x81.toByte(), 0x25.toByte(), 0x7F.toByte(), 0x75.toByte(), 0x08.toByte(), 
        0x95.toByte(), 0x02.toByte(), 0x81.toByte(), 0x02.toByte(), 0xC0.toByte()                 
    )

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                runOnUiThread {
                    tvBtStatus.text = "● Auto-Connected!"
                    tvBtStatus.setTextColor(Color.parseColor("#4CAF50"))
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
        
        window.decorView.setBackgroundColor(Color.parseColor("#080808"))
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        val filter = IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED)
        registerReceiver(btReceiver, filter)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#080808")) }

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
                            tvBtStatus.text = "● Connected"
                            tvBtStatus.setTextColor(Color.parseColor("#4CAF50"))
                            if (!onPage2) switchToPage2()
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            connectedDevice = null
                            tvBtStatus.text = "○ Waiting to pair..."
                            tvBtStatus.setTextColor(Color.GRAY)
                            if (onPage2) {
                                onPage2 = false
                                page1.visibility = View.VISIBLE
                                page1.animate().alpha(1f).setDuration(500L).start()
                                page2.animate().alpha(0f).setDuration(300L).withEndAction { page2.visibility = View.GONE }.start()
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
            setBackgroundColor(Color.parseColor("#080808"))
        }

        val tvTitle = TextView(this).apply {
            text = "MRB Controller"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8.dpToPx() }
        }

        tvBtStatus = TextView(this).apply {
            text = "Waiting to pair •••"
            textSize = 14f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 40.dpToPx() }
        }

        val btnConnect = Button(this).apply {
            text = "CONNECT BLUETOOTH"
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.BLACK)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(40.dpToPx(), 16.dpToPx(), 40.dpToPx(), 16.dpToPx())
            setOnClickListener { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        }

        root.addView(tvTitle); root.addView(tvBtStatus); root.addView(btnConnect)
        return root
    }

    private fun buildPage2(): View {
        val root = RelativeLayout(this).apply { setBackgroundColor(Color.parseColor("#080808")) }

        val topStatusContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_TOP)
                addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                setMargins(40.dpToPx(), 20.dpToPx(), 0, 0)
            }
        }
        val greenDot = View(this).apply {
            background = getSolidDrawable(Color.parseColor("#4CAF50"), Color.TRANSPARENT, 10f)
            layoutParams = LinearLayout.LayoutParams(8.dpToPx(), 8.dpToPx()).apply { rightMargin = 8.dpToPx() }
        }
        tvTopStatus = TextView(this).apply {
            text = "7"
            textSize = 14f; setTextColor(Color.parseColor("#555555")); typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        topStatusContainer.addView(greenDot); topStatusContainer.addView(tvTopStatus)

        // BRAKE (150x200)
        val brakePedal = buildGameBtn("BRAKE", Color.parseColor("#D32F2F"), getHandPath(), 150, 200) { on -> brakeOn = on; sendHIDReport() }
        val brakeParams = RelativeLayout.LayoutParams(150.dpToPx(), 200.dpToPx()).apply {
            addRule(RelativeLayout.ALIGN_PARENT_LEFT)
            addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            setMargins(20.dpToPx(), 0, 0, 40.dpToPx())
        }
        brakePedal.layoutParams = brakeParams

        // GAS (150x200)
        val gasPedal = buildGameBtn("GAS", Color.parseColor("#388E3C"), getSpeedoPath(), 150, 200) { on -> gasOn = on; sendHIDReport() }
        gasPedal.id = View.generateViewId() 
        val gasParams = RelativeLayout.LayoutParams(150.dpToPx(), 200.dpToPx()).apply {
            addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            setMargins(0, 0, 20.dpToPx(), 40.dpToPx())
        }
        gasPedal.layoutParams = gasParams

        // GEARS (120x80 each)
        val gearsStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            layoutParams = RelativeLayout.LayoutParams(120.dpToPx(), RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.LEFT_OF, gasPedal.id)
                addRule(RelativeLayout.ALIGN_BOTTOM, gasPedal.id) 
                setMargins(0, 0, 20.dpToPx(), 0) 
            }
        }
        val btnGearDown = buildGameBtn("REVERSE", Color.parseColor("#F57C00"), getUpArrowPath(), 120, 80) { on -> gearDown = on; sendHIDReport() }
        val btnGearUp = buildGameBtn("FRONT", Color.parseColor("#1976D2"), getDownArrowPath(), 120, 80) { on -> gearUp = on; sendHIDReport() }
        
        gearsStack.addView(btnGearDown)
        gearsStack.addView(Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, 16.dpToPx()) }) 
        gearsStack.addView(btnGearUp)

        // STEERING WHEEL
        wheelView = WheelView(this).apply {
            layoutParams = RelativeLayout.LayoutParams(260.dpToPx(), 260.dpToPx()).apply { 
                addRule(RelativeLayout.CENTER_IN_PARENT) 
            }
        }

        // TILT BAR
        tiltBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 50 
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#9E9D24")) 
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#222222"))
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, 4.dpToPx()).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                setMargins(40.dpToPx(), 0, 40.dpToPx(), 16.dpToPx())
            }
        }

        root.addView(topStatusContainer)
        root.addView(wheelView)
        root.addView(brakePedal)
        root.addView(gasPedal)
        root.addView(gearsStack)
        root.addView(tiltBar)
        
        return root
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildGameBtn(label: String, activeThemeColor: Int, pathData: String, wDp: Int, hDp: Int, onPress: (Boolean) -> Unit): View {
        val btn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val baseColor = Color.parseColor("#333333") 
        val baseTextColor = Color.parseColor("#666666") 
        val activeBgColor = Color.argb(40, Color.red(activeThemeColor), Color.green(activeThemeColor), Color.blue(activeThemeColor))

        btn.background = getWireframeDrawable(baseColor, cornerRadius)

        val iconSize = (min(wDp, hDp) * 0.35f).toInt()
        val ivIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize.dpToPx(), iconSize.dpToPx()).apply { bottomMargin = 8.dpToPx() }
            setImageDrawable(getVectorDrawable(this@MainActivity, iconSize, baseTextColor, pathData)) // <--- FIX HERE
        }

        val tvLabel = TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(baseTextColor)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.1f
        }

        btn.addView(ivIcon); btn.addView(tvLabel)

        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    btn.background = getSolidDrawable(activeBgColor, activeThemeColor, cornerRadius)
                    ivIcon.setImageDrawable(getVectorDrawable(this@MainActivity, iconSize, activeThemeColor, pathData)) // <--- FIX HERE
                    tvLabel.setTextColor(activeThemeColor)
                    onPress(true)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    btn.background = getWireframeDrawable(baseColor, cornerRadius)
                    ivIcon.setImageDrawable(getVectorDrawable(this@MainActivity, iconSize, baseTextColor, pathData)) // <--- FIX HERE
                    tvLabel.setTextColor(baseTextColor)
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
        page2.animate().alpha(1f).setDuration(500L).start()
        page1.animate().alpha(0f).setDuration(300L).withEndAction { page1.visibility = View.GONE }.start()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER && onPage2) {
            val yAxisTilt = event.values[1] 
            
            filtAngle = alpha * yAxisTilt + (1 - alpha) * filtAngle
            tiltAngle = filtAngle

            val rotationAngle = tiltAngle * 10f
            wheelView.rotation = rotationAngle
            tiltBar.progress = (50 + (tiltAngle * 5)).toInt().coerceIn(0, 100)

            sendHIDReport()
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendHIDReport() {
        if (hidDevice == null || connectedDevice == null) return

        val joyX = (tiltAngle * 15f).toInt().coerceIn(-127, 127).toByte()
        
        var buttons: Byte = 0x00 
        if (gasOn)    buttons = (buttons.toInt() or 0x01).toByte() 
        if (brakeOn)  buttons = (buttons.toInt() or 0x02).toByte() 
        if (gearUp)   buttons = (buttons.toInt() or 0x04).toByte() 
        if (gearDown) buttons = (buttons.toInt() or 0x08).toByte() 

        val report = byteArrayOf(buttons, joyX, 0x00.toByte())
        hidDevice?.sendReport(connectedDevice, 1, report)
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
        style = Paint.Style.STROKE; strokeWidth = 16f; color = Color.WHITE
    }
    private val paintSpokes = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 10f; color = Color.parseColor("#333333")
    }
    private val paintHub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 8f; color = Color.WHITE
    }
    private val paintDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.WHITE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f
        val radius = min(cx, cy) - 16f
        val hubRadius = radius * 0.25f
        
        for (i in 0..2) {
            canvas.drawLine(cx, cy - hubRadius, cx, cy - radius, paintSpokes)
            canvas.save(); canvas.rotate(120f, cx, cy); canvas.restore()
        }
        
        canvas.drawCircle(cx, cy, radius, paintWheel) 
        canvas.drawCircle(cx, cy, hubRadius, paintHub) 
        canvas.drawCircle(cx, cy - radius + 12f, 10f, paintDot) 
    }
}

fun Int.dpToPx(): Int = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()

fun getWireframeDrawable(strokeColor: Int, radius: Float): GradientDrawable {
    val gd = GradientDrawable()
    gd.setColor(Color.TRANSPARENT)
    gd.setStroke(2.dpToPx(), strokeColor)
    gd.cornerRadius = radius
    return gd
}

fun getSolidDrawable(fillColor: Int, strokeColor: Int, radius: Float): GradientDrawable {
    val gd = GradientDrawable()
    gd.setColor(fillColor)
    gd.setStroke(3.dpToPx(), strokeColor)
    gd.cornerRadius = radius
    return gd
}

fun getHandPath() = "M21,12V6A2,2 0 0,0 19,4A2,2 0 0,0 17,6V7.5A2,2 0 0,0 15,5.5A2,2 0 0,0 13,7.5V8.5A2,2 0 0,0 11,6.5A2,2 0 0,0 9,8.5V13L6.87,12.44C6.55,12.35 6.2,12.41 5.95,12.6L4.5,13.88L9.62,19.82C10.15,20.44 10.93,20.8 11.76,20.8H17C18.66,20.8 20,19.46 20,17.8V12A2,2 0 0,0 22,10A2,2 0 0,0 21,12Z"
fun getSpeedoPath() = "M12,4C7.58,4 4,7.58 4,12C4,14.05 4.78,15.92 6.06,17.34L7.48,15.92C6.55,14.89 6,13.5 6,12C6,8.69 8.69,6 12,6C15.31,6 18,8.69 18,12C18,13.5 17.45,14.89 16.52,15.92L17.94,17.34C19.22,15.92 20,14.05 20,12C20,7.58 16.42,4 12,4ZM12.5,8V12L15,14.5L13.5,16L10.5,12.5V8H12.5Z"
fun getUpArrowPath() = "M7.41,15.41L12,10.83L16.59,15.41L18,14L12,8L6,14L7.41,15.41Z"
fun getDownArrowPath() = "M7.41,8.59L12,13.17L16.59,8.59L18,10L12,16L6,10L7.41,8.59Z"

fun getVectorDrawable(context: Context, sizeDp: Int, color: Int, pathData: String): android.graphics.drawable.Drawable {
    val sizePx = sizeDp.dpToPx()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color }
    
    try {
        val path = PathParser.createPathFromPathData(pathData)
        val matrix = Matrix()
        matrix.postScale(sizePx / 24f, sizePx / 24f) 
        path.transform(matrix)
        canvas.drawPath(path, paint)
    } catch (e: Exception) {
        canvas.drawCircle(sizePx/2f, sizePx/2f, sizePx/3f, paint)
    }
    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}
