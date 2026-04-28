package com.reveila.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.content.pm.ServiceInfo;
import androidx.core.app.NotificationCompat;

/**
 * Manages unique foreground service notifications for Reveila components.
 */
public class ServiceManager {

    public static final String ACTION_RESTART = "reveila.action.RESTART_SERVICE";
    private static final String TAG = "ServiceManager";

    private final Context context;
    private final NotificationManager notificationManager;

    // Configurable via constructor
    private final String channelId;
    private final int notificationId;
    private final String channelName;

    private NotificationCompat.Builder builder;
    private int lastProgress = -1;

    /**
     * @param context        The service context
     * @param channelId      Unique string ID for the notification channel (e.g.,
     *                       "reveila_core_channel")
     * @param notificationId Unique integer for the notification (e.g., 1001)
     * @param channelName    User-visible name for the channel (e.g., "Reveila Core
     *                       Engine")
     */
    public ServiceManager(Context context, String channelId, int notificationId, String channelName) {
        this.context = context.getApplicationContext();
        this.notificationManager = (NotificationManager) this.context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.channelId = channelId;
        this.notificationId = notificationId;
        this.channelName = channelName;
    }

    public void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_LOW);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void ensureBuilder(Class<?> serviceClass) {
        if (builder != null)
            return;

        Intent notificationIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        Intent restartIntent = new Intent(context, RestartReceiver.class);
        restartIntent.setAction(ACTION_RESTART);
        restartIntent.putExtra("serviceClass", serviceClass.getName());
        PendingIntent restartPendingIntent = PendingIntent.getBroadcast(context, 1, restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int smallIconResId = context.getResources().getIdentifier("ic_launcher", "mipmap", context.getPackageName());
        if (smallIconResId == 0) {
            smallIconResId = android.R.drawable.ic_dialog_info;
        }

        builder = new NotificationCompat.Builder(context, channelId)
                .setContentTitle(channelName) // Use channelName as the title for clarity
                .setSmallIcon(smallIconResId)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_menu_rotate, "Restart", restartPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true);
    }

    public void startForeground(Service service, String contentText) {
        try {
            createNotificationChannel();
            ensureBuilder(service.getClass());

            builder.setContentText(contentText);
            Notification notification = builder.build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                service.startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                service.startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                service.startForeground(notificationId, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failure in startForeground for " + channelName, e);
        }
    }

    public void updateNotification(Service service, String message, int max, int progress, boolean indeterminate) {
        if (!indeterminate && progress == lastProgress)
            return;
        lastProgress = progress;

        ensureBuilder(service.getClass());

        builder.setContentText(message)
                .setProgress(max, progress, indeterminate);

        if (notificationManager != null) {
            notificationManager.notify(notificationId, builder.build());
        }
    }

    public void updateNotification(Service service, String message) {
        updateNotification(service, message, 0, 0, false);
    }
}