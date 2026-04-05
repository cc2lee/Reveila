package com.reveila.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reveila.android.ModelSettings
import com.reveila.android.SovereignSetupActivity
import com.reveila.android.VaultScanner

/**
 * Background worker that periodically performs a Delta Scan on the Knowledge Vault.
 */
class VaultScannerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = ModelSettings(applicationContext)
        val vaultUriString = settings.vaultUri ?: return Result.success()
        val keywords = settings.focusKeywords

        Log.i("VaultScannerWorker", "Starting periodic background scan for: $vaultUriString")

        return try {
            val scanner = VaultScanner(applicationContext)
            val uri = Uri.parse(vaultUriString)
            
            // Background scan doesn't have a UI ViewModel to update
            val result = scanner.performScan(uri, null, keywords)
            
            if (result.newFilesCount > 0) {
                notifyUser(result.newFilesCount, result.entitiesDiscoveredCount)
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e("VaultScannerWorker", "Background scan failed", e)
            Result.retry()
        }
    }

    private fun notifyUser(newFiles: Int, totalEntities: Int) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create Notification Channel for API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "REVEILA_MEM_CHANNEL",
                "Sovereign Memory Updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifies when the local AI knowledge graph is updated."
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, SovereignSetupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, "REVEILA_MEM_CHANNEL")
            .setSmallIcon(android.R.drawable.ic_popup_sync) // Fallback icon if ic_reveila_watchdog is missing
            .setContentTitle("Sovereign Memory Updated")
            .setContentText("Internalized $newFiles new documents and $totalEntities key entities.")
            .setPriority(NotificationCompat.PRIORITY_LOW) // Stay non-intrusive
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }
}
