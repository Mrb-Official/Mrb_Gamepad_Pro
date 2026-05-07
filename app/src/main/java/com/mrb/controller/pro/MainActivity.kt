package com.mrb.controller.pro

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.*
import android.content.pm.ActivityInfo
import android.graphics.*
import android.hardware.*
import android.os.*
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.*
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

    private var leftJoyX:  Byte = 0
    private var leftJoyY:  Byte = 0
    private var rightJoyX: Byte = 0
    private var rightJoyY: Byte = 0

    private var tiltEnabled = true
    private var tiltByte: Byte = 0
    private var filtX    = 0f
    private var filtXUI  = 0f
    private val alpha    = 0.15f
    private var lastSend = 0L
    private var connectedAnimDone = false
    private var animPlaying = false
    private var editMode = false

    private var rewardedAd: RewardedAd? = null
    private val AD_UNIT_ID = "ca-app-pub-5087876801896320/3281731159"
    private val handler = Handler(Looper.getMainLooper())

    private val customButtons = listOf(
        CustomBtn("horn",       "HORN",    R.drawable.ic_btn_horn,       0xFFFFEB3B.toInt(), byte1bit = 6),
        CustomBtn("handbrake",  "H.BRAKE", R.drawable.ic_btn_handbrake,  0xFFFF5722.toInt(), byte1bit = 7),
        CustomBtn("camera",     "CAM",     R.drawable.ic_btn_camera,     0xFF9C27B0.toInt(), byte2bit = 4),
        CustomBtn("nitro",      "NITRO",   R.drawable.ic_btn_nitro,      0xFF00BCD4.toInt(), byte2bit = 5),
        CustomBtn("lights",     "LIGHTS",  R.drawable.ic_btn_lights,     0xFFFFC107.toInt(), byte2bit = 6),
        CustomBtn("look_left",  "LOOK<",   R.drawable.ic_btn_look_left,  0xFF795548.toInt(), byte2bit = 7),
        CustomBtn("look_right", "LOOK>",   R.drawable.ic_btn_look_right, 0xFF8D6E63.toInt(), byte2bit = 7),
        CustomBtn("l1",         "L1",      R.drawable.ic_btn_l1,         0xFF3F51B5.toInt(), byte1bit = 4),
        CustomBtn("r1",         "R1",      R.drawable.ic_btn_r1,         0xFF5C6BC0.toInt(), byte1bit = 5),
        CustomBtn("start",      "START",   R.drawable.ic_btn_start,      0xFF4CAF50.toInt(), byte2bit = 4),
        CustomBtn("select",     "SELECT",  R.drawable.ic_btn_select,     0xFF388E3C.toInt(), byte2bit = 5),
        CustomBtn("reset",      "RESET",   R.drawable.ic_btn_reset,      0xFFF44336.toInt()),
        CustomBtn("pause",      "PAUSE",   R.drawable.ic_btn_pause,      0xFF9E9E9E.toInt()),
        CustomBtn("hazard",     "HAZARD",  R.drawable.ic_btn_hazard,     0xFFFF9800.toInt()),
        CustomBtn("boost",      "BOOST",   R.drawable.ic_btn_boost,      0xFF00E5FF.toInt()),
        CustomBtn("wipers",     "WIPERS",  R.drawable.ic_btn_wipers,     0xFF607D8B.toInt()),
        CustomBtn("map",        "MAP",     R.drawable.ic_btn_map,        0xFF26C6DA.toInt()),
        CustomBtn("touchpad",   "TOUCHPAD",R.drawable.ic_btn_custom,     0xFF00BCD4.toInt()),
        CustomBtn("left_joy",   "L.STICK", R.drawable.ic_btn_custom,     0xFF00BCD4.toInt()),
        CustomBtn("right_joy",  "R.STICK", R.drawable.ic_btn_custom,     0xFF4CAF50.toInt()),
        CustomBtn("tilt_tog",   "TILT",    R.drawable.ic_btn_custom,     0xFFFFD700.toInt()),
        CustomBtn("kb_w",       "W",       R.drawable.ic_btn_custom,     0xFF607D8B.toInt()),
        CustomBtn("kb_a",       "A",       R.drawable.ic_btn_custom,     0xFF607D8B.toInt()),
        CustomBtn("kb_s",       "S",       R.drawable.ic_btn_custom,     0xFF607D8B.toInt()),
        CustomBtn("kb_d",       "D",       R.drawable.ic_btn_custom,     0xFF607D8B.toInt()),
        CustomBtn("kb_space",   "SPACE",   R.drawable.ic_btn_custom,     0xFF455A64.toInt(), byte1bit = 6)
    )

    private val placedCustomBtns  = mutableListOf<PlacedCustomBtn>()
    private val placedCustomViews = mutableMapOf<String, FrameLayout>()
    private val customBtnStates   = mutableMapOf<String, Boolean>()
    private var editBar:   LinearLayout? = null
    private var crownView: ImageView?    = null

    private val defaultButtonIds = listOf(
        R.id.lay_brake, R.id.lay_gas, R.id.lay_gear_up, R.id.lay_gear_down,
        R.id.btn_a, R.id.btn_b, R.id.btn_x, R.id.btn_y,
        R.id.btn_dpad_up, R.id.btn_dpad_down, R.id.btn_dpad_left, R.id.btn_dpad_right
    )
    
    // 🔥 THE FIX: Changed non-existent drawables to the generic custom button icon
    private val defaultBtnInfo = mapOf(
        R.id.lay_brake to Pair("BRAKE", R.drawable.ic_btn_custom),
        R.id.lay_gas to Pair("GAS", R.drawable.ic_btn_custom),
        R.id.btn_a to Pair("A", R.drawable.btn_xbox_green),
        R.id.btn_b to Pair("B", R.drawable.btn_xbox_red),
        R.id.btn_x to Pair("X", R.drawable.btn_xbox_blue),
        R.id.btn_y to Pair("Y", R.drawable.btn_xbox_yellow),
        R.id.btn_dpad_up to Pair("UP", R.drawable.btn_normal_r12),
        R.id.btn_dpad_down to Pair("DOWN", R.drawable.btn_normal_r12),
        R.id.btn_dpad_left to Pair("LEFT", R.drawable.btn_normal_r12),
        R.id.btn_dpad_right to Pair("RIGHT", R.drawable.btn_normal_r12),
        R.id.lay_gear_up to Pair("R1", R.drawable.ic_btn_custom),
        R.id.lay_gear_down to Pair("L1", R.drawable.ic_btn_custom)
    )

    private var hiddenDefaultIds = mutableSetOf<Int>()
    private val defaultEditBadges = mutableListOf<View>()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val perms = arrayOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_ADVERTISE)
            val missing = perms.filter {
                checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) { requestPermissions(missing.toTypedArray(), 99); return }
        }
        initUI()
    }

    override fun onRequestPermissionsResult(req: Int, perms: Array<String>, results: IntArray) {
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
        txtTilt.visibility = View.GONE

        overlayFrame = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT)
        }
        (window.decorView as FrameLayout).addView(overlayFrame)

        val btnCrown = ImageView(this).apply {
            setImageResource(R.drawable.crown_24)
            setPadding(16, 8, 16, 8)
            layoutParams = FrameLayout.LayoutParams(80, 80).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = 4
            }
            setOnClickListener { onCrownClick() }
        }
        overlayFrame.addView(btnCrown)
        crownView = btnCrown
        updateCrownGlow(btnCrown)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        setupTouch(R.id.lay_brake,     R.drawable.btn_normal_r12,    R.drawable.btn_press_red,
            R.id.ic_brake,     0xFFFF4B4B.toInt()) { brakeOn = it }
        setupTouch(R.id.lay_gas,       R.drawable.btn_normal_r12,    R.drawable.btn_press_green,
            R.id.ic_gas,       0xFF3CFF6B.toInt()) { gasOn = it }
        setupTouch(R.id.lay_gear_up,   R.drawable.btn_gear_normal,   R.drawable.btn_press_orange,
            R.id.ic_gear_up,   0xFFFF6D00.toInt()) { gearUp = it }
        setupTouch(R.id.lay_gear_down, R.drawable.btn_gear_normal,   R.drawable.btn_press_blue,
            R.id.ic_gear_down, 0xFF00B4D8.toInt()) { gearDown = it }
        setupTouch(R.id.btn_a, R.drawable.btn_xbox_green,  R.drawable.btn_xbox_green_press,  null, 0) { btnA = it }
        setupTouch(R.id.btn_b, R.drawable.btn_xbox_red,    R.drawable.btn_xbox_red_press,    null, 0) { btnB = it }
        setupTouch(R.id.btn_x, R.drawable.btn_xbox_blue,   R.drawable.btn_xbox_blue_press,   null, 0) { btnX = it }
        setupTouch(R.id.btn_y, R.drawable.btn_xbox_yellow, R.drawable.btn_xbox_yellow_press, null, 0) { btnY = it }
        setupTouch(R.id.btn_dpad_up,    R.drawable.btn_normal_r12, R.drawable.btn_press_white, null, 0) { dpadUp    = it }
        setupTouch(R.id.btn_dpad_down,  R.drawable.btn_normal_r12, R.drawable.btn_press_white, null, 0) { dpadDown  = it }
        setupTouch(R.id.btn_dpad_left,  R.drawable.btn_normal_r12, R.drawable.btn_press_white, null, 0) { dpadLeft  = it }
        setupTouch(R.id.btn_dpad_right, R.drawable.btn_normal_r12, R.drawable.btn_press_white, null, 0) { dpadRight = it }

        overlayFrame.post { 
            loadCustomLayout() 
            loadHiddenDefaults() 
        }
        try { MobileAds.initialize(this) } catch (_: Exception) {}
        loadRewardedAd()
        setupHid()
    }

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
        crownView?.let { updateCrownGlow(it) }
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

    private fun isPremium(): Boolean {
        val expiry = getSharedPreferences("mrb_premium", MODE_PRIVATE).getLong("premium_expiry", 0L)
        return expiry > System.currentTimeMillis()
    }

    private fun updateCrownGlow(crown: ImageView) {
        if (isPremium()) crown.setColorFilter(Color.parseColor("#FFD700"))
        else crown.setColorFilter(Color.parseColor("#2196F3"))
    }

    private fun onCrownClick() {
        val existing = overlayFrame.findViewWithTag<View>("premium_popup")
        if (existing != null) {
            existing.animate().alpha(0f).translationY(80f).setDuration(250)
                .withEndAction { overlayFrame.removeView(existing) }.start()
            return
        }
        if (isPremium()) toggleEditMode() else showPremiumPopup()
    }

    private fun dismissPopup() {
        val v = overlayFrame.findViewWithTag<View>("premium_popup") ?: return
        v.animate().alpha(0f).translationY(80f).setDuration(250)
            .withEndAction { overlayFrame.removeView(v) }.start()
    }

    private fun showPremiumPopup() {
        val overlay = FrameLayout(this).apply {
            tag = "premium_popup"
            setBackgroundColor(Color.argb(200, 0, 0, 0))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setOnClickListener { dismissPopup() }
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1C1B1F"))
            setPadding(40, 32, 40, 32)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 32f)
                }
            }
            clipToOutline = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER; setMargins(60, 0, 60, 0)
            }
            alpha = 0f; translationY = 100f
            setOnClickListener { }
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }
        val crownIv = ImageView(this).apply {
            setImageResource(R.drawable.crown_24); setColorFilter(Color.parseColor("#FFD700"))
            layoutParams = LinearLayout.LayoutParams(48, 48).apply { setMargins(0, 0, 12, 0) }
        }
        val tvTitle = TextView(this).apply {
            text = "MRB Premium"; textSize = 20f; setTextColor(Color.parseColor("#FFD700"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnClose = TextView(this).apply {
            text = "✕"; textSize = 18f; setTextColor(Color.argb(180, 255, 255, 255))
            setPadding(12, 4, 12, 4); setOnClickListener { dismissPopup() }
        }
        topRow.addView(crownIv); topRow.addView(tvTitle); topRow.addView(btnClose)
        card.addView(topRow); 
        overlay.addView(card); overlayFrame.addView(overlay)
        card.animate().alpha(1f).translationY(0f).setDuration(300)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    private fun loadRewardedAd() {
        RewardedAd.load(this, AD_UNIT_ID, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
                override fun onAdFailedToLoad(e: LoadAdError) { rewardedAd = null }
            })
    }

    private fun toggleEditMode() {
        editMode = !editMode
        if (editMode) {
            showEditBar()
            placedCustomBtns.toList().forEach { pb ->
                val v   = placedCustomViews[pb.id] ?: return@forEach
                val def = customButtons.find { it.id == pb.id } ?: return@forEach
                v.removeAllViews()
                buildView(def, pb, v, inEditMode = true)
            }
            
            defaultButtonIds.forEach { id ->
                if (!hiddenDefaultIds.contains(id)) {
                    val v = findViewById<View>(id) ?: return@forEach
                    v.post {
                        val loc = IntArray(2)
                        v.getLocationOnScreen(loc)
                        val overlayLoc = IntArray(2)
                        overlayFrame.getLocationOnScreen(overlayLoc)
                        
                        val badge = FrameLayout(this).apply {
                            this.x = (loc[0] - overlayLoc[0]).toFloat()
                            this.y = (loc[1] - overlayLoc[1]).toFloat()
                            layoutParams = FrameLayout.LayoutParams(v.width, v.height)
                            setBackgroundColor(Color.argb(180, 200, 0, 0)) 
                            
                            addView(TextView(context).apply {
                                text = "✕"
                                textSize = 24f
                                setTextColor(Color.WHITE)
                                gravity = Gravity.CENTER
                                layoutParams = FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT, 
                                    FrameLayout.LayoutParams.MATCH_PARENT)
                            })
                            
                            setOnClickListener {
                                v.visibility = View.GONE
                                hiddenDefaultIds.add(id)
                                saveHiddenDefaults()
                                overlayFrame.removeView(this)
                                defaultEditBadges.remove(this)
                            }
                        }
                        overlayFrame.addView(badge)
                        defaultEditBadges.add(badge)
                    }
                }
            }
        } else {
            hideEditBar()
            placedCustomBtns.toList().forEach { pb ->
                val v   = placedCustomViews[pb.id] ?: return@forEach
                val def = customButtons.find { it.id == pb.id } ?: return@forEach
                v.removeAllViews()
                buildView(def, pb, v, inEditMode = false)
            }
            
            defaultEditBadges.forEach { overlayFrame.removeView(it) }
            defaultEditBadges.clear()
        }
    }

    private fun showEditBar() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#DD111111"))
            setPadding(12, 0, 12, 0); gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 52).apply { gravity = Gravity.BOTTOM }
        }
        val btnAdd   = iconBarBtn(R.drawable.add_circle_24, "ADD",   "#00C853") { showPicker() }
        val space    = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }
        val tvHint   = TextView(this).apply {
            text = "Drag  •  Resize  •  Delete"; textSize = 9f
            setTextColor(Color.argb(120, 255, 255, 255)); gravity = Gravity.CENTER
        }
        val space2   = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }
        val btnSave  = iconBarBtn(R.drawable.save_24, "SAVE", "#2196F3") { saveCustomLayout(); toggleEditMode() }
        val btnReset = iconBarBtn(R.drawable.auto_delete_24, "RESET", "#F44336") { confirmReset() }
        bar.addView(btnAdd); bar.addView(space); bar.addView(tvHint)
        bar.addView(space2); bar.addView(btnSave); bar.addView(btnReset)
        overlayFrame.addView(bar); editBar = bar
    }

    private fun hideEditBar() { editBar?.let { overlayFrame.removeView(it) }; editBar = null }

    private fun iconBarBtn(iconRes: Int, label: String, color: String, onClick: () -> Unit): LinearLayout {
        val btn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(12, 4, 12, 4); setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(4, 0, 4, 0) }
        }
        btn.addView(ImageView(this).apply {
            setImageResource(iconRes); setColorFilter(Color.parseColor(color))
            layoutParams = LinearLayout.LayoutParams(32, 32).apply { gravity = Gravity.CENTER_HORIZONTAL }
        })
        btn.addView(TextView(this).apply {
            text = label; textSize = 8f; setTextColor(Color.parseColor(color)); gravity = Gravity.CENTER
        })
        return btn
    }

    private fun showPicker() {
        val scroll = ScrollView(this)
        val grid = android.widget.GridLayout(this).apply { columnCount = 5; setPadding(8, 8, 8, 8) }
        
        // Custom Buttons
        for (btn in customButtons) {
            val placed = placedCustomBtns.any { it.id == btn.id }
            val chip = createPickerChip(btn.label, btn.iconRes, placed, btn.pressColor) {
                if (!placed) {
                    val cx = (overlayFrame.width / 2 - 60).toFloat()
                    val cy = (overlayFrame.height / 2 - 60).toFloat()
                    addCustomButton(btn, cx, cy)
                }
            }
            grid.addView(chip)
        }
        
        // Add Hidden Default Buttons Back
        val hiddenDefaults = defaultButtonIds.filter { hiddenDefaultIds.contains(it) }
        for (id in hiddenDefaults) {
            val info = defaultBtnInfo[id] ?: continue
            val chip = createPickerChip(info.first, info.second, false, 0xFF4CAF50.toInt()) {
                hiddenDefaultIds.remove(id)
                findViewById<View>(id)?.visibility = View.VISIBLE
                saveHiddenDefaults()
                toggleEditMode() // Refresh UI
                toggleEditMode()
            }
            grid.addView(chip)
        }
        
        scroll.addView(grid)
        android.app.AlertDialog.Builder(this)
            .setTitle("Add Button").setView(scroll)
            .setNegativeButton("Close", null).show()
    }

    private fun createPickerChip(label: String, iconRes: Int, placed: Boolean, pressColor: Int, onClick: () -> Unit): FrameLayout {
        val chip = FrameLayout(this).apply {
            layoutParams = android.widget.GridLayout.LayoutParams().apply {
                width = 130; height = 95; setMargins(4, 4, 4, 4)
            }
            setBackgroundColor(
                if (placed) Color.parseColor("#1A1A1A")
                else Color.argb(180, Color.red(pressColor), Color.green(pressColor), Color.blue(pressColor)))
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 12f)
                }
            }
            clipToOutline = true
        }
        chip.addView(ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(if (placed) Color.parseColor("#333333") else Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(38, 38).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP; topMargin = 10
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        })
        chip.addView(TextView(this).apply {
            text = label; textSize = 9f
            setTextColor(if (placed) Color.parseColor("#333333") else Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.BOTTOM; bottomMargin = 6 }
        })
        if (!placed) chip.setOnClickListener { onClick() }
        return chip
    }

    private fun addCustomButton(def: CustomBtn, x: Float, y: Float, w: Int = -1, h: Int = -1) {
        if (placedCustomBtns.any { it.id == def.id }) return
        val dw = if (w > 0) w else when {
            def.id == "left_joy" || def.id == "right_joy" -> 160
            def.id == "touchpad" -> 240
            def.id == "tilt_tog" -> 130
            def.id == "kb_space" -> 160
            def.id.startsWith("kb_") -> 60
            else -> 90
        }
        val dh = if (h > 0) h else when {
            def.id == "left_joy" || def.id == "right_joy" -> 160
            def.id == "touchpad" -> 200
            def.id == "tilt_tog" -> 50
            def.id.startsWith("kb_") -> 60
            else -> 100
        }
        val pb = PlacedCustomBtn(def.id, x, y, dw, dh)
        placedCustomBtns.add(pb)
        val container = FrameLayout(this).apply {
            this.x = x; this.y = y
            layoutParams = FrameLayout.LayoutParams(dw, dh)
        }
        buildView(def, pb, container, inEditMode = editMode)
        overlayFrame.addView(container)
        placedCustomViews[def.id] = container
    }

    private fun buildView(def: CustomBtn, pb: PlacedCustomBtn,
                          container: FrameLayout, inEditMode: Boolean) {
        container.removeAllViews()
        val lp = container.layoutParams as? FrameLayout.LayoutParams ?: FrameLayout.LayoutParams(pb.w, pb.h)
        lp.width = pb.w; lp.height = pb.h
        container.layoutParams = lp
        container.x = pb.x; container.y = pb.y

        when {
            def.id == "touchpad"                           -> buildTouchpad(pb, container, inEditMode)
            def.id == "left_joy" || def.id == "right_joy"  -> buildJoystick(def, pb, container, inEditMode)
            def.id == "tilt_tog"                           -> buildTiltToggle(pb, container, inEditMode)
            def.id.startsWith("kb_")                       -> buildKbKey(def, pb, container, inEditMode)
            else                                           -> buildNormalBtn(def, pb, container, inEditMode)
        }

        if (inEditMode) attachEditOverlay(def, pb, container)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildTouchpad(pb: PlacedCustomBtn, container: FrameLayout, inEditMode: Boolean) {
        container.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.argb(80, 50, 50, 50)) 
            setStroke(2, Color.argb(150, 200, 200, 200))
            cornerRadius = 24f
        }
        container.addView(TextView(this).apply {
            text = "TOUCH AREA"
            textSize = 12f
            setTextColor(Color.argb(100, 255, 255, 255))
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        })

        if (!inEditMode) {
            var startX = 0f
            var startY = 0f
            val maxDrag = 150f 

            container.setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        startX = e.x; startY = e.y; true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.x - startX).coerceIn(-maxDrag, maxDrag)
                        val dy = (e.y - startY).coerceIn(-maxDrag, maxDrag)
                        rightJoyX = ((dx / maxDrag) * 127).toInt().toByte()
                        rightJoyY = ((dy / maxDrag) * 127).toInt().toByte()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                        rightJoyX = 0; rightJoyY = 0; true
                    }
                    else -> false
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildJoystick(def: CustomBtn, pb: PlacedCustomBtn,
                              container: FrameLayout, inEditMode: Boolean) {
        val isLeft  = def.id == "left_joy"
        val color   = if (isLeft) "#00BCD4" else "#4CAF50"
        val size    = pb.w
        val thumbSz = (size * 0.38f).toInt()

        container.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(Color.argb(40, 255, 255, 255))
            setStroke(2, Color.parseColor(color))
        }

        val thumb = FrameLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor(color.replace("#", "#88")))
                setStroke(2, Color.parseColor(color))
            }
            layoutParams = FrameLayout.LayoutParams(thumbSz, thumbSz).apply { gravity = Gravity.CENTER }
        }
        thumb.addView(TextView(this).apply {
            text = if (isLeft) "L" else "R"; textSize = 14f
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        })
        container.addView(thumb)

        if (!inEditMode) {
            val maxR = (size - thumbSz) / 2f
            val cx = size / 2f; val cy = size / 2f
            container.setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE -> {
                        val dx = (e.x - cx).coerceIn(-maxR, maxR)
                        val dy = (e.y - cy).coerceIn(-maxR, maxR)
                        thumb.x = cx - thumbSz / 2f + dx
                        thumb.y = cy - thumbSz / 2f + dy
                        val nx = (dx / maxR * 127).toInt().toByte()
                        val ny = (dy / maxR * 127).toInt().toByte()
                        if (isLeft) { leftJoyX = nx; leftJoyY = ny }
                        else        { rightJoyX = nx; rightJoyY = ny }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                        thumb.x = cx - thumbSz / 2f; thumb.y = cy - thumbSz / 2f
                        if (isLeft) { leftJoyX = 0; leftJoyY = 0 }
                        else        { rightJoyX = 0; rightJoyY = 0 }
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun buildTiltToggle(pb: PlacedCustomBtn, container: FrameLayout, inEditMode: Boolean) {
        container.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(if (tiltEnabled) Color.parseColor("#44FFD700") else Color.parseColor("#22888888"))
            setStroke(2, if (tiltEnabled) Color.parseColor("#FFD700") else Color.parseColor("#666666"))
            cornerRadius = 24f
        }
        container.addView(TextView(this).apply {
            text = if (tiltEnabled) "🔄 TILT ON" else "🔄 TILT OFF"
            textSize = 11f
            setTextColor(if (tiltEnabled) Color.parseColor("#FFD700") else Color.argb(120, 255, 255, 255))
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        })
        if (!inEditMode) {
            container.setOnClickListener {
                tiltEnabled = !tiltEnabled
                if (!tiltEnabled) tiltByte = 0
                container.removeAllViews()
                buildTiltToggle(pb, container, false)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildKbKey(def: CustomBtn, pb: PlacedCustomBtn,
                           container: FrameLayout, inEditMode: Boolean) {
        container.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.argb(60, 200, 200, 200))
            setStroke(1, Color.argb(120, 255, 255, 255))
            cornerRadius = 10f
        }
        val tv = TextView(this).apply {
            text = def.label; textSize = 15f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        container.addView(tv)

        if (!inEditMode) {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            container.setOnTouchListener { _, e ->
                val pressed  = e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_POINTER_DOWN
                val released = e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_POINTER_UP || e.actionMasked == MotionEvent.ACTION_CANCEL
                if (pressed || released) {
                    when (def.id) {
                        "kb_w", "kb_up"    -> dpadUp    = pressed
                        "kb_a", "kb_left"  -> dpadLeft  = pressed
                        "kb_s", "kb_down"  -> dpadDown  = pressed
                        "kb_d", "kb_right" -> dpadRight = pressed
                    }
                    customBtnStates[def.id] = pressed
                    container.background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(if (pressed) Color.argb(160, 255, 255, 255) else Color.argb(60, 200, 200, 200))
                        setStroke(1, Color.argb(120, 255, 255, 255)); cornerRadius = 10f
                    }
                    tv.setTextColor(if (pressed) Color.parseColor("#111111") else Color.WHITE)
                    if (pressed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                }
                pressed || released
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildNormalBtn(def: CustomBtn, pb: PlacedCustomBtn,
                                container: FrameLayout, inEditMode: Boolean) {
        container.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor("#111111"))
            setStroke(2, Color.parseColor("#444444")); cornerRadius = 16f
        }
        container.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 16f)
            }
        }
        container.clipToOutline = true

        val iconIv = ImageView(this).apply {
            setImageResource(def.iconRes); setColorFilter(Color.parseColor("#888888"))
            layoutParams = FrameLayout.LayoutParams(
                (pb.w * 0.45f).toInt(), (pb.h * 0.38f).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                topMargin = (pb.h * 0.15f).toInt()
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val labelTv = TextView(this).apply {
            text = def.label; textSize = 8f; setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.BOTTOM; bottomMargin = 6 }
        }
        container.addView(iconIv); container.addView(labelTv)

        if (!inEditMode) {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            container.setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        customBtnStates[def.id] = true
                        container.background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(Color.argb(60, Color.red(def.pressColor),
                                Color.green(def.pressColor), Color.blue(def.pressColor)))
                            setStroke(2, Color.parseColor("#444444")); cornerRadius = 16f
                        }
                        iconIv.setColorFilter(def.pressColor); labelTv.setTextColor(def.pressColor)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                        customBtnStates[def.id] = false
                        container.background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(Color.parseColor("#111111"))
                            setStroke(2, Color.parseColor("#444444")); cornerRadius = 16f
                        }
                        iconIv.setColorFilter(Color.parseColor("#888888"))
                        labelTv.setTextColor(Color.parseColor("#888888"))
                        true
                    }
                    else -> false
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachEditOverlay(def: CustomBtn, pb: PlacedCustomBtn, container: FrameLayout) {
        container.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor("#1A1A1A"))
            setStroke(2, Color.parseColor("#FFD700")); cornerRadius = 16f
        }
        val delBtn = TextView(this).apply {
            text = "✕"; textSize = 10f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(230, 200, 0, 0))
            layoutParams = FrameLayout.LayoutParams(28, 28).apply { gravity = Gravity.TOP or Gravity.END }
            setOnClickListener {
                overlayFrame.removeView(container)
                placedCustomViews.remove(def.id)
                placedCustomBtns.removeAll { it.id == def.id }
                if (def.id == "left_joy")  { leftJoyX = 0; leftJoyY = 0 }
                if (def.id == "right_joy" || def.id == "touchpad") { rightJoyX = 0; rightJoyY = 0 }
            }
        }
        val resizeBtn = TextView(this).apply {
            text = "⤡"; textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(230, 0, 100, 200))
            layoutParams = FrameLayout.LayoutParams(28, 28).apply { gravity = Gravity.BOTTOM or Gravity.END }
        }
        container.addView(delBtn); container.addView(resizeBtn)

        var dX = 0f; var dY = 0f
        var resizing = false; var rsX = 0f; var rsW = 0; var rsH = 0

        resizeBtn.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { resizing = true; rsX = e.rawX; rsW = container.width; rsH = container.height; true }
                MotionEvent.ACTION_MOVE -> {
                    val d  = (e.rawX - rsX).toInt()
                    val nw = (rsW + d).coerceIn(40, 600)
                    val nh = (rsH + d / 2).coerceIn(40, 600)
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
                MotionEvent.ACTION_DOWN -> { dX = v.x - e.rawX; dY = v.y - e.rawY; v.elevation = 12f; true }
                MotionEvent.ACTION_MOVE -> {
                    val nx = (e.rawX + dX).coerceIn(0f, (overlayFrame.width  - v.width).toFloat())
                    val ny = (e.rawY + dY).coerceIn(0f, (overlayFrame.height - v.height - 52f))
                    v.x = nx; v.y = ny; pb.x = nx; pb.y = ny; true
                }
                MotionEvent.ACTION_UP -> { v.elevation = 0f; true }
                else -> false
            }
        }
    }

    private fun saveCustomLayout() {
        val arr = JSONArray()
        for (pb in placedCustomBtns) {
            arr.put(JSONObject().apply {
                put("id", pb.id); put("x", pb.x); put("y", pb.y)
                put("w", pb.w);   put("h", pb.h)
            })
        }
        getSharedPreferences("mrb_custom", MODE_PRIVATE)
            .edit().putString("custom_v1", arr.toString()).apply()
        Toast.makeText(this, "✅ Layout saved!", Toast.LENGTH_SHORT).show()
    }

    private fun loadCustomLayout() {
        val json = getSharedPreferences("mrb_custom", MODE_PRIVATE)
            .getString("custom_v1", null) ?: return
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o   = arr.getJSONObject(i)
                val def = customButtons.find { it.id == o.getString("id") } ?: continue
                addCustomButton(def,
                    o.getDouble("x").toFloat(), o.getDouble("y").toFloat(),
                    o.optInt("w", -1), o.optInt("h", -1))
            }
        } catch (_: Exception) {}
    }

    private fun saveHiddenDefaults() {
        val arr = JSONArray()
        hiddenDefaultIds.forEach { arr.put(it) }
        getSharedPreferences("mrb_custom", MODE_PRIVATE)
            .edit().putString("hidden_defaults", arr.toString()).apply()
    }

    private fun loadHiddenDefaults() {
        val json = getSharedPreferences("mrb_custom", MODE_PRIVATE)
            .getString("hidden_defaults", null) ?: return
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val id = arr.getInt(i)
                hiddenDefaultIds.add(id)
                findViewById<View>(id)?.visibility = View.GONE
            }
        } catch (_: Exception) {}
    }

    private fun confirmReset() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Reset Layout & Restore Buttons?")
            .setPositiveButton("Reset") { _, _ ->
                for (pb in placedCustomBtns)
                    placedCustomViews[pb.id]?.let { overlayFrame.removeView(it) }
                placedCustomBtns.clear(); placedCustomViews.clear()
                leftJoyX = 0; leftJoyY = 0; rightJoyX = 0; rightJoyY = 0
                getSharedPreferences("mrb_custom", MODE_PRIVATE).edit().remove("custom_v1").apply()
                
                hiddenDefaultIds.clear()
                defaultButtonIds.forEach { id ->
                    findViewById<View>(id)?.visibility = View.VISIBLE
                }
                getSharedPreferences("mrb_custom", MODE_PRIVATE).edit().remove("hidden_defaults").apply()
                Toast.makeText(this, "Layout Reset Complete", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null).show()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouch(id: Int, normalRes: Int, pressRes: Int,
                           iconId: Int?, pressIconColor: Int, onPress: (Boolean) -> Unit) {
        val view = findViewById<View>(id) ?: return
        val icon = iconId?.let { findViewById<ImageView>(it) }
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        view.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    onPress(true); view.setBackgroundResource(pressRes)
                    if (pressIconColor != 0) icon?.setColorFilter(pressIconColor)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                    else { @Suppress("DEPRECATION") vibrator.vibrate(30) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    onPress(false); view.setBackgroundResource(normalRes)
                    icon?.setColorFilter(Color.parseColor("#888888")); true
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
                connectedDevice = null; connectedAnimDone = false
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
        connectedAnimDone = true; animPlaying = true
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val totalSteps = 468; var step = 0
        fun haptic(ms: Long) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        val r = object : Runnable {
            override fun run() {
                if (step >= totalSteps) { wheelView.rotation = 0f; animPlaying = false; haptic(80); return }
                val t = step.toFloat() / totalSteps
                wheelView.rotation = -100f * sin(t * Math.PI.toFloat() * 2f)
                if (step == 0) haptic(40); if (step == totalSteps / 2) haptic(40)
                step++; handler.postDelayed(this, 16)
            }
        }
        handler.post(r)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        filtX = alpha * event.values[1] + (1 - alpha) * filtX
        if (tiltEnabled) {
            val raw = (filtX * 127f / 9.8f).toInt().coerceIn(-127, 127)
            tiltByte = when {
                raw > 0 -> raw.coerceAtLeast(10).toByte()
                raw < 0 -> raw.coerceAtMost(-10).toByte()
                else    -> 0
            }
        }
        val now = System.currentTimeMillis()
        if (now - lastSend > 40) { sendReport(); lastSend = now }
        runOnUiThread {
            if (!animPlaying) {
                filtXUI = 0.08f * filtX + 0.92f * filtXUI
                if (tiltEnabled) wheelView.rotation = (filtXUI * 9f).coerceIn(-180f, 180f)
            }
            tiltBar.progress = (100 + (filtX / 9.8f * 100).toInt()).coerceIn(0, 200)
        }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    @SuppressLint("MissingPermission")
    private fun sendReport() {
        try {
            val device = HidService.connectedDevice ?: return
            val hid    = HidService.hidDevice       ?: return
            
            var b1 = 0
            var b2 = 0
            
            // Standard Action Buttons (A, B, X, Y)
            if (btnA)     b1 = b1 or (1 shl 0)
            if (btnB)     b1 = b1 or (1 shl 1)
            if (btnX)     b1 = b1 or (1 shl 2)
            if (btnY)     b1 = b1 or (1 shl 3)
            if (gearDown) b1 = b1 or (1 shl 4) // L1 mapped properly
            if (gearUp)   b1 = b1 or (1 shl 5) // R1 mapped properly

            // D-Pad / WASD for Character Movement (Perfect mapping)
            if (dpadUp)   b2 = b2 or (1 shl 0)
            if (dpadDown) b2 = b2 or (1 shl 1)
            if (dpadLeft) b2 = b2 or (1 shl 2)
            if (dpadRight)b2 = b2 or (1 shl 3)

            // Custom Buttons logic safely mapped
            for (def in customButtons) {
                if (customBtnStates[def.id] == true) {
                    if (def.byte1bit >= 0) b1 = b1 or (1 shl def.byte1bit)
                    if (def.byte2bit >= 0) b2 = b2 or (1 shl def.byte2bit)
                }
            }
            
            val finalLeftX = if (leftJoyX.toInt() != 0) leftJoyX else tiltByte
            val finalLeftY = leftJoyY
            
            val finalRightX = rightJoyX
            val finalRightY = rightJoyY
            
            val gas   = if (gasOn)   0xFF.toByte() else 0x00.toByte()
            val brake = if (brakeOn) 0xFF.toByte() else 0x00.toByte()

            // 🔥 TERA EXACT 8-BYTE PAYLOAD 🔥
            hid.sendReport(device, 1,
                byteArrayOf(
                    b1.toByte(), b2.toByte(), 
                    finalLeftX, finalLeftY, 
                    finalRightX, finalRightY,
                    gas, brake
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
