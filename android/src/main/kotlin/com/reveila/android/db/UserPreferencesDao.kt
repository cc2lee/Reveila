package com.reveila.android.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserPreferencesDao {
    @Query("SELECT * FROM user_preferences WHERE id = 1")
    suspend fun getUserPreferences(): UserPreferences?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserPreferences(preferences: UserPreferences)
}
