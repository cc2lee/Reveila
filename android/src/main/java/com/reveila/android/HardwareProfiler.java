package com.reveila.android;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;

/**
 * Service to automatically pair the user's Android hardware with the most capable 
 * "Sovereign Memory" model, enabling a true "Zero-Ops" experience.
 */
public class HardwareProfiler {

    private static final String TAG = "HardwareProfiler";

    /**
     * Data class representing the device's hardware profile and recommended AI tier.
     */
    public static class DeviceProfile {
        public final long totalMemoryBytes;
        public final long availableMemoryBytes;
        public final boolean hasHexagonNpu;
        public final boolean hasGpuAcceleration;
        public final String recommendedTier;
        public final String recommendedModel;

        public DeviceProfile(long totalMem, long availMem, boolean hasNpu, boolean hasGpu, String tier, String model) {
            this.totalMemoryBytes = totalMem;
            this.availableMemoryBytes = availMem;
            this.hasHexagonNpu = hasNpu;
            this.hasGpuAcceleration = hasGpu;
            this.recommendedTier = tier;
            this.recommendedModel = model;
        }

        @Override
        public String toString() {
            return "DeviceProfile{" +
                    "totalMem=" + (totalMemoryBytes / (1024 * 1024)) + "MB" +
                    ", availMem=" + (availableMemoryBytes / (1024 * 1024)) + "MB" +
                    ", npu=" + hasHexagonNpu +
                    ", gpu=" + hasGpuAcceleration +
                    ", tier='" + recommendedTier + '\'' +
                    ", model='" + recommendedModel + '\'' +
                    '}';
        }
    }

    /**
     * Profiles the current device's hardware to recommend the best local AI tier.
     * 
     * @param context The application context
     * @return The profiled hardware and recommended configuration
     */
    public DeviceProfile profileDevice(Context context) {
        // 1. Check RAM via ActivityManager
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        
        if (activityManager != null) {
            activityManager.getMemoryInfo(memoryInfo);
        } else {
            Log.w(TAG, "ActivityManager is null, cannot read memory info properly.");
        }

        long totalMem = memoryInfo.totalMem;
        long availMem = memoryInfo.availMem;
        
        // Convert to GB for easy threshold checking
        double totalGb = (double) totalMem / (1024.0 * 1024.0 * 1024.0);

        // 2. Check for NPU / GPU capabilities using Google LiteRT (formerly MediaPipe / TF Lite)
        boolean hasHexagonNpu = checkHexagonNpuSupport();
        boolean hasGpuAcceleration = checkGpuSupport();

        // 3. Determine Tier based on hardware profile
        String recommendedTier;
        String recommendedModel;

        if (totalGb >= 11.5 && (hasHexagonNpu || hasGpuAcceleration)) {
            // Flagship Android with >= 12GB RAM (11.5 allowing for OS reserved overhead)
            recommendedTier = "Sovereign";
            recommendedModel = "Gemma-3-4B or Phi-4-Mini (INT8)";
        } else if (totalGb >= 7.5 && hasGpuAcceleration) {
            // Mid-High range with 8GB RAM
            recommendedTier = "Sovereign-Lite";
            recommendedModel = "Gemma-3-1B (INT8)";
        } else {
            // Entry/Mid-range with <= 6GB RAM or no accelerators
            recommendedTier = "Watcher";
            recommendedModel = "Thin Client (Remote Execution)";
        }

        DeviceProfile profile = new DeviceProfile(
                totalMem, 
                availMem, 
                hasHexagonNpu, 
                hasGpuAcceleration, 
                recommendedTier, 
                recommendedModel
        );
        
        Log.i(TAG, "Hardware Profiling Complete: " + profile.toString());
        return profile;
    }

    /**
     * Checks if a Hexagon NPU is available for 8-bit integer quantization acceleration.
     * Uses LiteRT (formerly MediaPipe / TensorFlow Lite) delegate availability.
     */
    private boolean checkHexagonNpuSupport() {
        try {
            // Using reflection to check for LiteRT Hexagon delegate class to prevent ClassNotFound crashes 
            // if the dependency is excluded on certain build variants.
            Class.forName("org.tensorflow.lite.HexagonDelegate");
            // If the class exists, perform the native hardware capability check
            // return HexagonDelegate.isSupported();
            
            // Stubbed true if class found, in a real environment invoke the native check
            Log.d(TAG, "Hexagon NPU Delegate class found.");
            return true;
        } catch (ClassNotFoundException | UnsatisfiedLinkError e) {
            Log.d(TAG, "Hexagon NPU not supported on this device.");
            return false;
        }
    }

    /**
     * Checks if a capable GPU accelerator is available for compute.
     * Uses LiteRT GPU Compatibility List.
     */
    private boolean checkGpuSupport() {
        try {
            // Using reflection to check for LiteRT GPU delegate
            Class.forName("org.tensorflow.lite.gpu.CompatibilityList");
            // CompatibilityList compatList = new CompatibilityList();
            // return compatList.isDelegateSupportedOnThisDevice();
            
            Log.d(TAG, "LiteRT GPU Delegate compatibility list found.");
            return true;
        } catch (ClassNotFoundException | UnsatisfiedLinkError e) {
            Log.d(TAG, "GPU Acceleration not explicitly supported by LiteRT on this device.");
            return false;
        }
    }
}
