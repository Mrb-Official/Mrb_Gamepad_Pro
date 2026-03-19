package com.mrb.controller.pro

import android.annotation.SuppressLint
import android.bluetooth.*
import android.companion.*
import android.content.*
import android.graphics.Color
import android.hardware.*
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), SensorEventListener {

    private var hidDevice: BluetoothHidDevice? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var connectedDevice: BluetoothDevice? = null
    private lateinit var sensorManager: SensorManager
    
    // Yahan crash hota tha, isliye ab isko nullable (?) bana diya hai taaki crash na ho
    private var txtStatus: TextView? = null
    
    private var gasOn = false
    private var brakeOn = false
    private var tiltValue: Byte = 0
    private var lastSendTime = 0L

    private val SELECT_DEVICE_REQUEST_CODE = 42

    private val HID_DESC = intArrayOf(
        0x05, 0x01, 0x09, 0x05, 0xa1, 0x01, 0x85, 0x01,
        0x05, 0x09, 0x19, 0x01, 0x29, 0x08, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
        0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x15, 0x81, 0x25, 0x7f, 0x75, 0x08, 0x95, 0x02, 0x81, 0x02,
        0xc0
    ).map { it.toByte() }.toByteArray()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        
        try {
            // Wapas direct R class use kar rahe hain
            setContentView(R.layout.activity_main)

            val btnGas = findViewById<View>(R.id.lay_gas)
            val btnBrake = findViewById<View>(R.id.lay_brake)
            val btnConnect = findViewById<View>(R.id.lay_steering)
            txtStatus = findViewById(R.id.txt_status)

            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            bluetoothAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

            btnConnect?.setOnClickListener { showGooglePairingPopup() }

            btnGas?.setOnTouchListener { v, event ->
                when(event.action) {
                    MotionEvent.ACTION_DOWN -> { gasOn = true; v.setBackgroundColor(Color.parseColor("#33FF33")); sendHIDReport(); true }
                    MotionEvent.ACTION_UP -> { gasOn = false; v.setBackgroundColor(Color.parseColor("#1A1A1A")); sendHIDReport(); true }
                    else -> false
                }
            }

            btnBrake?.setOnTouchListener { v, event ->
                when(event.action) {
                    MotionEvent.ACTION_DOWN -> { brakeOn = true; v.setBackgroundColor(Color.parseColor("#FF3333")); sendHIDReport(); true }
                    MotionEvent.ACTION_UP -> { brakeOn = false; v.setBackgroundColor(Color.parseColor("#1A1A1A")); sendHIDReport(); true }
                    else -> false
                }
            }

            setupHid()
            
        } catch (e: Exception) {
            Toast.makeText(this, "UI Load Safe Catch: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showGooglePairingPopup() {
        try {
            val deviceManager = getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
            val pairingRequest = AssociationRequest.Builder()
                .addDeviceFilter(BluetoothDeviceFilter.Builder().build())
                .setSingleDevice(false)
                .build()

            deviceManager.associate(pairingRequest, object : CompanionDeviceManager.Callback() {
                override fun onDeviceFound(chooserLauncher: IntentSender) {
                    try { startIntentSenderForResult(chooserLauncher, SELECT_DEVICE_REQUEST_CODE, null, 0, 0, 0) } 
                    catch (e: Exception) { runOnUiThread { txtStatus?.text = "POPUP ERROR" } }
                }
                override fun onFailure(error: CharSequence?) { runOnUiThread { txtStatus?.text = "NOT FOUND" } }
            }, null)
            
        } catch (e: Exception) {
            // Agar Companion Device Manager (Google Popup) crash kare toh direct Bluetooth Setting khol do
            Toast.makeText(this, "Opening Bluetooth Settings...", Toast.LENGTH_SHORT).show()
            val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
            startActivity(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SELECT_DEVICE_REQUEST_CODE && resultCode == RESULT_OK) {
            val deviceToPair: BluetoothDevice? = data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
            deviceToPair?.let { 
                connectedDevice = it
                txtStatus?.text = "CONNECTING..." 
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupHid() {
        bluetoothAdapter?.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                hidDevice = proxy as BluetoothHidDevice
                val sdp = BluetoothHidDeviceAppSdpSettings("MRB Gamepad", "Controller", "Meet", 0x08.toByte(), HID_DESC)
                hidDevice?.registerApp(sdp, null, null, { it?.run() }, object : BluetoothHidDevice.Callback() {
                    override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                        if (state == BluetoothProfile.STATE_CONNECTED) {
                            connectedDevice = device
                            runOnUiThread { txtStatus?.text = "CONNECTED" }
                        } else {
                            connectedDevice = null
                            runOnUiThread { txtStatus?.text = "TAP TO CONNECT" }
                        }
                    }
                })
            }
            override fun onServiceDisconnected(p0: Int) {}
        }, BluetoothProfile.HID_DEVICE)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            tiltValue = (event.values[1] * 12).toInt().coerceIn(-127, 127).toByte()
            val now = System.currentTimeMillis()
            if (now - lastSendTime > 40) { sendHIDReport(); lastSendTime = now }
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendHIDReport() {
        val device = connectedDevice ?: return
        var buttons = 0
        if (gasOn) buttons = buttons or 0x01
        if (brakeOn) buttons = buttons or 0x02
        hidDevice?.sendReport(device, 1, byteArrayOf(buttons.toByte(), tiltValue, 0x00))
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}
    override fun onResume() { 
        super.onResume()
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
    }
}
