package com.mrb.controller.pro

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.*
import android.content.*
import android.os.*

class HidService : Service() {

    companion object {
        var hidDevice: BluetoothHidDevice? = null
        var connectedDevice: BluetoothDevice? = null
        var isRegistered = false
        var onConnected: ((BluetoothDevice) -> Unit)? = null
        var onDisconnected: (() -> Unit)? = null

        // Standard Xbox-style HID report = 9 bytes:
        // [0] Buttons 1-8
        // [1] Buttons 9-16
        // [2] Left X   = Left Joy X / Tilt / WASD LR  (-127..127)
        // [3] Left Y   = Left Joy Y / WASD UD          (-127..127)
        // [4] Right X  = Right Joy X / Touchpad X      (-127..127)
        // [5] Right Y  = Right Joy Y / Touchpad Y      (-127..127)
        // [6] L-Trigger= Brake  (0..255)
        // [7] R-Trigger= Gas    (0..255)
        // [8] Hat      = D-Pad Hat Switch

        val HID_DESC = byteArrayOf(
                val HID_DESC = byteArrayOf(
            0x05, 0x01,             // Usage Page (Generic Desktop)
            0x09, 0x05,             // Usage (Gamepad)
            0xa1.toByte(), 0x01,    // Collection (Application)
            0x85.toByte(), 0x01,    // Report ID (1)

            // ── 16 Buttons (2 Bytes) ──
            0x05, 0x09, 0x19, 0x01, 0x29, 0x10, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95.toByte(), 0x10, 0x81.toByte(), 0x02,

            // ── Axes (6 Bytes) ──
            0x05, 0x01,
            0x09, 0x30,             // X (Left Stick X)
            0x09, 0x31,             // Y (Left Stick Y)
            0x09, 0x32,             // Z (Touchpad X)
            0x09, 0x35,             // Rz (Touchpad Y)
            0x09, 0x33,             // Rx (Brake - LT)
            0x09, 0x34,             // Ry (Gas - RT)
            
            0x15, 0x81.toByte(),    // Min -127
            0x25, 0x7f,             // Max 127
            0x75, 0x08,             // 8 bits each
            0x95.toByte(), 0x06,    // Total 6 axes
            0x81.toByte(), 0x02,

            0xc0.toByte()           // End
        )
                )
            }
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotification()
        initHid()
    }

    private fun createNotification() {
        val channel = NotificationChannel(
            "mrb_hid", "MRB Gamepad",
            NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        startForeground(1, Notification.Builder(this, "mrb_hid")
            .setContentTitle("MRB Gamepad Pro")
            .setContentText("Waiting for connection...")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build())
    }

    @SuppressLint("MissingPermission")
    private fun initHid() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        btManager.adapter.getProfileProxy(this,
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(p: Int, proxy: BluetoothProfile?) {
                    hidDevice = proxy as BluetoothHidDevice
                    val sdp = BluetoothHidDeviceAppSdpSettings(
                        "MRB Gamepad Pro", "Tilt Controller", "MeetDev",
                        0x08.toByte(), HID_DESC)
                    hidDevice?.registerApp(sdp, null, null,
                        { it?.run() },
                        object : BluetoothHidDevice.Callback() {
                            override fun onConnectionStateChanged(
                                device: BluetoothDevice?, state: Int) {
                                when (state) {
                                    BluetoothProfile.STATE_CONNECTED -> {
                                        connectedDevice = device
                                        handler.post { onConnected?.invoke(device!!) }
                                    }
                                    BluetoothProfile.STATE_DISCONNECTED -> {
                                        connectedDevice = null
                                        handler.post { onDisconnected?.invoke() }
                                    }
                                }
                            }
                            override fun onAppStatusChanged(
                                d: BluetoothDevice?, registered: Boolean) {
                                isRegistered = registered
                            }
                        })
                }
                override fun onServiceDisconnected(p: Int) {
                    hidDevice = null; isRegistered = false
                }
            }, BluetoothProfile.HID_DEVICE)
    }

    override fun onDestroy() {
        super.onDestroy()
        hidDevice = null; connectedDevice = null; isRegistered = false
    }
}
