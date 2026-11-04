package com.reveila.ai

import com.reveila.service.DataService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DualIndexSyncTest {

    @Test
    fun testDataServiceToKnowledgeVaultDualIndexSync() {
        val dataService = DataService()
        val vault = KnowledgeVault()

        vault.attachDataService(dataService, setOf("document", "note"))

        // 1. Persist a document entity through DataService
        val docMap = mapOf(
            "key" to mapOf("primary" to mapOf("id" to "doc-enterprise-101")),
            "attributes" to mapOf(
                "title" to "Enterprise Architecture",
                "content" to "The Reveila Suite utilizes a decoupled hexagonal persistence architecture."
            )
        )
        dataService.save("document", docMap)

        // Verify document is indexed in KnowledgeVault
        assertEquals(1, vault.getDocumentCount())
        assertTrue(vault.getChunkCount() > 0)

        // Verify semantic search retrieves the indexed content
        val searchResult = vault.search("hexagonal architecture")
        assertTrue(searchResult.contains("hexagonal persistence architecture"))

        // 2. Test Content Hash deduplication: Saving identical document should not re-index/duplicate
        val initialChunkCount = vault.getChunkCount()
        dataService.save("document", docMap)
        assertEquals(initialChunkCount, vault.getChunkCount())
        assertEquals(1, vault.getDocumentCount())

        // 3. Test Deletion synchronization
        val key = mapOf("primary" to mapOf("id" to "doc-enterprise-101"))
        dataService.delete("document", key)

        assertEquals(0, vault.getDocumentCount())
        assertEquals(0, vault.getChunkCount())
        val afterDeleteSearch = vault.search("hexagonal architecture")
        assertEquals("No relevant internal documents found.", afterDeleteSearch)
    }
}
