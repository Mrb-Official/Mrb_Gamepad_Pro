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
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.*

class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var rewardedAd: RewardedAd? = null

    private lateinit var ivIcon: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var tvDots: TextView
    private lateinit var btnWatchAd: TextView
    private lateinit var tvPremiumStatus: TextView

    private var dotCount = 0
    private val dotRunnable = object : Runnable {
        override fun run() {
            dotCount = (dotCount + 1) % 4
            tvDots.text = ".".repeat(dotCount)
            handler.postDelayed(this, 500)
        }
    }

    private val AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917" // Test ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.windowInsetsController?.apply {
            hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        try { MobileAds.initialize(this) } catch (e: Exception) { e.printStackTrace() }

        buildUI()
        checkPremium()

        // Permission check before starting service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val perms = arrayOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_ADVERTISE)
            val missing = perms.filter {
                checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                requestPermissions(missing.toTypedArray(), 99)
            } else {
                startService()
            }
        } else {
            startService()
        }

        playSound("boot.mp3")
        startPulse()
        handler.post(dotRunnable)
        loadRewardedAd()
    }

    private fun startService() {
        startForegroundService(Intent(this, HidService::class.java))
        HidService.onConnected = { device ->
            runOnUiThread { showConnectedAnim(device.name ?: "Device") }
        }
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
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 32
            }
        }

        tvPremiumStatus = TextView(this).apply {
            textSize = 11f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 12
            }
        }

        btnWatchAd = TextView(this).apply {
            text = "▶ Watch Ad = 1 Day Premium"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#FF6D00"))
            setPadding(32, 14, 32, 14)
            gravity = android.view.Gravity.CENTER
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 24f)
                }
            }
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = android.view.Gravity.CENTER
            }
            setOnClickListener { showRewardedAd() }
        }

        center.addView(ivIcon)
        center.addView(tvStatus)
        center.addView(tvDots)
        center.addView(tvSub)
        center.addView(tvPremiumStatus)
        center.addView(btnWatchAd)
        root.addView(center)
        setContentView(root)

        ivIcon.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(800)
            .setInterpolator(OvershootInterpolator())
            .start()
    }

    private fun checkPremium() {
        val prefs = getSharedPreferences("mrb_premium", Context.MODE_PRIVATE)
        val expiry = prefs.getLong("premium_expiry", 0L)
        val now = System.currentTimeMillis()
        if (expiry > now) {
            val hoursLeft = ((expiry - now) / 3600000).toInt()
            tvPremiumStatus.text = "⭐ Premium Active - ${hoursLeft}h remaining"
            tvPremiumStatus.setTextColor(Color.parseColor("#FFD700"))
            btnWatchAd.text = "▶ Watch Ad = Extend 1 Day"
        } else {
            tvPremiumStatus.text = "Watch ad to unlock Premium for 1 day"
            tvPremiumStatus.setTextColor(Color.argb(150, 255, 255, 255))
        }
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(this, AD_UNIT_ID, adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    runOnUiThread {
                        btnWatchAd.alpha = 1f
                        btnWatchAd.isEnabled = true
                    }
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    runOnUiThread { btnWatchAd.alpha = 0.5f }
                }
            })
    }

    private fun showRewardedAd() {
        val ad = rewardedAd ?: run {
            Toast.makeText(this, "Ad loading... try again", Toast.LENGTH_SHORT).show()
            loadRewardedAd()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                loadRewardedAd()
            }
        }
        ad.show(this) { grantPremium() }
    }

    private fun grantPremium() {
        val expiry = System.currentTimeMillis() + 86400000L
        getSharedPreferences("mrb_premium", Context.MODE_PRIVATE)
            .edit().putLong("premium_expiry", expiry).apply()
        runOnUiThread {
            tvPremiumStatus.text = "⭐ Premium Unlocked for 24 hours!"
            tvPremiumStatus.setTextColor(Color.parseColor("#FFD700"))
            btnWatchAd.text = "▶ Watch Ad = Extend 1 More Day"
            Toast.makeText(this, "🎉 Premium Active for 24 hours!", Toast.LENGTH_LONG).show()
        }
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
        ivIcon.clearAnimation()
        ivIcon.setColorFilter(Color.parseColor("#00FF88"))
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

    override fun onRequestPermissionsResult(
        req: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(req, perms, results)
        if (req == 99) startService()
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
