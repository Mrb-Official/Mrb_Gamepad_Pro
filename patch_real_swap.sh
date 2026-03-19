sed -i '/var btnByte1 = 0/,/val gas   =/c\
        var btnByte1 = 0\
        var btnByte2 = 0\
        if (btnA)     btnByte1 = btnByte1 or (1 shl 0) // Button 1\
        if (btnB)     btnByte1 = btnByte1 or (1 shl 1) // Button 2\
        \
        if (gearDown) btnByte1 = btnByte1 or (1 shl 6) // SWAPPED: Front ab 13 dega\
        if (gearUp)   btnByte1 = btnByte1 or (1 shl 7) // EXACT Button 7 (RT)\
        if (btnX)     btnByte1 = btnByte1 or (1 shl 2) // SWAPPED: X ab 4 dega\
        if (btnY)     btnByte1 = btnByte1 or (1 shl 4) // Button 5 (Y)\
\
        val gas   = if (gasOn)   0xFF.toByte() else 0x00.toByte()' app/src/main/java/com/mrb/controller/pro/MainActivity.kt
