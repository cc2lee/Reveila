package com.reveila.aegin.model

import java.io.File

/**
 * Abstraction over KnowledgeVault operations used by [VaultDeltaScanner],
 * allowing both Android and Desktop bridges (and unit tests) to share
 * identical delta-indexing logic.
 */
interface VaultIndexSink {
    val isReady: Boolean
    fun clear()
    fun setFocusKeywords(keywordsCsv: String)
    fun indexDocument(id: String, content: String, metadata: Map<String, Any?>)
    fun removeDocument(id: String)
    fun getDocumentCount(): Int
    fun getChunkCount(): Int
}

/**
 * Outcome of an incremental delta scan over a Personal Knowledge Folder.
 */
data class VaultDeltaScanSummary(
    val totalIndexedFiles: Int,
    val totalEntitiesFound: Int,
    val chunkCount: Int,
    val addedFiles: Int,
    val modifiedFiles: Int,
    val removedFiles: Int,
    val skippedFiles: Int,
    val fullRebuild: Boolean
)

/**
 * Stateful, thread-safe delta scanner for the Personal Knowledge Folder.
 *
 * Tracks each indexed file's `(lastModified, sizeBytes)` fingerprint per canonical vault root:
 * - **Unchanged files** are skipped before any disk read, PDF/DOCX extraction, or embedding.
 * - **Added / modified files** are extracted and indexed via [VaultIndexSink.indexDocument].
 * - **Deleted / emptied files** are evicted via [VaultIndexSink.removeDocument].
 * - **Folder switches** or external `KnowledgeVault.clear()` resets automatically trigger a clean rebuild.
 */
class VaultDeltaScanner(
    private val textExtractor: (File) -> String = VaultFileScanner::extractFileText
) {
    private data class FileFingerprint(
        val lastModified: Long,
        val sizeBytes: Long
    )

    private data class IndexedFileState(
        val fingerprint: FileFingerprint,
        val entitiesCount: Int
    )

    private val lock = Any()
    private var lastRootCanonicalPath: String? = null
    private val indexedFiles = linkedMapOf<String, IndexedFileState>()

    fun reset() {
        synchronized(lock) {
            lastRootCanonicalPath = null
            indexedFiles.clear()
        }
    }

    fun scan(
        rootDir: File,
        keywordsCsv: String,
        sink: VaultIndexSink,
        fallbackDir: File? = null
    ): VaultDeltaScanSummary = synchronized(lock) {
        var effectiveRoot = rootDir
        var scannedFiles = VaultFileScanner.collectSupportedFiles(rootDir)
        if (scannedFiles.isEmpty() && fallbackDir != null) {
            val fallbackFiles = VaultFileScanner.collectSupportedFiles(fallbackDir)
            if (fallbackFiles.isNotEmpty()) {
                effectiveRoot = fallbackDir
                scannedFiles = fallbackFiles
            }
        }

        val effectiveRootPath = runCatching { effectiveRoot.canonicalPath }.getOrElse { effectiveRoot.absolutePath }
        val keywords = keywordsCsv
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

        val folderChanged = lastRootCanonicalPath != null && lastRootCanonicalPath != effectiveRootPath
        val vaultExternallyCleared = sink.isReady && indexedFiles.isNotEmpty() && sink.getDocumentCount() == 0
        val fullRebuild = folderChanged || vaultExternallyCleared || lastRootCanonicalPath == null

        if (folderChanged) {
            if (sink.isReady) {
                sink.clear()
            }
            indexedFiles.clear()
        } else if (vaultExternallyCleared) {
            indexedFiles.clear()
        }

        if (sink.isReady) {
            sink.setFocusKeywords(keywordsCsv)
        }

        val currentPaths = scannedFiles.mapTo(HashSet(scannedFiles.size)) { it.relativePath }
        var addedFiles = 0
        var modifiedFiles = 0
        var removedFiles = 0
        var skippedFiles = 0

        // 1. Evict deleted files from the index
        val deletedPaths = indexedFiles.keys.filter { it !in currentPaths }
        for (deletedPath in deletedPaths) {
            if (sink.isReady) {
                sink.removeDocument(deletedPath)
            }
            indexedFiles.remove(deletedPath)
            removedFiles++
        }

        // 2. Process only added or modified files; skip unchanged files before any I/O
        var unreadyFilesCount = 0
        var unreadyEntitiesCount = 0

        for (item in scannedFiles) {
            val file = item.file
            val fingerprint = FileFingerprint(
                lastModified = file.lastModified(),
                sizeBytes = file.length()
            )
            val existing = indexedFiles[item.relativePath]
            if (sink.isReady && existing != null && existing.fingerprint == fingerprint) {
                skippedFiles++
                continue
            }

            val content = textExtractor(file)
            if (file.name == "Reveila_Sovereign_Overview.md" && content.contains(VaultFileScanner.LEGACY_STUB_MARKER)) {
                try {
                    file.delete()
                } catch (_: Exception) {
                }
                if (existing != null) {
                    if (sink.isReady) {
                        sink.removeDocument(item.relativePath)
                    }
                    indexedFiles.remove(item.relativePath)
                    removedFiles++
                }
                continue
            }

            if (content.isNotBlank()) {
                val matched = keywords.count { kw ->
                    item.relativePath.lowercase().contains(kw) || content.lowercase().contains(kw)
                }
                val entities = matched + 2

                if (sink.isReady) {
                    val meta = mapOf<String, Any?>(
                        "title" to file.name,
                        "relativePath" to item.relativePath,
                        "uri" to file.toURI().toString()
                    )
                    sink.indexDocument(item.relativePath, content, meta)
                    indexedFiles[item.relativePath] = IndexedFileState(
                        fingerprint = fingerprint,
                        entitiesCount = entities
                    )
                } else {
                    unreadyFilesCount++
                    unreadyEntitiesCount += entities
                }

                if (existing == null) {
                    addedFiles++
                } else {
                    modifiedFiles++
                }
            } else if (existing != null) {
                if (sink.isReady) {
                    sink.removeDocument(item.relativePath)
                }
                indexedFiles.remove(item.relativePath)
                removedFiles++
            }
        }

        if (sink.isReady) {
            lastRootCanonicalPath = effectiveRootPath
        }

        val totalFiles = if (sink.isReady) indexedFiles.size else unreadyFilesCount
        val totalEntities = if (sink.isReady) {
            indexedFiles.values.sumOf { it.entitiesCount }
        } else {
            unreadyEntitiesCount
        }
        val chunks = if (sink.isReady) sink.getChunkCount() else 0

        VaultDeltaScanSummary(
            totalIndexedFiles = totalFiles,
            totalEntitiesFound = totalEntities,
            chunkCount = chunks,
            addedFiles = addedFiles,
            modifiedFiles = modifiedFiles,
            removedFiles = removedFiles,
            skippedFiles = skippedFiles,
            fullRebuild = fullRebuild
        )
    }
}
