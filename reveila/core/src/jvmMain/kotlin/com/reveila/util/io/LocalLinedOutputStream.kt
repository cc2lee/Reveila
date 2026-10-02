package com.reveila.util.io

import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Convert the various newline conventions to the local platform's
 * newline convention.
 */
open class LocalLinedOutputStream(os: OutputStream) : FilterOutputStream(os) {

    private var lastb: Int = -1
    private val newline: ByteArray

    init {
        var s = System.getProperty("line.separator")
        if (s.isNullOrEmpty()) {
            s = "\n"
        }
        newline = s.toByteArray()
    }

    @Throws(IOException::class)
    override fun write(b: Int) {
        if (b == '\r'.code) {
            `out`.write(newline)
        } else if (b == '\n'.code) {
            if (lastb != '\r'.code) {
                `out`.write(newline)
            }
        } else {
            `out`.write(b)
        }
        lastb = b
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray) {
        write(b, 0, b.size)
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        for (i in 0 until len) {
            write(b[off + i].toInt())
        }
    }
}
