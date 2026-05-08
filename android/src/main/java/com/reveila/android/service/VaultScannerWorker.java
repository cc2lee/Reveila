package com.reveila.android.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.reveila.android.ModelSettings;
import com.reveila.android.ScanResult;
import com.reveila.android.VaultScanner;

/**
 * Java implementation of the Background scanner.
 * Decoupled from Kotlin Coroutines and specific UI Activities.
 */
public class VaultScannerWorker extends Worker {

    private static final String TAG = "VaultScannerWorker";
    private static final String CHANNEL_ID = "REVEILA_MEM_CHANNEL";

    public VaultScannerWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Assume ModelSettings is already converted to Java or is a simple POJO
        ModelSettings settings = new ModelSettings(getApplicationContext());
        String vaultUriString = settings.getVaultUri();
        
        if (vaultUriString == null || vaultUriString.isEmpty()) {
            return Result.success();
        }

        String keywords = settings.getFocusKeywords();

        Log.i(TAG, "Starting periodic background scan for: " + vaultUriString);

        try {
            VaultScanner scanner = new VaultScanner(getApplicationContext());
            Uri uri = Uri.parse(vaultUriString);
            
            // Note: Repository logic should be added here once VaultScanner 
            // is initialized with your Java Room stack.
            // For now, we pass null for the listener/repo to keep it headless.
            ScanResult result = scanner.performScan(uri, null, keywords, null);
            
            if (result.getNewFilesCount() > 0) {
                notifyUser(result.getNewFilesCount(), result.getEntitiesDiscoveredCount());
            }
            
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Background scan failed", e);
            return Result.retry();
        }
    }

    private void notifyUser(int newFiles, int totalEntities) {
        NotificationManager notificationManager = (NotificationManager) 
                getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Sovereign Memory Updates",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Notifies when the local AI knowledge graph is updated.");
            notificationManager.createNotificationChannel(channel);
        }

        // REDIRECT: Open the main launcher activity (Expo Shell) instead of SetupActivity
        Intent intent = getApplicationContext().getPackageManager()
                .getLaunchIntentForPackage(getApplicationContext().getPackageName());
        
        if (intent != null) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
            getApplicationContext(), 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync) 
            .setContentTitle("Sovereign Memory Updated")
            .setContentText("Internalized " + newFiles + " new documents and " + totalEntities + " key entities.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true);

        notificationManager.notify(1001, builder.build());
    }
}