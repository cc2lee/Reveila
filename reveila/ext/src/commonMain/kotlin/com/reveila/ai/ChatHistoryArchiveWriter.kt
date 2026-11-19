package com.reveila.ai

import com.reveila.system.Constants
import com.reveila.system.Properties
import com.reveila.system.concurrency.PlatformLock
import com.reveila.system.io.PlatformFileSystem
import com.reveila.system.platform.PlatformSystem
import com.reveila.util.json.JsonUtil

/**
 * Optional enterprise audit archive writer for compacted chat turns.
 *
 * When enabled via `logging.chat.archive.enabled=true` in `reveila.properties`,
 * turns evicted by sliding-window summarization are appended as JSONL records to
 * `<system.home>/logs/reveila-chat-archive.jsonl` with size-capped rotation
 * (`reveila-chat-archive.jsonl.1`, `.2`, ...) matching `reveila.log` conventions.
 *
 * When disabled (the default for mobile efficiency and privacy), zero files or I/O
 * are produced and compacted raw turns are discarded after summarization.
 */
object ChatHistoryArchiveWriter {

    const val ARCHIVE_FILE_NAME: String = "reveila-chat-archive.jsonl"
    const val DEFAULT_MAX_FILE_SIZE_BYTES: Long = 1_048_576L // 1 MB
    const val DEFAULT_FILE_COUNT: Int = 2

    private val lock = PlatformLock()

    fun isEnabled(properties: Properties?): Boolean {
        val raw = properties?.getProperty(Constants.LOG_CHAT_ARCHIVE_ENABLED)?.trim()
        return raw.equals("true", ignoreCase = true)
    }

    fun maxFileSizeBytes(properties: Properties?): Long {
        val raw = properties?.getProperty(Constants.LOG_CHAT_ARCHIVE_FILE_SIZE)?.trim()
        val parsed = raw?.toLongOrNull()
        return if (parsed != null && parsed > 0L) parsed else DEFAULT_MAX_FILE_SIZE_BYTES
    }

    fun maxFileCount(properties: Properties?): Int {
        val raw = properties?.getProperty(Constants.LOG_CHAT_ARCHIVE_FILE_COUNT)?.trim()
        val parsed = raw?.toIntOrNull()
        return if (parsed != null && parsed >= 1) parsed else DEFAULT_FILE_COUNT
    }

    fun resolveLogsDir(properties: Properties?, fs: PlatformFileSystem = PlatformFileSystem()): String {
        val home = properties?.getProperty(Constants.SYSTEM_HOME)?.trim()?.takeIf { it.isNotEmpty() }
            ?: properties?.getProperty("data.dir")?.trim()?.takeIf { it.isNotEmpty() }
            ?: fs.getDefaultSystemHome()
        return fs.resolve(home, "logs")
    }

    /**
     * Appends [compactedTurns] as single-line JSONL records to the size-capped audit archive
     * if `logging.chat.archive.enabled=true`.
     */
    fun archiveCompactedTurns(
        properties: Properties?,
        spaceId: String,
        spaceName: String,
        sessionId: String,
        compactedSummary: String,
        compactedTurns: List<ReveilaMessage>,
        fs: PlatformFileSystem = PlatformFileSystem()
    ): Boolean {
        if (!isEnabled(properties) || compactedTurns.isEmpty()) return false

        val maxFileBytes = maxFileSizeBytes(properties)
        val totalFileLimit = maxFileCount(properties)
        val logsDir = resolveLogsDir(properties, fs)
        val activePath = fs.resolve(logsDir, ARCHIVE_FILE_NAME)
        val archivedAt = PlatformSystem.currentTimeMillis()

        val lines = compactedTurns.map { msg ->
            val record = linkedMapOf<String, Any>(
                "archivedAt" to archivedAt,
                "spaceId" to spaceId,
                "spaceName" to spaceName,
                "sessionId" to sessionId,
                "role" to msg.role.name,
                "timestamp" to msg.timestamp,
                "content" to msg.content,
                "compactedIntoSummary" to compactedSummary
            )
            JsonUtil.toJsonString(record)
        }

        lock.withLock {
            if (!fs.exists(logsDir)) {
                fs.createDirectories(logsDir)
            }

            for (line in lines) {
                val entryBytes = "$line\n".encodeToByteArray()
                val currentSize = if (fs.exists(activePath)) fs.readBytes(activePath).size.toLong() else 0L
                if (currentSize > 0L && currentSize + entryBytes.size > maxFileBytes) {
                    rotateFiles(fs, logsDir, activePath, totalFileLimit)
                }
                val append = fs.exists(activePath)
                fs.writeBytes(activePath, entryBytes, append = append)
            }
        }
        return true
    }

    private fun rotateFiles(
        fs: PlatformFileSystem,
        logsDir: String,
        activePath: String,
        totalFileLimit: Int
    ) {
        val maxBackupIndex = totalFileLimit - 1
        if (maxBackupIndex <= 0) {
            fs.delete(activePath, recursive = false)
            return
        }

        val oldestBackup = fs.resolve(logsDir, "$ARCHIVE_FILE_NAME.$maxBackupIndex")
        if (fs.exists(oldestBackup)) {
            fs.delete(oldestBackup, recursive = false)
        }

        for (idx in (maxBackupIndex - 1) downTo 1) {
            val src = fs.resolve(logsDir, "$ARCHIVE_FILE_NAME.$idx")
            if (fs.exists(src)) {
                val dst = fs.resolve(logsDir, "$ARCHIVE_FILE_NAME.${idx + 1}")
                fs.writeBytes(dst, fs.readBytes(src), append = false)
                fs.delete(src, recursive = false)
            }
        }

        if (fs.exists(activePath)) {
            val firstBackup = fs.resolve(logsDir, "$ARCHIVE_FILE_NAME.1")
            fs.writeBytes(firstBackup, fs.readBytes(activePath), append = false)
            fs.delete(activePath, recursive = false)
        }
    }
}
