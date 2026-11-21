package com.reveila.persistence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultVectorStoreTest {

    @Test
    fun testInsertOverwriteDeleteAndSearch() {
        val store = DefaultVectorStore()
        store.insert("doc1_chunk_0", floatArrayOf(1f, 0f, 0f), "alpha")
        store.insert("doc2_chunk_0", floatArrayOf(0f, 1f, 0f), "beta")
        assertEquals(2, store.count())

        // Overwrite existing entry in O(1)
        store.insert("doc1_chunk_0", floatArrayOf(0.9f, 0.1f, 0f), "alpha-updated")
        assertEquals(2, store.count())

        val matches = store.search(floatArrayOf(1f, 0f, 0f), 2)
        assertEquals(2, matches.size)
        assertEquals("doc1_chunk_0", matches[0].id)
        assertEquals("alpha-updated", matches[0].payload)

        assertTrue(store.delete("doc1_chunk_0"))
        assertFalse(store.delete("doc1_chunk_0"))
        assertEquals(1, store.count())

        store.clear()
        assertEquals(0, store.count())
    }

    @Test
    fun testBulkInsertAndDelete() {
        val store = DefaultVectorStore()
        for (i in 0 until 2_000) {
            store.insert("doc_${i}_chunk_0", floatArrayOf(1f, i.toFloat()), "payload-$i")
        }
        assertEquals(2_000, store.count())
        assertEquals(2_000, store.getAll().size)

        for (i in 0 until 500) {
            assertTrue(store.delete("doc_${i}_chunk_0"))
        }
        assertEquals(1_500, store.count())
    }
}
