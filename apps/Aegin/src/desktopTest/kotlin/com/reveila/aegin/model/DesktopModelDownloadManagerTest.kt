package com.reveila.aegin.model

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

    @Test
    fun testPausePreservesPartAndCancelDeletesPart() {
        val payload = fakeGgufBytes(16_384)
        val expectedSha = sha256(payload)
        val firstChunkSent = CompletableDeferred<Unit>()

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port

        server.createContext("/slow.gguf") { exchange ->
            val range = exchange.requestHeaders.getFirst("Range")
            if (range != null && range.startsWith("bytes=")) {
                val start = range.removePrefix("bytes=").substringBefore('-').toIntOrNull() ?: 0
                val slice = payload.copyOfRange(start, payload.size)
                exchange.responseHeaders.add("Content-Range", "bytes $start-${payload.size - 1}/${payload.size}")
                exchange.sendResponseHeaders(206, slice.size.toLong())
                exchange.responseBody.use { it.write(slice) }
            } else {
                exchange.sendResponseHeaders(200, payload.size.toLong())
                exchange.responseBody.use { out ->
                    out.write(payload, 0, 4096)
                    out.flush()
                    firstChunkSent.complete(Unit)
                    try {
                        Thread.sleep(5000)
                        out.write(payload, 4096, payload.size - 4096)
                    } catch (_: Exception) {
                    }
                }
            }
        }

        server.start()
        val tempDir = createTempDirectory("aegin-pause-cancel-test").toFile()
        try {
            val targetFile = File(tempDir, "model.gguf")
            val partFile = File("${targetFile.absolutePath}.part")
            val manager = DesktopModelDownloadManager()

            runBlocking {
                val dlDeferred = async(Dispatchers.IO) {
                    manager.downloadAndAwait(
                        modelName = "model.gguf",
                        candidateUrls = listOf("http://127.0.0.1:$port/slow.gguf"),
                        targetFile = targetFile,
                        expectedSha256 = expectedSha,
                        approxSizeBytes = payload.size.toLong()
                    )
                }

                withTimeout(5000) { firstChunkSent.await() }
                delay(150)
                manager.pauseActiveDownload("model.gguf")
                val pausedOk = dlDeferred.await()

                assertFalse(pausedOk)
                assertEquals("PAUSED", manager.state.value.status)
                assertTrue(partFile.exists())
                assertTrue(partFile.length() > 0L)

                // Resume from paused .part should complete via HTTP 206 Range
                val resumedOk = manager.downloadAndAwait(
                    modelName = "model.gguf",
                    candidateUrls = listOf("http://127.0.0.1:$port/slow.gguf"),
                    targetFile = targetFile,
                    expectedSha256 = expectedSha,
                    approxSizeBytes = payload.size.toLong()
                )
                assertTrue(resumedOk)
                assertEquals("COMPLETED", manager.state.value.status)
                assertTrue(targetFile.exists())
                assertFalse(partFile.exists())

                // Cancel removes any partial file and emits CANCELLED
                partFile.writeBytes(payload.copyOfRange(0, 1024))
                manager.cancelActiveDownload("model.gguf", targetFile)
                assertEquals("CANCELLED", manager.state.value.status)
                assertFalse(partFile.exists())
            }
        } finally {
            server.stop(0)
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testInsufficientStorageFailsImmediatelyAsFatal() {
        val tempDir = createTempDirectory("aegin-storage-test").toFile()
        try {
            val targetFile = File(tempDir, "huge.gguf")
            val outcome = runBlocking {
                ResumableModelDownloader.downloadWithResume(
                    urls = listOf("http://127.0.0.1:1/huge.gguf"),
                    targetFile = targetFile,
                    approxSizeBytes = Long.MAX_VALUE / 2
                )
            }
            assertFalse(outcome.succeeded)
            assertTrue(outcome.fatal)
            assertTrue(outcome.errorMessage?.contains("Insufficient on-device storage", ignoreCase = true) == true)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testCleanupReplacedGgufFilesRemovesPreviousModelsAndParts() {
        val tempDir = createTempDirectory("aegin-replace-test").toFile()
        try {
            val oldModel = File(tempDir, "gemma-2-2b-it-Q4_K_M.gguf").apply { writeBytes(fakeGgufBytes(2048)) }
            val oldPart = File(tempDir, "gemma-2-2b-it-Q4_K_M.gguf.part").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val newModel = File(tempDir, "qwen2.5-0.5b-instruct-q4_k_m.gguf").apply { writeBytes(fakeGgufBytes(2048)) }
            val unrelatedManifest = File(tempDir, "manifest.json").apply { writeText("{}") }

            val removed = ResumableModelDownloader.cleanupReplacedGgufFiles(
                directories = listOf(tempDir),
                keepFilename = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
            )

            assertFalse(oldModel.exists())
            assertFalse(oldPart.exists())
            assertTrue(newModel.exists())
            assertTrue(unrelatedManifest.exists())
            assertTrue(removed.contains("gemma-2-2b-it-Q4_K_M.gguf"))
            assertTrue(removed.contains("gemma-2-2b-it-Q4_K_M.gguf.part"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testVaultFileScannerCollectsNestedDirectoriesAndSkipsHidden() {
        val tempDir = createTempDirectory("aegin-vault-scan-test").toFile()
        try {
            File(tempDir, "root_note.txt").writeText("Root note content")
            File(tempDir, "Architecture/Nested").mkdirs()
            File(tempDir, "Architecture/design.md").writeText("Design doc")
            File(tempDir, "Architecture/Nested/spec.json").writeText("{}")
            File(tempDir, ".hidden").mkdirs()
            File(tempDir, ".hidden/secret.txt").writeText("Should be skipped")
            File(tempDir, "Architecture/image.png").writeText("Unsupported extension")

            val collected = VaultFileScanner.collectSupportedFiles(tempDir)
            assertEquals(
                listOf("Architecture/design.md", "Architecture/Nested/spec.json", "root_note.txt"),
                collected.map { it.relativePath }
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testVaultFileScannerExtractsPdfBillText() {
        val tempDir = createTempDirectory("aegin-pdf-test").toFile()
        try {
            val pdfFile = File(tempDir, "electric-bill.pdf")
            val minimalPdf = """
                %PDF-1.4
                1 0 obj
                << /Length 74 >>
                stream
                BT
                /F1 12 Tf
                (City Power Electric Bill - Total Due: $185.20) Tj
                ET
                endstream
                endobj
                %%EOF
            """.trimIndent()
            pdfFile.writeBytes(minimalPdf.toByteArray(Charsets.ISO_8859_1))

            val collected = VaultFileScanner.collectSupportedFiles(tempDir)
            assertEquals(listOf("electric-bill.pdf"), collected.map { it.relativePath })

            val extracted = VaultFileScanner.extractPdfText(pdfFile)
            assertTrue(extracted.contains("City Power Electric Bill"))
            assertTrue(extracted.contains("185.20"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
