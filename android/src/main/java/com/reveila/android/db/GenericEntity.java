package com.reveila.android.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Java implementation of the generic persistence entity.
 * Stores flat JSON attributes for any entity type in the Knowledge Vault.
 */
@Entity(tableName = "generic_entities")
public class GenericEntity {

    @PrimaryKey
    @NonNull
    private final String id;
    
    private final String type;
    
    private final String attributesJson;

    public GenericEntity(@NonNull String id, String type, String attributesJson) {
        this.id = id;
        this.type = type;
        this.attributesJson = attributesJson;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getAttributesJson() {
        return attributesJson;
    }
}