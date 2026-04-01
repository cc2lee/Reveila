package com.reveila.android.db

import android.content.Context
import android.content.SharedPreferences

/**
 * Modern, robust alternative to Room for simple key-value persistence.
 * Avoids annotation processing issues in complex Expo/Monorepo builds.
 */
class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("reveila_prefs", Context.MODE_PRIVATE)

    fun getUserPreferences(): UserPreferences {
        return UserPreferences(
            user_agreement_accepted = prefs.getBoolean("user_agreement_accepted", false),
            acceptance_timestamp = if (prefs.contains("acceptance_timestamp")) prefs.getLong("acceptance_timestamp", 0) else null,
            acceptance_ip_or_machine_id = prefs.getString("acceptance_ip_or_machine_id", null)
        )
    }

    fun saveUserPreferences(preferences: UserPreferences) {
        prefs.edit().apply {
            putBoolean("user_agreement_accepted", preferences.user_agreement_accepted)
            preferences.acceptance_timestamp?.let { putLong("acceptance_timestamp", it) }
            preferences.acceptance_ip_or_machine_id?.let { putString("acceptance_ip_or_machine_id", it) }
            apply()
        }
    }
}

/**
 * Keeping the data class for source compatibility.
 */
data class UserPreferences(
    val id: Int = 1,
    val user_agreement_accepted: Boolean = false,
    val acceptance_timestamp: Long? = null,
    val acceptance_ip_or_machine_id: String? = null
)
