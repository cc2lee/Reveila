package com.reveila.system

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryProvisionerTest {

    private class FakeLibraryHttpClient : LibraryHttpClient {
        var manifestJson: String? = null
        val headByUrl = LinkedHashMap<String, RemoteArtifactHead>()
        val contentByUrl = LinkedHashMap<String, ByteArray>()
        val downloadCalls = mutableListOf<String>()

        override fun fetchText(url: String): String? =
            if (url.endsWith("/libs/manifest.json")) manifestJson else null

        override fun head(url: String): RemoteArtifactHead =
            headByUrl[url] ?: RemoteArtifactHead(exists = contentByUrl.containsKey(url))

        override fun downloadToFile(url: String, destination: File) {
            downloadCalls.add(url)
            val bytes = contentByUrl[url] ?: error("404 Not Found: $url")
            destination.parentFile?.mkdirs()
            destination.writeBytes(bytes)
        }
    }

    @Test
    fun downloadsMissingSkipsUpToDateAndUpdatesOnNewerVersionOrPublishDate() {
        val tempDir = Files.createTempDirectory("reveila-lib-prov-test").toFile()
        try {
            val libsDir = File(tempDir, "libs")
            val baseUrl = "http://192.168.1.219:8000"
            val jarUrl = "$baseUrl/libs/sample-plugin-lib.jar"
            val http = FakeLibraryHttpClient()
            http.contentByUrl[jarUrl] = "jar-bytes-v1".toByteArray()
            http.manifestJson = """
                {
                  "libraries": [
                    {
                      "name": "sample-plugin-lib.jar",
                      "version": "1.0.0",
                      "publishedAt": "2026-11-20T10:00:00Z"
                    }
                  ]
                }
            """.trimIndent()

            val req = listOf(LibraryDescriptor(name = "sample-plugin-lib.jar"))

            // 1. Missing locally -> downloads v1.0.0
            val p1 = LibraryProvisioner(libsDir, baseUrl, http)
            val r1 = p1.ensureLibraries(req)
            assertEquals(1, r1.downloadedFiles.size)
            assertEquals(0, r1.skippedUpToDateFiles.size)
            assertEquals("jar-bytes-v1", File(libsDir, "sample-plugin-lib.jar").readText())

            // 2. Same version & publish date -> skips download
            val p2 = LibraryProvisioner(libsDir, baseUrl, http)
            val r2 = p2.ensureLibraries(req)
            assertEquals(0, r2.downloadedFiles.size)
            assertEquals(1, r2.skippedUpToDateFiles.size)
            assertEquals(1, http.downloadCalls.size)

            // 3. Newer version on download server -> re-downloads
            http.contentByUrl[jarUrl] = "jar-bytes-v1.1".toByteArray()
            http.manifestJson = """
                {
                  "libraries": [
                    {
                      "name": "sample-plugin-lib.jar",
                      "version": "1.1.0",
                      "publishedAt": "2026-11-20T10:00:00Z"
                    }
                  ]
                }
            """.trimIndent()
            val p3 = LibraryProvisioner(libsDir, baseUrl, http)
            val r3 = p3.ensureLibraries(req)
            assertEquals(1, r3.downloadedFiles.size)
            assertEquals("jar-bytes-v1.1", File(libsDir, "sample-plugin-lib.jar").readText())

            // 4. Same version, newer publish date -> re-downloads
            http.contentByUrl[jarUrl] = "jar-bytes-v1.1-hotfix".toByteArray()
            http.manifestJson = """
                {
                  "libraries": [
                    {
                      "name": "sample-plugin-lib.jar",
                      "version": "1.1.0",
                      "publishedAt": "2026-11-21T18:00:00Z"
                    }
                  ]
                }
            """.trimIndent()
            val p4 = LibraryProvisioner(libsDir, baseUrl, http)
            val r4 = p4.ensureLibraries(req)
            assertEquals(1, r4.downloadedFiles.size)
            assertEquals("jar-bytes-v1.1-hotfix", File(libsDir, "sample-plugin-lib.jar").readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun fallsBackToHttpHeadLastModifiedWhenNoManifestHosted() {
        val tempDir = Files.createTempDirectory("reveila-lib-head-test").toFile()
        try {
            val libsDir = File(tempDir, "libs")
            val baseUrl = "http://192.168.1.219:8000"
            val jarUrl = "$baseUrl/libs/plain-static.jar"
            val http = FakeLibraryHttpClient()
            http.manifestJson = null // Plain static HTTP file server without manifest.json
            http.contentByUrl[jarUrl] = "static-v1".toByteArray()
            http.headByUrl[jarUrl] = RemoteArtifactHead(exists = true, lastModifiedMs = 1_700_000_000_000L, etag = "\"v1\"")

            val req = listOf(LibraryDescriptor(name = "plain-static.jar"))

            // First run downloads
            val r1 = LibraryProvisioner(libsDir, baseUrl, http).ensureLibraries(req)
            assertEquals(1, r1.downloadedFiles.size)

            // Same Last-Modified & ETag -> skips
            val r2 = LibraryProvisioner(libsDir, baseUrl, http).ensureLibraries(req)
            assertEquals(0, r2.downloadedFiles.size)
            assertEquals(1, r2.skippedUpToDateFiles.size)

            // Newer Last-Modified -> re-downloads
            http.contentByUrl[jarUrl] = "static-v2".toByteArray()
            http.headByUrl[jarUrl] = RemoteArtifactHead(exists = true, lastModifiedMs = 1_700_000_100_000L, etag = "\"v2\"")
            val r3 = LibraryProvisioner(libsDir, baseUrl, http).ensureLibraries(req)
            assertEquals(1, r3.downloadedFiles.size)
            assertTrue(File(libsDir, "plain-static.jar").readText() == "static-v2")
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
