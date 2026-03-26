package com.reveila.android.ui.models

import java.util.UUID

/**
 * Represents a directional semantic relationship between two MemoryNodes.
 * 
 * @property id Unique identifier for the edge.
 * @property sourceId The UUID of the origin node.
 * @property targetId The UUID of the destination node.
 * @property label The verb or relationship type (e.g., 'OWNER_OF', 'MEMBER_OF').
 */
data class MemoryEdge(
    val id: String = UUID.randomUUID().toString(),
    val sourceId: String,
    val targetId: String,
    val label: String
)
