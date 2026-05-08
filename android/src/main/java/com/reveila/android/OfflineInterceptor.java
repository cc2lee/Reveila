package com.reveila.android;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * A strict debug-mode interceptor that aggressively enforces the "Flight Mode" 
 * offline verification test by killing any non-localhost HTTP traffic from the app.
 */
public class OfflineInterceptor implements Interceptor {

    private static final String TAG = "OfflineInterceptor";

    // [ ] 4. Offline Verification: Add debug-mode Network Interceptor to block non-localhost network calls
    @NonNull
    @Override
    public Response intercept(@NonNull Interceptor.Chain chain) throws IOException {
        Request request = chain.request();
        String host = request.url().host();
        
        // Strict localhost checking logic for offline Personal Edition guarantees
        if (!host.equals("localhost") && !host.equals("127.0.0.1")) {
            Log.e(TAG, "SECURITY VIOLATION: Flight Mode paradox check failed. Attempted external call to: " + host);
            throw new IOException("Reveila Sovereign Mode actively blocked an external network call to " + host + ". Only localhost is permitted.");
        }
        
        Log.d(TAG, "Localhost traffic validated: " + host);
        return chain.proceed(request);
    }
}