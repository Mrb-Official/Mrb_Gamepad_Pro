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

        val HID_DESC = byteArrayOf(
            0x05, 0x01,             // Usage Page (Generic Desktop)
            0x09, 0x05,             // Usage (Gamepad)
            0xa1.toByte(), 0x01,    // Collection (Application)
            0x85.toByte(), 0x01,    // Report ID (1)

            // ── Buttons 1-16 (2 Bytes) ──
            0x05, 0x09,             // Usage Page (Button)
            0x19, 0x01,             // Usage Minimum (1)
            0x29, 0x10,             // Usage Maximum (16)
            0x15, 0x00,             // Logical Minimum (0)
            0x25, 0x01,             // Logical Maximum (1)
            0x75, 0x01,             // Report Size (1 bit)
            0x95.toByte(), 0x10,    // Report Count (16)
            0x81.toByte(), 0x02,    // Input (Data, Var, Abs)

            // ── Left Joystick X & Y (2 Bytes) ──
            0x05, 0x01,             // Usage Page (Generic Desktop)
            0x09, 0x30,             // Usage (X)
            0x09, 0x31,             // Usage (Y)
            0x15, 0x81.toByte(),    // Min -127
            0x25, 0x7f,             // Max 127
            0x75, 0x08,             // Size 8 bits
            0x95.toByte(), 0x02,    // Count 2
            0x81.toByte(), 0x02,    // Input (Data, Var, Abs)

            // ── Right Joystick Rx & Ry (2 Bytes) ──
            0x09, 0x33,             // Usage (Rx) -> Right Stick X
            0x09, 0x34,             // Usage (Ry) -> Right Stick Y
            0x15, 0x81.toByte(),    
            0x25, 0x7f,             
            0x75, 0x08,             
            0x95.toByte(), 0x02,    
            0x81.toByte(), 0x02,    

            // ── Triggers Z & Rz (2 Bytes) ──
            0x09, 0x32,             // Usage (Z) -> Brake (LT)
            0x09, 0x35,             // Usage (Rz) -> Gas (RT)
            0x15, 0x00,             // Min 0
            0x26, 0xff.toByte(), 0x00, // Max 255
            0x75, 0x08,             // Size 8 bits
            0x95.toByte(), 0x02,    // Count 2
            0x81.toByte(), 0x02,    // Input

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
