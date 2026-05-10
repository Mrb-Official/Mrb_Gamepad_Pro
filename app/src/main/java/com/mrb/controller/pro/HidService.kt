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

        // Report = 8 bytes:
        // [0] Buttons 1-8
        // [1] Buttons 9-16
        // [2] X  = Left Joy X / Tilt / WASD left-right  (-127..127)
        // [3] Y  = Left Joy Y / WASD up-down             (-127..127)
        // [4] Rx = Right Joy X / Touchpad X              (-127..127)
        // [5] Ry = Right Joy Y / Touchpad Y              (-127..127)
        // [6] Gas   (0..255)
        // [7] Brake (0..255)

        val HID_DESC = byteArrayOf(
            0x05, 0x01,
            0x09, 0x05,
            0xa1.toByte(), 0x01,
            0x85.toByte(), 0x01,

            // Buttons 1-8
            0x05, 0x09,
            0x19, 0x01,
            0x29, 0x08,
            0x15, 0x00,
            0x25, 0x01,
            0x75, 0x01,
            0x95.toByte(), 0x08,
            0x81.toByte(), 0x02,

            // Buttons 9-16
            0x05, 0x09,
            0x19, 0x09,
            0x29, 0x10,
            0x15, 0x00,
            0x25, 0x01,
            0x75, 0x01,
            0x95.toByte(), 0x08,
            0x81.toByte(), 0x02,

            // X axis (left joy X / tilt)
            0x05, 0x01,
            0x09, 0x30,
            0x15, 0x81.toByte(),
            0x25, 0x7f,
            0x75, 0x08,
            0x95.toByte(), 0x01,
            0x81.toByte(), 0x02,

            // Y axis (left joy Y)
            0x09, 0x31,
            0x15, 0x81.toByte(),
            0x25, 0x7f,
            0x75, 0x08,
            0x95.toByte(), 0x01,
            0x81.toByte(), 0x02,

            // z axis (right joy X / touchpad X)
            0x09, 0x32,
            0x15, 0x81.toByte(),
            0x25, 0x7f,
            0x75, 0x08,
            0x95.toByte(), 0x01,
            0x81.toByte(), 0x02,

            // Rz axis (right joy Y / touchpad Y)
            0x09, 0x35,
            0x15, 0x81.toByte(),
            0x25, 0x7f,
            0x75, 0x08,
            0x95.toByte(), 0x01,
            0x81.toByte(), 0x02,

            // Gas
            0x05, 0x02,
            0x09, 0xC4.toByte(),
            0x15, 0x00,
            0x26, 0xff.toByte(), 0x00,
            0x75, 0x08,
            0x95.toByte(), 0x01,
            0x81.toByte(), 0x02,

            // Brake
            0x09, 0xC5.toByte(),
            0x15, 0x00,
            0x26, 0xff.toByte(), 0x00,
            0x75, 0x08,
            0x95.toByte(), 0x01,
            0x81.toByte(), 0x02,

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
