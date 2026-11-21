package com.reveila.persistence

import com.reveila.system.concurrency.PlatformLock
import kotlin.math.sqrt

/**
 * In-memory thread-safe VectorStore implementation supporting cosine similarity search.
 * Computes exact inner product / cosine similarity over dense embedding vectors.
 */
open class DefaultVectorStore : VectorStore {

    data class Entry(
        val id: String,
        val vector: FloatArray,
        val payload: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as Entry
            return id == other.id && vector.contentEquals(other.vector) && payload == other.payload
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + vector.contentHashCode()
            result = 31 * result + payload.hashCode()
            return result
        }
    }

    private val entries = linkedMapOf<String, Entry>()
    private val lock = PlatformLock()

    override fun insert(id: String, vector: FloatArray, payload: String) {
        lock.withLock {
            entries[id] = Entry(id, vector, payload)
        }
    }

    override fun search(query: FloatArray, limit: Int): List<VectorMatch> {
        val snapshot: List<Entry> = lock.withLock {
            entries.values.toList()
        }

        if (snapshot.isEmpty() || query.isEmpty()) {
            return emptyList()
        }

        val qNorm = norm(query)
        if (qNorm < 1e-6f) {
            return emptyList()
        }

        val scored = snapshot.map { entry ->
            val eNorm = norm(entry.vector)
            val score = if (eNorm < 1e-6f) {
                0.0
            } else {
                val dot = dotProduct(query, entry.vector)
                (dot / (qNorm * eNorm)).toDouble().coerceIn(-1.0, 1.0)
            }
            VectorMatch(entry.id, entry.vector, score, entry.payload)
        }

        return scored
            .sortedByDescending { it.score }
            .take(limit)
    }

    override fun count(): Int {
        return lock.withLock {
            entries.size
        }
    }

    override fun delete(id: String): Boolean {
        return lock.withLock {
            entries.remove(id) != null
        }
    }

    override fun clear() {
        lock.withLock {
            entries.clear()
        }
    }

    open fun getAll(): List<Entry> {
        return lock.withLock {
            entries.values.toList()
        }
    }

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        val len = minOf(a.size, b.size)
        var sum = 0.0f
        for (i in 0 until len) {
            sum += a[i] * b[i]
        }
        return sum
    }

    private fun norm(v: FloatArray): Float {
        var sumSquares = 0.0f
        for (f in v) {
            sumSquares += f * f
        }
        return sqrt(sumSquares.toDouble()).toFloat()
    }
}
