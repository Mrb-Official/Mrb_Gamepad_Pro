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

        // 🛠️ THE ULTIMATE 8-BYTE HID DESCRIPTOR (WITH TOUCHPAD) 🛠️
        // Report layout (8 bytes total):
        // [0] Buttons 1-8 (Action Buttons)
        // [1] Buttons 9-16 (D-Pad & Extras)
        // [2] X  axis (Left Stick X / Steering)
        // [3] Y  axis (Left Stick Y / Aage-Peeche)
        // [4] Z  axis (Right Stick X / Touchpad X)
        // [5] Rz axis (Right Stick Y / Touchpad Y)
        // [6] Brake (L2 / LT)
        // [7] Gas (Accelerator / R2 / RT)

        val HID_DESC = byteArrayOf(
            // --- HEADER ---
            0x05, 0x01,                 // Usage Page (Generic Desktop Ctrls)
            0x09, 0x05,                 // Usage (Game Pad)
            0xa1.toByte(), 0x01,        // Collection (Application)
            0x85.toByte(), 0x01,        // Report ID (1)
            
            // --- BYTE 1 & 2: 16 Buttons (Action + D-Pad) ---
            0x05, 0x09,                 // Usage Page (Button)
            0x19, 0x01,                 // Usage Minimum (1)
            0x29, 0x10,                 // Usage Maximum (16)
            0x15, 0x00,                 // Logical Minimum (0)
            0x25, 0x01,                 // Logical Maximum (1)
            0x75, 0x01,                 // Report Size (1 bit)
            0x95.toByte(), 0x10,        // Report Count (16 buttons = 2 Bytes)
            0x81.toByte(), 0x02,        // Input (Data,Var,Abs)
            
            // --- BYTE 3, 4, 5, 6: Left & Right Joysticks (Touchpad) ---
            0x05, 0x01,                 // Usage Page (Generic Desktop Ctrls)
            0x09, 0x30,                 // Usage (X) - Left Stick Left/Right
            0x09, 0x31,                 // Usage (Y) - Left Stick Up/Down
            0x09, 0x32,                 // Usage (Z) - Right Stick Left/Right (Touchpad / Camera)
            0x09, 0x35,                 // Usage (Rz) - Right Stick Up/Down (Touchpad / Camera)
            0x15, 0x81.toByte(),        // Logical Minimum (-127)
            0x25, 0x7f,                 // Logical Maximum (127)
            0x75, 0x08,                 // Report Size (8 bits)
            0x95.toByte(), 0x04,        // Report Count (4 axes = 4 Bytes)
            0x81.toByte(), 0x02,        // Input (Data,Var,Abs)
            
            // --- BYTE 7: Brake (L2 / LT) ---
            0x05, 0x02,                 // Usage Page (Sim Ctrls)
            0x09, 0xC5.toByte(),        // Usage (Brake)
            0x15, 0x00,                 // Logical Minimum (0)
            0x26, 0xff.toByte(), 0x00,  // Logical Maximum (255)
            0x75, 0x08,                 // Report Size (8 bits)
            0x95.toByte(), 0x01,        // Report Count (1 Byte)
            0x81.toByte(), 0x02,        // Input (Data,Var,Abs)
            
            // --- BYTE 8: Gas (Accelerator / R2 / RT) ---
            0x05, 0x02,                 // Usage Page (Sim Ctrls)
            0x09, 0xC4.toByte(),        // Usage (Accelerator)
            0x15, 0x00,                 // Logical Minimum (0)
            0x26, 0xff.toByte(), 0x00,  // Logical Maximum (255)
            0x75, 0x08,                 // Report Size (8 bits)
            0x95.toByte(), 0x01,        // Report Count (1 Byte)
            0x81.toByte(), 0x02,        // Input (Data,Var,Abs)
            
            // --- END ---
            0xc0.toByte()               // End Collection
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
                    
                    // 🛠️ TERA ORIGINAL STABLE SDP SETUP (Bina QoS ke) 🛠️
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
                                        handler.post { device?.let { onConnected?.invoke(it) } }
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
