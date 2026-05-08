package com.reveila.android;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;

/**
 * Service to automatically pair the user's Android hardware with the most
 * capable
 * "Sovereign Memory" model, enabling a true "Zero-Ops" experience.
 */
public class HardwareProfiler {

    private static final String TAG = "HardwareProfiler";

    /**
     * Data class representing the device's hardware profile and recommended AI
     * tier.
     */
    public static class DeviceProfile {
        private final long totalMemoryBytes;
        private final long availableMemoryBytes;
        private final boolean hasHexagonNpu;
        private final boolean hasGpuAcceleration;
        private final String recommendedTier;
        private final String recommendedModel;

        public DeviceProfile(long totalMem, long availMem, boolean hasNpu, boolean hasGpu, String tier, String model) {
            this.totalMemoryBytes = totalMem;
            this.availableMemoryBytes = availMem;
            this.hasHexagonNpu = hasNpu;
            this.hasGpuAcceleration = hasGpu;
            this.recommendedTier = tier;
            this.recommendedModel = model;
        }

        // --- Getters for ReasoningEngine Compatibility ---

        public boolean hasHexagonNpu() {
            return hasHexagonNpu;
        }

        public boolean hasGpuAcceleration() {
            return hasGpuAcceleration;
        }

        public long getTotalMemoryBytes() {
            return totalMemoryBytes;
        }

        public String getRecommendedTier() {
            return recommendedTier;
        }

        public String getRecommendedModel() {
            return recommendedModel;
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
     */
    public DeviceProfile profileDevice(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();

        if (activityManager != null) {
            activityManager.getMemoryInfo(memoryInfo);
        }

        long totalMem = memoryInfo.totalMem;
        long availMem = memoryInfo.availMem;
        double totalGb = totalMem / (1024.0 * 1024.0 * 1024.0);

        boolean hasNpu = checkHexagonNpuSupport();
        boolean hasGpu = checkGpuSupport();

        String recommendedTier;
        String recommendedModel;

        if (totalGb >= 11.5 && (hasNpu || hasGpu)) {
            recommendedTier = "Sovereign";
            recommendedModel = "Gemma-3-4B or Phi-4-Mini (INT8)";
        } else if (totalGb >= 7.5 && hasGpu) {
            recommendedTier = "Sovereign-Lite";
            recommendedModel = "Gemma-3-1B (INT8)";
        } else {
            recommendedTier = "Watcher";
            recommendedModel = "Thin Client (Remote Execution)";
        }

        return new DeviceProfile(totalMem, availMem, hasNpu, hasGpu, recommendedTier, recommendedModel);
    }

    private boolean checkHexagonNpuSupport() {
        try {
            Class.forName("org.tensorflow.lite.HexagonDelegate");
            return true;
        } catch (ClassNotFoundException | UnsatisfiedLinkError e) {
            return false;
        }
    }

    private boolean checkGpuSupport() {
        try {
            Class.forName("org.tensorflow.lite.gpu.CompatibilityList");
            return true;
        } catch (ClassNotFoundException | UnsatisfiedLinkError e) {
            return false;
        }
    }
}