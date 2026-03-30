package com.reveila.android.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [UserPreferences::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userPreferencesDao(): UserPreferencesDao
}
