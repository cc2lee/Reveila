package com.reveila.tools.download

import com.reveila.ai.ReveilaTool
import com.reveila.util.json.JsonUtil

/**
 * ReveilaTool wrapper exposing [ResumableDownloadManager] to autonomous agents and tool dispatchers.
 */
class ResumableDownloadTool(
    val manager: ResumableDownloadManager = ResumableDownloadManager()
) : ReveilaTool {

    override fun getName(): String = "resumable_download"

    override fun getDescription(): String = """
        Generic resumable file downloader with pre-download storage checks, pause/resume/cancel, SHA-256 verification, and temp folder housekeeping.
        Actions:
        - download: { "action": "download", "url": "...", "destinationPath": "...", "expectedSha256": "...", "expectedSizeBytes": 1234 }
        - pause: { "action": "pause", "downloadId": "..." }
        - resume: { "action": "resume", "downloadId": "..." }
        - cancel: { "action": "cancel", "downloadId": "..." }
        - status: { "action": "status", "downloadId": "..." }
        - check_storage: { "action": "check_storage", "destinationPath": "...", "requiredBytes": 1234 }
        - list_incomplete: { "action": "list_incomplete" }
        - cleanup_temp: { "action": "cleanup_temp", "maxAgeHours": 168 }
    """.trimIndent()

    override fun execute(args: Map<String, Any?>): String {
        val action = (args["action"] as? String)?.lowercase()?.trim() ?: "status"
        return try {
            when (action) {
                "download", "start" -> {
                    val url = (args["url"] as? String)?.trim()
                        ?: return "Error: 'url' is required."
                    val destinationPath = (args["destinationPath"] as? String)?.trim()
                        ?: return "Error: 'destinationPath' is required."
                    val downloadId = (args["downloadId"] as? String)?.trim().orEmpty()
                    val sha = (args["expectedSha256"] as? String)?.trim().orEmpty()
                    val expectedSize = (args["expectedSizeBytes"] as? Number)?.toLong() ?: -1L
                    val tempDir = (args["tempDir"] as? String)?.trim().orEmpty()
                    val handle = manager.download(
                        DownloadRequest(
                            downloadId = downloadId,
                            url = url,
                            destinationPath = destinationPath,
                            tempDir = tempDir,
                            expectedSha256 = sha,
                            expectedSizeBytes = expectedSize
                        )
                    )
                    JsonUtil.toJsonString(
                        mapOf(
                            "status" to handle.getStatus().name,
                            "downloadId" to handle.downloadId,
                            "destinationPath" to destinationPath
                        )
                    )
                }

                "pause" -> {
                    val id = (args["downloadId"] as? String)?.trim()
                        ?: return "Error: 'downloadId' is required."
                    val ok = manager.pause(id)
                    JsonUtil.toJsonString(mapOf("downloadId" to id, "paused" to ok))
                }

                "resume" -> {
                    val id = (args["downloadId"] as? String)?.trim()
                        ?: return "Error: 'downloadId' is required."
                    val handle = manager.resume(id)
                    JsonUtil.toJsonString(
                        mapOf(
                            "downloadId" to id,
                            "resumed" to (handle != null),
                            "status" to (handle?.getStatus()?.name ?: "NOT_FOUND")
                        )
                    )
                }

                "cancel" -> {
                    val id = (args["downloadId"] as? String)?.trim()
                        ?: return "Error: 'downloadId' is required."
                    val ok = manager.cancel(id)
                    JsonUtil.toJsonString(mapOf("downloadId" to id, "cancelled" to ok))
                }

                "status" -> {
                    val id = (args["downloadId"] as? String)?.trim()
                        ?: return "Error: 'downloadId' is required."
                    val p = manager.getProgress(id)
                        ?: return JsonUtil.toJsonString(mapOf("downloadId" to id, "status" to "NOT_FOUND"))
                    JsonUtil.toJsonString(
                        mapOf(
                            "downloadId" to p.downloadId,
                            "status" to p.status.name,
                            "percentage" to p.percentage,
                            "downloadedBytes" to p.downloadedBytes,
                            "totalBytes" to p.totalBytes,
                            "message" to p.message
                        )
                    )
                }

                "check_storage" -> {
                    val dest = (args["destinationPath"] as? String)?.trim()
                        ?: manager.resolveTempDirectory()
                    val required = (args["requiredBytes"] as? Number)?.toLong() ?: 0L
                    val avail = manager.checkAvailableStorage(dest)
                    val enough = manager.hasEnoughStorage(required, dest)
                    JsonUtil.toJsonString(
                        mapOf(
                            "path" to dest,
                            "availableBytes" to avail,
                            "requiredBytes" to required,
                            "hasEnoughStorage" to enough
                        )
                    )
                }

                "list_incomplete" -> {
                    val items = manager.listIncompleteDownloads().map {
                        mapOf(
                            "downloadId" to it.downloadId,
                            "url" to it.url,
                            "destinationPath" to it.destinationPath,
                            "tempFilePath" to it.tempFilePath,
                            "expectedSizeBytes" to it.expectedSizeBytes
                        )
                    }
                    JsonUtil.toJsonString(items)
                }

                "cleanup_temp" -> {
                    val maxAgeHours = (args["maxAgeHours"] as? Number)?.toLong()
                    val report = if (maxAgeHours != null && maxAgeHours >= 0L) {
                        manager.cleanupTempDirectory(maxAgeHours * 3600L * 1000L)
                    } else {
                        manager.runHousekeeping()
                    }
                    JsonUtil.toJsonString(
                        mapOf(
                            "scannedFiles" to report.scannedFiles,
                            "deletedFiles" to report.deletedFiles,
                            "reclaimedBytes" to report.reclaimedBytes,
                            "skippedActiveFiles" to report.skippedActiveFiles
                        )
                    )
                }

                else -> "Unsupported action '$action'."
            }
        } catch (e: Exception) {
            "Error executing resumable_download ($action): ${e.message}"
        }
    }
}
