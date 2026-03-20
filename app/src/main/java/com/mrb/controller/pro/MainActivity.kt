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
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

class MainActivity : AppCompatActivity(), SensorEventListener {

    private var connectedDevice: BluetoothDevice? = null
    private lateinit var sensorManager: SensorManager
    private lateinit var rootFrame: FrameLayout
    private lateinit var gameArea: FrameLayout
    private lateinit var wheelView: ImageView
    private lateinit var txtStatus: TextView
    private lateinit var txtTilt: TextView
    private lateinit var tiltBar: ProgressBar

    // Button states
    private val btnStates = mutableMapOf<String, Boolean>()

    private var tiltByte: Byte = 0
    private var filtX = 0f
    private var filtXUI = 0f
    private val alpha = 0.15f
    private var lastSend = 0L
    private var connectedAnimDone = false
    private var animPlaying = false
    private var editMode = false

    private val handler = Handler(Looper.getMainLooper())

    // All button definitions
    val allButtons by lazy { listOf(
        BtnDef("gas",        "GAS",      R.drawable.ic_btn_gas,        0xFF00C853.toInt(), isGas = true),
        BtnDef("brake",      "BRAKE",    R.drawable.ic_btn_brake,      0xFFD50000.toInt(), isBrake = true),
        BtnDef("gear_up",    "REVERSE",  R.drawable.expand_circle_up_24,   0xFFFF6D00.toInt(), byte1bit = 7),
        BtnDef("gear_down",  "FRONT",    R.drawable.expand_circle_down_24, 0xFF00B4D8.toInt(), byte1bit = 6),
        BtnDef("btn_a",      "A",        R.drawable.ic_btn_a,          0xFF3CFF6B.toInt(), byte1bit = 0),
        BtnDef("btn_b",      "B",        R.drawable.ic_btn_b,          0xFFFF4B4B.toInt(), byte1bit = 1),
        BtnDef("btn_x",      "X",        R.drawable.ic_btn_x,          0xFF3DB9FF.toInt(), byte1bit = 4),
        BtnDef("btn_y",      "Y",        R.drawable.ic_btn_y,          0xFFFFD700.toInt(), byte1bit = 3),
        BtnDef("dpad_up",    "UP",       R.drawable.ic_arrow_up,       0xFFFFFFFF.toInt(), byte2bit = 2),
        BtnDef("dpad_down",  "DOWN",     R.drawable.ic_arrow_down,     0xFFFFFFFF.toInt(), byte2bit = 3),
        BtnDef("dpad_left",  "LEFT",     R.drawable.ic_arrow_left,     0xFFFFFFFF.toInt(), byte2bit = 6),
        BtnDef("dpad_right", "RIGHT",    R.drawable.ic_arrow_right,    0xFFFFFFFF.toInt(), byte2bit = 5),
        BtnDef("horn",       "HORN",     R.drawable.ic_btn_horn,       0xFFFFEB3B.toInt(), byte1bit = 2),
        BtnDef("handbrake",  "H.BRAKE",  R.drawable.ic_btn_handbrake,  0xFFFF5722.toInt(), byte1bit = 5),
        BtnDef("camera",     "CAM",      R.drawable.ic_btn_camera,     0xFF9C27B0.toInt(), byte2bit = 0),
        BtnDef("nitro",      "NITRO",    R.drawable.ic_btn_nitro,      0xFF00BCD4.toInt(), byte2bit = 1),
        BtnDef("lights",     "LIGHTS",   R.drawable.ic_btn_lights,     0xFFFFC107.toInt(), byte2bit = 4),
        BtnDef("look_left",  "LOOK←",   R.drawable.ic_btn_look_left,  0xFF795548.toInt(), byte2bit = 7),
        BtnDef("look_right", "LOOK→",   R.drawable.ic_btn_look_right, 0xFF8D6E63.toInt(), byte2bit = 8),
        BtnDef("l1",         "L1",       R.drawable.ic_btn_l1,         0xFF3F51B5.toInt()),
        BtnDef("r1",         "R1",       R.drawable.ic_btn_r1,         0xFF3F51B5.toInt()),
        BtnDef("start",      "START",    R.drawable.ic_btn_start,      0xFF4CAF50.toInt()),
        BtnDef("select",     "SELECT",   R.drawable.ic_btn_select,     0xFF388E3C.toInt()),
        BtnDef("reset",      "RESET",    R.drawable.ic_btn_reset,      0xFFF44336.toInt()),
        BtnDef("pause",      "PAUSE",    R.drawable.ic_btn_pause,      0xFF9E9E9E.toInt()),
        BtnDef("hazard",     "HAZARD",   R.drawable.ic_btn_hazard,     0xFFFF9800.toInt()),
        BtnDef("boost",      "BOOST",    R.drawable.ic_btn_boost,      0xFF00E5FF.toInt()),
        BtnDef("wipers",     "WIPERS",   R.drawable.ic_btn_wipers,     0xFF607D8B.toInt()),
        BtnDef("map",        "MAP",      R.drawable.ic_btn_map,        0xFF26C6DA.toInt()),
        BtnDef("custom1",    "BTN 1",    R.drawable.ic_btn_custom,     0xFFE91E63.toInt()),
        BtnDef("custom2",    "BTN 2",    R.drawable.ic_btn_custom,     0xFFAD1457.toInt()),
        BtnDef("custom3",    "BTN 3",    R.drawable.ic_btn_custom,     0xFF880E4F.toInt()),
        BtnDef("custom4",    "BTN 4",    R.drawable.ic_btn_custom,     0xFFE040FB.toInt()),
        BtnDef("custom5",    "BTN 5",    R.drawable.ic_btn_custom,     0xFF7B1FA2.toInt()),
        BtnDef("custom6",    "BTN 6",    R.drawable.ic_btn_custom,     0xFF4A148C.toInt()),
        BtnDef("custom7",    "BTN 7",    R.drawable.ic_btn_custom,     0xFF6200EA.toInt()),
        BtnDef("custom8",    "BTN 8",    R.drawable.ic_btn_custom,     0xFF311B92.toInt()),
    )}

    // Default positions (landscape 1280x720 approx)
    private val defaultLayout = listOf(
        PlacedBtn("brake",      10f,  260f, 150, 200),
        PlacedBtn("gas",        1120f,260f, 150, 200),
        PlacedBtn("gear_up",    970f, 260f, 140, 95),
        PlacedBtn("gear_down",  970f, 365f, 140, 95),
        PlacedBtn("btn_a",      1170f,170f,  50,  50),
        PlacedBtn("btn_b",      1220f,120f,  50,  50),
        PlacedBtn("btn_x",      1120f,120f,  50,  50),
        PlacedBtn("btn_y",      1170f, 70f,  50,  50),
        PlacedBtn("dpad_up",    50f,   70f,  50,  50),
        PlacedBtn("dpad_down",  50f,  170f,  50,  50),
        PlacedBtn("dpad_left",   0f,  120f,  50,  50),
        PlacedBtn("dpad_right", 100f, 120f,  50,  50),
    )

    data class PlacedBtn(
        val id: String,
        var x: Float,
        var y: Float,
        var w: Int,
        var h: Int
    )

    private val placedButtons = mutableListOf<PlacedBtn>()
    private val placedViews = mutableMapOf<String, FrameLayout>()

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
            if (missing.isNotEmpty()) {
                requestPermissions(missing.toTypedArray(), 99)
                return
            }
        }
        initUI()
    }

    override fun onRequestPermissionsResult(
        req: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(req, perms, results)
        if (req == 99) initUI()
    }

    private fun initUI() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.windowInsetsController?.apply {
            hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        rootFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#080808"))
        }

        gameArea = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT)
        }

        // Steering wheel
        wheelView = ImageView(this).apply {
            setImageResource(R.drawable.ic_steering_wheel)
            layoutParams = FrameLayout.LayoutParams(220, 220).apply {
                gravity = Gravity.CENTER
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        gameArea.addView(wheelView)

        // Status + tilt bar overlay
        val centerOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT)
        }

        txtStatus = TextView(this).apply {
            text = "Waiting..."
            textSize = 10f
            setTextColor(Color.argb(100, 255, 255, 255))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8
            }
        }

        // Spacer for wheel
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, 240)
        }

        tiltBar = ProgressBar(this, null,
            android.R.attr.progressBarStyleHorizontal).apply {
            max = 200; progress = 100
            layoutParams = LinearLayout.LayoutParams(
                300, 6).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 8
            }
        }

        txtTilt = TextView(this).apply {
            text = "0.0°"
            textSize = 10f
            setTextColor(Color.argb(120, 255, 255, 255))
            gravity = Gravity.CENTER
            typeface = Typeface.create("monospace", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 4
            }
        }

        centerOverlay.addView(txtStatus)
        centerOverlay.addView(spacer)
        centerOverlay.addView(tiltBar)
        centerOverlay.addView(txtTilt)
        gameArea.addView(centerOverlay)

        // Edit button - top center
        val btnEdit = TextView(this).apply {
            text = "⚙"
            textSize = 14f
            setTextColor(Color.argb(80, 255, 255, 255))
            setPadding(16, 8, 16, 8)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = 8
            }
            setOnClickListener { toggleEditMode() }
        }
        gameArea.addView(btnEdit)

        rootFrame.addView(gameArea)
        setContentView(rootFrame)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Load layout then render
        loadLayout()
        setupHid()
    }

    private fun toggleEditMode() {
        editMode = !editMode
        if (editMode) showEditBar() else hideEditBar()
        // Rebuild all buttons
        placedButtons.forEach { pb ->
            placedViews[pb.id]?.let { v ->
                v.removeAllViews()
                val def = allButtons.find { it.id == pb.id } ?: return@forEach
                buildBtnView(def, pb, v)
            }
        }
    }

    private var editBar: View? = null

    private fun showEditBar() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#CC111111"))
            setPadding(8, 0, 8, 0)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 48).apply {
                gravity = Gravity.BOTTOM
            }
            tag = "edit_bar"
        }

        val btnAdd = barBtn("＋ ADD", "#00C853") { showPicker() }
        val btnSave = barBtn("💾 SAVE", "#2196F3") {
            saveLayout()
            editMode = false
            hideEditBar()
            placedButtons.forEach { pb ->
                placedViews[pb.id]?.let { v ->
                    v.removeAllViews()
                    val def = allButtons.find { it.id == pb.id } ?: return@forEach
                    buildBtnView(def, pb, v)
                }
            }
        }
        val btnReset = barBtn("RESET", "#F44336") { confirmReset() }
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        val tvHint = TextView(this).apply {
            text = "✏ Drag to move  ⤡ Corner to resize  ✕ to delete"
            textSize = 9f
            setTextColor(Color.argb(120, 255, 255, 255))
            gravity = Gravity.CENTER
        }

        bar.addView(btnAdd)
        bar.addView(btnSave)
        bar.addView(btnReset)
        bar.addView(spacer)
        bar.addView(tvHint)

        gameArea.addView(bar)
        editBar = bar
    }

    private fun hideEditBar() {
        editBar?.let { gameArea.removeView(it) }
        editBar = null
    }

    private fun barBtn(text: String, color: String, onClick: () -> Unit) =
        TextView(this).apply {
            this.text = text
            textSize = 10f
            setTextColor(Color.parseColor(color))
            setPadding(10, 4, 10, 4)
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(4, 0, 4, 0)
            }
        }

    private fun showPicker() {
        val scroll = ScrollView(this)
        val grid = GridLayout(this).apply {
            columnCount = 5
            setPadding(8, 8, 8, 8)
        }

        allButtons.forEach { btn ->
            val placed = placedButtons.any { it.id == btn.id }
            val chip = FrameLayout(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 130; height = 90
                    setMargins(4, 4, 4, 4)
                }
                setBackgroundColor(
                    if (placed) Color.parseColor("#1A1A1A")
                    else Color.argb(160,
                        Color.red(btn.pressColor),
                        Color.green(btn.pressColor),
                        Color.blue(btn.pressColor)))
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, 12f)
                    }
                }
                clipToOutline = true
            }

            val iconIv = ImageView(this).apply {
                setImageResource(btn.iconRes)
                setColorFilter(
                    if (placed) Color.parseColor("#444444") else Color.WHITE)
                layoutParams = FrameLayout.LayoutParams(36, 36).apply {
                    gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                    topMargin = 10
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            val labelTv = TextView(this).apply {
                text = btn.label
                textSize = 9f
                setTextColor(
                    if (placed) Color.parseColor("#444444") else Color.WHITE)
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.BOTTOM
                    bottomMargin = 4
                }
            }

            chip.addView(iconIv)
            chip.addView(labelTv)

            if (!placed) {
                chip.setOnClickListener {
                    // Add to center of screen
                    val cx = (gameArea.width / 2 - 35).toFloat()
                    val cy = (gameArea.height / 2 - 60).toFloat()
                    addButton(btn, cx, cy)
                }
            }
            grid.addView(chip)
        }

        scroll.addView(grid)
        android.app.AlertDialog.Builder(this)
            .setTitle("Add Button")
            .setView(scroll)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun addButton(def: BtnDef, x: Float, y: Float,
        w: Int = 70, h: Int = 120) {
        if (placedButtons.any { it.id == def.id }) return
        val pb = PlacedBtn(def.id, x, y, w, h)
        placedButtons.add(pb)
        val container = FrameLayout(this).apply {
            this.x = x; this.y = y
            layoutParams = FrameLayout.LayoutParams(w, h)
        }
        buildBtnView(def, pb, container)
        gameArea.addView(container)
        placedViews[def.id] = container
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildBtnView(def: BtnDef, pb: PlacedBtn, container: FrameLayout) {
        container.setBackgroundColor(Color.parseColor("#111111"))
        container.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 16f)
            }
        }
        container.clipToOutline = true

        val iconIv = ImageView(this).apply {
            setImageResource(def.iconRes)
            setColorFilter(Color.parseColor("#888888"))
            layoutParams = FrameLayout.LayoutParams(
                (pb.w * 0.5f).toInt(),
                (pb.h * 0.4f).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                topMargin = (pb.h * 0.15f).toInt()
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val labelTv = TextView(this).apply {
            text = def.label
            textSize = 9f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = 6
            }
        }

        container.addView(iconIv)
        container.addView(labelTv)

        if (editMode) {
            // Delete
            val delBtn = TextView(this).apply {
                text = "✕"
                textSize = 9f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setBackgroundColor(Color.argb(220, 200, 0, 0))
                layoutParams = FrameLayout.LayoutParams(26, 26).apply {
                    gravity = Gravity.TOP or Gravity.END
                }
                setOnClickListener {
                    gameArea.removeView(container)
                    placedViews.remove(def.id)
                    placedButtons.removeAll { it.id == def.id }
                }
            }

            // Resize
            val resizeBtn = TextView(this).apply {
                text = "⤡"
                textSize = 11f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setBackgroundColor(Color.argb(220, 0, 100, 200))
                layoutParams = FrameLayout.LayoutParams(26, 26).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                }
            }

            container.addView(delBtn)
            container.addView(resizeBtn)

            var dX = 0f; var dY = 0f
            var resizing = false
            var rsX = 0f; var rsW = 0; var rsH = 0

            resizeBtn.setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        resizing = true; rsX = e.rawX
                        rsW = container.width; rsH = container.height; true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val d = (e.rawX - rsX).toInt()
                        val nw = (rsW + d).coerceIn(50, 300)
                        val nh = (rsH + d / 2).coerceIn(40, 280)
                        container.layoutParams = FrameLayout.LayoutParams(nw, nh)
                        pb.w = nw; pb.h = nh; true
                    }
                    MotionEvent.ACTION_UP -> { resizing = false; true }
                    else -> false
                }
            }

            container.setOnTouchListener { v, e ->
                if (resizing) return@setOnTouchListener false
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dX = v.x - e.rawX; dY = v.y - e.rawY
                        v.elevation = 10f; true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val nx = (e.rawX + dX).coerceIn(0f,
                            (gameArea.width - v.width).toFloat())
                        val ny = (e.rawY + dY).coerceIn(0f,
                            (gameArea.height - v.height - 48f))
                        v.x = nx; v.y = ny
                        pb.x = nx; pb.y = ny; true
                    }
                    MotionEvent.ACTION_UP -> { v.elevation = 0f; true }
                    else -> false
                }
            }
        } else {
            // Play mode
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            container.setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        btnStates[def.id] = true
                        container.setBackgroundColor(Color.argb(60,
                            Color.red(def.pressColor),
                            Color.green(def.pressColor),
                            Color.blue(def.pressColor)))
                        iconIv.setColorFilter(def.pressColor)
                        labelTv.setTextColor(def.pressColor)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                            vibrator.vibrate(VibrationEffect.createOneShot(
                                30, VibrationEffect.DEFAULT_AMPLITUDE))
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        btnStates[def.id] = false
                        container.setBackgroundColor(Color.parseColor("#111111"))
                        iconIv.setColorFilter(Color.parseColor("#888888"))
                        labelTv.setTextColor(Color.parseColor("#888888"))
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun saveLayout() {
        val arr = JSONArray()
        placedButtons.forEach { pb ->
            arr.put(JSONObject().apply {
                put("id", pb.id)
                put("x", pb.x)
                put("y", pb.y)
                put("w", pb.w)
                put("h", pb.h)
            })
        }
        getSharedPreferences("mrb_layout", Context.MODE_PRIVATE)
            .edit().putString("layout_v1", arr.toString()).apply()
        Toast.makeText(this, "✅ Layout Saved!", Toast.LENGTH_SHORT).show()
    }

    private fun loadLayout() {
        val json = getSharedPreferences("mrb_layout", Context.MODE_PRIVATE)
            .getString("layout_v1", null)

        val toLoad = if (json != null) {
            try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    PlacedBtn(o.getString("id"),
                        o.getDouble("x").toFloat(),
                        o.getDouble("y").toFloat(),
                        o.optInt("w", 70),
                        o.optInt("h", 120))
                }
            } catch (_: Exception) { null }
        } else null

        // Use saved or default
        val layout = toLoad ?: defaultLayout

        // Wait for gameArea to be laid out
        gameArea.post {
            layout.forEach { pb ->
                val def = allButtons.find { it.id == pb.id } ?: return@forEach
                val container = FrameLayout(this).apply {
                    x = pb.x; y = pb.y
                    layoutParams = FrameLayout.LayoutParams(pb.w, pb.h)
                }
                placedButtons.add(pb)
                buildBtnView(def, pb, container)
                gameArea.addView(container)
                placedViews[pb.id] = container
            }
        }
    }

    private fun confirmReset() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Reset to Default?")
            .setMessage("All custom positions will be reset")
            .setPositiveButton("Reset") { _, _ ->
                placedButtons.forEach { pb ->
                    placedViews[pb.id]?.let { gameArea.removeView(it) }
                }
                placedButtons.clear()
                placedViews.clear()
                getSharedPreferences("mrb_layout", Context.MODE_PRIVATE)
                    .edit().remove("layout_v1").apply()
                gameArea.post {
                    defaultLayout.forEach { pb ->
                        val def = allButtons.find { it.id == pb.id } ?: return@forEach
                        val container = FrameLayout(this).apply {
                            x = pb.x; y = pb.y
                            layoutParams = FrameLayout.LayoutParams(pb.w, pb.h)
                        }
                        placedButtons.add(pb.copy())
                        buildBtnView(def, pb, container)
                        gameArea.addView(container)
                        placedViews[pb.id] = container
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupHid() {
        connectedDevice = HidService.connectedDevice
        if (connectedDevice != null) {
            txtStatus.text = "● ${connectedDevice?.name}"
            txtStatus.setTextColor(Color.parseColor("#00FF88"))
            if (!connectedAnimDone) playConnectedAnim()
        } else {
            txtStatus.text = "Waiting..."
            txtStatus.setTextColor(Color.argb(100, 255, 255, 255))
        }
        HidService.onDisconnected = {
            runOnUiThread {
                connectedDevice = null
                connectedAnimDone = false
                txtStatus.text = "Disconnected"
                txtStatus.setTextColor(Color.argb(100, 255, 255, 255))
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
        animPlaying = true
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val totalSteps = 468
        var step = 0
        fun haptic(ms: Long) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vibrator.vibrate(VibrationEffect.createOneShot(
                    ms, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        val r = object : Runnable {
            override fun run() {
                if (step >= totalSteps) {
                    wheelView.rotation = 0f
                    animPlaying = false
                    haptic(80)
                    return
                }
                val t = step.toFloat() / totalSteps
                wheelView.rotation = -100f * sin(t * Math.PI.toFloat() * 2f)
                if (step == 0) haptic(40)
                if (step == totalSteps / 2) haptic(40)
                step++
                handler.postDelayed(this, 16)
            }
        }
        handler.post(r)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        filtX = alpha * event.values[1] + (1 - alpha) * filtX
        tiltByte = (filtX * 127f / 9.8f).toInt().coerceIn(-127, 127).toByte()
        val now = System.currentTimeMillis()
        if (now - lastSend > 40) { sendReport(); lastSend = now }
        runOnUiThread {
            if (!animPlaying) {
                filtXUI = 0.08f * filtX + 0.92f * filtXUI
                wheelView.rotation = (filtXUI * 9f).coerceIn(-180f, 180f)
            }
            txtTilt.text = "%.1f°".format(filtX * 9f)
            tiltBar.progress = (100 + (filtX / 9.8f * 100).toInt()).coerceIn(0, 200)
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendReport() {
        val device = HidService.connectedDevice ?: return
        val hid = HidService.hidDevice ?: return

        var b1 = 0; var b2 = 0
        var gasVal = 0x00.toByte()
        var brakeVal = 0x00.toByte()

        allButtons.forEach { def ->
            val pressed = btnStates[def.id] == true
            if (!pressed) return@forEach
            when {
                def.isGas -> gasVal = 0xFF.toByte()
                def.isBrake -> brakeVal = 0xFF.toByte()
                def.byte1bit >= 0 -> b1 = b1 or (1 shl def.byte1bit)
                def.byte2bit >= 0 -> b2 = b2 or (1 shl def.byte2bit)
            }
        }

        hid.sendReport(device, 1,
            byteArrayOf(b1.toByte(), b2.toByte(), tiltByte, 0x00, gasVal, brakeVal))
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
