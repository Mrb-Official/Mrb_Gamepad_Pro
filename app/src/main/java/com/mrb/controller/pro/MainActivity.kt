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
    private lateinit var txtStatus: TextView
    private lateinit var txtTilt: TextView
    private lateinit var tiltBar: ProgressBar
    private lateinit var wheelView: ImageView
    private lateinit var overlayFrame: FrameLayout

    // Default button states
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

    // Custom button states
    private val customBtnStates = mutableMapOf<String, Boolean>()

    private var tiltByte: Byte = 0
    private var filtX     = 0f
    private var filtXUI   = 0f
    private val alpha     = 0.15f
    private var lastSend  = 0L
    private var connectedAnimDone = false
    private var animPlaying = false
    private var editMode = false

    private val handler = Handler(Looper.getMainLooper())

    // Custom buttons definitions - 30 buttons
    private val customButtons = listOf(
        CustomBtn("horn",       "HORN",     R.drawable.ic_btn_horn,       0xFFFFEB3B.toInt(), byte1bit = 2),
        CustomBtn("handbrake",  "H.BRAKE",  R.drawable.ic_btn_handbrake,  0xFFFF5722.toInt(), byte1bit = 6),
        CustomBtn("camera",     "CAM",      R.drawable.ic_btn_camera,     0xFF9C27B0.toInt(), byte1bit = 5),
        CustomBtn("nitro",      "NITRO",    R.drawable.ic_btn_nitro,      0xFF00BCD4.toInt(), byte2bit = 0),
        CustomBtn("lights",     "LIGHTS",   R.drawable.ic_btn_lights,     0xFFFFC107.toInt(), byte2bit = 1),
        CustomBtn("look_left",  "LOOKu2190",   R.drawable.ic_btn_look_left,  0xFF795548.toInt(), byte2bit = 4),
        CustomBtn("look_right", "LOOKu2192",   R.drawable.ic_btn_look_right, 0xFF8D6E63.toInt(), byte2bit = 7),
        CustomBtn("l1",         "L1",       R.drawable.ic_btn_l1,         0xFF3F51B5.toInt(), byte2bit = 2),
        CustomBtn("r1",         "R1",       R.drawable.ic_btn_r1,         0xFF5C6BC0.toInt(), byte2bit = 3),
        CustomBtn("start",      "START",    R.drawable.ic_btn_start,      0xFF4CAF50.toInt(), byte2bit = 5),
        CustomBtn("select",     "SELECT",   R.drawable.ic_btn_select,     0xFF388E3C.toInt(), byte2bit = 6),
        CustomBtn("reset",      "RESET",    R.drawable.ic_btn_reset,      0xFFF44336.toInt(), byte1bit = -1),
        CustomBtn("pause",      "PAUSE",    R.drawable.ic_btn_pause,      0xFF9E9E9E.toInt(), byte1bit = -1),
        CustomBtn("hazard",     "HAZARD",   R.drawable.ic_btn_hazard,     0xFFFF9800.toInt(), byte1bit = -1),
        CustomBtn("boost",      "BOOST",    R.drawable.ic_btn_boost,      0xFF00E5FF.toInt(), byte1bit = -1),
        CustomBtn("wipers",     "WIPERS",   R.drawable.ic_btn_wipers,     0xFF607D8B.toInt(), byte1bit = -1),
        CustomBtn("map",        "MAP",      R.drawable.ic_btn_map,        0xFF26C6DA.toInt(), byte1bit = -1),
        CustomBtn("custom1",    "BTN 1",    R.drawable.ic_btn_custom,     0xFFE91E63.toInt(), byte1bit = -1),
        CustomBtn("custom2",    "BTN 2",    R.drawable.ic_btn_custom,     0xFFAD1457.toInt(), byte1bit = -1),
        CustomBtn("custom3",    "BTN 3",    R.drawable.ic_btn_custom,     0xFF880E4F.toInt(), byte1bit = -1),
        CustomBtn("custom4",    "BTN 4",    R.drawable.ic_btn_custom,     0xFFE040FB.toInt(), byte1bit = -1),
        CustomBtn("custom5",    "BTN 5",    R.drawable.ic_btn_custom,     0xFF7B1FA2.toInt(), byte1bit = -1),
        CustomBtn("custom6",    "BTN 6",    R.drawable.ic_btn_custom,     0xFF4A148C.toInt(), byte1bit = -1),
        CustomBtn("custom7",    "BTN 7",    R.drawable.ic_btn_custom,     0xFF6200EA.toInt(), byte1bit = -1),
        CustomBtn("custom8",    "BTN 8",    R.drawable.ic_btn_custom,     0xFF311B92.toInt(), byte1bit = -1),
        CustomBtn("turbo",      "TURBO",    R.drawable.ic_btn_nitro,      0xFFFF6F00.toInt(), byte1bit = -1),
        CustomBtn("siren",      "SIREN",    R.drawable.ic_btn_horn,       0xFF1565C0.toInt(), byte1bit = -1),
        CustomBtn("cinematic",  "CIN",      R.drawable.ic_btn_camera,     0xFF6A1B9A.toInt(), byte1bit = -1),
        CustomBtn("slowmo",     "SLOW MO",  R.drawable.ic_btn_pause,      0xFF00838F.toInt(), byte1bit = -1),
        CustomBtn("screenshot", "SHOT",     R.drawable.ic_btn_camera,     0xFF558B2F.toInt(), byte1bit = -1),
    )

    private val placedCustomBtns = mutableListOf<PlacedCustomBtn>()
    private val placedCustomViews = mutableMapOf<String, FrameLayout>()
    private var editBar: LinearLayout? = null

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

    @SuppressLint("ClickableViewAccessibility")
    private fun initUI() {
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

        // Overlay for custom buttons
        overlayFrame = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT)
        }

        // Add overlay on top of existing layout
        (window.decorView as FrameLayout).addView(overlayFrame)

        // Edit button - top center
        // Crown button - top center
        val btnCrown = ImageView(this).apply {
            setImageResource(R.drawable.crown_24)
            setColorFilter(Color.parseColor("#2196F3"))
            setPadding(16, 8, 16, 8)
            layoutParams = FrameLayout.LayoutParams(80, 80).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = 4
            }
            setOnClickListener { onCrownClick() }
        }
        overlayFrame.addView(btnCrown)
        updateCrownGlow(btnCrown)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Default buttons setup
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

        // Load saved custom buttons
        overlayFrame.post { loadCustomLayout() }

        setupHid()
    }

    private fun isPremium(): Boolean {
        val expiry = getSharedPreferences("mrb_premium", MODE_PRIVATE)
            .getLong("premium_expiry", 0L)
        return expiry > System.currentTimeMillis()
    }

    private fun updateCrownGlow(crown: ImageView) {
        if (isPremium()) {
            crown.setColorFilter(Color.parseColor("#FFD700"))
            // Yellow pulse animation
            val anim = android.animation.ObjectAnimator.ofFloat(crown, "alpha", 1f, 0.4f, 1f).apply {
                duration = 1500
                repeatCount = android.animation.ValueAnimator.INFINITE
            }
            anim.start()
        } else {
            crown.setColorFilter(Color.parseColor("#2196F3"))
            // Blue pulse animation
            val anim = android.animation.ObjectAnimator.ofFloat(crown, "alpha", 1f, 0.3f, 1f).apply {
                duration = 1500
                repeatCount = android.animation.ValueAnimator.INFINITE
            }
            anim.start()
        }
    }

    private fun onCrownClick() {
        if (isPremium()) {
            toggleEditMode()
        } else {
            showPremiumPopup()
        }
    }

    private fun showPremiumPopup() {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            // Force landscape
            setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
        }

        val scroll = android.widget.ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1C1B1F"))
            setPadding(32, 32, 32, 32)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 32f)
                }
            }
            clipToOutline = true
        }

        // Left side - crown + title + subtitle
        val leftCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 16, 0)
            }
        }

        val crownIv = ImageView(this).apply {
            setImageResource(R.drawable.crown_24)
            setColorFilter(Color.parseColor("#FFD700"))
            layoutParams = LinearLayout.LayoutParams(80, 80).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 12
            }
        }

        val tvTitle = TextView(this).apply {
            text = "MRB Premium"
            textSize = 20f
            setTextColor(Color.parseColor("#FFD700"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 6 }
        }

        val tvSub = TextView(this).apply {
            text = "Watch an ad to unlock\npremium for 24 hours"
            textSize = 12f
            setTextColor(Color.argb(180, 255, 255, 255))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }

        val btnAd = TextView(this).apply {
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.motion_play_24, 0, 0, 0)
            text = "  Watch Ad = 1 Day Free"
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#FF6D00"))
            setPadding(20, 14, 20, 14)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 24f)
                }
            }
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 }
            setOnClickListener {
                dialog.dismiss()
                showAdFromMainActivity()
            }
        }

        val btnTry = TextView(this).apply {
            text = "Try without Premium"
            textSize = 11f
            setTextColor(Color.argb(130, 255, 255, 255))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener { dialog.dismiss(); toggleEditMode() }
        }

        leftCol.addView(crownIv)
        leftCol.addView(tvTitle)
        leftCol.addView(tvSub)
        leftCol.addView(btnAd)
        leftCol.addView(btnTry)

        // Right side - benefits
        val rightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvBenefitsTitle = TextView(this).apply {
            text = "Benefits"
            textSize = 14f
            setTextColor(Color.parseColor("#FFD700"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
        }

        val benefits = listOf(
            "🎮  Custom button layout",
            "➕  30+ extra buttons",
            "↔  Drag and resize",
            "💾  Save your layout",
            "⭐  Premium crown badge"
        )
        val tvBenefits = TextView(this).apply {
            text = benefits.joinToString("\n")
            textSize = 12f
            setTextColor(Color.WHITE)
            lineHeight = (textSize * 2.2f).toInt()
        }

        rightCol.addView(tvBenefitsTitle)
        rightCol.addView(tvBenefits)

        root.addView(leftCol)
        root.addView(rightCol)
        scroll.addView(root)
        dialog.setContentView(scroll)
        dialog.show()
    }

    private fun showAdFromMainActivity() {
        // Use SplashActivity instance if available, else reload
        val splash = application as? SplashActivity
        val ad = splash?.rewardedAd
        if (ad != null) {
            ad.show(this) {
                val expiry = System.currentTimeMillis() + 86400000L
                getSharedPreferences("mrb_premium", MODE_PRIVATE)
                    .edit().putLong("premium_expiry", expiry).apply()
                Toast.makeText(this, "⭐ Premium Active for 24h!", Toast.LENGTH_LONG).show()
                // Update crown
                initUI()
            }
        } else {
            Toast.makeText(this, "Ad loading... try again in a moment", Toast.LENGTH_SHORT).show()
        }
    }
    }
    private fun toggleEditMode() {
        editMode = !editMode
        if (editMode) {
            showEditBar()
            // Show drag handles on placed buttons
            for (pb in placedCustomBtns) {
                val v = placedCustomViews[pb.id]; if (v != null) {
                    v.removeAllViews()
                    val def = customButtons.find { btn -> btn.id == pb.id } ?: continue
                    buildCustomBtnView(def, pb, v, editMode = true)
                }
            }
        } else {
            hideEditBar()
            for (pb in placedCustomBtns) {
                val v = placedCustomViews[pb.id]; if (v != null) {
                    v.removeAllViews()
                    val def = customButtons.find { btn -> btn.id == pb.id } ?: continue
                    buildCustomBtnView(def, pb, v, editMode = false)
                }
            }
        }
    }

    private fun showEditBar() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#DD111111"))
            setPadding(12, 0, 12, 0)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 52).apply {
                gravity = Gravity.BOTTOM
            }
        }

        val btnAdd = iconBarBtn(R.drawable.add_circle_24, "ADD", "#00C853") { showPicker() }
        val space = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        val tvHint = TextView(this).apply {
            text = "Drag to move  •  ⤡ Resize  •  ✕ Delete"
            textSize = 9f
            setTextColor(Color.argb(120, 255, 255, 255))
            gravity = Gravity.CENTER
        }
        val space2 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        val btnSave = iconBarBtn(R.drawable.save_24, "SAVE", "#2196F3") { saveCustomLayout(); toggleEditMode() }
        val btnReset = iconBarBtn(R.drawable.auto_delete_24, "RESET", "#F44336") { confirmReset() }
            toggleEditMode()
        }
        val btnReset = barBtn("RESET", "#F44336") { confirmReset() }

        bar.addView(btnAdd)
        bar.addView(space)
        bar.addView(tvHint)
        bar.addView(space2)
        bar.addView(btnSave)
        bar.addView(btnReset)

        overlayFrame.addView(bar)
        editBar = bar
    }

    private fun hideEditBar() {
        editBar?.let { overlayFrame.removeView(it) }
        editBar = null
    }

    private fun barBtn(text: String, color: String, onClick: () -> Unit) =
    private fun iconBarBtn(iconRes: Int, text: String, color: String, onClick: () -> Unit): LinearLayout {
        val btn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(12, 4, 12, 4)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(4, 0, 4, 0)
            }
        }
        val iv = ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(Color.parseColor(color))
            layoutParams = LinearLayout.LayoutParams(32, 32).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        val tv = TextView(this).apply {
            this.text = text
            textSize = 8f
            setTextColor(Color.parseColor(color))
            gravity = Gravity.CENTER
        }
        btn.addView(iv)
        btn.addView(tv)
        return btn
    }
        TextView(this).apply {
            this.text = text
            textSize = 10f
            setTextColor(Color.parseColor(color))
            setPadding(12, 6, 12, 6)
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

        for (btn in customButtons) {
            val placed = placedCustomBtns.any { it.id == btn.id }
            val chip = FrameLayout(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 130; height = 95
                    setMargins(4, 4, 4, 4)
                }
                setBackgroundColor(
                    if (placed) Color.parseColor("#1A1A1A")
                    else Color.argb(180,
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
                setColorFilter(if (placed) Color.parseColor("#333333") else Color.WHITE)
                layoutParams = FrameLayout.LayoutParams(38, 38).apply {
                    gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                    topMargin = 10
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            val labelTv = TextView(this).apply {
                text = btn.label
                textSize = 9f
                setTextColor(if (placed) Color.parseColor("#333333") else Color.WHITE)
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.BOTTOM
                    bottomMargin = 6
                }
            }

            chip.addView(iconIv)
            chip.addView(labelTv)

            if (!placed) {
                chip.setOnClickListener {
                    val cx = (overlayFrame.width / 2 - 40).toFloat()
                    val cy = (overlayFrame.height / 2 - 50).toFloat()
                    addCustomButton(btn, cx, cy)
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

    private fun addCustomButton(def: CustomBtn, x: Float, y: Float,
        w: Int = 80, h: Int = 100) {
        if (placedCustomBtns.any { it.id == def.id }) return
        val pb = PlacedCustomBtn(def.id, x, y, w, h)
        placedCustomBtns.add(pb)

        val container = FrameLayout(this).apply {
            this.x = x; this.y = y
            layoutParams = FrameLayout.LayoutParams(w, h)
        }
        buildCustomBtnView(def, pb, container, editMode = editMode)
        overlayFrame.addView(container)
        placedCustomViews[def.id] = container
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildCustomBtnView(def: CustomBtn, pb: PlacedCustomBtn,
        container: FrameLayout, editMode: Boolean) {

        container.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor("#111111"))
            setStroke(2, Color.parseColor("#444444"))
            cornerRadius = 16f
        }
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
                (pb.w * 0.45f).toInt(),
                (pb.h * 0.38f).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                topMargin = (pb.h * 0.15f).toInt()
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val labelTv = TextView(this).apply {
            text = def.label
            textSize = 8f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.05f
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
            // Edit border
            container.setBackgroundColor(Color.parseColor("#1A1A1A"))
            val stroke = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke(2, Color.parseColor("#FFD700"))
                    cornerRadius = 16f
                }
            }
            container.addView(stroke)

            // Delete
            val delBtn = TextView(this).apply {
                text = "✕"
                textSize = 9f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setBackgroundColor(Color.argb(230, 200, 0, 0))
                layoutParams = FrameLayout.LayoutParams(26, 26).apply {
                    gravity = Gravity.TOP or Gravity.END
                }
                setOnClickListener {
                    overlayFrame.removeView(container)
                    placedCustomViews.remove(def.id)
                    placedCustomBtns.removeAll { it.id == def.id }
                }
            }

            // Resize handle
            val resizeBtn = TextView(this).apply {
                text = "⤡"
                textSize = 11f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setBackgroundColor(Color.argb(230, 0, 100, 200))
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
                        val nw = (rsW + d).coerceIn(50, 280)
                        val nh = (rsH + d / 2).coerceIn(40, 240)
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
                        v.elevation = 12f; true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val nx = (e.rawX + dX).coerceIn(0f,
                            (overlayFrame.width - v.width).toFloat())
                        val ny = (e.rawY + dY).coerceIn(0f,
                            (overlayFrame.height - v.height - 52f))
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
                        customBtnStates[def.id] = true
                        container.background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.argb(60, Color.red(def.pressColor), Color.green(def.pressColor), Color.blue(def.pressColor))); setStroke(2, Color.parseColor("#444444")); cornerRadius = 16f }
                        iconIv.setColorFilter(def.pressColor)
                        labelTv.setTextColor(def.pressColor)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                            vibrator.vibrate(VibrationEffect.createOneShot(
                                30, VibrationEffect.DEFAULT_AMPLITUDE))
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        customBtnStates[def.id] = false
                        container.background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.parseColor("#111111")); setStroke(2, Color.parseColor("#444444")); cornerRadius = 16f }
                        iconIv.setColorFilter(Color.parseColor("#888888"))
                        labelTv.setTextColor(Color.parseColor("#888888"))
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun saveCustomLayout() {
        val arr = JSONArray()
        for (pb in placedCustomBtns) {
            arr.put(JSONObject().apply {
                put("id", pb.id)
                put("x", pb.x)
                put("y", pb.y)
                put("w", pb.w)
                put("h", pb.h)
            })
        }
        getSharedPreferences("mrb_custom", Context.MODE_PRIVATE)
            .edit().putString("custom_v1", arr.toString()).apply()
        Toast.makeText(this, "✅ Saved!", Toast.LENGTH_SHORT).show()
    }

    private fun loadCustomLayout() {
        val json = getSharedPreferences("mrb_custom", Context.MODE_PRIVATE)
            .getString("custom_v1", null) ?: return
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val def = customButtons.find { it.id == o.getString("id") } ?: continue
                addCustomButton(def,
                    o.getDouble("x").toFloat(),
                    o.getDouble("y").toFloat(),
                    o.optInt("w", 80),
                    o.optInt("h", 100))
            }
        } catch (_: Exception) {}
    }

    private fun confirmReset() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Remove all custom buttons?")
            .setPositiveButton("Remove") { _, _ ->
                for (pb in placedCustomBtns) {
                    placedCustomViews[pb.id]?.let { overlayFrame.removeView(it) }
                }
                placedCustomBtns.clear()
                placedCustomViews.clear()
                getSharedPreferences("mrb_custom", Context.MODE_PRIVATE)
                    .edit().remove("custom_v1").apply()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouch(id: Int, normalRes: Int, pressRes: Int,
        iconId: Int?, pressIconColor: Int, onPress: (Boolean) -> Unit) {
        val view = findViewById<View>(id) ?: return
        val icon = iconId?.let { findViewById<ImageView>(it) }
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    onPress(true)
                    view.setBackgroundResource(pressRes)
                    if (pressIconColor != 0) icon?.setColorFilter(pressIconColor)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        vibrator.vibrate(VibrationEffect.createOneShot(
                            30, VibrationEffect.DEFAULT_AMPLITUDE))
                    else { @Suppress("DEPRECATION") vibrator.vibrate(30) }
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

    private fun setupHid() {
        connectedDevice = HidService.connectedDevice
        if (connectedDevice != null) {
            txtStatus.text = "● ${connectedDevice?.name}"
            txtStatus.setTextColor(Color.parseColor("#00FF88"))
            if (!connectedAnimDone) playConnectedAnim()
        } else {
            txtStatus.text = "Waiting for device..."
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
        val totalSteps = 468; var step = 0
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
                    haptic(80); return
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
        val rawTilt = (filtX * 127f / 9.8f).toInt().coerceIn(-127, 127)
        tiltByte = when {
            rawTilt > 0 -> rawTilt.coerceAtLeast(10).toByte()
            rawTilt < 0 -> rawTilt.coerceAtMost(-10).toByte()
            else -> 0.toByte()
        }
        val now = System.currentTimeMillis()
        if (now - lastSend > 40) { sendReport(); lastSend = now }
        runOnUiThread {
            if (!animPlaying) {
                filtXUI = 0.08f * filtX + 0.92f * filtXUI
                wheelView.rotation = (filtXUI * 9f).coerceIn(-180f, 180f)
            }
            txtTilt.text = "%.1fu00b0".format(filtX * 9f)
            tiltBar.progress = (100 + (filtX / 9.8f * 100).toInt()).coerceIn(0, 200)
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendReport() {
        val device = HidService.connectedDevice ?: return
        val hid = HidService.hidDevice ?: return

        var b1 = 0; var b2 = 0

        // Default buttons
        if (btnA)     b1 = b1 or (1 shl 0)
        if (btnB)     b1 = b1 or (1 shl 1)
        if (gearDown) b1 = b1 or (1 shl 6)
        if (gearUp)   b1 = b1 or (1 shl 7)
        if (btnY)     b1 = b1 or (1 shl 4)
        if (btnX)     b1 = b1 or (1 shl 3)
        if (dpadUp)   b2 = b2 or (1 shl 2)
        if (dpadDown) b2 = b2 or (1 shl 3)
        if (dpadLeft) b2 = b2 or (1 shl 6)
        if (dpadRight)b2 = b2 or (1 shl 5)

        // Custom buttons
        for (def in customButtons) {
            if (customBtnStates[def.id] == true) {
                if (def.byte1bit >= 0) b1 = b1 or (1 shl def.byte1bit)
                if (def.byte2bit >= 0) b2 = b2 or (1 shl def.byte2bit)
            }
        }

        val gas   = if (gasOn)   0xFF.toByte() else 0x00.toByte()
        val brake = if (brakeOn) 0xFF.toByte() else 0x00.toByte()

        hid.sendReport(device, 1,
            byteArrayOf(b1.toByte(), b2.toByte(), tiltByte, 0x00, gas, brake))
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
