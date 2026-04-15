package com.reveila.android.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [GenericEntity::class], version = 1, exportSchema = false)
abstract class ReveilaDatabase : RoomDatabase() {

    abstract fun genericDao(): GenericDao

    companion object {
        @Volatile
        private var INSTANCE: ReveilaDatabase? = null

        fun getDatabase(context: Context): ReveilaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ReveilaDatabase::class.java,
                    "reveila_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
