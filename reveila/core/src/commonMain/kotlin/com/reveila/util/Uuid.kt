package com.reveila.util

import kotlin.random.Random

object Uuid {
    /**
     * Generates a random RFC 4122 version 4 UUID string.
     */
    fun randomUuid(): String {
        val bytes = Random.Default.nextBytes(16)
        bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte() // version 4
        bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte() // variant RFC 4122
        val hexChars = "0123456789abcdef"
        val sb = StringBuilder(36)
        for (i in 0 until 16) {
            if (i == 4 || i == 6 || i == 8 || i == 10) sb.append('-')
            val b = bytes[i].toInt() and 0xff
            sb.append(hexChars[b ushr 4])
            sb.append(hexChars[b and 0x0f])
        }
        return sb.toString()
    }
}
