package com.reveila.aegin.model

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VaultDeltaScannerTest {

    private class RecordingVaultSink : VaultIndexSink {
        override var isReady: Boolean = true
        var clearCalls: Int = 0
        val indexedIds = mutableListOf<String>()
        val removedIds = mutableListOf<String>()
        val storedDocs = linkedMapOf<String, String>()
        var lastKeywords: String = ""

        override fun clear() {
            clearCalls++
            storedDocs.clear()
        }

        override fun setFocusKeywords(keywordsCsv: String) {
            lastKeywords = keywordsCsv
        }

        override fun indexDocument(id: String, content: String, metadata: Map<String, Any?>) {
            indexedIds.add(id)
            storedDocs[id] = content
        }

        override fun removeDocument(id: String) {
            removedIds.add(id)
            storedDocs.remove(id)
        }

        override fun getDocumentCount(): Int = storedDocs.size

        override fun getChunkCount(): Int = storedDocs.size * 2

        fun resetCounters() {
            clearCalls = 0
            indexedIds.clear()
            removedIds.clear()
        }
    }

    @Test
    fun rescanSkipsUnchangedFilesAndProcessesOnlyDeltas() {
        val tempRoot = Files.createTempDirectory("vault-delta-test").toFile()
        try {
            val notesDir = File(tempRoot, "notes").apply { mkdirs() }
            val fileA = File(tempRoot, "roadmap.md").apply { writeText("Q4 architectural roadmap") }
            val fileB = File(notesDir, "budget.txt").apply { writeText("Annual hardware budget") }
            val fileC = File(notesDir, "legacy.txt").apply { writeText("Old report to be deleted") }

            val extractedFiles = mutableListOf<String>()
            val scanner = VaultDeltaScanner { file ->
                extractedFiles.add(file.name)
                VaultFileScanner.extractFileText(file)
            }
            val sink = RecordingVaultSink()

            // 1. Initial scan indexes all 3 files
            val first = scanner.scan(tempRoot, "roadmap, budget", sink)
            assertTrue(first.fullRebuild)
            assertEquals(3, first.totalIndexedFiles)
            assertEquals(3, first.addedFiles)
            assertEquals(0, first.modifiedFiles)
            assertEquals(0, first.removedFiles)
            assertEquals(0, first.skippedFiles)
            assertEquals(3, extractedFiles.size)
            assertEquals(0, sink.clearCalls)

            // 2. Immediate re-scan with no changes must read ZERO files and call indexDocument ZERO times
            extractedFiles.clear()
            sink.resetCounters()
            val second = scanner.scan(tempRoot, "roadmap, budget", sink)
            assertFalse(second.fullRebuild)
            assertEquals(3, second.totalIndexedFiles)
            assertEquals(0, second.addedFiles)
            assertEquals(0, second.modifiedFiles)
            assertEquals(0, second.removedFiles)
            assertEquals(3, second.skippedFiles)
            assertTrue(extractedFiles.isEmpty(), "Expected 0 file extractions on unchanged re-scan, got $extractedFiles")
            assertTrue(sink.indexedIds.isEmpty())
            assertTrue(sink.removedIds.isEmpty())
            assertEquals(0, sink.clearCalls)

            // 3. Delta: modify fileB, add fileD, delete fileC; leave fileA untouched
            fileB.writeText("Updated annual hardware budget with extra GPU nodes")
            fileB.setLastModified(fileB.lastModified() + 5_000L)
            val fileD = File(tempRoot, "strategy.md").apply { writeText("New strategy brief") }
            assertTrue(fileC.delete())

            extractedFiles.clear()
            sink.resetCounters()
            val third = scanner.scan(tempRoot, "roadmap, budget, strategy", sink)
            assertFalse(third.fullRebuild)
            assertEquals(3, third.totalIndexedFiles)
            assertEquals(1, third.addedFiles)
            assertEquals(1, third.modifiedFiles)
            assertEquals(1, third.removedFiles)
            assertEquals(1, third.skippedFiles)
            assertEquals(setOf(fileB.name, fileD.name), extractedFiles.toSet())
            assertFalse(fileA.name in extractedFiles, "Unchanged fileA must not be re-read")
            assertEquals(setOf("notes/budget.txt", "strategy.md"), sink.indexedIds.toSet())
            assertEquals(listOf("notes/legacy.txt"), sink.removedIds)
            assertEquals(0, sink.clearCalls)
            assertEquals(setOf("roadmap.md", "notes/budget.txt", "strategy.md"), sink.storedDocs.keys)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun switchingFolderClearsOldIndexAndRebuildsCleanly() {
        val rootA = Files.createTempDirectory("vault-delta-rootA").toFile()
        val rootB = Files.createTempDirectory("vault-delta-rootB").toFile()
        try {
            File(rootA, "alpha.txt").writeText("Alpha content")
            File(rootB, "beta.txt").writeText("Beta content")

            val scanner = VaultDeltaScanner()
            val sink = RecordingVaultSink()

            val first = scanner.scan(rootA, "alpha", sink)
            assertEquals(1, first.totalIndexedFiles)
            assertEquals(setOf("alpha.txt"), sink.storedDocs.keys)

            sink.resetCounters()
            val switched = scanner.scan(rootB, "beta", sink)
            assertTrue(switched.fullRebuild)
            assertEquals(1, sink.clearCalls, "Switching vault root must clear previous folder's index")
            assertEquals(1, switched.totalIndexedFiles)
            assertEquals(1, switched.addedFiles)
            assertEquals(setOf("beta.txt"), sink.storedDocs.keys)
        } finally {
            rootA.deleteRecursively()
            rootB.deleteRecursively()
        }
    }

    @Test
    fun externalVaultClearTriggersAutomaticReindex() {
        val tempRoot = Files.createTempDirectory("vault-delta-clear").toFile()
        try {
            File(tempRoot, "doc1.md").writeText("Important engineering spec")

            val scanner = VaultDeltaScanner()
            val sink = RecordingVaultSink()

            scanner.scan(tempRoot, "spec", sink)
            assertEquals(1, sink.getDocumentCount())

            // Simulate external KnowledgeVault.clear() (e.g. from Settings reset)
            sink.clear()
            sink.resetCounters()
            assertEquals(0, sink.getDocumentCount())

            val rebuilt = scanner.scan(tempRoot, "spec", sink)
            assertTrue(rebuilt.fullRebuild)
            assertEquals(1, rebuilt.totalIndexedFiles)
            assertEquals(1, rebuilt.addedFiles)
            assertEquals(0, rebuilt.skippedFiles)
            assertEquals(1, sink.getDocumentCount())
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
