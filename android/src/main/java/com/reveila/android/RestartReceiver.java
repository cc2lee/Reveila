package com.reveila.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.reveila.android.ServiceManager;

public class RestartReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ServiceManager.ACTION_RESTART.equals(intent.getAction())) {
            try {
                String serviceClassName = intent.getStringExtra("serviceClass");
                Intent serviceIntent = new Intent(context, Class.forName(serviceClassName));

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } catch (ClassNotFoundException e) {
                Log.e("RestartReceiver", "Failed to restart Reveila service", e);
            }
        }
    }
}
