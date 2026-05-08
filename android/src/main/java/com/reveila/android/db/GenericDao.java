package com.reveila.android.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Java Data Access Object for generic entity persistence.
 * Handles the low-level SQL operations for the Sovereign Engine.
 */
@Dao
public interface GenericDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(GenericEntity entity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<GenericEntity> entities);

    @Query("SELECT * FROM generic_entities WHERE id = :id AND type = :type LIMIT 1")
    GenericEntity findById(String id, String type);

    @Query("SELECT * FROM generic_entities WHERE type = :type")
    List<GenericEntity> findByType(String type);

    @Query("DELETE FROM generic_entities WHERE id = :id AND type = :type")
    void deleteById(String id, String type);

    @Query("SELECT COUNT(*) FROM generic_entities WHERE type = :type")
    long countByType(String type);
    
    @Query("SELECT COUNT(*) FROM generic_entities WHERE id = :id AND type = :type")
    int exists(String id, String type);
    
    /**
     * Simplistic pagination without sorting/filtering to satisfy the repository interface.
     */
    @Query("SELECT * FROM generic_entities WHERE type = :type LIMIT :limit OFFSET :offset")
    List<GenericEntity> fetchPage(String type, int limit, int offset);
}