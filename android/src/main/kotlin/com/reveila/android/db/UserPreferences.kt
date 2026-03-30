package com.reveila.android.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_preferences")
data class UserPreferences(
    @PrimaryKey
    val id: Int = 1,
    val user_agreement_accepted: Boolean = false,
    val acceptance_timestamp: Long? = null,
    val acceptance_ip_or_machine_id: String? = null
)
