package com.reveila.aegin

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.reveila.aegin.bridge.PlatformEngineBridge
import com.reveila.aegin.ui.AeginApp

class MainActivity : ComponentActivity() {
    private lateinit var folderPickerLauncher: ActivityResultLauncher<Uri?>

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        PlatformEngineBridge.attachContext(applicationContext)

        folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            PlatformEngineBridge.onSystemFolderPicked(uri)
        }
        PlatformEngineBridge.attachFolderPicker {
            folderPickerLauncher.launch(null)
        }

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
