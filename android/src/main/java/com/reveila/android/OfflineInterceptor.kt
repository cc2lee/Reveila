package com.reveila.android

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * A strict debug-mode interceptor that aggressively enforces the "Flight Mode" 
 * offline verification test by killing any non-localhost HTTP traffic from the app.
 */
class OfflineInterceptor : Interceptor {

    private val TAG = "OfflineInterceptor"

    // [ ] 4. Offline Verification: Add debug-mode Network Interceptor to block non-localhost network calls
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        
        // Strict localhost checking logic for offline Personal Edition guarantees
        if (host != "localhost" && host != "127.0.0.1") {
            Log.e(TAG, "SECURITY VIOLATION: Flight Mode paradox check failed. Attempted external call to: $host")
            throw IOException("Reveila Sovereign Mode actively blocked an external network call to $host. Only localhost is permitted.")
        }
        
        Log.d(TAG, "Localhost traffic validated: $host")
        return chain.proceed(request)
    }
}
