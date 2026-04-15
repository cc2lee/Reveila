package com.reveila.android.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "generic_entities")
data class GenericEntity(
    @PrimaryKey
    val id: String,
    val type: String,
    val attributesJson: String
)
