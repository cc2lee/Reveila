package com.reveila.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.content.pm.ServiceInfo;
import androidx.core.app.NotificationCompat;

import com.reveila.android.RestartReceiver;

/**
 * Manages the foreground service notification for the Reveila service.
 */
public class ServiceManager {

    public static final String CHANNEL_ID = "ReveilaServiceChannel";
    public static final int NOTIFICATION_ID = 1;
    public static final String ACTION_RESTART = "reveila.action.RESTART_SERVICE";

    private final Context context;
    private final NotificationManager notificationManager;

    public ServiceManager(Context context) {
        this.context = context.getApplicationContext();
        this.notificationManager = (NotificationManager) this.context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Reveila Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            notificationManager.createNotificationChannel(serviceChannel);
        }
    }

    public Notification buildNotification(String contentText, Class<?> serviceClass) {
        // Intent to open the app when the notification is tapped
        Intent notificationIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        // Intent for the "Restart" action, targeting the RestartReceiver
        Intent restartIntent = new Intent(context, RestartReceiver.class);
        restartIntent.setAction(ACTION_RESTART);
        restartIntent.putExtra("serviceClass", serviceClass.getName());
        PendingIntent restartPendingIntent = PendingIntent.getBroadcast(context, 1, restartIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Find the app's launcher icon
        int smallIconResId = context.getResources().getIdentifier("ic_launcher", "mipmap", context.getPackageName());
        if (smallIconResId == 0) {
            smallIconResId = android.R.drawable.ic_dialog_info; // Fallback icon
        }

        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Reveila Service")
                .setContentText(contentText)
                .setSmallIcon(smallIconResId)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_menu_rotate, "Restart", restartPendingIntent)
                .setOngoing(true)
                .build();
    }

    public void startForeground(Service service, String contentText) {
        createNotificationChannel();
        Notification notification = buildNotification(contentText, service.getClass());
        // For Android 10 (API 29) and above, we must specify the foreground service type.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            service.startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            service.startForeground(NOTIFICATION_ID, notification);
        }
    }
}
