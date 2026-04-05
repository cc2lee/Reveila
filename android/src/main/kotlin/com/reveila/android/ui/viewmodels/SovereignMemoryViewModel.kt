package com.reveila.android.ui.viewmodels

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reveila.android.ui.models.MemoryEdge
import com.reveila.android.ui.models.MemoryNode
import com.reveila.android.ui.models.NodeType
import com.reveila.persistence.VectorStore
import com.reveila.system.PlatformAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The ViewModel bridging the Sovereign Entity Map UI with the underlying 
 * SQLite-vec Database Engine.
 */
class SovereignMemoryViewModel : ViewModel() {

    // ==========================================
    // Reactive UI State (Jetpack Compose triggers)
    // ==========================================

    private val _nodes = MutableStateFlow<List<MemoryNode>>(emptyList())
    val nodes: StateFlow<List<MemoryNode>> = _nodes.asStateFlow()

    private val _edges = MutableStateFlow<List<MemoryEdge>>(emptyList())
    val edges: StateFlow<List<MemoryEdge>> = _edges.asStateFlow()

    private val _discoveryLogs = MutableStateFlow<List<String>>(emptyList())
    val discoveryLogs: StateFlow<List<String>> = _discoveryLogs.asStateFlow()

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress.asStateFlow()

    private val _focusKeywords = MutableStateFlow("")
    val focusKeywords: StateFlow<String> = _focusKeywords.asStateFlow()

    private val _indexedFiles = MutableStateFlow<Map<String, Long>>(emptyMap())
    val indexedFiles: StateFlow<Map<String, Long>> = _indexedFiles.asStateFlow()

    fun setFocusKeywords(keywords: String) {
        _focusKeywords.value = keywords
    }

    fun getIndexedFiles(): Map<String, Long> {
        return _indexedFiles.value
    }

    fun markFileAsIndexed(uri: String, lastModified: Long) {
        _indexedFiles.value = _indexedFiles.value + (uri to lastModified)
    }

    // Connect via PlatformAdapter
    // TODO: Retrieve the proper PlatformAdapter instance from context
    private var vectorStore: VectorStore? = null

    // Helper if you had PlatformAdapter
    // fun init(adapter: PlatformAdapter) {
    //     vectorStore = adapter.getRepository("VectorStore") as? VectorStore
    // }

    /**
     * Executes the "Reflection Loop" logic.
     * Inserts new relationship facts directly into the local SQLite-vec database 
     * while seamlessly emitting state updates to pop them into the Compose UI.
     */
    fun insertFact(sourceLabel: String, sourceType: NodeType, relation: String, targetLabel: String, targetType: NodeType, payload: String) {
        viewModelScope.launch(Dispatchers.IO) {
            
            val currentNodes = _nodes.value.toMutableList()
            
            // 1. Process Source Node
            var sourceNode = currentNodes.find { it.label == sourceLabel }
            if (sourceNode == null) {
                sourceNode = MemoryNode(
                    id = UUID.randomUUID().toString(),
                    label = sourceLabel,
                    type = sourceType,
                    position = calculateNextOffset(currentNodes.size)
                )
                currentNodes.add(sourceNode)
                
                // Persist the entity embedding into SQLite-vec
                // Note: In production, this passes through your local LiteRT embedding model first
                val dummyEmbedding = FloatArray(1536) { Math.random().toFloat() }
                vectorStore?.insert(sourceNode.id, dummyEmbedding, "Entity: $sourceLabel ($sourceType). Context: $payload")
            }

            // 2. Process Target Node
            var targetNode = currentNodes.find { it.label == targetLabel }
            if (targetNode == null) {
                targetNode = MemoryNode(
                    id = UUID.randomUUID().toString(),
                    label = targetLabel,
                    type = targetType,
                    position = calculateNextOffset(currentNodes.size)
                )
                currentNodes.add(targetNode)
                
                val dummyEmbedding = FloatArray(1536) { Math.random().toFloat() }
                vectorStore?.insert(targetNode.id, dummyEmbedding, "Entity: $targetLabel ($targetType). Context: $payload")
            }

            // 3. Create the Edge
            val currentEdges = _edges.value.toMutableList()
            // Prevent duplicate edges visually
            if (currentEdges.none { it.sourceId == sourceNode.id && it.targetId == targetNode.id && it.label == relation }) {
                currentEdges.add(
                    MemoryEdge(
                        sourceId = sourceNode.id,
                        targetId = targetNode.id,
                        label = relation
                    )
                )
            }

            // 4. Update Terminal Log
            val currentLogs = _discoveryLogs.value.toMutableList()
            currentLogs.add("Discovered relationship: [$sourceLabel] -> [$relation] -> [$targetLabel]")

            // 5. Emit StateFlow Updates (Triggers UI Recomposition)
            _nodes.value = currentNodes
            _edges.value = currentEdges
            _discoveryLogs.value = currentLogs
        }
    }

    /**
     * Dynamically update the overall vault indexing scan progress
     */
    fun setScanProgress(progress: Float) {
        _scanProgress.value = progress.coerceIn(0f, 1f)
    }

    /**
     * Helper logic to organically scatter new nodes across the Canvas as they are discovered.
     */
    private fun calculateNextOffset(index: Int): Offset {
        // Spiral algorithmic placement (Golden Ratio) for natural graph distribution
        val radius = 150f + (index * 25f)
        val angle = index * 2.4f 
        val centerX = 500f
        val centerY = 800f
        val x = (Math.cos(angle.toDouble()) * radius).toFloat() + centerX
        val y = (Math.sin(angle.toDouble()) * radius).toFloat() + centerY
        return Offset(x, y)
    }
}
