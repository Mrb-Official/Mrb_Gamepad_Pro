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
            0x05, 0x01, 0x09, 0x05, 0xa1.toByte(), 0x01, 0x85.toByte(), 0x01,
            // Buttons 1-16
            0x05, 0x09, 0x19, 0x01, 0x29, 0x10, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95.toByte(), 0x10, 0x81.toByte(), 0x02,
            // Axes: LX, LY, Z, Rz, Rx, Ry
            0x05, 0x01,
            0x09, 0x30, 0x09, 0x31, // LX, LY
            0x09, 0x32, 0x09, 0x35, // Z, Rz (Touchpad - Camera)
            0x09, 0x33, 0x09, 0x34, // Rx, Ry (Gas, Brake - LT/RT)
            0x15, 0x81.toByte(), 0x25, 0x7f, 0x75, 0x08, 0x95.toByte(), 0x06, 0x81.toByte(), 0x02,
            0xc0.toByte()
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() { super.onCreate() ; createNotification() ; initHid() }

    private fun createNotification() {
        val channel = NotificationChannel("mrb_hid", "MRB Gamepad", NotificationManager.IMPORTANCE_LOW)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
        startForeground(1, Notification.Builder(this, "mrb_hid").setContentTitle("MRB Gamepad Pro").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).build())
    }

    @SuppressLint("MissingPermission")
    private fun initHid() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        btManager.adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(p: Int, proxy: BluetoothProfile?) {
                hidDevice = proxy as BluetoothHidDevice
                val sdp = BluetoothHidDeviceAppSdpSettings("MRB Gamepad Pro", "Controller", "MeetDev", 0x08.toByte(), HID_DESC)
                hidDevice?.registerApp(sdp, null, null, { it?.run() }, object : BluetoothHidDevice.Callback() {
                    override fun onConnectionStateChanged(d: BluetoothDevice?, s: Int) {
                        if (s == BluetoothProfile.STATE_CONNECTED) { connectedDevice = d ; handler.post { onConnected?.invoke(d!!) } }
                        else if (s == BluetoothProfile.STATE_DISCONNECTED) { connectedDevice = null ; handler.post { onDisconnected?.invoke() } }
                    }
                    override fun onAppStatusChanged(d: BluetoothDevice?, r: Boolean) { isRegistered = r }
                })
            }
            override fun onServiceDisconnected(p: Int) { hidDevice = null ; isRegistered = false }
        }, BluetoothProfile.HID_DEVICE)
    }
}
