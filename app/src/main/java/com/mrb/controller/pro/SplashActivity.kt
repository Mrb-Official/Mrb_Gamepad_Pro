package com.mrb.controller.pro

import android.animation.*
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

    private val handler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null

    private lateinit var ivIcon: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var tvDots: TextView

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
            hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        buildUI()

        // Start HID Service
        startForegroundService(Intent(this, HidService::class.java))

        // Listen for connection
        HidService.onConnected = { device ->
            runOnUiThread { showConnectedAnim(device.name ?: "Device") }
        }

        // Boot sound
        playSound("boot.mp3")

        // Pulse + dots
        startPulse()
        handler.post(dotRunnable)
    }

    private fun buildUI() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#080808"))
        }

        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT)
        }

        ivIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.stat_sys_data_bluetooth)
            setColorFilter(Color.WHITE)
            alpha = 0f; scaleX = 0.5f; scaleY = 0.5f
            layoutParams = LinearLayout.LayoutParams(130, 130).apply {
                gravity = android.view.Gravity.CENTER
                bottomMargin = 28
            }
        }

        tvStatus = TextView(this).apply {
            text = "Waiting"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = android.view.Gravity.CENTER
        }

        tvDots = TextView(this).apply {
            text = "..."
            textSize = 18f
            setTextColor(Color.argb(180, 255, 255, 255))
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 20
            }
        }

        val tvSub = TextView(this).apply {
            text = "Connect with Bluetooth device to start gamepad"
            textSize = 12f
            setTextColor(Color.argb(120, 255, 255, 255))
            gravity = android.view.Gravity.CENTER
        }

        center.addView(ivIcon)
        center.addView(tvStatus)
        center.addView(tvDots)
        center.addView(tvSub)
        root.addView(center)
        setContentView(root)

        // Animate icon in
        ivIcon.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(800)
            .setInterpolator(OvershootInterpolator())
            .start()
    }

    private fun startPulse() {
        ObjectAnimator.ofPropertyValuesHolder(
            ivIcon,
            PropertyValuesHolder.ofFloat("scaleX", 1f, 1.12f, 1f),
            PropertyValuesHolder.ofFloat("scaleY", 1f, 1.12f, 1f)
        ).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun playSound(filename: String) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                val afd = assets.openFd(filename)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                prepare()
                setVolume(0f, 0f)
                start()
                fadeVol(0f, 1f, 600)
                val dur = duration.toLong()
                handler.postDelayed({ fadeVol(1f, 0f, 500) },
                    (dur - 600).coerceAtLeast(100))
            }
        } catch (_: Exception) {}
    }

    private fun MediaPlayer.fadeVol(from: Float, to: Float, ms: Long) {
        val steps = 20; val stepMs = ms / steps
        var vol = from; val delta = (to - from) / steps
        val r = object : Runnable {
            var i = 0
            override fun run() {
                if (i++ >= steps) { setVolume(to, to); return }
                vol += delta
                setVolume(vol, vol)
                handler.postDelayed(this, stepMs)
            }
        }
        handler.post(r)
    }

    private fun showConnectedAnim(deviceName: String) {
        handler.removeCallbacks(dotRunnable)
        mediaPlayer?.release()

        // Change icon
        ivIcon.clearAnimation()
        ivIcon.setColorFilter(Color.parseColor("#00FF88"))

        // Bounce
        ivIcon.animate()
            .scaleX(1.4f).scaleY(1.4f).setDuration(150)
            .withEndAction {
                ivIcon.animate()
                    .scaleX(1f).scaleY(1f).setDuration(400)
                    .setInterpolator(OvershootInterpolator(2f))
                    .start()
            }.start()

        tvStatus.text = "Device Connected"
        tvStatus.setTextColor(Color.parseColor("#00FF88"))
        tvDots.text = ""

        playSound("connect.mp3")

        handler.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 1800)
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
        mediaPlayer?.release()
        HidService.onConnected = null
    }
}
