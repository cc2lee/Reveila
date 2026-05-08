package com.reveila.android.db;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Java implementation of the Preference Manager.
 * Provides a robust, zero-annotation alternative to Room for simple state.
 */
public class PreferenceManager {
    private static final String PREF_NAME = "reveila_prefs";
    private static final String KEY_AGREEMENT = "user_agreement_accepted";
    private static final String KEY_TIMESTAMP = "acceptance_timestamp";
    private static final String KEY_MACHINE_ID = "acceptance_ip_or_machine_id";

    private final SharedPreferences prefs;

    public PreferenceManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public UserPreferences getUserPreferences() {
        boolean accepted = prefs.getBoolean(KEY_AGREEMENT, false);
        
        // Handle nullable Long by checking existence
        Long timestamp = null;
        if (prefs.contains(KEY_TIMESTAMP)) {
            timestamp = prefs.getLong(KEY_TIMESTAMP, 0);
        }

        String machineId = prefs.getString(KEY_MACHINE_ID, null);

        return new UserPreferences(accepted, timestamp, machineId);
    }

    public void saveUserPreferences(UserPreferences preferences) {
        SharedPreferences.Editor editor = prefs.edit();
        
        editor.putBoolean(KEY_AGREEMENT, preferences.isUserAgreementAccepted());

        if (preferences.getAcceptanceTimestamp() != null) {
            editor.putLong(KEY_TIMESTAMP, preferences.getAcceptanceTimestamp());
        } else {
            editor.remove(KEY_TIMESTAMP);
        }

        if (preferences.getAcceptanceIpOrMachineId() != null) {
            editor.putString(KEY_MACHINE_ID, preferences.getAcceptanceIpOrMachineId());
        } else {
            editor.remove(KEY_MACHINE_ID);
        }

        editor.apply();
    }
}