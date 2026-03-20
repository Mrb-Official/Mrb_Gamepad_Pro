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

data class GameButton(
    val id: String,
    val label: String,
    val color: Int,
    var x: Float = 0f,
    var y: Float = 0f,
    var width: Int = 120,
    var height: Int = 80,
    var visible: Boolean = false
)

class CustomLayoutActivity : AppCompatActivity() {

    private lateinit var gameLayout: FrameLayout
    private val placedButtons = mutableListOf<View>()
    private val buttonData = mutableListOf<GameButton>()

    private val allButtons = listOf(
        GameButton("gas",        "GAS",        0xFF00C853.toInt()),
        GameButton("brake",      "BRAKE",      0xFFD50000.toInt()),
        GameButton("gear_up",    "GEAR+",      0xFF00C853.toInt()),
        GameButton("gear_down",  "GEAR-",      0xFFFF6D00.toInt()),
        GameButton("btn_a",      "A",          0xFF3CFF6B.toInt()),
        GameButton("btn_b",      "B",          0xFFFF4B4B.toInt()),
        GameButton("btn_x",      "X",          0xFF3DB9FF.toInt()),
        GameButton("btn_y",      "Y",          0xFFFFD700.toInt()),
        GameButton("dpad_up",    "D↑",         0xFFFFFFFF.toInt()),
        GameButton("dpad_down",  "D↓",         0xFFFFFFFF.toInt()),
        GameButton("dpad_left",  "D←",         0xFFFFFFFF.toInt()),
        GameButton("dpad_right", "D→",         0xFFFFFFFF.toInt()),
        GameButton("horn",       "HORN",       0xFFFFEB3B.toInt()),
        GameButton("handbrake",  "H.BRAKE",    0xFFFF5722.toInt()),
        GameButton("camera",     "CAM",        0xFF9C27B0.toInt()),
        GameButton("nitro",      "NITRO",      0xFF00BCD4.toInt()),
        GameButton("lights",     "LIGHTS",     0xFFFFC107.toInt()),
        GameButton("wipers",     "WIPERS",     0xFF607D8B.toInt()),
        GameButton("look_left",  "LOOK←",      0xFF795548.toInt()),
        GameButton("look_right", "LOOK→",      0xFF795548.toInt()),
        GameButton("reset",      "RESET",      0xFFF44336.toInt()),
        GameButton("pause",      "PAUSE",      0xFF9E9E9E.toInt()),
        GameButton("l1",         "L1",         0xFF3F51B5.toInt()),
        GameButton("l2",         "L2",         0xFF3F51B5.toInt()),
        GameButton("r1",         "R1",         0xFF3F51B5.toInt()),
        GameButton("r2",         "R2",         0xFF3F51B5.toInt()),
        GameButton("start",      "START",      0xFF4CAF50.toInt()),
        GameButton("select",     "SELECT",     0xFF4CAF50.toInt()),
        GameButton("custom1",    "BTN 1",      0xFFE91E63.toInt()),
        GameButton("custom2",    "BTN 2",      0xFFE91E63.toInt()),
        GameButton("custom3",    "BTN 3",      0xFFE91E63.toInt()),
        GameButton("custom4",    "BTN 4",      0xFFE91E63.toInt()),
        GameButton("custom5",    "BTN 5",      0xFFE91E63.toInt()),
        GameButton("custom6",    "BTN 6",      0xFFE91E63.toInt()),
        GameButton("custom7",    "BTN 7",      0xFFE91E63.toInt()),
        GameButton("custom8",    "BTN 8",      0xFFE91E63.toInt()),
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
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#080808"))
        }

        // Game area
        gameLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#0D0D0D"))
        }

        // Steering wheel placeholder center
        val wheelPlaceholder = TextView(this).apply {
            text = "🎮 GAME AREA"
            textSize = 16f
            setTextColor(Color.argb(40, 255, 255, 255))
            gravity = android.view.Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT)
        }
        gameLayout.addView(wheelPlaceholder)

        // Top bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(16, 8, 16, 8)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                56)
        }

        val tvTitle = TextView(this).apply {
            text = "Custom Layout"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val btnSave = buildTopBtn("SAVE", "#00C853") { saveLayout(); finish() }
        val btnReset = buildTopBtn("RESET", "#FF5722") { resetLayout() }
        val btnBack = buildTopBtn("BACK", "#666666") { finish() }

        topBar.addView(btnBack)
        topBar.addView(tvTitle)
        topBar.addView(btnReset)
        topBar.addView(Space(this).apply {
            layoutParams = LinearLayout.LayoutParams(8, 1)
        })
        topBar.addView(btnSave)

        // Button panel bottom
        val btnPanel = HorizontalScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#161616"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 90).apply {
                gravity = android.view.Gravity.BOTTOM
            }
        }

        val btnPanelInner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
        }

        allButtons.forEach { btn ->
            val chip = buildChip(btn)
            btnPanelInner.addView(chip)
        }

        btnPanel.addView(btnPanelInner)

        root.addView(gameLayout)
        root.addView(topBar)
        root.addView(btnPanel)

        setContentView(root)
    }

    private fun buildTopBtn(text: String, color: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 11f
            setTextColor(Color.parseColor(color))
            setPadding(12, 4, 12, 4)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
        }
    }

    private fun buildChip(btn: GameButton): View {
        return TextView(this).apply {
            text = btn.label
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(16, 8, 16, 8)
            setBackgroundColor(Color.argb(180,
                Color.red(btn.color),
                Color.green(btn.color),
                Color.blue(btn.color)))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(4, 0, 4, 0)
            }
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 8f)
                }
            }
            clipToOutline = true
            setOnClickListener { addButtonToLayout(btn) }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addButtonToLayout(btn: GameButton, x: Float = 100f, y: Float = 100f) {
        // Check if already placed
        val existing = placedButtons.find { it.tag == btn.id }
        if (existing != null) {
            Toast.makeText(this, "${btn.label} already placed", Toast.LENGTH_SHORT).show()
            return
        }

        val btnView = FrameLayout(this).apply {
            tag = btn.id
            layoutParams = FrameLayout.LayoutParams(btn.width, btn.height).apply {
                leftMargin = x.toInt()
                topMargin = y.toInt()
            }
            setBackgroundColor(Color.argb(180,
                Color.red(btn.color),
                Color.green(btn.color),
                Color.blue(btn.color)))
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 16f)
                }
            }
            clipToOutline = true
        }

        val label = TextView(this).apply {
            text = btn.label
            textSize = 11f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = android.view.Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT)
        }

        // Delete button
        val deletBtn = TextView(this).apply {
            text = "✕"
            textSize = 10f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(180, 200, 0, 0))
            gravity = android.view.Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(28, 28).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
            }
            setOnClickListener {
                gameLayout.removeView(btnView)
                placedButtons.remove(btnView)
            }
        }

        btnView.addView(label)
        btnView.addView(deletBtn)

        // Drag logic
        var dX = 0f; var dY = 0f
        btnView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    v.elevation = 10f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val newX = (event.rawX + dX).coerceIn(0f,
                        (gameLayout.width - v.width).toFloat())
                    val newY = (event.rawY + dY).coerceIn(56f,
                        (gameLayout.height - v.height - 90f))
                    v.x = newX; v.y = newY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.elevation = 0f
                    true
                }
                else -> false
            }
        }

        gameLayout.addView(btnView)
        placedButtons.add(btnView)
    }

    private fun saveLayout() {
        val arr = JSONArray()
        placedButtons.forEach { v ->
            val id = v.tag as String
            val obj = JSONObject().apply {
                put("id", id)
                put("x", v.x)
                put("y", v.y)
                put("w", v.width)
                put("h", v.height)
            }
            arr.put(obj)
        }
        getSharedPreferences("mrb_layout", Context.MODE_PRIVATE)
            .edit().putString("custom_layout", arr.toString()).apply()
        Toast.makeText(this, "Layout saved!", Toast.LENGTH_SHORT).show()
    }

    private fun loadLayout() {
        val prefs = getSharedPreferences("mrb_layout", Context.MODE_PRIVATE)
        val json = prefs.getString("custom_layout", null) ?: return
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                val btn = allButtons.find { it.id == id } ?: continue
                addButtonToLayout(btn,
                    obj.getDouble("x").toFloat(),
                    obj.getDouble("y").toFloat())
                val v = placedButtons.last()
                v.layoutParams = FrameLayout.LayoutParams(
                    obj.getInt("w"), obj.getInt("h"))
                v.x = obj.getDouble("x").toFloat()
                v.y = obj.getDouble("y").toFloat()
            }
        } catch (_: Exception) {}
    }

    private fun resetLayout() {
        placedButtons.forEach { gameLayout.removeView(it) }
        placedButtons.clear()
        getSharedPreferences("mrb_layout", Context.MODE_PRIVATE)
            .edit().remove("custom_layout").apply()
    }
}
