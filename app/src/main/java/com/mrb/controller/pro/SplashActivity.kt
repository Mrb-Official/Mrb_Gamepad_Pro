package com.mrb.controller.pro

import android.animation.*
import android.bluetooth.*
import android.content.*
import android.content.pm.ActivityInfo
import android.graphics.*
import android.media.*
import android.os.*
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var ivIcon: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var tvDots: TextView
    private lateinit var rootLayout: FrameLayout

    private var dotCount = 0
    private val dotRunnable = object : Runnable {
        override fun run() {
            dotCount = (dotCount + 1) % 4
            tvDots.text = ".".repeat(dotCount)
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.windowInsetsController?.apply {
            hide(android.view.WindowInsets.Type.statusBars() or
                android.view.WindowInsets.Type.navigationBars())
            systemBarsBehavior =
                android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Build UI
        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#080808"))
        }

        val centerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT)
        }

        // BT Icon
        ivIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.stat_sys_data_bluetooth)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(120, 120).apply {
                gravity = android.view.Gravity.CENTER
                bottomMargin = 32
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        // Status text
        tvStatus = TextView(this).apply {
            text = "Waiting"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        // Dots
        tvDots = TextView(this).apply {
            text = "..."
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 24
            }
        }

        // Subtitle
        val tvSub = TextView(this).apply {
            text = "Connect with Bluetooth device to start gamepad"
            textSize = 13f
            setTextColor(Color.argb(140, 255, 255, 255))
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        centerLayout.addView(ivIcon)
        centerLayout.addView(tvStatus)
        centerLayout.addView(tvDots)
        centerLayout.addView(tvSub)
        rootLayout.addView(centerLayout)
        setContentView(rootLayout)

        // Boot animation
        ivIcon.alpha = 0f
        ivIcon.scaleX = 0.5f
        ivIcon.scaleY = 0.5f
        ivIcon.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(800)
            .setInterpolator(OvershootInterpolator())
            .start()

        // Pulse animation on icon
        startPulse()

        // Boot sound
        playBootSound()

        // Start dots
        handler.post(dotRunnable)

        // Init BT
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = btManager.adapter
        initHidWatcher()
    }

    private fun startPulse() {
        val pulse = ObjectAnimator.ofPropertyValuesHolder(
            ivIcon,
            PropertyValuesHolder.ofFloat("scaleX", 1f, 1.1f, 1f),
            PropertyValuesHolder.ofFloat("scaleY", 1f, 1.1f, 1f)
        ).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        pulse.start()
    }

    private fun playBootSound() {
        try {
            mediaPlayer = MediaPlayer().apply {
                val afd = assets.openFd("boot.mp3")
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                prepare()
                // Fade in
                setVolume(0f, 0f)
                start()
                // Gradual volume up
                fadeVolume(0f, 1f, 800)
                // Auto fade out before end
                val duration = duration
                handler.postDelayed({
                    fadeVolume(1f, 0f, 600)
                }, (duration - 600).toLong().coerceAtLeast(0))
            }
        } catch (e: Exception) {
            // No sound file = skip
        }
    }

    private fun fadeVolume(from: Float, to: Float, durationMs: Long) {
        val steps = 20
        val stepTime = durationMs / steps
        var current = from
        val delta = (to - from) / steps
        val r = object : Runnable {
            var step = 0
            override fun run() {
                if (step >= steps) {
                    mediaPlayer?.setVolume(to, to)
                    return
                }
                current += delta
                mediaPlayer?.setVolume(current, current)
                step++
                handler.postDelayed(this, stepTime)
            }
        }
        handler.post(r)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun initHidWatcher() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        btManager.adapter.getProfileProxy(this,
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(p: Int, proxy: BluetoothProfile?) {
                    val hid = proxy as BluetoothHidDevice
                    val sdp = BluetoothHidDeviceAppSdpSettings(
                        "MRB Gamepad Pro", "Tilt Controller", "MeetDev",
                        0x08.toByte(), byteArrayOf())
                    hid.registerApp(sdp, null, null, { it?.run() },
                        object : BluetoothHidDevice.Callback() {
                            override fun onConnectionStateChanged(
                                device: BluetoothDevice?, state: Int) {
                                if (state == BluetoothProfile.STATE_CONNECTED) {
                                    runOnUiThread { showConnectedAnim(device?.name ?: "Device") }
                                }
                            }
                            override fun onAppStatusChanged(
                                d: BluetoothDevice?, registered: Boolean) {
                                if (registered) runOnUiThread {
                                    tvStatus.text = "Waiting"
                                }
                            }
                        })
                }
                override fun onServiceDisconnected(p: Int) {}
            }, BluetoothProfile.HID_DEVICE)
    }

    private fun showConnectedAnim(deviceName: String) {
        handler.removeCallbacks(dotRunnable)

        // Play connect sound
        playConnectSound()

        // Change icon to controller
        ivIcon.setImageResource(android.R.drawable.ic_menu_send)
        ivIcon.setColorFilter(Color.parseColor("#00FF88"))

        // Bounce animation
        ivIcon.animate()
            .scaleX(1.3f).scaleY(1.3f)
            .setDuration(200)
            .withEndAction {
                ivIcon.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(300)
                    .setInterpolator(OvershootInterpolator())
                    .start()
            }.start()

        tvStatus.text = "Device Connected"
        tvStatus.setTextColor(Color.parseColor("#00FF88"))
        tvDots.text = ""

        // Go to MainActivity after 1.5s
        handler.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 1500)
    }

    private fun playConnectSound() {
        try {
            val mp = MediaPlayer().apply {
                val afd = assets.openFd("connect.mp3")
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                prepare()
                setVolume(0f, 0f)
                start()
                fadeVolumeStatic(this, 0f, 1f, 400, handler)
                handler.postDelayed({
                    fadeVolumeStatic(this, 1f, 0f, 500, handler)
                }, 800)
            }
        } catch (e: Exception) {}
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.windowInsetsController?.apply {
                hide(android.view.WindowInsets.Type.statusBars() or
                    android.view.WindowInsets.Type.navigationBars())
                systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        mediaPlayer?.release()
    }

    companion object {
        fun fadeVolumeStatic(mp: MediaPlayer, from: Float, to: Float,
            durationMs: Long, handler: Handler) {
            val steps = 20
            val stepTime = durationMs / steps
            var current = from
            val delta = (to - from) / steps
            val r = object : Runnable {
                var step = 0
                override fun run() {
                    if (step >= steps) { mp.setVolume(to, to); return }
                    current += delta
                    mp.setVolume(current, current)
                    step++
                    handler.postDelayed(this, stepTime)
                }
            }
            handler.post(r)
        }
    }
}
