package com.mrb.controller.pro

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class HidService : Service() {

    companion object {
        var hidDevice: BluetoothHidDevice? = null
        var connectedDevice: BluetoothDevice? = null
        var isRegistered = false
        var onConnected: ((BluetoothDevice) -> Unit)? = null
        var onDisconnected: (() -> Unit)? = null

        // 🛠️ THE ULTIMATE 8-BYTE HID DESCRIPTOR 🛠️
        // Report layout:
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
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothManager: BluetoothManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) return

        bluetoothAdapter?.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = proxy as BluetoothHidDevice
                    val sdp = BluetoothHidDeviceAppSdpSettings(
                        "MRB Gamepad Pro",
                        "Best Wireless Controller",
                        "MRB",
                        BluetoothHidDevice.SUBCLASS1_COMBO,
                        HID_DESC
                    )
                    val inQos = BluetoothHidDeviceAppQosSettings(
                        BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
                        800, 9, 0, 11250, BluetoothHidDeviceAppQosSettings.MAX
                    )
                    val outQos = BluetoothHidDeviceAppQosSettings(
                        BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
                        800, 9, 0, 11250, BluetoothHidDeviceAppQosSettings.MAX
                    )
                    
                    hidDevice?.registerApp(sdp, inQos, outQos, { it.run() },
                        object : BluetoothHidDevice.Callback() {
                            override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
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
                            override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                                isRegistered = registered
                            }
                        }
                    )
                }
            }
            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = null
                    isRegistered = false
                }
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
