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

        // 🔥 NAYA 9-BYTE HYBRID DESCRIPTOR (Mobile + PC/Xbox Dono ke liye) 🔥
        val HID_DESC = byteArrayOf(
            0x05, 0x01, 0x09, 0x05, 0xa1.toByte(), 0x01, 0x85.toByte(), 0x01,

            // Buttons (16 buttons)
            0x05, 0x09, 0x19, 0x01, 0x29, 0x10, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95.toByte(), 0x10, 0x81.toByte(), 0x02,

            // Axes: X, Y, Z, Rz, Rx, Ry (Total 6 axes)
            0x05, 0x01, 
            0x09, 0x30, 0x09, 0x31, // X, Y (Left Stick)
            0x09, 0x32, 0x09, 0x35, // Z, Rz (Right Stick - Android Tester ke liye)
            0x09, 0x33, 0x09, 0x34, // Rx, Ry (Triggers - PC/GTA 5 ke liye)
            0x15, 0x81.toByte(), 0x25, 0x7f, 0x75, 0x08, 0x95.toByte(), 0x06, 0x81.toByte(), 0x02,

            // D-Pad (Hat Switch)
            0x09, 0x39, 0x15, 0x00, 0x25, 0x07, 0x35, 0x00, 0x46, 0x3b, 0x01, 0x75, 0x04, 0x95.toByte(), 0x01, 0x81.toByte(), 0x42,
            0x75, 0x04, 0x95.toByte(), 0x01, 0x81.toByte(), 0x03, // Padding

            0xc0.toByte()
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
}
