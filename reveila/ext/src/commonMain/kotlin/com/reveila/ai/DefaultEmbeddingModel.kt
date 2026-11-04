package com.reveila.ai

import kotlin.math.sqrt

/**
 * High-performance, deterministic on-device semantic embedding generator.
 * Produces normalized 128-dimensional dense vectors using word tokenization,
 * subword n-gram hashing, and positional weighting.
 *
 * Designed for Kotlin Multiplatform mobile execution without requiring heavyweight native C++ runtimes.
 */
open class DefaultEmbeddingModel(val dimensions: Int = 128) : ReveilaEmbeddingModel {

    override fun embed(text: String): FloatArray {
        val vector = FloatArray(dimensions)
        if (text.isBlank()) {
            return vector
        }

        val cleaned = text.lowercase()
        val tokens = cleaned.split(Regex("[^a-z0-9_-]+")).filter { it.isNotBlank() }

        if (tokens.isEmpty()) {
            return vector
        }

        for ((index, token) in tokens.withIndex()) {
            // Positional weight: early tokens in title/heading get a slight boost
            val posWeight = 1.0f + (1.0f / (1.0f + index * 0.05f))

            // 1. Full token hash projection
            val tokenHash = hashToken(token)
            val bucket = (tokenHash % dimensions + dimensions) % dimensions
            val sign = if (((tokenHash ushr 16) and 1) == 0) 1.0f else -1.0f
            vector[bucket] += sign * posWeight * 2.0f

            // 2. Character 3-gram subword hashing for morphological similarity
            if (token.length >= 3) {
                for (i in 0..(token.length - 3)) {
                    val tri = token.substring(i, i + 3)
                    val triHash = hashToken(tri)
                    val triBucket = (triHash % dimensions + dimensions) % dimensions
                    val triSign = if (((triHash ushr 16) and 1) == 0) 0.5f else -0.5f
                    vector[triBucket] += triSign * posWeight
                }
            }
        }

        // L2 Unit Normalization so dot product == cosine similarity
        var sumSquares = 0.0f
        for (v in vector) {
            sumSquares += v * v
        }

        val norm = sqrt(sumSquares.toDouble()).toFloat()
        if (norm > 1e-6f) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }

        return vector
    }

    private fun hashToken(token: String): Int {
        var h = 0x811c9dc5.toInt() // FNV-1a 32-bit offset basis
        for (ch in token) {
            h = h xor ch.code
            h *= 0x01000193 // FNV-1a prime
        }
        // Bit-mix finalizer
        h = h xor (h ushr 16)
        h *= 0x45d9f3b
        h = h xor (h ushr 16)
        return h
    }
}
