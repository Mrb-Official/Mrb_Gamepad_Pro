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

    // ✅ FIX: Correct HID descriptor with proper byte conversion
        val HID_DESC = byteArrayOf(
            0x05.toByte(), 0x01.toByte(), 0x09.toByte(), 0x05.toByte(),
            0xA1.toByte(), 0x01.toByte(), 0x85.toByte(), 0x01.toByte(),
            0x05.toByte(), 0x09.toByte(), 0x19.toByte(), 0x01.toByte(),
            0x29.toByte(), 0x10.toByte(), 0x15.toByte(), 0x00.toByte(),
            0x25.toByte(), 0x01.toByte(), 0x75.toByte(), 0x01.toByte(),
            0x95.toByte(), 0x10.toByte(), 0x81.toByte(), 0x02.toByte(),
            0x05.toByte(), 0x01.toByte(), 0x09.toByte(), 0x30.toByte(),
            0x09.toByte(), 0x31.toByte(), 0x15.toByte(), 0x81.toByte(),
            // ✅ FIX: Use signed range for thumbsticks (-127 to 127)
            0x25.toByte(), 0x7F.toByte(),                         
            0x75.toByte(), 0x08.toByte(), 0x95.toByte(), 0x02.toByte(),
            0x81.toByte(), 0x02.toByte(),
            0x09.toByte(), 0x32.toByte(), 0x09.toByte(), 0x35.toByte(),
            // ✅ FIX: Use signed range for touchpad axes (-127 to 127)
            0x15.toByte(), 0x81.toByte(), 0x25.toByte(), 0x7F.toByte(), 
            0x75.toByte(), 0x08.toByte(), 0x95.toByte(), 0x02.toByte(),
            0x81.toByte(), 0x02.toByte(),
            0x09.toByte(), 0x33.toByte(), 0x09.toByte(), 0x34.toByte(),
            // ✅ FIX: Use unsigned range for triggers (0 to 255)
            0x15.toByte(), 0x00.toByte(), 0x25.toByte(), 0xFF.toByte(), 
            0x75.toByte(), 0x08.toByte(), 0x95.toByte(), 0x02.toByte(),
            0x81.toByte(), 0x02.toByte(),
            0xC0.toByte()
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() { super.onCreate(); createNotification(); initHid() }

    private fun createNotification() {
        val channel = NotificationChannel("mrb_hid", "MRB Gamepad", NotificationManager.IMPORTANCE_LOW)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
        startForeground(1, Notification.Builder(this, "mrb_hid")
            .setContentTitle("MRB Gamepad Pro")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build())
    }

    @SuppressLint("MissingPermission")
    private fun initHid() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        btManager.adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                hidDevice = proxy as BluetoothHidDevice
                val sdp = BluetoothHidDeviceAppSdpSettings(
                    "MRB Gamepad Pro",
                    "Controller",
                    "MeetDev",
                    0x08.toByte(),
                    HID_DESC
                )
                hidDevice?.registerApp(sdp, null, null, { it?.run() }, object : BluetoothHidDevice.Callback() {
                    override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                        if (state == BluetoothProfile.STATE_CONNECTED) {
                            connectedDevice = device
                            handler.post { onConnected?.invoke(device!!) }
                        } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                            connectedDevice = null
                            handler.post { onDisconnected?.invoke() }
                        }
                    }
                    override fun onAppStatusChanged(device: BluetoothDevice?, registered: Boolean) {
                        isRegistered = registered
                    }
                })
            }
            override fun onServiceDisconnected(profile: Int) {
                hidDevice = null
                isRegistered = false
            }
        }, BluetoothProfile.HID_DEVICE)
    }
}
