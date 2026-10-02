package com.reveila.util.io

import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Convert lines into the canonical MIME format,
 * by terminating lines with CRLF (\r\n).
 */
open class CRLFOutputStream(os: OutputStream) : FilterOutputStream(os) {

    protected var lastb: Int = -1

    companion object {
        @JvmField
        protected val newline: ByteArray = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte())
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
        var start = off
        val total = off + len
        for (i in start until total) {
            if (b[i] == '\r'.code.toByte()) {
                `out`.write(b, start, i - start)
                `out`.write(newline)
                start = i + 1
            } else if (b[i] == '\n'.code.toByte()) {
                if (lastb != '\r'.code) {
                    `out`.write(b, start, i - start)
                    `out`.write(newline)
                }
                start = i + 1
            }
            lastb = b[i].toInt()
        }
        if ((total - start) > 0) {
            `out`.write(b, start, total - start)
        }
    }
}
