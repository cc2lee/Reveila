package com.reveila.android.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * The main Room database for the Reveila Suite.
 * Centralizes access to the generic persistence layer in a headless Java format.
 */
@Database(entities = {GenericEntity.class}, version = 1, exportSchema = false)
public abstract class ReveilaDatabase extends RoomDatabase {

    public abstract GenericDao genericDao();

    private static volatile ReveilaDatabase INSTANCE;

    /**
     * Singleton pattern to ensure only one instance of the database is open at a time.
     */
    public static ReveilaDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (ReveilaDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            ReveilaDatabase.class,
                            "reveila_database"
                    )
                    // Consider adding .fallbackToDestructiveMigration() during development
                    // to avoid versioning errors while you finalize the schema.
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}