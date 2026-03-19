sed -i '/var btnByte1 = 0/,/val gas   =/c\
        var btnByte1 = 0\
        var btnByte2 = 0\
        if (btnA)     btnByte1 = btnByte1 or (1 shl 0) // Button 1 (A)\
        if (btnB)     btnByte1 = btnByte1 or (1 shl 1) // Button 2 (B)\
        if (btnX)     btnByte1 = btnByte1 or (1 shl 2) // Button 3 (X)\
        if (gearDown) btnByte1 = btnByte1 or (1 shl 3) // EXACT Button 4 (LB)\
        if (btnY)     btnByte1 = btnByte1 or (1 shl 4) // Button 5 (Y)\
        if (gearUp)   btnByte1 = btnByte1 or (1 shl 7) // EXACT Button 7 (RT) - The Magic Bit!\
\
        val gas   = if (gasOn)   0xFF.toByte() else 0x00.toByte()' app/src/main/java/com/mrb/controller/pro/MainActivity.kt
