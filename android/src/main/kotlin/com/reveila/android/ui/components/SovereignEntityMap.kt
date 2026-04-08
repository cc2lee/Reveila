package com.reveila.android.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.reveila.android.ui.models.MemoryEdge
import com.reveila.android.ui.models.MemoryNode
import com.reveila.android.ui.models.NodeType

/**
 * An interactive, visual representation of the user's local "Sovereign Memory".
 * Renders entities (nodes) and their relationships (edges) directly onto a Canvas.
 */
@Composable
fun SovereignEntityMap(
    nodes: List<MemoryNode>,
    edges: List<MemoryEdge>
) {
    // Interaction States: Pan and Zoom
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Track which nodes have been "discovered" to trigger the pop-in animation
    val visibleNodes = remember { mutableStateListOf<String>() }
    // Track edges to trigger the "Wow" factor connection flash
    val visibleEdges = remember { mutableStateListOf<String>() }

    // When the nodes/edges lists update from background indexing
    LaunchedEffect(nodes, edges) {
        nodes.forEach { node ->
            if (!visibleNodes.contains(node.id)) visibleNodes.add(node.id)
        }
        edges.forEach { edge ->
            if (!visibleEdges.contains(edge.id)) visibleEdges.add(edge.id)
        }
    }

    // Pre-calculate animations outside the Canvas DrawScope
    val nodeRadii = nodes.associate { node ->
        val isVisible = visibleNodes.contains(node.id)
        val animatedRadius by animateFloatAsState(
            targetValue = if (isVisible) 40f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "NodeRadius_${node.id}"
        )
        node.id to animatedRadius
    }

    val edgeFlashes = edges.associate { edge ->
        val isVisible = visibleEdges.contains(edge.id)
        val animatedFlash by animateFloatAsState(
            targetValue = if (isVisible) 1f else 0f,
            animationSpec = tween(durationMillis = 800), // Slower fade for dramatic M&A discoveries
            label = "EdgeFlash_${edge.id}"
        )
        edge.id to animatedFlash
    }

    // Theme colors
    val backgroundColor = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(Unit) {
                // Implement interaction: Panning and Zooming the knowledge graph
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.2f, 5f)
                    offset += pan
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        ) {
            // 1. Draw Edges (Relationships) as animated/solid lines
            edges.forEach { edge ->
                val sourceNode = nodes.find { it.id == edge.sourceId }
                val targetNode = nodes.find { it.id == edge.targetId }
                
                // Only draw the line if both nodes exist and have popped in
                if (sourceNode != null && targetNode != null) {
                    val srcRadius = nodeRadii[sourceNode.id] ?: 0f
                    val tgtRadius = nodeRadii[targetNode.id] ?: 0f
                    val flashAlpha = edgeFlashes[edge.id] ?: 0f
                    
                    if (srcRadius > 0f && tgtRadius > 0f && flashAlpha > 0f) {
                        
                        // "Wow Factor": Complex relationships flash bright Reveila Teal and settle to a soft slate line
                        val isHighRisk = edge.label.contains("RISK", ignoreCase = true) || edge.label.contains("M&A", ignoreCase = true)
                        
                        // If high risk, it flashes brightly, otherwise it settles to a muted semi-transparent line
                        val targetAlpha = if (isHighRisk) (0.4f + (0.6f * (1f - flashAlpha))) else 0.3f
                        val edgeColor = if (isHighRisk) primaryColor else onBackgroundColor
                        val currentThickness = if (isHighRisk) (4f + (6f * (1f - flashAlpha))) else 3f

                        drawLine(
                            color = edgeColor.copy(alpha = targetAlpha),
                            start = sourceNode.position,
                            end = targetNode.position,
                            strokeWidth = currentThickness / scale 
                        )
                    }
                }
            }

            // 2. Draw Nodes (Entities)
            nodes.forEach { node ->
                val currentRadius = nodeRadii[node.id] ?: 0f
                if (currentRadius > 0f) {
                    // Use primary color for Core entities (like Projects)
                    val nodeColor = if (node.type == NodeType.PROJECT) primaryColor else getColorForType(node.type)
                    
                    drawCircle(
                        color = nodeColor,
                        radius = currentRadius,
                        center = node.position
                    )
                }
            }
        }
    }
}

/**
 * Maps the semantic category to a specific UI color on the Canvas.
 */
private fun getColorForType(type: NodeType): Color {
    return when (type) {
        NodeType.PERSON -> Color(0xFF4FC3F7)    // Light Blue
        NodeType.COMPANY -> Color(0xFFFFB74D)   // Orange
        NodeType.PROJECT -> Color(0xFF00E5FF)   // Reveila Teal
        NodeType.CONCEPT -> Color(0xFFBA68C8)   // Purple
        NodeType.DOCUMENT -> Color(0xFFE57373)  // Red
    }
}
