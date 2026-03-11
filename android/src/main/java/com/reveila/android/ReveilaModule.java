package com.reveila.android;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;

import android.content.Intent;
import android.os.Build;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.reveila.system.Reveila;

import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.reveila.android.safety.MobileKillSwitch;
import com.reveila.core.safety.AgentSafetyCommand;
import com.reveila.core.safety.CommandType;
import androidx.fragment.app.FragmentActivity;

public class ReveilaModule extends ReactContextBaseJavaModule {

    private MobileKillSwitch killSwitch;

    // It's a best practice to run network operations on a dedicated background thread pool.
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    ReveilaModule(ReactApplicationContext context) {
        super(context);
    }

    @ReactMethod
    public void triggerEmergencyStop(Promise promise) {
        FragmentActivity activity = (FragmentActivity) getCurrentActivity();
        if (activity == null) {
            promise.reject("E_NO_ACTIVITY", "Activity is not available");
            return;
        }

        if (killSwitch == null) {
            killSwitch = new MobileKillSwitch(activity, this::sendSafetyEvent);
        }

        killSwitch.emergencyStopAll();
        promise.resolve(true);
    }

    private void sendSafetyEvent(AgentSafetyCommand command) {
        WritableMap params = Arguments.createMap();
        params.putString("agentId", command.agentId());
        params.putString("commandType", command.commandType().name());
        params.putString("securityToken", command.securityToken());

        getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("onSafetyCommand", params);
    }

    @NonNull
    @Override
    public String getName() {
        // This is the name that will be used to access the module from JavaScript
        return "ReveilaModule";
    }

    /**
     * Starts the Reveila background service.
     * @param systemHome Optional path to the system home directory.
     * @param promise Promise to resolve when the service is starting.
     */
    @ReactMethod
    public void startService(String systemHome, Promise promise) {
        try {
            ReactApplicationContext context = getReactApplicationContext();
            Intent intent = new Intent(context, ReveilaService.class);
            if (systemHome != null && !systemHome.isEmpty()) {
                intent.putExtra("systemHome", systemHome);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("E_START_FAILED", e.getMessage(), e);
        }
    }

    /**
     * This method is called when the React Native instance is destroyed.
     * It's the ideal place to clean up resources like the thread pool.
     */
    @Override
    public void invalidate() {
        executorService.shutdown();
    }

    @ReactMethod
    public void isRunning(Promise promise) {
        // This method allows the UI to check if the backend service is fully initialized.
        promise.resolve(ReveilaService.isRunning());
    }

    /**
     * Invokes a method on a component running within the embedded Reveila instance.
     * @param componentName The name of the component to target.
     * @param methodName The name of the method to invoke on the component.
     * @param params An array of parameters to pass to the method.
     * @param promise The promise to resolve with the result or reject with an error.
     */
    @ReactMethod
    public void invoke(String componentName, String methodName, ReadableArray params, Promise promise) {
        executorService.execute(() -> {
            try {
                // First, check if the service is fully initialized and running.
                Reveila reveilaInstance = ReveilaService.getReveilaInstance();
                if (!ReveilaService.isRunning() || reveilaInstance == null) {
                    promise.reject("E_NOT_READY", "The Reveila service is not yet available.");
                    return;
                }

                Object[] javaParams = ReactNativeJsonConverter.toArray(params);
                Object result = reveilaInstance.invoke(componentName, methodName, javaParams);

                // Convert the Java result to a React Native Writable type (Map, Array, etc.)
                Object writableResult = ReactNativeJsonConverter.convertObjectToWritable(result);
                promise.resolve(writableResult);
            } catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : e.toString();
                promise.reject("E_INVOKE_FAILED", message, e);
            }
        });
    }
}