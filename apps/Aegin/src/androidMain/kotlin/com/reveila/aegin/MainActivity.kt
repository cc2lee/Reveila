package com.reveila.aegin

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.reveila.aegin.bridge.PlatformEngineBridge
import com.reveila.aegin.ui.AeginApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlatformEngineBridge.attachContext(applicationContext)

        try {
            val serviceIntent = Intent(this, AeginEngineService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (_: Exception) {
        }

        setContent {
            AeginApp()
        }
    }
}
