package com.reveila.aegin.model

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopModelDownloadManagerTest {

    private fun fakeGgufBytes(size: Int = 4096): ByteArray {
        val bytes = ByteArray(size) { (it % 251).toByte() }
        bytes[0] = 'G'.code.toByte()
        bytes[1] = 'G'.code.toByte()
        bytes[2] = 'U'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        return bytes
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun testRedirectRangeResumeAndSha256Verification() {
        val payload = fakeGgufBytes(8192)
        val expectedSha = sha256(payload)
        var observedRangeHeader: String? = null

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port

        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "http://127.0.0.1:$port/cdn/model.gguf")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }

        server.createContext("/cdn/model.gguf") { exchange ->
            val range = exchange.requestHeaders.getFirst("Range")
            observedRangeHeader = range
            if (range != null && range.startsWith("bytes=")) {
                val start = range.removePrefix("bytes=").substringBefore('-').toIntOrNull() ?: 0
                val slice = payload.copyOfRange(start, payload.size)
                exchange.responseHeaders.add("Content-Range", "bytes $start-${payload.size - 1}/${payload.size}")
                exchange.sendResponseHeaders(206, slice.size.toLong())
                exchange.responseBody.use { it.write(slice) }
            } else {
                exchange.sendResponseHeaders(200, payload.size.toLong())
                exchange.responseBody.use { it.write(payload) }
            }
        }

        server.start()
        val tempDir = createTempDirectory("aegin-dl-test").toFile()
        try {
            val targetFile = File(tempDir, "test-model.gguf")
            val partFile = File("${targetFile.absolutePath}.part")
            // Pre-populate first 2048 bytes in .part to verify Range resume across redirect
            partFile.writeBytes(payload.copyOfRange(0, 2048))

            val manager = DesktopModelDownloadManager()
            val ok = runBlocking {
                manager.downloadAndAwait(
                    modelName = "test-model.gguf",
                    candidateUrls = listOf("http://127.0.0.1:$port/redirect"),
                    targetFile = targetFile,
                    expectedSha256 = expectedSha,
                    approxSizeBytes = payload.size.toLong()
                )
            }

            assertTrue(ok)
            assertEquals("bytes=2048-", observedRangeHeader)
            assertTrue(targetFile.exists())
            assertFalse(partFile.exists())
            assertEquals(expectedSha, sha256(targetFile.readBytes()))
            assertEquals("COMPLETED", manager.state.value.status)
            assertEquals(100, manager.state.value.percent)
        } finally {
            server.stop(0)
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testRejectsCorruptHtmlPayloadWithoutPromotingFile() {
        val htmlError = "<html><body>Not a GGUF file</body></html>".repeat(64).toByteArray()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port

        server.createContext("/bad.gguf") { exchange ->
            exchange.sendResponseHeaders(200, htmlError.size.toLong())
            exchange.responseBody.use { it.write(htmlError) }
        }

        server.start()
        val tempDir = createTempDirectory("aegin-bad-dl").toFile()
        try {
            val targetFile = File(tempDir, "bad.gguf")
            val outcome = runBlocking {
                ResumableModelDownloader.downloadWithResume(
                    urls = listOf("http://127.0.0.1:$port/bad.gguf"),
                    targetFile = targetFile
                )
            }
            assertFalse(outcome.succeeded)
            assertFalse(targetFile.exists())
            assertFalse(File("${targetFile.absolutePath}.part").exists())
        } finally {
            server.stop(0)
            tempDir.deleteRecursively()
        }
    }
}
