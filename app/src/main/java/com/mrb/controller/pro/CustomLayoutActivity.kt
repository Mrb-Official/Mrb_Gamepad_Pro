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
import kotlin.math.*

data class BtnDef(
    val id: String,
    val label: String,
    val icon: String,
    val pressColor: Int,
    val byte1: Int = -1,
    val byte2: Int = -1
)

class CustomLayoutActivity : AppCompatActivity() {

    private lateinit var rootFrame: FrameLayout
    private lateinit var gameArea: FrameLayout
    private lateinit var wheelImg: ImageView
    private var editMode = false
    private val placedViews = mutableMapOf<String, View>()

    data class PlacedBtn(
        val def: BtnDef,
        var x: Float,
        var y: Float,
        var w: Int = 70,
        var h: Int = 120
    )
    private val placedButtons = mutableListOf<PlacedBtn>()

    val allButtons = listOf(
        BtnDef("gas",         "GAS",        "⚡", 0xFF00C853.toInt()),
        BtnDef("brake",       "BRAKE",      "🛑", 0xFFD50000.toInt()),
        BtnDef("gear_up",     "GEAR+",      "⬆", 0xFF76FF03.toInt()),
        BtnDef("gear_down",   "GEAR-",      "⬇", 0xFFFF6D00.toInt()),
        BtnDef("btn_a",       "A",          "🅐", 0xFF3CFF6B.toInt()),
        BtnDef("btn_b",       "B",          "🅑", 0xFFFF4B4B.toInt()),
        BtnDef("btn_x",       "X",          "🅧", 0xFF3DB9FF.toInt()),
        BtnDef("btn_y",       "Y",          "🅨", 0xFFFFD700.toInt()),
        BtnDef("dpad_up",     "UP",         "△", 0xFFFFFFFF.toInt()),
        BtnDef("dpad_down",   "DOWN",       "▽", 0xFFFFFFFF.toInt()),
        BtnDef("dpad_left",   "LEFT",       "◁", 0xFFFFFFFF.toInt()),
        BtnDef("dpad_right",  "RIGHT",      "▷", 0xFFFFFFFF.toInt()),
        BtnDef("horn",        "HORN",       "📯", 0xFFFFEB3B.toInt()),
        BtnDef("handbrake",   "H.BRAKE",   "🔒", 0xFFFF5722.toInt()),
        BtnDef("camera",      "CAM",        "📷", 0xFF9C27B0.toInt()),
        BtnDef("nitro",       "NITRO",      "💨", 0xFF00BCD4.toInt()),
        BtnDef("lights",      "LIGHTS",     "💡", 0xFFFFC107.toInt()),
        BtnDef("wipers",      "WIPERS",     "🌧", 0xFF607D8B.toInt()),
        BtnDef("look_left",   "LOOK←",     "👁", 0xFF795548.toInt()),
        BtnDef("look_right",  "LOOK→",     "👁", 0xFF8D6E63.toInt()),
        BtnDef("reset",       "RESET",      "🔄", 0xFFF44336.toInt()),
        BtnDef("pause",       "PAUSE",      "⏸", 0xFF9E9E9E.toInt()),
        BtnDef("l1",          "L1",         "◀", 0xFF3F51B5.toInt()),
        BtnDef("l2",          "L2",         "◀◀", 0xFF303F9F.toInt()),
        BtnDef("r1",          "R1",         "▶", 0xFF3F51B5.toInt()),
        BtnDef("r2",          "R2",         "▶▶", 0xFF303F9F.toInt()),
        BtnDef("start",       "START",      "▶",  0xFF4CAF50.toInt()),
        BtnDef("select",      "SELECT",     "☰",  0xFF388E3C.toInt()),
        BtnDef("hazard",      "HAZARD",     "⚠", 0xFFFF9800.toInt()),
        BtnDef("boost",       "BOOST",      "🚀", 0xFF00E5FF.toInt()),
        BtnDef("map",         "MAP",        "🗺", 0xFF26C6DA.toInt()),
        BtnDef("music",       "MUSIC",      "🎵", 0xFFEC407A.toInt()),
        BtnDef("custom1",     "BTN 1",      "①",  0xFFE91E63.toInt()),
        BtnDef("custom2",     "BTN 2",      "②",  0xFFAD1457.toInt()),
        BtnDef("custom3",     "BTN 3",      "③",  0xFF880E4F.toInt()),
        BtnDef("custom4",     "BTN 4",      "④",  0xFFE040FB.toInt()),
        BtnDef("custom5",     "BTN 5",      "⑤",  0xFF7B1FA2.toInt()),
        BtnDef("custom6",     "BTN 6",      "⑥",  0xFF4A148C.toInt()),
        BtnDef("custom7",     "BTN 7",      "⑦",  0xFF6200EA.toInt()),
        BtnDef("custom8",     "BTN 8",      "⑧",  0xFF311B92.toInt()),
    )

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
        rootFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#080808"))
        }

        // Game area
        gameArea = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT)
        }

        // Steering wheel center
        wheelImg = ImageView(this).apply {
            setImageResource(R.drawable.ic_steering_wheel)
            layoutParams = FrameLayout.LayoutParams(280, 280).apply {
                gravity = Gravity.CENTER
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        gameArea.addView(wheelImg)

        // Top bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(12, 0, 12, 0)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 52).apply {
                gravity = Gravity.TOP
            }
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnBack = buildBarBtn("← BACK", "#666666") { finish() }
        val tvTitle = TextView(this).apply {
            text = "Layout Editor"
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnEdit = buildBarBtn("✏ EDIT", "#FFD700") { toggleEditMode() }
        val btnPlus = buildBarBtn("＋ ADD", "#00C853") { showButtonPicker() }
        val btnSave = buildBarBtn("💾 SAVE", "#2196F3") { saveLayout(); finish() }
        val btnReset = buildBarBtn("🗑 RESET", "#F44336") { resetLayout() }

        topBar.addView(btnBack)
        topBar.addView(tvTitle)
        topBar.addView(btnEdit)
        topBar.addView(btnPlus)
        topBar.addView(btnSave)
        topBar.addView(btnReset)

        rootFrame.addView(gameArea)
        rootFrame.addView(topBar)
        setContentView(rootFrame)
    }

    private fun buildBarBtn(text: String, color: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 10f
            setTextColor(Color.parseColor(color))
            setPadding(10, 6, 10, 6)
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(2, 0, 2, 0)
            }
        }
    }

    private fun toggleEditMode() {
        editMode = !editMode
        Toast.makeText(this,
            if (editMode) "Edit ON - Drag buttons" else "Edit OFF",
            Toast.LENGTH_SHORT).show()
        // Refresh all buttons
        placedButtons.forEach { pb ->
            placedViews[pb.def.id]?.let { rebuildBtn(pb, it.parent as? FrameLayout) }
        }
    }

    private fun showButtonPicker() {
        val dialog = android.app.AlertDialog.Builder(this)
        val scroll = ScrollView(this)
        val grid = GridLayout(this).apply {
            columnCount = 4
            setPadding(8, 8, 8, 8)
        }

        allButtons.forEach { btn ->
            val already = placedButtons.any { it.def.id == btn.id }
            val chip = FrameLayout(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 160
                    height = 80
                    setMargins(4, 4, 4, 4)
                }
                setBackgroundColor(if (already)
                    Color.argb(60, Color.red(btn.pressColor),
                        Color.green(btn.pressColor), Color.blue(btn.pressColor))
                else
                    Color.argb(180, Color.red(btn.pressColor),
                        Color.green(btn.pressColor), Color.blue(btn.pressColor)))
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, 12f)
                    }
                }
                clipToOutline = true
            }

            val iconTv = TextView(this).apply {
                text = btn.icon
                textSize = 22f
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, 48)
            }

            val labelTv = TextView(this).apply {
                text = btn.label
                textSize = 9f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.BOTTOM
                    bottomMargin = 4
                }
            }

            chip.addView(iconTv)
            chip.addView(labelTv)

            if (!already) {
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

    @SuppressLint("ClickableViewAccessibility")
    private fun addButton(def: BtnDef, x: Float, y: Float, w: Int = 70, h: Int = 120) {
        if (placedButtons.any { it.def.id == def.id }) return

        val pb = PlacedBtn(def, x, y, w, h)
        placedButtons.add(pb)

        val container = FrameLayout(this)
        buildBtnView(pb, container)
        container.x = x
        container.y = y + 52f // offset for topbar
        container.layoutParams = FrameLayout.LayoutParams(w, h)
        gameArea.addView(container)
        placedViews[def.id] = container
    }

    private fun rebuildBtn(pb: PlacedBtn, container: FrameLayout?) {
        container ?: return
        container.removeAllViews()
        buildBtnView(pb, container)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildBtnView(pb: PlacedBtn, container: FrameLayout) {
        val def = pb.def

        // Background
        container.setBackgroundColor(Color.parseColor("#111111"))
        container.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 16f)
            }
        }
        container.clipToOutline = true

        // Icon
        val iconTv = TextView(this).apply {
            text = def.icon
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#888888"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT)
        }

        // Label
        val labelTv = TextView(this).apply {
            text = def.label
            textSize = 9f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.1f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = 6
            }
        }

        container.addView(iconTv)
        container.addView(labelTv)

        // Edit mode controls
        if (editMode) {
            // Delete
            val delBtn = TextView(this).apply {
                text = "✕"
                textSize = 9f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setBackgroundColor(Color.argb(200, 200, 0, 0))
                layoutParams = FrameLayout.LayoutParams(24, 24).apply {
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
                text = "↔"
                textSize = 9f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setBackgroundColor(Color.argb(200, 0, 100, 200))
                layoutParams = FrameLayout.LayoutParams(24, 24).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                }
            }

            container.addView(delBtn)
            container.addView(resizeBtn)

            // Drag
            var dX = 0f; var dY = 0f
            var resizing = false
            var resizeStartX = 0f
            var resizeStartW = 0
            var resizeStartH = 0

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
                        val newW = (resizeStartW + diff).coerceIn(60, 300)
                        val newH = (resizeStartH + diff / 2).coerceIn(50, 250)
                        container.layoutParams = FrameLayout.LayoutParams(newW, newH)
                        pb.w = newW; pb.h = newH
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
                        val ny = (e.rawY + dY).coerceIn(52f,
                            (gameArea.height - v.height).toFloat())
                        v.x = nx; v.y = ny
                        pb.x = nx; pb.y = ny - 52f
                        true
                    }
                    MotionEvent.ACTION_UP -> { v.elevation = 0f; true }
                    else -> false
                }
            }
        } else {
            // Play mode - press color change
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            container.setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        container.setBackgroundColor(
                            Color.argb(60,
                                Color.red(def.pressColor),
                                Color.green(def.pressColor),
                                Color.blue(def.pressColor)))
                        iconTv.setTextColor(def.pressColor)
                        labelTv.setTextColor(def.pressColor)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                            vibrator.vibrate(android.os.VibrationEffect.createOneShot(
                                30, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        container.setBackgroundColor(Color.parseColor("#111111"))
                        iconTv.setTextColor(Color.parseColor("#888888"))
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
            val obj = JSONObject().apply {
                put("id", pb.def.id)
                put("x", pb.x)
                put("y", pb.y)
                put("w", pb.w)
                put("h", pb.h)
            }
            arr.put(obj)
        }
        getSharedPreferences("mrb_layout", Context.MODE_PRIVATE)
            .edit().putString("custom_layout", arr.toString()).apply()
        Toast.makeText(this, "✅ Layout Saved!", Toast.LENGTH_SHORT).show()
    }

    private fun loadLayout() {
        val prefs = getSharedPreferences("mrb_layout", Context.MODE_PRIVATE)
        val json = prefs.getString("custom_layout", null) ?: return
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                val def = allButtons.find { it.id == id } ?: continue
                addButton(def,
                    obj.getDouble("x").toFloat(),
                    obj.getDouble("y").toFloat(),
                    obj.optInt("w", 70),
                    obj.optInt("h", 120))
            }
        } catch (_: Exception) {}
    }

    private fun resetLayout() {
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
