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

        // Report layout (8 bytes total):
        // [0] Buttons 1-8   (byte1)
        // [1] Buttons 9-16  (byte2)
        // [2] X  axis       (left joystick / tilt steering) -127..127
        // [3] Y  axis       (left joystick Y)               -127..127
        // [4] Rx axis       (right joystick X)              -127..127
        // [5] Ry axis       (right joystick Y)              -127..127
        // [6] Gas  (throttle) 0..255
        // [7] Brake          0..255

        val HID_DESC = byteArrayOf(
            0x05, 0x01,             // Usage Page (Generic Desktop)
            0x09, 0x05,             // Usage (Gamepad)
            0xa1.toByte(), 0x01,    // Collection (Application)
            0x85.toByte(), 0x01,    // Report ID (1)

            // ── Buttons 1-8 ──────────────────────────────
            0x05, 0x09,             // Usage Page (Button)
            0x19, 0x01,             // Usage Minimum (1)
            0x29, 0x08,             // Usage Maximum (8)
            0x15, 0x00,             // Logical Minimum (0)
            0x25, 0x01,             // Logical Maximum (1)
            0x75, 0x01,             // Report Size (1)
            0x95.toByte(), 0x08,    // Report Count (8)
            0x81.toByte(), 0x02,    // Input (Data, Var, Abs)

            // ── Buttons 9-16 ─────────────────────────────
            0x05, 0x09,
            0x19, 0x09,
            0x29, 0x10,
            0x15, 0x00,
            0x25, 0x01,
            0x75, 0x01,
            0x95.toByte(), 0x08,
            0x81.toByte(), 0x02,

            // ── Left Joystick X (steering / tilt) ────────
            0x05, 0x01,             // Usage Page (Generic Desktop)
            0x09, 0x30,             // Usage (X)
            0x15, 0x81.toByte(),    // Logical Minimum (-127)
            0x25, 0x7f,             // Logical Maximum (127)
            0x75, 0x08,             // Report Size (8)
            0x95.toByte(), 0x01,    // Report Count (1)
            0x81.toByte(), 0x02,    // Input (Data, Var, Abs)

            // ── Left Joystick Y ───────────────────────────
            0x09, 0x31,             // Usage (Y)
            0x15, 0x81.toByte(),
            0x25, 0x7f,
            0x75, 0x08,
            0x95.toByte(), 0x01,
            0x81.toByte(), 0x02,

            // ── Right Joystick X (Rx) ─────────────────────
            0x09, 0x33,             // Usage (Rx)
            0x15, 0x81.toByte(),
            0x25, 0x7f,
            0x75, 0x08,
            0x95.toByte(), 0x01,
            0x81.toByte(), 0x02,

            // ── Right Joystick Y (Ry) ─────────────────────
            0x09, 0x34,             // Usage (Ry)
            0x15, 0x81.toByte(),
            0x25, 0x7f,
            0x75, 0x08,
            0x95.toByte(), 0x01,
            0x81.toByte(), 0x02,

            // ── Gas / Throttle ────────────────────────────
            0x05, 0x02,             // Usage Page (Simulation)
            0x09, 0xC4.toByte(),    // Usage (Accelerator)
            0x15, 0x00,
            0x26, 0xff.toByte(), 0x00,
            0x75, 0x08,
            0x95.toByte(), 0x01,
            0x81.toByte(), 0x02,

            // ── Brake ─────────────────────────────────────
            0x09, 0xC5.toByte(),    // Usage (Brake)
            0x15, 0x00,
            0x26, 0xff.toByte(), 0x00,
            0x75, 0x08,
            0x95.toByte(), 0x01,
            0x81.toByte(), 0x02,

            0xc0.toByte()           // End Collection
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
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
        val notif = Notification.Builder(this, "mrb_hid")
            .setContentTitle("MRB Gamepad Pro")
            .setContentText("Waiting for connection...")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build()
        startForeground(1, notif)
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
                    hidDevice = null
                    isRegistered = false
                }
            }, BluetoothProfile.HID_DEVICE)
    }

    override fun onDestroy() {
        super.onDestroy()
        hidDevice = null
        connectedDevice = null
        isRegistered = false
    }
}
