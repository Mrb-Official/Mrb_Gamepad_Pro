package com.mrb.controller.pro

import android.annotation.SuppressLint
import android.content.*
import android.content.pm.ActivityInfo
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

// byte1bit = bit position in btnByte1 (-1 = not in byte1)
// byte2bit = bit position in btnByte2 (-1 = not in byte2)
// isGas/isBrake = special axis buttons
data class BtnDef(
    val id: String,
    val label: String,
    val iconRes: Int,       // drawable resource
    val pressColor: Int,
    val byte1bit: Int = -1,
    val byte2bit: Int = -1,
    val isGas: Boolean = false,
    val isBrake: Boolean = false
)

data class PlacedBtn(
    val def: BtnDef,
    var x: Float,
    var y: Float,
    var w: Int = 70,
    var h: Int = 120
)

class CustomLayoutActivity : AppCompatActivity() {

    private lateinit var gameArea: FrameLayout
    private val placedViews = mutableMapOf<String, FrameLayout>()
    private val placedButtons = mutableListOf<PlacedBtn>()
    private var editMode = false

    // All 39 buttons with proper HID mapping
    private val allButtons by lazy { listOf(
        // Gas/Brake = axis
        BtnDef("gas",         "GAS",       R.drawable.ic_btn_gas,        0xFF00C853.toInt(), isGas = true),
        BtnDef("brake",       "BRAKE",     R.drawable.ic_btn_brake,      0xFFD50000.toInt(), isBrake = true),
        // Gears = byte1 bit 6,7
        BtnDef("gear_up",     "REVERSE",   R.drawable.expand_circle_up_24,   0xFFFF6D00.toInt(), byte1bit = 7),
        BtnDef("gear_down",   "FRONT",     R.drawable.expand_circle_down_24, 0xFF00B4D8.toInt(), byte1bit = 6),
        // ABXY
        BtnDef("btn_a",       "A",         R.drawable.ic_btn_a,          0xFF3CFF6B.toInt(), byte1bit = 0),
        BtnDef("btn_b",       "B",         R.drawable.ic_btn_b,          0xFFFF4B4B.toInt(), byte1bit = 1),
        BtnDef("btn_x",       "X",         R.drawable.ic_btn_x,          0xFF3DB9FF.toInt(), byte2bit = 6),
        BtnDef("btn_y",       "Y",         R.drawable.ic_btn_y,          0xFFFFD700.toInt(), byte1bit = 4),
        // DPad
        BtnDef("dpad_up",     "UP",        R.drawable.ic_arrow_up,       0xFFFFFFFF.toInt(), byte2bit = 2),
        BtnDef("dpad_down",   "DOWN",      R.drawable.ic_arrow_down,     0xFFFFFFFF.toInt(), byte2bit = 3),
        BtnDef("dpad_left",   "LEFT",      R.drawable.ic_arrow_left,     0xFFFFFFFF.toInt(), byte2bit = 6),
        BtnDef("dpad_right",  "RIGHT",     R.drawable.ic_arrow_right,    0xFFFFFFFF.toInt(), byte2bit = 5),
        // Extra buttons
        BtnDef("horn",        "HORN",      R.drawable.ic_btn_horn,       0xFFFFEB3B.toInt(), byte1bit = 2),
        BtnDef("handbrake",   "H.BRAKE",  R.drawable.ic_btn_handbrake,  0xFFFF5722.toInt(), byte1bit = 3),
        BtnDef("camera",      "CAM",       R.drawable.ic_btn_camera,     0xFF9C27B0.toInt(), byte1bit = 5),
        BtnDef("nitro",       "NITRO",     R.drawable.ic_btn_nitro,      0xFF00BCD4.toInt(), byte2bit = 0),
        BtnDef("lights",      "LIGHTS",    R.drawable.ic_btn_lights,     0xFFFFC107.toInt(), byte2bit = 1),
        BtnDef("look_left",   "LOOK←",    R.drawable.ic_btn_look_left,  0xFF795548.toInt(), byte2bit = 4),
        BtnDef("look_right",  "LOOK→",    R.drawable.ic_btn_look_right, 0xFF8D6E63.toInt(), byte2bit = 7),
        BtnDef("l1",          "L1",        R.drawable.ic_btn_l1,         0xFF3F51B5.toInt(), byte1bit = -1, byte2bit = -1),
        BtnDef("r1",          "R1",        R.drawable.ic_btn_r1,         0xFF3F51B5.toInt(), byte1bit = -1, byte2bit = -1),
        BtnDef("start",       "START",     R.drawable.ic_btn_start,      0xFF4CAF50.toInt(), byte1bit = -1, byte2bit = -1),
        BtnDef("select",      "SELECT",    R.drawable.ic_btn_select,     0xFF388E3C.toInt(), byte1bit = -1, byte2bit = -1),
        BtnDef("reset",       "RESET",     R.drawable.ic_btn_reset,      0xFFF44336.toInt(), byte1bit = -1, byte2bit = -1),
        BtnDef("pause",       "PAUSE",     R.drawable.ic_btn_pause,      0xFF9E9E9E.toInt(), byte1bit = -1, byte2bit = -1),
        BtnDef("hazard",      "HAZARD",    R.drawable.ic_btn_hazard,     0xFFFF9800.toInt(), byte1bit = -1, byte2bit = -1),
        BtnDef("boost",       "BOOST",     R.drawable.ic_btn_boost,      0xFF00E5FF.toInt(), byte1bit = -1, byte2bit = -1),
        BtnDef("wipers",      "WIPERS",    R.drawable.ic_btn_wipers,     0xFF607D8B.toInt(), byte1bit = -1, byte2bit = -1),
        BtnDef("map",         "MAP",       R.drawable.ic_btn_map,        0xFF26C6DA.toInt(), byte1bit = -1, byte2bit = -1),
        BtnDef("custom1",     "BTN 1",     R.drawable.ic_btn_custom,     0xFFE91E63.toInt(), byte1bit = -1, byte2bit = -1),
        BtnDef("custom2",     "BTN 2",     R.drawable.ic_btn_custom,     0xFFAD1457.toInt(), byte1bit = -1, byte2bit = -1),
        BtnDef("custom3",     "BTN 3",     R.drawable.ic_btn_custom,     0xFF880E4F.toInt(), byte1bit = -1, byte2bit = -1),
        BtnDef("custom4",     "BTN 4",     R.drawable.ic_btn_custom,     0xFFE040FB.toInt(), byte1bit = -1, byte2bit = -1),
        BtnDef("custom5",     "BTN 5",     R.drawable.ic_btn_custom,     0xFF7B1FA2.toInt(), byte1bit = -1, byte2bit = -1),
        BtnDef("custom6",     "BTN 6",     R.drawable.ic_btn_custom,     0xFF4A148C.toInt(), byte1bit = -1, byte2bit = -1),
        BtnDef("custom7",     "BTN 7",     R.drawable.ic_btn_custom,     0xFF6200EA.toInt(), byte1bit = -1, byte2bit = -1),
        BtnDef("custom8",     "BTN 8",     R.drawable.ic_btn_custom,     0xFF311B92.toInt(), byte1bit = -1, byte2bit = -1),
    )}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.windowInsetsController?.apply {
            hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        buildUI()
        loadLayout()
    }

    private fun buildUI() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#080808"))
        }

        gameArea = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT)
        }

        // Steering wheel
        val wheel = ImageView(this).apply {
            setImageResource(R.drawable.ic_steering_wheel)
            layoutParams = FrameLayout.LayoutParams(220, 220).apply {
                gravity = Gravity.CENTER
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0.3f
        }
        gameArea.addView(wheel)

        // Top bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(8, 0, 8, 0)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 48).apply {
                gravity = Gravity.TOP
            }
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnBack = barBtn("← BACK", "#888888") { finish() }
        val tvTitle = TextView(this).apply {
            text = "Layout Editor"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnEditToggle = barBtn("✏ EDIT", "#FFD700") { toggleEdit() }
        val btnAdd = barBtn("＋ ADD", "#00C853") { showPicker() }
        val btnSave = barBtn("💾 SAVE", "#2196F3") { saveLayout(); finish() }
        val btnReset = barBtn("🗑", "#F44336") { confirmReset() }

        topBar.addView(btnBack)
        topBar.addView(tvTitle)
        topBar.addView(btnEditToggle)
        topBar.addView(btnAdd)
        topBar.addView(btnSave)
        topBar.addView(btnReset)

        root.addView(gameArea)
        root.addView(topBar)
        setContentView(root)
    }

    private fun barBtn(text: String, color: String, onClick: () -> Unit) =
        TextView(this).apply {
            this.text = text
            textSize = 10f
            setTextColor(Color.parseColor(color))
            setPadding(8, 4, 8, 4)
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(2, 0, 2, 0)
            }
        }

    private fun toggleEdit() {
        editMode = !editMode
        Toast.makeText(this,
            if (editMode) "Edit ON - Drag to move" else "Edit OFF",
            Toast.LENGTH_SHORT).show()
        placedButtons.forEach { pb ->
            placedViews[pb.def.id]?.let { container ->
                container.removeAllViews()
                buildBtnView(pb, container)
            }
        }
    }

    private fun showPicker() {
        val scroll = ScrollView(this)
        val grid = GridLayout(this).apply {
            columnCount = 5
            setPadding(8, 8, 8, 8)
        }

        allButtons.forEach { btn ->
            val placed = placedButtons.any { it.def.id == btn.id }
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
                setColorFilter(if (placed) Color.parseColor("#444444") else Color.WHITE)
                layoutParams = FrameLayout.LayoutParams(40, 40).apply {
                    gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                    topMargin = 8
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            val labelTv = TextView(this).apply {
                text = btn.label
                textSize = 9f
                setTextColor(if (placed) Color.parseColor("#444444") else Color.WHITE)
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
                    addButton(btn, 200f, 150f)
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

    private fun addButton(def: BtnDef, x: Float, y: Float, w: Int = 70, h: Int = 120) {
        if (placedButtons.any { it.def.id == def.id }) return
        val pb = PlacedBtn(def, x, y, w, h)
        placedButtons.add(pb)

        val container = FrameLayout(this).apply {
            this.x = x
            this.y = y + 48f
            layoutParams = FrameLayout.LayoutParams(w, h)
        }
        buildBtnView(pb, container)
        gameArea.addView(container)
        placedViews[def.id] = container
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildBtnView(pb: PlacedBtn, container: FrameLayout) {
        val def = pb.def

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
            layoutParams = FrameLayout.LayoutParams(48, 48).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                topMargin = (pb.h * 0.2f).toInt()
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
            // Delete btn
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
                    placedButtons.removeAll { it.def.id == def.id }
                }
            }

            // Resize handle
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
            var resizeStartX = 0f
            var resizeStartW = 0; var resizeStartH = 0

            resizeBtn.setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        resizing = true
                        resizeStartX = e.rawX
                        resizeStartW = container.width
                        resizeStartH = container.height
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val diff = (e.rawX - resizeStartX).toInt()
                        val nw = (resizeStartW + diff).coerceIn(60, 300)
                        val nh = (resizeStartH + diff / 2).coerceIn(50, 250)
                        container.layoutParams = FrameLayout.LayoutParams(nw, nh)
                        pb.w = nw; pb.h = nh
                        true
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
                        val ny = (e.rawY + dY).coerceIn(48f,
                            (gameArea.height - v.height).toFloat())
                        v.x = nx; v.y = ny
                        pb.x = nx; pb.y = ny - 48f
                        true
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
                        container.setBackgroundColor(Color.argb(60,
                            Color.red(def.pressColor),
                            Color.green(def.pressColor),
                            Color.blue(def.pressColor)))
                        iconIv.setColorFilter(def.pressColor)
                        labelTv.setTextColor(def.pressColor)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                            vibrator.vibrate(VibrationEffect.createOneShot(
                                30, VibrationEffect.DEFAULT_AMPLITUDE))
                        // Send HID
                        sendBtnReport(def, true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        container.setBackgroundColor(Color.parseColor("#111111"))
                        iconIv.setColorFilter(Color.parseColor("#888888"))
                        labelTv.setTextColor(Color.parseColor("#888888"))
                        sendBtnReport(def, false)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun sendBtnReport(def: BtnDef, pressed: Boolean) {
        val device = HidService.connectedDevice ?: return
        val hid = HidService.hidDevice ?: return

        // Get current state from MainActivity via HidService
        // Build report based on button def
        var b1 = 0; var b2 = 0
        val gas = if (def.isGas && pressed) 0xFF.toByte() else 0x00.toByte()
        val brake = if (def.isBrake && pressed) 0xFF.toByte() else 0x00.toByte()

        if (pressed) {
            if (def.byte1bit >= 0) b1 = b1 or (1 shl def.byte1bit)
            if (def.byte2bit >= 0) b2 = b2 or (1 shl def.byte2bit)
        }

        hid.sendReport(device, 1,
            byteArrayOf(b1.toByte(), b2.toByte(), 0x00, 0x00, gas, brake))
    }

    private fun saveLayout() {
        val arr = JSONArray()
        placedButtons.forEach { pb ->
            arr.put(JSONObject().apply {
                put("id", pb.def.id)
                put("x", pb.x)
                put("y", pb.y)
                put("w", pb.w)
                put("h", pb.h)
            })
        }
        getSharedPreferences("mrb_layout", Context.MODE_PRIVATE)
            .edit().putString("custom_layout", arr.toString()).apply()
        Toast.makeText(this, "✅ Saved!", Toast.LENGTH_SHORT).show()
    }

    private fun loadLayout() {
        val json = getSharedPreferences("mrb_layout", Context.MODE_PRIVATE)
            .getString("custom_layout", null) ?: return
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val def = allButtons.find { it.id == obj.getString("id") } ?: continue
                addButton(def,
                    obj.getDouble("x").toFloat(),
                    obj.getDouble("y").toFloat(),
                    obj.optInt("w", 70),
                    obj.optInt("h", 120))
            }
        } catch (_: Exception) {}
    }

    private fun confirmReset() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Reset Layout?")
            .setMessage("All buttons will be removed")
            .setPositiveButton("Reset") { _, _ ->
                placedButtons.forEach { pb ->
                    placedViews[pb.def.id]?.let { gameArea.removeView(it) }
                }
                placedButtons.clear()
                placedViews.clear()
                getSharedPreferences("mrb_layout", Context.MODE_PRIVATE)
                    .edit().remove("custom_layout").apply()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
