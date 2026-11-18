package com.reveila.tools.download

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ResumableDownloadManagerTest {

    private fun sampleBytes(size: Int): ByteArray =
        ByteArray(size) { ((it * 31 + 7) % 251).toByte() }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun testStoragePreflightRejectsOversizedDownloadBeforeHttpRequest() {
        val tempRoot = createTempDirectory("reveila-dl-storage").toFile()
        try {
            val sysHome = File(tempRoot, "system-home")
            val manager = ResumableDownloadManager(configuredSystemHome = sysHome.absolutePath)
            val dstFile = File(tempRoot, "out/model.bin")
            val errDeferred = CompletableDeferred<Throwable>()

            manager.download(
                request = DownloadRequest(
                    downloadId = "huge-model",
                    url = "http://127.0.0.1:1/never-called",
                    destinationPath = dstFile.absolutePath,
                    expectedSizeBytes = Long.MAX_VALUE / 2
                ),
                callback = object : DownloadCallback {
                    override fun onProgress(percentage: Int) {}
                    override fun onSuccess(filePath: String) {}
                    override fun onError(error: Throwable) {
                        errDeferred.complete(error)
                    }
                }
            )

            val err = runBlocking { withTimeout(5000) { errDeferred.await() } }
            assertTrue(err is InsufficientStorageException)
            assertEquals(DownloadStatus.FAILED, manager.getProgress("huge-model")?.status)
            assertFalse(dstFile.exists())
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun testPauseResumeCancelAndFileDownloadCallback() {
        val payload = sampleBytes(24_576)
        val expectedSha = sha256(payload)
        val firstChunkSent = CompletableDeferred<Unit>()
        var observedRange: String? = null

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port

        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "http://127.0.0.1:$port/asset.bin")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }

        server.createContext("/asset.bin") { exchange ->
            val range = exchange.requestHeaders.getFirst("Range")
            if (range != null && range.startsWith("bytes=")) {
                observedRange = range
                val start = range.removePrefix("bytes=").substringBefore('-').toIntOrNull() ?: 0
                val slice = payload.copyOfRange(start, payload.size)
                exchange.responseHeaders.add("Content-Range", "bytes $start-${payload.size - 1}/${payload.size}")
                exchange.sendResponseHeaders(206, slice.size.toLong())
                exchange.responseBody.use { it.write(slice) }
            } else {
                exchange.sendResponseHeaders(200, payload.size.toLong())
                exchange.responseBody.use { out ->
                    out.write(payload, 0, 8192)
                    out.flush()
                    firstChunkSent.complete(Unit)
                    try {
                        Thread.sleep(5000)
                        out.write(payload, 8192, payload.size - 8192)
                    } catch (_: Exception) {
                    }
                }
            }
        }

        server.start()
        val tempRoot = createTempDirectory("reveila-dl-flow").toFile()
        try {
            val sysHome = File(tempRoot, "system-home")
            val manager = ResumableDownloadManager(configuredSystemHome = sysHome.absolutePath)
            val dstFile = File(tempRoot, "models/asset.bin")
            val tempDir = File(manager.resolveTempDirectory())

            runBlocking {
                val handle = manager.download(
                    DownloadRequest(
                        downloadId = "asset-1",
                        url = "http://127.0.0.1:$port/redirect",
                        destinationPath = dstFile.absolutePath,
                        expectedSha256 = expectedSha,
                        expectedSizeBytes = payload.size.toLong()
                    )
                )

                withTimeout(5000) { firstChunkSent.await() }
                Thread.sleep(150)

                assertTrue(manager.pause("asset-1"))
                assertEquals(DownloadStatus.PAUSED, handle.getStatus())
                val partFile = File(tempDir, "asset-1.part")
                val metaFile = File(tempDir, "asset-1.part.meta")
                assertTrue(partFile.exists())
                assertTrue(metaFile.exists())
                assertTrue(manager.listIncompleteDownloads().any { it.downloadId == "asset-1" })

                // Resume via FileDownloadCallback (ModelManager.DownloadCallback equivalent)
                val successDeferred = CompletableDeferred<File>()
                val progressSeen = mutableListOf<Int>()
                val resumedHandle = manager.resume(
                    "asset-1",
                    object : FileDownloadCallback {
                        override fun onProgress(percentage: Int) {
                            progressSeen.add(percentage)
                        }

                        override fun onSuccess(modelFile: File) {
                            successDeferred.complete(modelFile)
                        }

                        override fun onError(e: Exception) {
                            successDeferred.completeExceptionally(e)
                        }
                    }.asDownloadCallback()
                )
                assertNotNull(resumedHandle)

                val finalFile = withTimeout(5000) { successDeferred.await() }
                assertEquals(dstFile.absolutePath, finalFile.absolutePath)
                assertTrue(finalFile.exists())
                assertEquals(expectedSha, sha256(finalFile.readBytes()))
                assertFalse(partFile.exists())
                assertFalse(metaFile.exists())
                assertNotNull(observedRange)
                assertTrue(progressSeen.contains(100))

                // Cancel deletes partial files
                partFile.writeBytes(byteArrayOf(1, 2, 3, 4))
                metaFile.writeText("{\"downloadId\":\"asset-1\"}")
                assertTrue(manager.cancel("asset-1"))
                assertFalse(partFile.exists())
                assertFalse(metaFile.exists())
            }
        } finally {
            server.stop(0)
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun testHousekeepingCleansStaleTempFilesAndOrphanMeta() {
        val tempRoot = createTempDirectory("reveila-dl-clean").toFile()
        try {
            val sysHome = File(tempRoot, "system-home")
            val manager = ResumableDownloadManager(configuredSystemHome = sysHome.absolutePath)
            val stagingDir = File(manager.resolveTempDirectory())

            val stalePart = File(stagingDir, "stale-model.part").apply {
                writeBytes(ByteArray(2048))
                setLastModified(System.currentTimeMillis() - 10L * 24 * 3600 * 1000)
            }
            val staleMeta = File(stagingDir, "stale-model.part.meta").apply {
                writeText("{\"downloadId\":\"stale-model\"}")
                setLastModified(System.currentTimeMillis() - 10L * 24 * 3600 * 1000)
            }
            val orphanMeta = File(stagingDir, "orphan.part.meta").apply {
                writeText("{\"downloadId\":\"orphan\"}")
            }
            val freshPart = File(stagingDir, "fresh-model.part").apply {
                writeBytes(ByteArray(1024))
            }

            val report = manager.cleanupTempDirectory(maxAgeMillis = 7L * 24 * 3600 * 1000)
            assertFalse(stalePart.exists())
            assertFalse(staleMeta.exists())
            assertFalse(orphanMeta.exists())
            assertTrue(freshPart.exists())
            assertEquals(3, report.deletedFiles)
            assertTrue(report.reclaimedBytes >= 2048L)

            val tool = ResumableDownloadTool(manager)
            val checkJson = tool.execute(mapOf("action" to "check_storage", "destinationPath" to tempRoot.absolutePath, "requiredBytes" to 1024))
            assertTrue(checkJson.contains("\"hasEnoughStorage\":true"))
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
