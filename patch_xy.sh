sed -i '/var btnByte1 = 0/,/val gas   =/c\
        var btnByte1 = 0\
        var btnByte2 = 0\
        if (btnA)     btnByte1 = btnByte1 or (1 shl 0) // Button 1\
        if (btnB)     btnByte1 = btnByte1 or (1 shl 1) // Button 2\
        if (gearDown) btnByte1 = btnByte1 or (1 shl 3) // EXACT Button 4 (LB)\
        if (gearUp)   btnByte1 = btnByte1 or (1 shl 7) // EXACT Button 7 (RT)\
\
        // Yahan se Byte 2 shuru (Buttons 9-16)\
        if (btnX)     btnByte2 = btnByte2 or (1 shl 4) // Button 13\
        if (btnY)     btnByte2 = btnByte2 or (1 shl 5) // Button 14\
\
        val gas   = if (gasOn)   0xFF.toByte() else 0x00.toByte()' app/src/main/java/com/mrb/controller/pro/MainActivity.kt
