package com.reveila.android.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GenericDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: GenericEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entities: List<GenericEntity>)

    @Query("SELECT * FROM generic_entities WHERE id = :id AND type = :type LIMIT 1")
    fun findById(id: String, type: String): GenericEntity?

    @Query("SELECT * FROM generic_entities WHERE type = :type")
    fun findByType(type: String): List<GenericEntity>

    @Query("DELETE FROM generic_entities WHERE id = :id AND type = :type")
    fun deleteById(id: String, type: String)

    @Query("SELECT COUNT(*) FROM generic_entities WHERE type = :type")
    fun countByType(type: String): Long
    
    @Query("SELECT COUNT(*) FROM generic_entities WHERE id = :id AND type = :type")
    fun exists(id: String, type: String): Int
    
    // Simplistic pagination without sorting/filtering for now, 
    // to satisfy the interface.
    @Query("SELECT * FROM generic_entities WHERE type = :type LIMIT :limit OFFSET :offset")
    fun fetchPage(type: String, limit: Int, offset: Int): List<GenericEntity>
}
