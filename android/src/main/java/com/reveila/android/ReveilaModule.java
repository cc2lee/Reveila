package com.reveila.android;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.reveila.system.Reveila;

public class ReveilaModule extends ReactContextBaseJavaModule {

    // It's a best practice to run network operations on a dedicated background thread pool.
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    ReveilaModule(ReactApplicationContext context) {
        super(context);
    }

    @NonNull
    @Override
    public String getName() {
        // This is the name that will be used to access the module from JavaScript
        return "ReveilaModule";
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
    public void isReady(Promise promise) {
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