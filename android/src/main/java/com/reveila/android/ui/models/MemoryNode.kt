package com.reveila.android.ui.models

import androidx.compose.ui.geometry.Offset
import java.util.UUID

/**
 * Represents a discrete entity within the Sovereign Memory Graph.
 * 
 * @property id Unique identifier for the node.
 * @property label The display text (e.g., 'Project Titan', 'John Doe').
 * @property type The classification of the node, used for semantic grouping and color-coding.
 * @property position The spatial coordinates for rendering on the Compose Canvas.
 */
data class MemoryNode(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val type: NodeType,
    var position: Offset = Offset.Zero
)

/**
 * Defines the semantic category of a MemoryNode for color-coding and clustering.
 */
enum class NodeType {
    PERSON,
    COMPANY,
    PROJECT,
    CONCEPT,
    DOCUMENT
}
