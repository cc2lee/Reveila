package com.reveila.persistence

/**
 * Represents a single result from a VectorStore similarity search.
 */
data class VectorMatch(
    val id: String,
    val vector: FloatArray,
    val score: Double,
    val payload: String
) {
    fun id(): String = id
    fun vector(): FloatArray = vector
    fun score(): Double = score
    fun payload(): String = payload

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as VectorMatch
        if (id != other.id) return false
        if (!vector.contentEquals(other.vector)) return false
        if (score != other.score) return false
        if (payload != other.payload) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + vector.contentHashCode()
        result = 31 * result + score.hashCode()
        result = 31 * result + payload.hashCode()
        return result
    }
}
