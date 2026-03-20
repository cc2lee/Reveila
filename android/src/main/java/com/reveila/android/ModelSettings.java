package com.reveila.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;

/**
 * Manages UI-configurable settings for local AI execution, ensuring 
 * defaults are safely constrained by the host device's physical hardware.
 */
public class ModelSettings {

    private static final String PREF_NAME = "ReveilaModelPrefs";
    private static final String KEY_QUANTIZATION = "quantization_preference";

    public static final String QUANT_F16 = "F16";
    public static final String QUANT_Q4_K_M = "Q4_K_M";

    private final SharedPreferences prefs;
    private final Context context;

    public ModelSettings(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        
        // Attempt to load settings from the file system to keep internal and external state synced
        loadConfigurationFromFile();
    }

    /**
     * Checks if the user has completed the Sovereign Mode onboarding by 
     * verifying the existence and structural integrity of the sovereign-config.json file.
     * If the file is corrupted, it deletes it to automatically prompt a resetup.
     */
    public boolean isSetupComplete() {
        File configFile = new File(context.getFilesDir(), "system-home/standard/configs/settings/sovereign-config.json");
        if (!configFile.exists()) {
            return false;
        }

        try (FileInputStream fis = new FileInputStream(configFile)) {
            byte[] data = new byte[(int) configFile.length()];
            fis.read(data);
            String jsonString = new String(data, StandardCharsets.UTF_8);
            
            // Validate the JSON structure to ensure it's not corrupted
            JSONObject json = new JSONObject(jsonString);
            if (json.has("quantization") && json.has("tier") && json.has("model")) {
                return true;
            } else {
                Log.w("ModelSettings", "sovereign-config.json is missing required fields. Deleting to force resetup.");
                configFile.delete();
                return false;
            }
        } catch (Exception e) {
            Log.e("ModelSettings", "sovereign-config.json is corrupted or unreadable. Deleting to force resetup.", e);
            configFile.delete();
            return false;
        }
    }

    /**
     * Retrieves the current user-selected quantization level for the model.
     * If not explicitly set by the user, dynamically computes the safest default.
     */
    public String getQuantizationLevel() {
        String defaultQuant = calculateSafeDefaultQuantization(null);
        return prefs.getString(KEY_QUANTIZATION, defaultQuant);
    }

    public void setQuantizationLevel(String level) {
        prefs.edit().putString(KEY_QUANTIZATION, level).apply();
    }

    /**
     * Saves the final finalized hardware and model configuration to the system-home directory
     * so it persists cleanly as a tangible file the rest of the Reveila core can read.
     */
    public void saveConfigurationToFile(HardwareProfiler.DeviceProfile profile) {
        try {
            File configDir = new File(context.getFilesDir(), "system-home/standard/configs/settings");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            
            File configFile = new File(configDir, "sovereign-config.json");
            String quant = calculateSafeDefaultQuantization(profile);
            
            JSONObject json = new JSONObject();
            json.put("quantization", quant);
            json.put("tier", profile.recommendedTier);
            json.put("model", profile.recommendedModel);
            json.put("has_npu", profile.hasHexagonNpu);
            json.put("total_ram_gb", profile.totalMemoryBytes / (1024.0 * 1024.0 * 1024.0));

            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(json.toString(4)); // Pretty print
                writer.flush();
            }
            Log.i("ModelSettings", "Sovereign configuration saved to: " + configFile.getAbsolutePath());
            
        } catch (Exception e) {
            Log.e("ModelSettings", "Failed to save sovereign config to file", e);
        }
    }

    /**
     * Loads the configuration from the sovereign-config.json file if it exists,
     * overriding local SharedPreferences.
     */
    private void loadConfigurationFromFile() {
        try {
            File configFile = new File(context.getFilesDir(), "system-home/standard/configs/settings/sovereign-config.json");
            if (configFile.exists()) {
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    byte[] data = new byte[(int) configFile.length()];
                    fis.read(data);
                    String jsonString = new String(data, StandardCharsets.UTF_8);
                    JSONObject json = new JSONObject(jsonString);
                    
                    if (json.has("quantization")) {
                        setQuantizationLevel(json.getString("quantization"));
                    }
                    Log.i("ModelSettings", "Successfully synced settings from sovereign-config.json");
                }
            }
        } catch (Exception e) {
            Log.w("ModelSettings", "Failed to read sovereign config from file on startup.", e);
        }
    }

    /**
     * Automatically profiles the device's RAM. 
     * If the host has less than 12GB of physical RAM, it strictly defaults to Q4_K_M.
     */
    private String calculateSafeDefaultQuantization(HardwareProfiler.DeviceProfile existingProfile) {
        HardwareProfiler.DeviceProfile profile = existingProfile;
        if (profile == null) {
            HardwareProfiler profiler = new HardwareProfiler();
            profile = profiler.profileDevice(context);
        }

        double totalGb = profile.totalMemoryBytes / (1024.0 * 1024.0 * 1024.0);

        if (totalGb < 11.5) {
            return QUANT_Q4_K_M;
        } else {
            return QUANT_F16;
        }
    }
}
