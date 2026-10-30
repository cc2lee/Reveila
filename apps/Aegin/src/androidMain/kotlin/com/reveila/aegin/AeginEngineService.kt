package com.reveila.aegin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.reveila.aegin.bridge.PlatformEngineBridge
import java.util.concurrent.Executors

/**
 * Lightweight Android Foreground Service hosting the bytecode-sliced Reveila Core Engine.
 */
class AeginEngineService : Service() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        PlatformEngineBridge.attachContext(applicationContext)
        startForegroundCompat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        PlatformEngineBridge.attachContext(applicationContext)
        startForegroundCompat()
        executor.execute {
            try {
                PlatformEngineBridge.ensureStartedBlocking()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Reveila Engine in AeginEngineService", e)
            }
        }
        return START_STICKY
    }

    private fun startForegroundCompat() {
        try {
            val channelId = "aegin_reveila_engine"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                val channel = NotificationChannel(
                    channelId,
                    "Aegin Sovereign Engine",
                    NotificationManager.IMPORTANCE_LOW
                )
                nm?.createNotificationChannel(channel)
                val notification = Notification.Builder(this, channelId)
                    .setContentTitle("Aegin Sovereign AI")
                    .setContentText("Reveila Core Engine & KnowledgeVault active")
                    .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                    .build()
                startForeground(1001, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Foreground notification skipped: ${e.message}")
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AeginEngineService"
    }
}
