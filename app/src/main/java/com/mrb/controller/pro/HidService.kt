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

        // HID Descriptor – all hex values converted to Byte using .toByte()
        val HID_DESC = byteArrayOf(
            0x05.toByte(), 0x01.toByte(),           // Usage Page (Generic Desktop)
            0x09.toByte(), 0x05.toByte(),           // Usage (Game Pad)
            0xA1.toByte(), 0x01.toByte(),           // Collection (Application)
            0x85.toByte(), 0x01.toByte(),           //   Report ID (1)

            // Buttons (16)
            0x05.toByte(), 0x09.toByte(),           //   Usage Page (Button)
            0x19.toByte(), 0x01.toByte(),           //   Usage Minimum (1)
            0x29.toByte(), 0x10.toByte(),           //   Usage Maximum (16)
            0x15.toByte(), 0x00.toByte(),           //   Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(),           //   Logical Maximum (1)
            0x75.toByte(), 0x01.toByte(),           //   Report Size (1)
            0x95.toByte(), 0x10.toByte(),           //   Report Count (16)
            0x81.toByte(), 0x02.toByte(),           //   Input (Data, Var, Abs)

            // Axes
            0x05.toByte(), 0x01.toByte(),           //   Usage Page (Generic Desktop)
            // LX, LY (signed)
            0x09.toByte(), 0x30.toByte(),           //   Usage (X)
            0x09.toByte(), 0x31.toByte(),           //   Usage (Y)
            0x15.toByte(), 0x81.toByte(),           //   Logical Minimum (-127)
            0x25.toByte(), 0x7F.toByte(),           //   Logical Maximum (127)
            0x75.toByte(), 0x08.toByte(),           //   Report Size (8)
            0x95.toByte(), 0x02.toByte(),           //   Report Count (2)
            0x81.toByte(), 0x02.toByte(),           //   Input (Data, Var, Abs)

            // Z, Rz (touchpad) – signed as well
            0x09.toByte(), 0x32.toByte(),           //   Usage (Z)
            0x09.toByte(), 0x35.toByte(),           //   Usage (Rz)
            0x15.toByte(), 0x81.toByte(),           //   Logical Minimum (-127)
            0x25.toByte(), 0x7F.toByte(),           //   Logical Maximum (127)
            0x75.toByte(), 0x08.toByte(),           //   Report Size (8)
            0x95.toByte(), 0x02.toByte(),           //   Report Count (2)
            0x81.toByte(), 0x02.toByte(),           //   Input (Data, Var, Abs)

            // Rx, Ry (Gas/Brake) – unsigned 0-255
            0x09.toByte(), 0x33.toByte(),           //   Usage (Rx)
            0x09.toByte(), 0x34.toByte(),           //   Usage (Ry)
            0x15.toByte(), 0x00.toByte(),           //   Logical Minimum (0)
            0x25.toByte(), 0xFF.toByte(),           //   Logical Maximum (255)
            0x75.toByte(), 0x08.toByte(),           //   Report Size (8)
            0x95.toByte(), 0x02.toByte(),           //   Report Count (2)
            0x81.toByte(), 0x02.toByte(),           //   Input (Data, Var, Abs)

            0xC0.toByte()                           // End Collection
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
