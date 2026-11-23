package com.reveila.system

import com.reveila.system.logging.PlatformLogger
import com.reveila.util.json.JsonUtil
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLClassLoader
import java.security.MessageDigest
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Metadata describing a required component/plugin library.
 */
data class LibraryDescriptor(
    val name: String,
    val version: String? = null,
    val publishedAt: String? = null,
    val sha256: String? = null,
    val url: String? = null
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): LibraryDescriptor? {
            val name = map["name"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return LibraryDescriptor(
                name = name,
                version = map["version"]?.toString()?.trim()?.takeIf { it.isNotEmpty() },
                publishedAt = (map["publishedAt"] ?: map["published_at"] ?: map["publishDate"])
                    ?.toString()?.trim()?.takeIf { it.isNotEmpty() },
                sha256 = map["sha256"]?.toString()?.trim()?.takeIf { it.isNotEmpty() },
                url = map["url"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            )
        }
    }
}

/**
 * Remote HTTP HEAD metadata returned by the download server for a library artifact.
 */
data class RemoteArtifactHead(
    val exists: Boolean,
    val lastModifiedMs: Long? = null,
    val contentLength: Long? = null,
    val etag: String? = null
)

/**
 * Result of provisioning required libraries for a component or batch of components.
 */
data class LibraryProvisionReport(
    val resolvedFiles: List<File>,
    val downloadedFiles: List<File>,
    val skippedUpToDateFiles: List<File>
)

/**
 * Pluggable HTTP client abstraction for [LibraryProvisioner] (enables deterministic unit tests).
 */
interface LibraryHttpClient {
    fun fetchText(url: String): String?
    fun head(url: String): RemoteArtifactHead
    fun downloadToFile(url: String, destination: File)
}

/**
 * Default JVM HTTP implementation backed by [HttpURLConnection].
 */
class JvmLibraryHttpClient(
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 60_000
) : LibraryHttpClient {

    override fun fetchText(url: String): String? {
        val conn = URI.create(url).toURL().openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    override fun head(url: String): RemoteArtifactHead {
        val conn = URI.create(url).toURL().openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "HEAD"
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = connectTimeoutMs
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            if (code in 200..299) {
                val lm = conn.lastModified.takeIf { it > 0L }
                val len = conn.contentLengthLong.takeIf { it >= 0L }
                val etag = conn.getHeaderField("ETag")?.trim()?.takeIf { it.isNotEmpty() }
                RemoteArtifactHead(exists = true, lastModifiedMs = lm, contentLength = len, etag = etag)
            } else {
                RemoteArtifactHead(exists = false)
            }
        } catch (_: Exception) {
            RemoteArtifactHead(exists = false)
        } finally {
            conn.disconnect()
        }
    }

    override fun downloadToFile(url: String, destination: File) {
        val conn = URI.create(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code downloading library from $url")
            }
            destination.parentFile?.mkdirs()
            conn.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * Verifies required libraries before a Reveila component/plugin starts:
 * 1. Checks whether each required library already exists in `${system.home}/libs/`.
 * 2. Compares installed `version`, `publishedAt` / `Last-Modified`, and `sha256` against the
 *    download server (`download.base.url`, via `/libs/manifest.json` or HTTP `HEAD` fallback).
 * 3. Atomically downloads missing or outdated libraries and records installed metadata in
 *    `${libsDir}/.installed-libs.json`.
 */
class LibraryProvisioner(
    private val libsDir: File,
    private val downloadBaseUrl: String?,
    private val httpClient: LibraryHttpClient = JvmLibraryHttpClient(),
    private val logger: PlatformLogger? = null
) {

    private data class InstalledRecord(
        val name: String,
        val version: String? = null,
        val publishedAtMs: Long? = null,
        val sha256: String? = null,
        val etag: String? = null
    )

    private val metadataFile = File(libsDir, ".installed-libs.json")
    private var cachedRemoteManifest: Map<String, LibraryDescriptor>? = null
    private var manifestFetched: Boolean = false

    fun ensureLibraries(metaObject: MetaObject): LibraryProvisionReport {
        val descriptors = metaObject.getRequiredLibraries().mapNotNull { LibraryDescriptor.fromMap(it) }
        return ensureLibraries(descriptors)
    }

    fun ensureLibraries(requirements: List<LibraryDescriptor>): LibraryProvisionReport {
        if (requirements.isEmpty()) {
            return LibraryProvisionReport(emptyList(), emptyList(), emptyList())
        }

        libsDir.mkdirs()
        val installed = loadInstalledRecords().toMutableMap()
        val remoteManifest = fetchRemoteManifestOnce()

        val resolved = ArrayList<File>()
        val downloaded = ArrayList<File>()
        val skipped = ArrayList<File>()

        for (req in requirements) {
            val safeName = sanitizeFilename(req.name)
            val targetFile = File(libsDir, safeName)
            val manifestEntry = remoteManifest[safeName.lowercase()]
            val effectiveReq = mergeDescriptor(req, manifestEntry)
            val localRec = installed[safeName.lowercase()]

            val downloadUrl = resolveDownloadUrl(safeName, effectiveReq)
            val headMeta = if (downloadUrl != null &&
                (manifestEntry == null || (effectiveReq.version == null && effectiveReq.publishedAt == null && effectiveReq.sha256 == null))
            ) {
                httpClient.head(downloadUrl)
            } else {
                null
            }

            val decision = evaluateDownloadNeed(
                targetFile = targetFile,
                localRecord = localRec,
                effectiveReq = effectiveReq,
                headMeta = headMeta
            )

            if (decision.shouldDownload) {
                if (downloadUrl == null) {
                    if (targetFile.exists()) {
                        logger?.warning("Library '$safeName' exists locally; download.base.url is not configured so skipping update check.")
                        resolved.add(targetFile)
                        skipped.add(targetFile)
                        continue
                    }
                    throw IllegalStateException(
                        "Required library '$safeName' is missing from ${libsDir.absolutePath} and 'download.base.url' is not configured."
                    )
                }

                logger?.info("Downloading required library '$safeName' (${decision.reason}) from $downloadUrl")
                val partFile = File(libsDir, "$safeName.part")
                try {
                    httpClient.downloadToFile(downloadUrl, partFile)
                    val computedSha = computeSha256(partFile)
                    if (!effectiveReq.sha256.isNullOrBlank() &&
                        !computedSha.equals(effectiveReq.sha256.trim(), ignoreCase = true)
                    ) {
                        partFile.delete()
                        throw IllegalStateException(
                            "SHA-256 mismatch for library '$safeName': expected ${effectiveReq.sha256}, got $computedSha"
                        )
                    }
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    if (!partFile.renameTo(targetFile)) {
                        partFile.copyTo(targetFile, overwrite = true)
                        partFile.delete()
                    }

                    val remotePublishedMs = parseTimestampMs(effectiveReq.publishedAt)
                        ?: headMeta?.lastModifiedMs
                        ?: targetFile.lastModified()
                    if (remotePublishedMs > 0L) {
                        targetFile.setLastModified(remotePublishedMs)
                    }

                    installed[safeName.lowercase()] = InstalledRecord(
                        name = safeName,
                        version = effectiveReq.version,
                        publishedAtMs = remotePublishedMs,
                        sha256 = computedSha,
                        etag = headMeta?.etag
                    )
                    saveInstalledRecords(installed)
                    resolved.add(targetFile)
                    downloaded.add(targetFile)
                } catch (e: Exception) {
                    partFile.delete()
                    if (targetFile.exists() && decision.allowExistingOnDownloadFailure) {
                        logger?.warning("Failed to update library '$safeName' (${e.message}); continuing with existing local copy.")
                        resolved.add(targetFile)
                        skipped.add(targetFile)
                    } else {
                        throw e
                    }
                }
            } else {
                logger?.info("Library '$safeName' is up-to-date (${decision.reason}).")
                resolved.add(targetFile)
                skipped.add(targetFile)
            }
        }

        return LibraryProvisionReport(
            resolvedFiles = resolved,
            downloadedFiles = downloaded,
            skippedUpToDateFiles = skipped
        )
    }

    /**
     * Builds a child [URLClassLoader] containing the resolved JAR files so newly downloaded
     * libraries can be loaded at runtime without restarting the JVM.
     */
    fun buildClassLoader(parent: ClassLoader, files: List<File>): ClassLoader? {
        val jars = files.filter { it.exists() && it.isFile }
        if (jars.isEmpty()) return null
        val urls = jars.map { it.toURI().toURL() }.toTypedArray()
        return URLClassLoader(urls, parent)
    }

    private data class DownloadDecision(
        val shouldDownload: Boolean,
        val reason: String,
        val allowExistingOnDownloadFailure: Boolean = false
    )

    private fun evaluateDownloadNeed(
        targetFile: File,
        localRecord: InstalledRecord?,
        effectiveReq: LibraryDescriptor,
        headMeta: RemoteArtifactHead?
    ): DownloadDecision {
        if (!targetFile.exists() || targetFile.length() == 0L) {
            return DownloadDecision(shouldDownload = true, reason = "missing locally", allowExistingOnDownloadFailure = false)
        }

        // 1. Compare semantic version when remote/profile declares one
        val remoteVersion = effectiveReq.version
        val localVersion = localRecord?.version
        if (!remoteVersion.isNullOrBlank()) {
            if (localVersion.isNullOrBlank()) {
                return DownloadDecision(
                    shouldDownload = true,
                    reason = "local version unknown, remote version=$remoteVersion",
                    allowExistingOnDownloadFailure = true
                )
            }
            if (compareVersions(remoteVersion, localVersion) > 0) {
                return DownloadDecision(
                    shouldDownload = true,
                    reason = "newer version available ($localVersion -> $remoteVersion)",
                    allowExistingOnDownloadFailure = true
                )
            }
        }

        // 2. Compare publish date / Last-Modified timestamp
        val remotePublishedMs = parseTimestampMs(effectiveReq.publishedAt) ?: headMeta?.lastModifiedMs
        val localPublishedMs = localRecord?.publishedAtMs ?: targetFile.lastModified()
        if (remotePublishedMs != null && remotePublishedMs > localPublishedMs + 1000L) {
            return DownloadDecision(
                shouldDownload = true,
                reason = "newer publish date ($remotePublishedMs > $localPublishedMs)",
                allowExistingOnDownloadFailure = true
            )
        }

        // 3. Compare SHA-256 digest if declared
        val remoteSha = effectiveReq.sha256
        if (!remoteSha.isNullOrBlank()) {
            val localSha = localRecord?.sha256 ?: computeSha256(targetFile)
            if (!localSha.equals(remoteSha.trim(), ignoreCase = true)) {
                return DownloadDecision(
                    shouldDownload = true,
                    reason = "checksum changed",
                    allowExistingOnDownloadFailure = true
                )
            }
        }

        // 4. Compare ETag from HTTP HEAD fallback if available
        if (headMeta != null && headMeta.exists && !headMeta.etag.isNullOrBlank() &&
            !localRecord?.etag.isNullOrBlank() && headMeta.etag != localRecord?.etag
        ) {
            return DownloadDecision(
                shouldDownload = true,
                reason = "remote ETag changed",
                allowExistingOnDownloadFailure = true
            )
        }

        return DownloadDecision(shouldDownload = false, reason = "local copy matches version/publish date")
    }

    private fun fetchRemoteManifestOnce(): Map<String, LibraryDescriptor> {
        if (manifestFetched) return cachedRemoteManifest ?: emptyMap()
        manifestFetched = true
        val base = downloadBaseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: return emptyMap()
        val raw = httpClient.fetchText("$base/libs/manifest.json") ?: return emptyMap()
        val parsed = LinkedHashMap<String, LibraryDescriptor>()
        try {
            val root = JsonUtil.parseJsonStringToMap(raw)
            val entries = (root["libraries"] ?: root["libs"] ?: root["artifacts"]) as? List<*>
            entries?.forEach { item ->
                if (item is Map<*, *>) {
                    val m = LinkedHashMap<String, Any?>()
                    item.forEach { (k, v) -> if (k != null) m[k.toString()] = v }
                    LibraryDescriptor.fromMap(m)?.let { desc ->
                        parsed[sanitizeFilename(desc.name).lowercase()] = desc
                    }
                }
            }
        } catch (e: Exception) {
            logger?.warning("Failed to parse remote library manifest from $base/libs/manifest.json: ${e.message}")
        }
        cachedRemoteManifest = parsed
        return parsed
    }

    private fun mergeDescriptor(req: LibraryDescriptor, manifest: LibraryDescriptor?): LibraryDescriptor {
        if (manifest == null) return req
        return LibraryDescriptor(
            name = req.name,
            version = manifest.version ?: req.version,
            publishedAt = manifest.publishedAt ?: req.publishedAt,
            sha256 = manifest.sha256 ?: req.sha256,
            url = req.url ?: manifest.url
        )
    }

    private fun resolveDownloadUrl(safeName: String, desc: LibraryDescriptor): String? {
        if (!desc.url.isNullOrBlank()) {
            if (desc.url.startsWith("http://") || desc.url.startsWith("https://")) {
                return desc.url
            }
            val base = downloadBaseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: return null
            return "$base/${desc.url.trimStart('/')}"
        }
        val base = downloadBaseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: return null
        return "$base/libs/$safeName"
    }

    private fun loadInstalledRecords(): Map<String, InstalledRecord> {
        if (!metadataFile.exists()) return emptyMap()
        val result = LinkedHashMap<String, InstalledRecord>()
        try {
            val root = JsonUtil.parseJsonStringToMap(metadataFile.readText(Charsets.UTF_8))
            root.forEach { (key, value) ->
                if (value is Map<*, *>) {
                    val name = value["name"]?.toString() ?: key
                    result[key.lowercase()] = InstalledRecord(
                        name = name,
                        version = value["version"]?.toString()?.takeIf { it.isNotBlank() },
                        publishedAtMs = (value["publishedAtMs"] as? Number)?.toLong()
                            ?: value["publishedAtMs"]?.toString()?.toLongOrNull(),
                        sha256 = value["sha256"]?.toString()?.takeIf { it.isNotBlank() },
                        etag = value["etag"]?.toString()?.takeIf { it.isNotBlank() }
                    )
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun saveInstalledRecords(records: Map<String, InstalledRecord>) {
        try {
            val map = LinkedHashMap<String, Any?>()
            records.forEach { (k, rec) ->
                map[k] = linkedMapOf(
                    "name" to rec.name,
                    "version" to (rec.version ?: ""),
                    "publishedAtMs" to (rec.publishedAtMs ?: 0L),
                    "sha256" to (rec.sha256 ?: ""),
                    "etag" to (rec.etag ?: "")
                )
            }
            metadataFile.writeText(JsonUtil.toJsonString(map), Charsets.UTF_8)
        } catch (e: Exception) {
            logger?.warning("Failed to persist local library metadata: ${e.message}")
        }
    }

    private fun sanitizeFilename(raw: String): String {
        return raw.substringAfterLast('/').substringAfterLast('\\').trim()
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buf)
                if (read < 0) break
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        internal fun compareVersions(a: String, b: String): Int {
            val partsA = a.trim().removePrefix("v").split(".", "-", "_")
            val partsB = b.trim().removePrefix("v").split(".", "-", "_")
            val maxLen = maxOf(partsA.size, partsB.size)
            for (i in 0 until maxLen) {
                val segA = partsA.getOrNull(i) ?: "0"
                val segB = partsB.getOrNull(i) ?: "0"
                val numA = segA.toLongOrNull()
                val numB = segB.toLongOrNull()
                val cmp = if (numA != null && numB != null) {
                    numA.compareTo(numB)
                } else {
                    segA.compareTo(segB, ignoreCase = true)
                }
                if (cmp != 0) return cmp
            }
            return 0
        }

        internal fun parseTimestampMs(raw: String?): Long? {
            val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            trimmed.toLongOrNull()?.let { return it }
            runCatching { return Instant.parse(trimmed).toEpochMilli() }
            runCatching { return ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
            runCatching { return ZonedDateTime.parse(trimmed).toInstant().toEpochMilli() }
            return null
        }
    }
}
